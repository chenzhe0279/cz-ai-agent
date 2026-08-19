package com.cz.czaiagent.agent;


import cn.hutool.core.collection.CollUtil;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.cz.czaiagent.agent.model.AgentState;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.tool.ToolCallback;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 处理工具调用的基础代理类，具体实现了 think 和 act 方法，可以用作创建实例的父类
 */
@EqualsAndHashCode(callSuper = true)
@Data
@Slf4j
public class ToolCallAgent extends ReActAgent{

    //可调用的工具
    private final ToolCallback[] availableTools;

    //保存了工具调用信息的响应反馈
    private ChatResponse toolCallChatResponse;

    //工具调用管理者
    private final ToolCallingManager toolCallingManager;

    // 禁用内置的工具调用机制，自己维护上下文
    /**
     * 聊天模型的配置选项 (ChatOptions)
     * <p>
     * 用于配置大语言模型（LLM）的生成参数，例如温度（temperature）、最大输出Token数（maxTokens）、
     * Top-P、停止词（stop sequences）等。
     * </p>
     * <p>
     * <b>设计意图：</b>
     * 由于本代理（ToolCallAgent）禁用了 Spring AI 框架内置的自动工具调用机制（Auto Tool Calling），
     * 转而采用 ReAct 模式手动维护对话上下文和工具调用状态。因此，需要显式地持有这些配置选项，
     * 以便在手动构建 Prompt 和调用底层 ChatModel 时，能够精确控制模型的生成行为，
     * 确保模型能够按照预期的格式输出思考过程（Thought）或工具调用指令（Action）。
     * </p>
     */
    private final ChatOptions chatOptions;

    /**
     * 构造函数
     * <p>
     * 说明：toolCallingManager 和 chatOptions 不需要通过参数传入，
     * 因为它们的实例化不依赖于外部状态或动态配置。
     * toolCallingManager 使用默认的构建器创建即可满足需求；
     * chatOptions 使用固定的配置（禁用 Spring AI 自动工具调用）来支持 ReAct 模式的手动上下文维护。
     * 因此它们可以直接在内部初始化，而 availableTools 因每个 Agent 实例所需的工具不同，必须通过参数传入。
     * </p>
     *
     * @param availableTools 可调用的工具集合
     */
    public ToolCallAgent(ToolCallback[] availableTools){
        super();
        this.availableTools = availableTools;
        
        // 使用默认配置构建工具调用管理者，无需外部传参
        this.toolCallingManager = ToolCallingManager.builder().build();
        
        // 禁用 Spring AI 内置的工具调用机制，自己维护选项和消息上下文，无需外部传参
        this.chatOptions = DashScopeChatOptions.builder()
                .withProxyToolCalls(true)
                .build();
    }

    /**
     * 处理当前状态并决定下一步行动
     *
     * @return 是否需要执行行动（true = 大模型要求调用工具，进入 act() 阶段；false = 大模型直接给出最终回答，循环结束）
     */
    @Override
    public boolean think() {
        log.info("Thinking...");
        // ========== 第一步：将"下一步提示词"追加到消息上下文中 ==========
        // getNextStepPrompt() 是 BaseAgent 中的属性，用于在每轮思考前
        // 动态注入额外的用户指令（例如 "请继续执行" 之类的引导语）
        if (getNextStepPrompt() != null && !getNextStepPrompt().isEmpty()) {
            // 将下一步提示词包装为 UserMessage（用户消息），追加到消息列表
            UserMessage userMessage = new UserMessage(getNextStepPrompt());
            getMessageList().add(userMessage);
        }

        // ========== 第二步：构建 Prompt 并发送给大模型 ==========
        // 获取当前完整的消息上下文（包含历史对话 + 刚追加的 nextStepPrompt）
        List<Message> messageList = getMessageList();

        // 用消息列表 + chatOptions 构建 Prompt 对象
        // chatOptions 中设置了 proxyToolCalls=true，即禁用 Spring AI 的自动工具调用，
        // 让框架只返回工具调用意图，由我们自己来执行工具
        Prompt prompt = new Prompt(messageList, chatOptions);

        try {
        // 调用大模型，关键链式调用说明：·
        //   .prompt(prompt)       —— 传入构建好的 Prompt（消息列表 + 选项）
        //   .system(...)          —— 设置系统提示词（定义 Agent 的角色和行为规范）
        //   .tools(availableTools)—— 告知大模型当前可用的工具列表
        //                          大模型会从中选择需要调用的工具
        //   .call()               —— 发起同步调用
        //   .chatResponse()       —— 获取完整的 ChatResponse（包含原始响应元数据）
        ChatResponse chatResponse = getChatClient().prompt(prompt)
                .system(getSystemPrompt())
                .tools(availableTools)
                .call()
                .chatResponse();

        // 将响应保存到实例变量，供后续 act() 阶段使用
        // act() 需要从这里取出工具调用信息来执行具体的工具
        this.toolCallChatResponse = chatResponse;

        // ========== 第三步：解析大模型的响应 ==========
        // 从响应中提取助手消息（即大模型的回复）
        AssistantMessage assistantMessage = chatResponse.getResult().getOutput();
        // 获取大模型返回的文本内容（可能是最终回答，也可能是"我要调用XX工具"的说明）
        String result = assistantMessage.getText();
        // 获取大模型决定要调用的工具列表
        // 如果大模型认为不需要工具，这个列表为空
        List<AssistantMessage.ToolCall> toolCallList = assistantMessage.getToolCalls();

        // ========== 第四步：日志输出 ==========
        log.info(getName() + "的思考: " + result);
        log.info(getName() + "选择了 " + toolCallList.size() + " 个工具来使用");

        // 将每个工具调用的名称和参数拼接成可读的字符串，用于日志输出
        String toolCallInfo = toolCallList.stream()
                .map(toolCall -> String.format("工具名称：%s，参数：%s",
                        toolCall.name(),        // 工具名称，如 "searchImage"
                        toolCall.arguments())   // 工具参数，如 {"query": "星空壁纸"}（JSON 字符串）
                )
                .collect(Collectors.joining("\n"));  // 多个工具调用之间用换行分隔
        log.info(toolCallInfo);
        // ========== 第五步：根据是否有工具调用，决定返回值 ==========
        if (toolCallList.isEmpty()) {
            // 情况 A：大模型没有选择任何工具 → 说明它已经给出了最终回答
            // 此时将助手消息记录到消息上下文中（保持对话历史完整）
            getMessageList().add(assistantMessage);
            // 返回 false → ReActAgent.step() 中判断为"无需行动"，循环结束
            return false;
        } else {
            // 情况 B：大模型选择了工具 → 需要进入 act() 阶段执行工具
            // 注意：这里不记录助手消息，因为 act() 执行工具后，
            // 工具调用请求和工具执行结果会一起被记录到消息上下文中，
            // 避免重复记录
            return true;
        }} catch (Exception e) {
            // ========== 异常处理 ==========
            // 调用大模型过程中出错（网络超时、API 限流、模型返回异常等）
            log.error(getName() + "的思考过程遇到了问题: " + e.getMessage());

            // 将错误信息包装为 AssistantMessage 追加到消息上下文
            // 这样 Agent 不会因一次错误而完全中断，错误信息会作为对话历史保留
            getMessageList().add(
                    new AssistantMessage("处理时遇到错误: " + e.getMessage()));
            // 返回 false → 终止当前循环，不再进入 act() 阶段
            return false;
        }
    }

    /**
     * 执行工具调用并处理结果
     * 这是 ReAct 循环中"行动"阶段的具体实现，在 think() 返回 true（即大模型决定要调用工具）之后被调用
     *
     * @return 执行结果（所有工具执行结果的拼接字符串）
     */
    @Override
    public String act() {
        // ========== 第一步：防御性校验 ==========
        // 检查 think() 阶段保存的响应中是否真的包含工具调用请求
        // 理论上走到 act() 时一定有工具调用（因为 think() 返回 true 才进来），
        // 但这里做防御性判断，避免意外情况导致空指针
        if (!toolCallChatResponse.hasToolCalls()) {
            return "没有工具调用";
        }

        // ========== 第二步：执行工具调用 ==========
        // 用当前消息上下文 + chatOptions 重新构建 Prompt
        // 这个 Prompt 会作为上下文信息传递给 ToolCallingManager，
        // 让它知道当前对话的完整状态
        Prompt prompt = new Prompt(getMessageList(), chatOptions);

        // 调用 ToolCallingManager 执行工具，这是核心步骤：
        //   - prompt：当前对话上下文
        //   - toolCallChatResponse：think() 阶段大模型返回的响应，其中包含了
        //     大模型决定要调用的工具列表及每个工具的参数
        ToolExecutionResult toolExecutionResult = toolCallingManager.executeToolCalls(prompt, toolCallChatResponse);

        // ========== 第三步：更新消息上下文 ==========
        // conversationHistory() 返回的是工具执行后的完整对话历史，它包含了：
        //   - 之前的所有历史消息（用户消息、助手消息等）
        //   - 本次助手的工具调用请求消息（AssistantMessage，包含要调用哪些工具）
        //   - 工具执行完成后的返回结果消息（ToolResponseMessage，包含每个工具的执行结果）
        // 用这个完整历史替换原来的 messageList，确保下一轮 think() 能看到最新的对话状态
        setMessageList(toolExecutionResult.conversationHistory());

        // ========== 第四步：提取并格式化本次工具执行结果 ==========
        // 从更新后的对话历史中取出最后一条消息
        // 这条消息一定是 ToolResponseMessage（工具响应消息），
        // 因为它是在 executeToolCalls 执行完后追加到对话历史末尾的
        // CollUtil.getLast() 是 Hutool 工具类的方法，等价于 list.get(list.size() - 1)
        ToolResponseMessage toolResponseMessage = (ToolResponseMessage) CollUtil.getLast(toolExecutionResult.conversationHistory());

        // 从 ToolResponseMessage 中提取每个工具的响应，格式化为可读字符串
        // getResponses() 返回的是 List<ToolResponseMessage.ToolResponse>，
        // 每个 ToolResponse 包含：
        //   - name()：工具名称
        //   - responseData()：工具返回的执行结果（通常是字符串或 JSON）
        String results = toolResponseMessage.getResponses().stream()
                .map(response -> "工具 " + response.name() + " 完成了它的任务！结果: " + response.responseData())
                .collect(Collectors.joining("\n"));  // 多个工具结果之间用换行分隔
        //判断是否调用了终止工具
        boolean terminateToolCalled = toolResponseMessage.getResponses().stream()
                .anyMatch(response -> "doTerminate".equals(response.name()));
        if (terminateToolCalled) {
            setState(AgentState.FINISHED);
        }
        // 将工具执行结果输出到日志
        log.info(results);
        // 返回结果给调用方（ReActAgent.step() → BaseAgent.run()），
        // 最终会被拼接到 "Step N: xxx" 格式中作为本轮循环的输出
        return results;
    }
}

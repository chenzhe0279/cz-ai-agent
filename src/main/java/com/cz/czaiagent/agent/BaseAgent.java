package com.cz.czaiagent.agent;

import com.cz.czaiagent.agent.model.AgentState;

import com.cz.czaiagent.service.HumanInteractionService;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import org.jsoup.internal.StringUtil;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * 抽象基础代理类，用于管理代理状态和执行流程。
 *
 * 提供状态转换、内存管理和基于步骤的执行循环的基础功能。
 * 子类必须实现step方法。
 */
@Data
@Slf4j
public abstract class BaseAgent {

    // 核心属性
    private String name;

    //系统预设提示词
    private String systemPrompt;

    //下一步提示词
    private String nextStepPrompt;

    //智能体初始状态
    private AgentState state = AgentState.IDLE;

    //执行控制，智能体最大步数和当前步数
    private int maxSteps = 10;
    private int currentStep = 0;

    // ========== 循环检测与处理机制相关属性 ==========

    //循环检测：相同内容在助手消息中重复出现的次数阈值（不含最后一条本身），达到即判定陷入循环
    //默认值 2 表示：最后一条助手消息的内容，在此前的助手消息中至少再出现 2 次（即同一内容共出现 3 次）才判定为循环
    //该值越小检测越敏感；@Data 会自动生成 getter/setter，子类（如 CzManus）可按需调整灵敏度
    private int duplicateThreshold = 2;

    //检测到循环时注入的干预提示词
    //声明为 static final 常量：所有智能体实例共享同一份文案，且运行期不可修改
    //其作用是提醒大模型"你正在重复同样的输出"，引导它放弃已尝试过的无效路径、改用新策略
    private static final String STUCK_PROMPT = "观察到重复响应。请考虑采用新的策略，避免重复已尝试过的无效路径。";

    //注入干预提示词前的原始下一步提示词，用于 cleanup 时恢复，避免污染下一轮对话
    //背景：CzManus 是单例 Bean，若 run() 结束后 nextStepPrompt 中残留干预提示词，
    //下一次 run() 会在第一轮思考时就把它发给大模型，因此必须先备份、结束后还原
    private String originalNextStepPrompt;

    //是否已注入过干预提示词，防止持续 stuck 时提示词无限膨胀
    //若不加此标记，每一步检测到循环都会再往 nextStepPrompt 前面拼一次 STUCK_PROMPT，
    //导致上下文越来越长、浪费 token 甚至撑爆模型上下文窗口
    private boolean stuckPromptInjected = false;

    //大模型(LLM)
    private ChatClient chatClient;

    // Memory（需要自主维护会话上下文）
    private List<Message> messageList = new ArrayList<>();

    private HumanInteractionService humanInteractionService;

    /**
     * SSE (Server-Sent Events) 推送器实例。
     * <p>
     * 设计意图与上下文说明：
     * 1. 核心作用：用于在智能体流式运行期间（如 runStream 模式），向客户端实时推送自定义事件（例如工具调用状态、人工交互请求等）。
     * 2. transient 关键字：BaseAgent 及其子类可能需要参与序列化（如存入 Redis 或 HttpSession），而 SseEmitter 封装了底层的 HTTP 响应流，
     *    属于不可序列化的网络资源。使用 transient 修饰可避免序列化时抛出 NotSerializableException。
     * 3. 封装性：声明为 private，外部及子类必须通过下方的 sendSseEvent 方法进行推送，确保推送逻辑的统一管理和异常隔离。
     */
    private transient SseEmitter streamEmitter;

    /**
     * 向客户端发送 SSE (Server-Sent Events) 事件。
     * <p>
     * 详细说明：
     * - 访问控制为 protected：允许子类（如具体的 Agent 实现、自定义工具类）在需要时调用，向客户端推送业务事件。
     * - 事件规范：遵循 W3C SSE 标准，通过 event 字段区分事件类型，data 字段承载具体数据。前端可通过 EventSource 监听特定事件名。
     *
     * @param eventName 事件名称（如 "tool_call", "ask_human", "thinking" 等），用于前端分类处理
     * @param content   事件携带的数据内容，通常为 JSON 序列化后的字符串或纯文本
     */
    protected void sendSseEvent(String eventName, String content) {
        // 1. 前置防御性校验：
        // - 若 streamEmitter 为 null（例如在非流式的 run() 同步模式下，或未正确绑定会话），则跳过推送。
        // - 若 content 为 null 或空白字符串，推送无意义且可能引发前端解析异常，直接拦截。
        if (streamEmitter == null || content == null || content.isBlank()) {
            return;
        }

        try {
            // 2. 构建并执行 SSE 推送：
            // 利用 Spring 的 SseEmitter.event() 链式调用，精确指定事件名 (name) 和负载数据 (data)。
            streamEmitter.send(
                    SseEmitter.event()
                            .name(eventName)
                            .data(content)
            );
        } catch (IOException e) {
            // 3. 异常捕获与降级处理：
            // 当客户端主动断开连接、网络波动或服务器超时导致写入失败时，底层会抛出 IOException。
            // 此处仅记录 warn 级别日志（保留完整异常堆栈以便排查），绝不向上抛出异常。
            // 设计考量：SSE 推送属于"旁路"的交互/反馈功能，不应因为推送失败而中断智能体核心的思考与执行主流程。
            log.warn("SSE 推送事件失败，event={}", eventName, e);
        }
    }

    /**
     * 安全推送普通文本事件：
     * - 连接正常：返回 true；
     * - 客户端已断开或 emitter 已完成（用户停止/刷新/关闭页面、流已被结束）：
     *   记录 warn 并返回 false，调用方应提前结束循环，避免 IllegalStateException 打断收尾。
     */
    private boolean trySendEmitter(SseEmitter emitter, String content) {
        try {
            emitter.send(content);
            return true;
        } catch (IOException e) {
            log.warn("SSE 推送失败（连接可能已断开），提前结束智能体循环: {}", e.getMessage());
            return false;
        } catch (IllegalStateException e) {
            log.warn("SSE 推送失败（emitter 已完成，客户端可能已停止/关闭），提前结束智能体循环");
            return false;
        }
    }

    /**
     * 安全关闭 SSE 连接：emitter 已被容器/客户端完成时忽略，避免再次抛 IllegalStateException。
     */
    private void completeEmitterQuietly(SseEmitter emitter) {
        try {
            emitter.complete();
        } catch (IllegalStateException e) {
            log.debug("emitter 已完成，跳过 complete()");
        }
    }
    /**
     * 运行代理
     *
     * @param userPrompt 用户提示词
     * @return 执行结果
     */
    public String run(String userPrompt){
        //智能体状态判断
        if(this.state != AgentState.IDLE){
            throw new RuntimeException("Cannot run agent from state: " + this.state);
        }
        //用户输入提示词状态判断
        if(StringUtil.isBlank(userPrompt)){
            throw new RuntimeException("Cannot run agent with empty user prompt");
        }
        //以上判断都通过，表示agent开始运行，需修改状态
        state = AgentState.RUNNING;
        //把用户提示词记录到消息上下文
        messageList.add(new UserMessage(userPrompt));
        //定义一个保存agent运行的结果列表
        List<String> results = new ArrayList<>();
        //开始运行
        try {
            for (int i = 0; i < maxSteps && state != AgentState.FINISHED; i++) {
                int stepNumber = i + 1;
                //设置当前步数
                currentStep = stepNumber;
                log.info("Executing step " + stepNumber + "/" + maxSteps);
                //单步执行
                String stepResult = step();
                //每一步执行完检查是否陷入循环，若陷入则注入干预提示词引导大模型更换策略
                //检测时机说明：放在 step() 之后，此时本步产生的助手消息/工具响应已写入 messageList，
                //能基于最新上下文判断；注入的干预提示词将在下一步 think() 中生效
                if (isStuck()) {
                    handleStuckState();
                }
                String result = "Step " + stepNumber + ": " + stepResult;
                results.add(result);
            }
            //检查是否超出了最大步数限制
            if(currentStep >= maxSteps){
                state = AgentState.FINISHED;
                results.add("Terminated: Reached max steps (" + maxSteps + ")");
            }
            //返回结果
            return String.join("\n", results);
        } catch (Exception e) {
            //如果发生异常，修改状态为ERROR
            state = AgentState.ERROR;
            log.error("Error executing agent", e);
            return "执行错误" + e.getMessage();
        }finally {
            // 清理资源
            this.cleanup();
        }
    }


    /**
     * 运行代理（流式输出）
     *
     * @param userPrompt 用户提示词
     * @return 执行结果
     */
    public SseEmitter runStream(String userPrompt){
        // 创建 SseEmitter 实例，设置超时时间为 600000 毫秒（10分钟），防止长时间无数据推送导致连接超时断开
        SseEmitter emitter = new SseEmitter(600000L);

        // 使用 CompletableFuture.runAsync 将智能体的执行逻辑放入异步线程中运行，避免阻塞当前 HTTP 请求主线程
        CompletableFuture.runAsync(() -> {
            // 使用 try-catch 块捕获前置校验过程中可能抛出的异常，确保 SSE 连接能被正确关闭
            try {
                // 校验智能体当前状态，只有处于 IDLE（空闲）状态才能启动运行
                if (this.state != AgentState.IDLE) {
                    // 若状态不正确，通过 SSE 向前端发送错误提示信息
                    emitter.send("错误：无法从状态运行代理: " + this.state);
                    // 正常完成 SSE 连接，通知客户端流已结束
                    emitter.complete();
                    // 终止当前异步线程的后续执行
                    return;
                }
                // 校验用户输入的提示词是否为空（使用 Jsoup 的 StringUtil 工具类判断）
                if (StringUtil.isBlank(userPrompt)) {
                    // 若提示词为空，通过 SSE 向前端发送错误提示信息
                    emitter.send("错误：不能使用空提示词运行代理");
                    // 正常完成 SSE 连接
                    emitter.complete();
                    // 终止当前异步线程的后续执行
                    return;
                }
            } catch (Exception e) {
                // 若前置校验或发送消息时发生异常，以错误状态关闭 SSE 连接，并将异常信息传递给客户端
                emitter.completeWithError(e);
                // 发生异常后直接返回，不再执行后续的智能体运行逻辑
                return;
            }

            // 前置校验全部通过，将智能体状态修改为 RUNNING（运行中）
            state = AgentState.RUNNING;
            // 绑定当前 Agent 异步线程与 SSE 会话，供 askHuman 工具发送事件。
            humanInteractionService.openSession(emitter);
            // 将用户的输入提示词封装为 UserMessage 并添加到消息上下文列表中，作为大模型的首轮输入
            messageList.add(new UserMessage(userPrompt));

            // 开始智能体的多步骤循环执行逻辑
            try {
                // 循环执行，条件为：当前步数未达到最大步数限制，且智能体状态未变为 FINISHED（已完成）
                for (int i = 0; i < maxSteps && state != AgentState.FINISHED; i++) {
                    // 计算当前是第几步（从 1 开始计数，便于日志和前端展示）
                    int stepNumber = i + 1;
                    // 更新智能体的当前执行步数属性
                    currentStep = stepNumber;
                    // 记录当前正在执行的步骤日志
                    log.info("Executing step " + stepNumber + "/" + maxSteps);
                    
                    // 调用子类实现的具体单步执行逻辑（如思考、调用工具等），获取本步的执行结果
                    String stepResult = step();
                    
                    // 每一步执行完毕后，检查智能体是否陷入了重复输出的死循环
                    // 此时本步产生的消息已写入 messageList，能基于最新上下文进行准确判断
                    if (isStuck()) {
                        // 若检测到陷入循环，则调用处理方法注入干预提示词，引导大模型更换策略
                        handleStuckState();
                    }
                    
                    // 将步骤序号和单步执行结果拼接成格式化的字符串，用于向前端展示
                    String result = "Step " + stepNumber + ": " + stepResult;
                    
                    // （已废弃）原本用于保存所有步骤结果的列表，流式输出下无需在内存中累积
                    // List<String> results = new ArrayList<>();

                    // 通过 SSE 将当前步骤的执行结果实时推送给客户端
                    if (!trySendEmitter(emitter, result)) {
                        // 客户端已断开：继续执行没有意义，提前结束循环，避免收尾报错
                        break;
                    }
                }
                
                // 循环结束后，检查是否是因为达到了最大步数限制而退出循环
                if (currentStep >= maxSteps) {
                    // 若是达到最大步数，将智能体状态强制修改为 FINISHED
                    state = AgentState.FINISHED;
                    // 通过 SSE 向前端推送达到最大步数的提示信息
                    trySendEmitter(emitter, "执行结束: 达到最大步骤 (" + maxSteps + ")");
                }
                
                // 所有步骤正常执行完毕，正常关闭 SSE 连接，通知客户端流式数据已全部发送完成
                completeEmitterQuietly(emitter);
            } catch (Exception e) {
                // 若在执行过程中发生任何异常，将智能体状态修改为 ERROR（错误）
                state = AgentState.ERROR;
                // 记录详细的错误日志，便于后续排查问题
                log.error("执行智能体失败", e);
                try {
                    // 尝试通过 SSE 向前端推送具体的错误信息
                    emitter.send("执行错误: " + e.getMessage());
                    // 推送完成后，正常关闭 SSE 连接
                    emitter.complete();
                } catch (Exception ex) {
                    // 若在推送错误信息或关闭连接时再次发生异常（例如连接已断开），则以错误状态强制关闭连接
                    emitter.completeWithError(ex);
                }
            } finally {
                humanInteractionService.closeCurrentSession();
                // 无论执行成功还是失败，最终都必须执行清理逻辑，释放资源并重置智能体状态
                this.cleanup();
            }
        });
        
        // 立即返回 SseEmitter 对象给 Spring MVC 框架，由框架负责维持 HTTP 长连接并推送数据
        return emitter;
    }

    /**
     * 执行单个步骤
     *
     * @return 步骤执行结果
     */
    public abstract String step();

    /**
     * 检查智能体是否陷入循环
     * 从消息上下文末尾向前定位最后一条内容非空的助手（ASSISTANT）消息，
     * 统计其内容在更早的助手消息中重复出现的次数，达到阈值即判定为陷入循环。
     * 说明：工具执行后末尾可能是 ToolResponseMessage，因此不能直接取最后一条消息判断，
     * 需要向前找到最后一条助手消息，这样"重复最终回答"和"重复工具调用"两种循环都能检出。
     *
     * @return 是否陷入循环
     */
    protected boolean isStuck() {
        //取出当前消息上下文（即智能体的"记忆"），后续所有重复性判断都基于它
        List<Message> messages = messageList;
        //消息总数不足 2 条时不可能构成"重复"（至少需要一条参照 + 一条重复），直接返回未陷入循环
        if (messages.size() < 2) {
            return false;
        }

        //从末尾向前查找最后一条内容非空的助手消息
        //-1 是"未找到"的哨兵值
        int lastAssistantIndex = -1;
        //倒序遍历：从最新消息往回找，遇到的第一条满足条件的即为"最后一条助手消息"
        for (int i = messages.size() - 1; i >= 0; i--) {
            Message msg = messages.get(i);
            //两个筛选条件：
            //1. 消息类型必须是 ASSISTANT（大模型的回复），用户消息和系统提示词不参与重复统计
            //2. 文本内容必须非空——携带工具调用请求的助手消息文本常为 null/空串，无比较意义
            if (msg.getMessageType() == MessageType.ASSISTANT
                    && msg.getText() != null && !msg.getText().isEmpty()) {
                //记录下标后立即跳出循环，保证只取"最后一条"
                lastAssistantIndex = i;
                break;
            }
        }
        //整个上下文中都找不到有内容的助手消息，说明还没有可供比较的输出，不算循环
        //（例如刚执行完工具、末尾只有 ToolResponseMessage 且此前的助手消息都是空文本的情况）
        if (lastAssistantIndex == -1) {
            return false;
        }

        //计算相同内容在更早的助手消息中出现的次数
        //取出最后一条助手消息的文本，作为重复比对的基准内容
        String lastContent = messages.get(lastAssistantIndex).getText();
        //重复计数器：统计基准内容在历史助手消息中出现的次数
        int duplicateCount = 0;
        //从基准消息的前一条开始继续倒序遍历，只与"更早"的消息比较，避免自己和自己比较
        for (int i = lastAssistantIndex - 1; i >= 0; i--) {
            Message msg = messages.get(i);
            //同样是只看助手消息；用 equals 做全等比较，内容完全一致才算一次重复
            //（lastContent 已保证非空，调用 equals 不会空指针）
            if (msg.getMessageType() == MessageType.ASSISTANT
                    && lastContent.equals(msg.getText())) {
                duplicateCount++;
            }
        }

        //重复次数达到阈值即判定陷入循环
        //例：duplicateThreshold=2 时，同一内容累计出现 3 次（历史 2 次 + 最新 1 次）触发
        return duplicateCount >= duplicateThreshold;
    }

    /**
     * 处理陷入循环的状态
     * 将干预提示词前置注入 nextStepPrompt，下一轮 think() 会把它作为 UserMessage
     * 发给大模型，引导其放弃重复路径、尝试新策略
     */
    protected void handleStuckState() {
        //已注入过则跳过，避免持续 stuck 时提示词无限膨胀
        //只记录日志提示当前仍处于循环中，不再修改 nextStepPrompt
        if (stuckPromptInjected) {
            log.error(name + " 仍处于循环状态（干预提示词已注入，跳过重复注入）");
            return;
        }
        //备份原始提示词，供 cleanup() 恢复
        //必须在覆盖 nextStepPrompt 之前执行，否则原始内容将永久丢失
        originalNextStepPrompt = nextStepPrompt;
        //把干预提示词拼接到 nextStepPrompt 最前面（原有内容跟在后面，可能为 null 时用空串兜底）
        //前置的原因：让大模型在读到本轮引导语时，第一眼看到的是"换策略"的指令，权重更高
        //注入后无需立即发送给模型——下一轮 think() 开头会自动把 nextStepPrompt 包装成
        //UserMessage 追加进 messageList，随 Prompt 一起发给大模型
        nextStepPrompt = STUCK_PROMPT + "\n" + (nextStepPrompt != null ? nextStepPrompt : "");
        //置位注入标记，后续步骤即使再次检测到循环也不会重复拼接
        stuckPromptInjected = true;
        log.error(name + " 检测到循环状态，已注入干预提示词: " + STUCK_PROMPT);
    }

    /**
     * 清理资源
     * 在 run() 的 finally 中调用，无论正常结束还是异常都会执行：
     * 1. 清空消息上下文，释放本轮对话占用的内存
     * 2. 重置状态为 IDLE、步数归零，使单例智能体可以被再次 run()
     * 3. 恢复原始的下一步提示词，避免循环干预提示词污染下一轮对话
     */
    protected void cleanup() {
        messageList.clear();
        currentStep = 0;
        state = AgentState.IDLE;
        //仅在本轮确实注入过干预提示词时才执行恢复逻辑，未触发循环时保持 nextStepPrompt 原样
        if (stuckPromptInjected) {
            //用备份的原始提示词覆盖掉含干预内容的 nextStepPrompt，保证单例智能体下次 run() 干净启动
            nextStepPrompt = originalNextStepPrompt;
            //备份字段置空，释放引用、避免陈旧数据干扰下一轮
            originalNextStepPrompt = null;
            //复位注入标记，使下一轮对话重新具备"检测-注入"能力
            stuckPromptInjected = false;
        }
        log.info(name + " 资源清理完成，状态已重置为 IDLE");
    }
}

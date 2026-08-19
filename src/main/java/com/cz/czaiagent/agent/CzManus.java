package com.cz.czaiagent.agent;


import com.cz.czaiagent.advisor.MyLoggerAdvisor;
import jakarta.annotation.Resource;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.model.function.FunctionCallback;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.stereotype.Component;

@Component
public class CzManus extends ToolCallAgent{

    // 构造方法，Spring 会自动注入以下两个依赖：
    //   - allTools：Spring 容器中所有注册的工具（如 DatabaseTool、EmailTool 等）
    //   - dashscopeChatModel：阿里云通义千问的 ChatModel 实例
    public CzManus(ToolCallback[] allTools, ChatModel dashscopeChatModel){
        // 调用父类 ToolCallAgent 的构造方法，传入工具列表
        // 父类构造方法中会初始化：
        //   - availableTools = allTools
        //   - toolCallingManager = ToolCallingManager.builder().build()
        //   - chatOptions = DashScopeChatOptions(proxyToolCalls=true)
        super(allTools);

        // ========== 配置 Agent 基本信息 ==========
        // 设置 Agent 名称，用于日志输出时标识当前 Agent
        // 例如日志会打印 "yuManus的思考: xxx"、"yuManus选择了 N 个工具来使用"
        this.setName("czManus");

        // ========== 系统提示词 ==========
        // 定义 Agent 的角色和行为规范，在每轮对话中作为 system 消息发送给大模型
        // 告诉大模型它是谁、能做什么
        String SYSTEM_PROMPT = """  
                You are CzManus, an all-capable AI assistant, aimed at solving any task presented by the user.  
                You have various tools at your disposal that you can call upon to efficiently complete complex requests.  
                """;
        // 设置到父类 BaseAgent 的 systemPrompt 属性
        // think() 中通过 .system(getSystemPrompt()) 注入到 Prompt
        this.setSystemPrompt(SYSTEM_PROMPT);

        // ========== 下一步提示词 ==========
        // 在每轮 think() 开始时追加的用户消息，指导大模型如何决策工具调用
        // 告诉大模型：
        //   1. 根据需求主动选择最合适的工具或工具组合
        //   2. 复杂任务可以分步使用不同工具
        //   3. 每次工具调用后说明结果并建议下一步
        //   4. 如果任务完成，调用 terminate 工具结束
        String NEXT_STEP_PROMPT = """  
                Based on user needs, proactively select the most appropriate tool or combination of tools.  
                For complex tasks, you can break down the problem and use different tools step by step to solve it.  
                After using each tool, clearly explain the execution results and suggest the next steps.  
                If you want to stop the interaction at any point, use the `terminate` tool/function call.  
                """;
        // 设置到父类 BaseAgent 的 nextStepPrompt 属性
        // think() 中会将其包装为 UserMessage 追加到消息列表
        this.setNextStepPrompt(NEXT_STEP_PROMPT);

        // 设置最大执行步数为 20（默认是 10）
        // 在 BaseAgent.run() 的循环中，如果步数超过 maxSteps，
        // 会强制结束并返回 "Terminated: Reached max steps (20)"
        // 调大这个值是为了给复杂任务留足思考-行动循环的空间
        this.setMaxSteps(30);

        // ========== 初始化 ChatClient ==========
        // 用大模型 ChatModel 构建 ChatClient，这是 think() 中调用大模型的核心组件
        // dashscopeChatModel：Spring AI 注入的阿里云通义千问模型实例
        // defaultAdvisors()：添加拦截器（Advisor），在每次调用大模型前后执行额外逻辑
        //   - MyLoggerAdvisor：记录请求和响应的日志，方便调试
        ChatClient chatClient = ChatClient.builder(dashscopeChatModel)
                .defaultAdvisors(new MyLoggerAdvisor())
                .build();

        // 将构建好的 ChatClient 设置到父类 BaseAgent 的 chatClient 属性
        // think() 中通过 getChatClient() 获取并调用大模型
        this.setChatClient(chatClient);
    }
}

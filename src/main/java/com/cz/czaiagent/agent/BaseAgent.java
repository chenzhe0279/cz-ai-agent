package com.cz.czaiagent.agent;

import com.cz.czaiagent.agent.model.AgentState;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import org.jsoup.internal.StringUtil;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;

import java.util.ArrayList;
import java.util.List;

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

    //大模型(LLM)
    private ChatClient chatClient;

    // Memory（需要自主维护会话上下文）
    private List<Message> messageList = new ArrayList<>();

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
     * 执行单个步骤
     *
     * @return 步骤执行结果
     */
    public abstract String step();

    /**
     * 清理资源
     */
    protected void cleanup() {
        // 子类可以重写此方法来清理资源
    }
}

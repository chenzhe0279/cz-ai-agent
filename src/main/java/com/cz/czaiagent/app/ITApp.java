package com.cz.czaiagent.app;

import jakarta.annotation.Resource;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 使用 Spring AI 框架的原生 ChatModel 来构建一个带上下文记忆的多轮对话
 * 并借助 CommandLineRunner 在 Spring Boot 启动后自动执行。
 */
@Component
public class ITApp implements CommandLineRunner {

    // 依赖注入：Spring AI 自动配置好的 ChatModel 实例
    // 该实例在 application.yml 中配置了 API Key 和模型名称
    @Resource
    private ChatModel dashscopeChatModel;

    // 存储整个对话历史的消息列表
    // 每轮对话都会追加新的 SystemMessage / UserMessage / AssistantMessage
    private List<Message> messages = new ArrayList<>();

    /**
     * CommandLineRunner 接口的唯一方法，Spring 启动完成后自动调用
     */
    @Override
    public void run(String... args) throws Exception {
        // ========== 第一轮对话 ==========
        // 添加系统消息：设定 AI 的角色定位
        messages.add(new SystemMessage("你是一个Java高级工程师。你拥有完整的对话历史记录，请基于历史内容回答问题，不要声明自己无法记住。"));
        // 添加用户消息：用户首次自我介绍
        messages.add(new UserMessage("我是陈爱国，编程爱好者"));
        // 调用 AI 模型，将当前所有消息打包成 Prompt 发送
        ChatResponse chatResponse = dashscopeChatModel.call(new Prompt(messages));
        // 从响应中提取 AI 回复的文本内容
        // getResult() 取第一个结果（通常只有1个）
        // getOutput() 取输出消息（AssistantMessage）
        // getText() 取纯文本内容
        String content = chatResponse.getResult().getOutput().getText();
        // 【关键】将 AI 的回复追加到消息列表，作为下一轮对话的上下文
        messages.add(new AssistantMessage(content));

        //第二轮对话
        messages.add(new UserMessage("给点学习的建议好吗？"));
        chatResponse = dashscopeChatModel.call(new Prompt(messages));
        content = chatResponse.getResult().getOutput().getText();
        messages.add(new AssistantMessage(content));


        //第三轮对话
        messages.add(new UserMessage("之前我跟你说过我的名字，还有我让你帮忙做了哪些事情，帮我回忆一下好吗?"));
        chatResponse = dashscopeChatModel.call(new Prompt(messages));
        content = chatResponse.getResult().getOutput().getText();

        System.out.println(content);

    }
}

package com.cz.czaiagent.demo.invoke;

import jakarta.annotation.Resource;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RestController;

/**
 * Spring AI 框架调用 AI大模型 (阿里)
 */
@Component
public class SpringAiInvoke implements CommandLineRunner {

    //依赖注入
    @Resource
    private ChatModel dashscopeChatModel;

    @Override
    public void run(String... args) throws Exception {
        AssistantMessage assistantMessage = dashscopeChatModel.call(new Prompt("你好，我是陈爱国"))
                .getResult()
                .getOutput();
        System.out.println(assistantMessage.getText());
    }
}

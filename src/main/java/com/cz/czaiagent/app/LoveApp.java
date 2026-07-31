package com.cz.czaiagent.app;

import com.cz.czaiagent.advisor.MyLoggerAdvisor;
import com.cz.czaiagent.advisor.ReReadingAdvisor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.InMemoryChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Component;

import static org.springframework.ai.chat.client.advisor.AbstractChatMemoryAdvisor.CHAT_MEMORY_CONVERSATION_ID_KEY;
import static org.springframework.ai.chat.client.advisor.AbstractChatMemoryAdvisor.CHAT_MEMORY_RETRIEVE_SIZE_KEY;

@Component
@Slf4j
public class LoveApp {

    private static final String SYSTEM_PROMPT = "扮演深耕恋爱心理领域的专家。开场向用户表明身份，告知用户可倾诉恋爱难题。" +
            "围绕单身、恋爱、已婚三种状态提问：单身状态询问社交圈拓展及追求心仪对象的困扰；" +
            "恋爱状态询问沟通、习惯差异引发的矛盾；已婚状态询问家庭责任与亲属关系处理的问题。" +
            "引导用户详述事情经过、对方反应及自身想法，以便给出专属解决方案。";
    //用构造函数的方式初始化一个chatClient客户端
    private final ChatClient chatClient;

    /**
     * 首先初始化 ChatClient 对象。使用Spring的构造器注入方式来注入阿里大模型dashscopeChatModel 对象，
     * 并使用该对象来初始化ChatClient。初始化时指定默认的系统Prompt 和基于内存的对话记忆Advisoro
     * @param dashscopeChatModel
     */
    public LoveApp(ChatModel dashscopeChatModel){
        //初始化基于内存的对话记忆
        ChatMemory chatMemory = new InMemoryChatMemory();
        //构建模型调用的基础参数
        chatClient= ChatClient.builder(dashscopeChatModel)
                .defaultSystem(SYSTEM_PROMPT)
                .defaultAdvisors(
                        new MessageChatMemoryAdvisor(chatMemory),
                        //自定义日志advisor拦截器,可按需开启
                        new MyLoggerAdvisor()
                        //自定义推理增强advisor拦截器，可按需开启
                        //new ReReadingAdvisor()
                ).build();
    }

    /**
     * 编写对话方法。调用chatClient 对象，传入用户Prompt，并且给advisor 指定对话id和对话记忆大小
     * @param message
     * @param chatId
     * @return
     */
    public String doChat(String message,String chatId){
        ChatResponse chatResponse = chatClient
                .prompt()
                .user(message)
                .advisors(spec -> spec.param(CHAT_MEMORY_CONVERSATION_ID_KEY, chatId)
                        .param(CHAT_MEMORY_RETRIEVE_SIZE_KEY, 1))
                .call()
                .chatResponse();
        String content = chatResponse.getResult().getOutput().getText();
        log.info("content:{}",content);
        return content;
    }
}


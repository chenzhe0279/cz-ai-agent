package com.cz.czaiagent.demo.invoke;

import dev.langchain4j.community.model.dashscope.QwenChatModel;

public class LangChainAiInvoke {
    public static void main(String[] args) {
        QwenChatModel chatModel = QwenChatModel.builder()
                .apiKey(TestApiKey.API_KEY)
                .modelName("qwen-max")
                .build();
        String text = chatModel.chat("我是陈爱国，我正在学习AI开发，想转行AI应用工程师");
        System.out.println(text);
    }
}

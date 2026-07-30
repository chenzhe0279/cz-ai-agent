package com.cz.czaiagent;

import cn.hutool.core.lang.UUID;
import com.cz.czaiagent.app.LoveApp;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class CzAiAgentApplicationTests {

    @Resource
    private LoveApp loveApp;
    @Test
    void testDoChat() {
        String chatId = UUID.randomUUID().toString();
        //第一轮对话
        String message = "你好，我是编程爱好者陈爱国";
        String answer = loveApp.doChat(message, chatId);
        Assertions.assertNotNull(answer);
        //第二轮对话
        message = "我想让我喜欢的女孩子也喜欢我";
        answer = loveApp.doChat(message, chatId);
        Assertions.assertNotNull(answer);
        //第三轮对话
        message = "我叫什么名字来着？我刚刚跟说过，帮我回忆一下";
        answer = loveApp.doChat(message, chatId);
        Assertions.assertNotNull(answer);
    }

}

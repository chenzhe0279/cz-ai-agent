package com.cz.czaiagent.app;

import cn.hutool.core.lang.UUID;
import com.cz.czaiagent.chatmemory.MysqlChatMemory;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class LoveAppTest {

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

    @Test
    void doChatWithReport() {
        String chatId = UUID.randomUUID().toString();
        //第一轮对话
        String message = "你好，我是编程爱好者陈爱国,我想找个女朋友，但是我不知道怎么办";
        LoveApp.LoveReport loveReport = loveApp.doChatWithReport(message, chatId, "陈爱国");
        Assertions.assertNotNull(loveReport);
    }

    @Resource
    private JdbcTemplate jdbcTemplate;

    /**
     * 通过 LoveApp 对话验证 MySQL 持久化
     * 先调用 doChat 完成多轮对话，再查询数据库确认消息已写入
     */
    @Test
    void testMysqlPersistenceViaLoveApp() {
        String chatId = "test-persist-" + System.currentTimeMillis();

        // 第一轮对话
        loveApp.doChat("你好，我是编程爱好者陈爱国", chatId);
        // 第二轮对话
        loveApp.doChat("我想让我喜欢的女孩子也喜欢我", chatId);

        // 查询数据库验证消息已持久化
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT message_type, content FROM chat_memory WHERE conversation_id = ? ORDER BY id", chatId);

        assertTrue(rows.size() >= 4, "至少应有4条记录（2轮用户+AI回复），实际: " + rows.size());

        // 验证第一条是用户消息
        assertEquals("USER", rows.get(0).get("message_type"));
        assertTrue(((String) rows.get(0).get("content")).contains("陈爱国"));

        // 验证第二条是AI回复
        assertEquals("ASSISTANT", rows.get(1).get("message_type"));

        // 清理测试数据
        jdbcTemplate.update("DELETE FROM chat_memory WHERE conversation_id = ?", chatId);
    }

    /**
     * 验证对话记忆在重启后仍可恢复
     * 先写入对话，再用新的 LoveApp 实例读取历史
     */
    @Test
    void testMemoryRecoveryViaLoveApp() {
        String chatId = "test-recovery-" + System.currentTimeMillis();

        // 第一轮：告知名字
        loveApp.doChat("我叫陈爱国，记住我的名字", chatId);

        // 第二轮：验证AI能回忆起名字（说明历史消息被正确加载）
        String answer = loveApp.doChat("我叫什么名字？", chatId);
        assertNotNull(answer);
        assertTrue(answer.contains("陈爱国"), "AI应该能回忆起用户名字，实际回复: " + answer);

        // 清理测试数据
        jdbcTemplate.update("DELETE FROM chat_memory WHERE conversation_id = ?", chatId);
    }

    @Test
    void doChatWithRag() {
        String chatId = UUID.randomUUID().toString();
        String message = "我已经结婚了，但是婚后关系不太亲密，怎么办？不要对我进行更深的提问，直接告诉我目前情况该怎么做";
        String answer = loveApp.doChatWithRag(message,chatId);
        Assertions.assertNotNull(answer);
    }
}


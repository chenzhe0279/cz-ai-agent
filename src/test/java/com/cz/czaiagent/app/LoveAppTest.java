package com.cz.czaiagent.app;

import cn.hutool.core.lang.UUID;
import com.cz.czaiagent.chatmemory.MysqlChatMemory;
import com.cz.czaiagent.rag.GitHubDocumentReader;
import com.cz.czaiagent.rag.TranslationQueryTransformer;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;
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
        //String message = "我是一个28岁的金融男，金牛座，平时喜欢运动、旅游和摄影。有没有合适的对象推荐？只推荐一个你认为最匹配的就行，不要对我进行更深的提问，直接根据信息给我推荐";
        String message = "我已经结婚了，但是婚后关系不太亲密，怎么办？";
        String answer = loveApp.doChatWithRag(message,chatId);
        Assertions.assertNotNull(answer);
    }

    @Test
    void doChatWithFallbackSearch() {
        String chatId = UUID.randomUUID().toString();
        String message = "我已经结婚了，但是婚后关系不太亲密，怎么办？";
        String status = "已婚";
        String answer = loveApp.doChatWithFallbackSearch(message,chatId,status);
        Assertions.assertNotNull(answer);
    }

    @Resource
    private GitHubDocumentReader gitHubDocumentReader;
    @Test
    void testGitHubDocumentReader() {
        List<Document> documents = gitHubDocumentReader.readRepository("spring-projects", "spring-ai");
        for (Document doc : documents) {
            System.out.println("内容预览：" + doc.getText().substring(0, Math.min(200, doc.getText().length())));
            System.out.println("元数据：" + doc.getMetadata());
            System.out.println("---");
        }
    }

    @Resource
    private TranslationQueryTransformer translationQueryTransformer;
    @Test
    void testTranslationQueryTransformer() {
        String query = "如何拓展社交圈？";
        Query transQuery = new Query(query);
        Query transform = translationQueryTransformer.transform(transQuery);
        System.out.println("原始问题：" + query);
        System.out.println("翻译后问题：" + transform.text());
    }

    @Test
    void doChatWithTools() {
        // 测试联网搜索问题的答案
        testMessage("周末想带女朋友去上海约会，推荐几个适合情侣的小众打卡地？");

        // 测试网页抓取：恋爱案例分析
        testMessage("最近和对象吵架了，看看编程导航网站（codefather.cn）的其他情侣是怎么解决矛盾的？");

        // 测试资源下载：图片下载
        testMessage("直接下载一张适合做手机壁纸的星空情侣图片为.png文件");

        // 测试终端操作：执行代码
        testMessage("执行 Python3 脚本来生成数据分析报告");

        // 测试文件操作：保存用户档案
        testMessage("保存我的恋爱档案为文件");

        // 测试 PDF 生成
        testMessage("生成一份‘七夕约会计划’PDF，包含餐厅预订、活动流程和礼物清单");
    }

    private void testMessage(String message) {
        String chatId = UUID.randomUUID().toString();
        String answer = loveApp.doChatWithTools(message, chatId);
        Assertions.assertNotNull(answer);
    }

    @Test
    void doChatWithMcp() {
        String chatId = UUID.randomUUID().toString();
        //String message = "我的另一半居住在上海静安区，请帮我找到 5 公里内合适的约会地点";
        String message = "我的另一半喜欢小狗，帮我找一些可爱小狗的照片";
        String answer = loveApp.doChatWithMcp(message, chatId);
        System.out.println(answer);
    }

}


package com.cz.czaiagent.app;

import com.cz.czaiagent.advisor.ForbiddenWordAdvisor;
import com.cz.czaiagent.advisor.MyLoggerAdvisor;
import com.cz.czaiagent.chatmemory.MysqlChatMemory;
import com.cz.czaiagent.rag.LoveAppRagCloudAdvisorConfig;
import com.cz.czaiagent.utils.PromptTemplate;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.QuestionAnswerAdvisor;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.InMemoryChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

import static org.springframework.ai.chat.client.advisor.AbstractChatMemoryAdvisor.CHAT_MEMORY_CONVERSATION_ID_KEY;
import static org.springframework.ai.chat.client.advisor.AbstractChatMemoryAdvisor.CHAT_MEMORY_RETRIEVE_SIZE_KEY;

@Component
@Slf4j
public class LoveApp {

    private final PromptTemplate systemPromptTemplate;

    private final PromptTemplate reportPromptTemplate;

    private final ChatClient chatClient;

    public LoveApp(ChatModel dashscopeChatModel, JdbcTemplate jdbcTemplate) {
        this.systemPromptTemplate = new PromptTemplate("prompts/love-expert.st");
        this.reportPromptTemplate = new PromptTemplate("prompts/love-report.st");

        // 使用 MySQL 持久化对话记忆
        //ChatMemory chatMemory = new MysqlChatMemory(jdbcTemplate);
        //基于内存的持久化对话记忆
        ChatMemory chatMemory = new InMemoryChatMemory();

        String defaultSystemPrompt = systemPromptTemplate.render(Map.of(
                "userStatus", "单身、恋爱、已婚三种",
                "question", "单身状态询问社交圈拓展及追求心仪对象的困扰；恋爱状态询问沟通、习惯差异引发的矛盾；已婚状态询问家庭责任与亲属关系处理的问题"
        ));

        chatClient = ChatClient.builder(dashscopeChatModel)
                .defaultSystem(defaultSystemPrompt)
                .defaultAdvisors(
                        new MessageChatMemoryAdvisor(chatMemory),
                        new MyLoggerAdvisor()
                        //new ForbiddenWordAdvisor()
                ).build();
    }

    /**
     * 普通对话
     * @param message 用户输入
     * @param chatId 会话ID
     * @return AI回复文本
     */
    public String doChat(String message, String chatId) {
        ChatResponse chatResponse = chatClient
                .prompt()
                .user(message)
                .advisors(spec -> spec.param(CHAT_MEMORY_CONVERSATION_ID_KEY, chatId)
                        .param(CHAT_MEMORY_RETRIEVE_SIZE_KEY, 1))
                .call()
                .chatResponse();
        String content = chatResponse.getResult().getOutput().getText();
        log.info("content:{}", content);
        return content;
    }

    record LoveReport(String title, List<String> suggestions) {}

    /**
     * AI恋爱报告功能，实战结构化输出
     * @param message 用户输入
     * @param chatId 会话ID
     * @param username 用户名，用于报告标题
     * @return 恋爱报告
     */
    public LoveReport doChatWithReport(String message, String chatId, String username) {
        String systemPrompt = reportPromptTemplate.render(Map.of(
                "userStatus", "单身、恋爱、已婚三种",
                "question", "单身状态询问社交圈拓展及追求心仪对象的困扰；恋爱状态询问沟通、习惯差异引发的矛盾；已婚状态询问家庭责任与亲属关系处理的问题",
                "username", username
        ));

        LoveReport loveReport = chatClient
                .prompt()
                .system(systemPrompt)
                .user(message)
                .advisors(spec -> spec.param(CHAT_MEMORY_CONVERSATION_ID_KEY, chatId)
                        .param(CHAT_MEMORY_RETRIEVE_SIZE_KEY, 1))
                .call()
                .entity(LoveReport.class);
        log.info("loveReport:{}", loveReport);
        return loveReport;
    }

    //RAG检索增强生成
    @Resource
    private VectorStore loveAppVectorStore;

    @Resource
    private Advisor loveAppRagCloudAdvisor;

    @Resource
    private VectorStore pgVectorVectorStore;
    public String doChatWithRag(String message, String chatId) {
        ChatResponse chatResponse = chatClient
                .prompt()
                .system("你是一位恋爱匹配顾问。当用户描述自己的个人信息（如年龄、职业、星座、爱好等）并请求推荐对象时，" +
                        "你需要判断用户的性别，然后从知识库检索结果中筛选出与用户性别相反的恋爱对象进行推荐。" +
                        "例如：用户说自己是男生，就只推荐标注为【女生】的对象；用户说自己是女生，就只推荐标注为【男生】的对象。" +
                        "推荐时重点关注对方的择偶要求与用户自身条件的匹配度，而不是简单找最相似的对象。")
                .user(message)
                .advisors(spec -> spec.param(CHAT_MEMORY_CONVERSATION_ID_KEY, chatId)
                        .param(CHAT_MEMORY_RETRIEVE_SIZE_KEY, 10))
                // 开启日志，便于观察效果
                .advisors(new MyLoggerAdvisor())
                //应用RAG 知识库问答
                .advisors(new QuestionAnswerAdvisor(loveAppVectorStore))
                // 应用增强检索服务（云知识库服务）
                //.advisors(loveAppRagCloudAdvisor)
                //应用增强检索服务（PgVector服务）
                //.advisors(new QuestionAnswerAdvisor(pgVectorVectorStore))
                .call()
                .chatResponse();
        String content = chatResponse.getResult().getOutput().getText();
        log.info("content: {}", content);
        return content;
    }

}


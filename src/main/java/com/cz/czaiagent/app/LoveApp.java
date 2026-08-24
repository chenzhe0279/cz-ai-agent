package com.cz.czaiagent.app;

import com.cz.czaiagent.advisor.ForbiddenWordAdvisor;
import com.cz.czaiagent.advisor.MyLoggerAdvisor;
import com.cz.czaiagent.chatmemory.MysqlChatMemory;
import com.cz.czaiagent.demo.rag.QueryRewriter;
import com.cz.czaiagent.rag.LoveAppCompositeDocumentRetriever;
import com.cz.czaiagent.rag.LoveAppRagCloudAdvisorConfig;
import com.cz.czaiagent.rag.LoveAppRagCustomAdvisorFactory;
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
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.retrieval.search.DocumentRetriever;
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

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

        String defaultSystemPrompt = systemPromptTemplate.render(Map.of());

        chatClient = ChatClient.builder(dashscopeChatModel)
                .defaultSystem(defaultSystemPrompt)
                .defaultAdvisors(
                        new MessageChatMemoryAdvisor(chatMemory),
                        new MyLoggerAdvisor()
                        //new ForbiddenWordAdvisor()
                ).build();
    }

    /**
     * 普通对话（支持多轮对话，同步）
     *
     * @param message 用户输入
     * @param chatId  会话ID
     * @return AI回复文本
     */
    public String doChat(String message, String chatId) {
        ChatResponse chatResponse = chatClient
                .prompt()
                .user(message)
                .advisors(spec -> spec.param(CHAT_MEMORY_CONVERSATION_ID_KEY, chatId)
                        .param(CHAT_MEMORY_RETRIEVE_SIZE_KEY, 10))
                .call()
                .chatResponse();
        String content = chatResponse.getResult().getOutput().getText();
        log.info("content:{}", content);
        return content;
    }

    /**
     * 普通对话（支持多轮对话，SSE流式传输）
     *
     * @param message 用户输入
     * @param chatId  会话ID
     * @return AI回复文本
     */
    public Flux<String> doChatByStream(String message, String chatId) {
        return chatClient
                .prompt()
                .user(message)
                .advisors(spec -> spec.param(CHAT_MEMORY_CONVERSATION_ID_KEY, chatId)
                        .param(CHAT_MEMORY_RETRIEVE_SIZE_KEY, 10))
                .stream()
                .content();
    }

    record LoveReport(String title, List<String> suggestions) {
    }

    /**
     * AI恋爱报告功能，实战结构化输出
     *
     * @param message  用户输入
     * @param chatId   会话ID
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
                        .param(CHAT_MEMORY_RETRIEVE_SIZE_KEY, 10))
                .call()
                .entity(LoveReport.class);
        log.info("loveReport:{}", loveReport);
        return loveReport;
    }

    //RAG检索增强生成（向量库已停用：为避免启动时调用嵌入 API 产生费用，相关注入与使用代码全部注释）
    //@Resource
    //private VectorStore loveAppVectorStore;

    //@Resource
    //private Advisor loveAppRagCloudAdvisor;

    //@Resource
    //private VectorStore pgVectorVectorStore;

    @Resource
    private JdbcTemplate jdbcTemplate;
    //@Resource
    //private QueryRewriter queryRewriter;

    /*
    public String doChatWithRag(String message, String chatId) {
        String rewriteMessage = queryRewriter.rewrite(message);
        ChatResponse chatResponse = chatClient
                .prompt()
//                .system("你是一位恋爱匹配顾问。当用户描述自己的个人信息（如年龄、职业、星座、爱好等）并请求推荐对象时，" +
//                        "你需要判断用户的性别，然后从知识库检索结果中筛选出与用户性别相反的恋爱对象进行推荐。" +
//                        "例如：用户说自己是男生，就只推荐标注为【女生】的对象；用户说自己是女生，就只推荐标注为【男生】的对象。" +
//                        "推荐时重点关注对方的择偶要求与用户自身条件的匹配度，而不是简单找最相似的对象。")
                .user(rewriteMessage)
                .advisors(spec -> spec.param(CHAT_MEMORY_CONVERSATION_ID_KEY, chatId)
                        .param(CHAT_MEMORY_RETRIEVE_SIZE_KEY, 10))
                // 开启日志，便于观察效果
                .advisors(new MyLoggerAdvisor())
                //应用RAG 知识库问答
                //.advisors(new QuestionAnswerAdvisor(loveAppVectorStore))
                // 使用自定义RAG顾问，传入向量库并限定检索范围为"已婚"状态
                .advisors(LoveAppRagCustomAdvisorFactory.createLoveAppRagCustomAdvisor(loveAppVectorStore, "单身"))
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
    */

    /*
    /**
     * 降级搜索，优先使用向量库检索，无结果时自动降级查询 MySQL
     *
     * @param message 用户输入
     * @param chatId  会话ID
     * @return AI回复文本
     *\/
    public String doChatWithFallbackSearch(String message, String chatId, String status) {
        // 手动构建向量库检索器（绕过 Spring AI M6 版 RetrievalAugmentationAdvisor 的已知 Bug）

        // 1. 构建过滤表达式：限定只检索状态为"单身"的文档数据
        Filter.Expression expression = new FilterExpressionBuilder()
                .eq("status", status) // 设置元数据过滤条件，key为"status"，value为"单身"
                .build(); // 生成最终的过滤表达式对象

        // 2. 构建向量库文档检索器 (VectorStoreDocumentRetriever)
        DocumentRetriever vectorRetriever = VectorStoreDocumentRetriever.builder()
                .vectorStore(loveAppVectorStore) // 指定使用的向量数据库实例
                .filterExpression(expression)    // 应用上一步构建的元数据过滤表达式，缩小检索范围
                .similarityThreshold(0.5)        // 设置相似度阈值，仅返回相似度得分 >= 0.5 的文档，过滤掉相关性较低的结果
                .topK(3)                         // 设置返回的最相关文档数量（Top-K），这里限制最多返回 3 条结果
                .build(); // 完成检索器的构建

        // 使用组合检索器：优先向量库，无结果时自动降级查询 MySQL
        DocumentRetriever fallBackSearchRetriever = new LoveAppCompositeDocumentRetriever(vectorRetriever, jdbcTemplate, status);

        // 手动执行检索
        List<Document> documents = fallBackSearchRetriever.retrieve(new Query(message));

        // 构建上下文：检索到文档则拼入用户消息，否则返回拒答提示
        String userMessage;
        if (documents != null && !documents.isEmpty()) {
            StringBuilder context = new StringBuilder();
            context.append("以下是从知识库中检索到的相关内容，请基于这些内容回答用户的问题：\n\n");
            for (int i = 0; i < documents.size(); i++) {
                context.append("【内容").append(i + 1).append("】\n");
                context.append(documents.get(i).getText()).append("\n\n");
            }
            context.append("用户的问题：").append(message);
            userMessage = context.toString();
        } else {
            userMessage = "请回复用户：抱歉，我只能回答恋爱相关的问题，别的没办法帮到您哦。用户原始问题：" + message;
        }

        ChatResponse chatResponse = chatClient
                .prompt()
                .user(userMessage)
                .advisors(spec -> spec.param(CHAT_MEMORY_CONVERSATION_ID_KEY, chatId)
                        .param(CHAT_MEMORY_RETRIEVE_SIZE_KEY, 10))
                .advisors(new MyLoggerAdvisor())
                .call()
                .chatResponse();
        String content = chatResponse.getResult().getOutput().getText();
        log.info("content: {}", content);
        return content;
    }
    */

    @Resource
    private ToolCallback[] allTools;

    public String doChatWithTools(String message, String chatId) {
        ChatResponse response = chatClient
                .prompt()
                .user(message)
                .advisors(spec -> spec.param(CHAT_MEMORY_CONVERSATION_ID_KEY, chatId)
                        .param(CHAT_MEMORY_RETRIEVE_SIZE_KEY, 10))
                // 开启日志，便于观察效果
                .advisors(new MyLoggerAdvisor())
                .tools(allTools)
                .call()
                .chatResponse();
        String content = response.getResult().getOutput().getText();
        log.info("content: {}", content);
        return content;
    }


    /**
     * 使用MCP工具进行对话，即调用MCP的工具进行对话，如调用百度地图API进行导航
     */
    @Resource
    private ToolCallbackProvider toolCallbackProvider;

    public String doChatWithMcp(String message, String chatId) {
        ChatResponse response = chatClient
                .prompt()
                .user(message)
                .advisors(spec -> spec.param(CHAT_MEMORY_CONVERSATION_ID_KEY, chatId)
                        .param(CHAT_MEMORY_RETRIEVE_SIZE_KEY, 10))
                .advisors(new MyLoggerAdvisor())
                .tools(toolCallbackProvider)
                .call()
                .chatResponse();
        String content = response.getResult().getOutput().getText();
        log.info("content: {}", content);
        return content;
    }
}


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

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;

import static org.springframework.ai.chat.client.advisor.AbstractChatMemoryAdvisor.CHAT_MEMORY_CONVERSATION_ID_KEY;
import static org.springframework.ai.chat.client.advisor.AbstractChatMemoryAdvisor.CHAT_MEMORY_RETRIEVE_SIZE_KEY;

@Component
@Slf4j
public class LoveApp {

    private final PromptTemplate systemPromptTemplate;

    private final PromptTemplate reportPromptTemplate;

    private final ChatClient chatClient;

    // 会话记忆：供前端传入 history 时重建上下文（实现中断后续写等）
    private final ChatMemory chatMemory;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public LoveApp(ChatModel dashscopeChatModel, JdbcTemplate jdbcTemplate) {
        this.systemPromptTemplate = new PromptTemplate("prompts/love-expert.st");
        this.reportPromptTemplate = new PromptTemplate("prompts/love-report.st");

        // 使用 MySQL 持久化对话记忆
        //ChatMemory chatMemory = new MysqlChatMemory(jdbcTemplate);
        //基于内存的持久化对话记忆
        this.chatMemory = new InMemoryChatMemory();

        String defaultSystemPrompt = systemPromptTemplate.render(Map.of());

        chatClient = ChatClient.builder(dashscopeChatModel)
                .defaultSystem(defaultSystemPrompt)
                .defaultAdvisors(
                        new MessageChatMemoryAdvisor(this.chatMemory),
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
    /** 重建该会话记忆：用前端传入的 history（不含本次 user）替换，供 MessageChatMemoryAdvisor 取用，实现中断后续写 */
    /**
     * 重建指定会话的聊天记忆。
     * 当前端传入历史消息时，先清空该会话在内存中的旧记忆，再写入前端提供的历史消息，
     * 这样后续 MessageChatMemoryAdvisor 可以基于前端历史继续对话，实现中断后的续写。
     *
     * @param chatId  会话 ID
     * @param history 前端传入的 JSON 格式历史消息
     */
    private void rebuildMemory(String chatId, String history) {
        // 如果 chatMemory 未初始化（为 null），直接返回，避免空指针异常
        if (chatMemory == null) return;
        // 将前端传入的 history 字符串解析为 Spring AI 的消息列表
        List<Message> hist = parseHistory(history);
        // 如果解析结果为空，说明前端没有提供历史消息，保留后端已有的记忆，不做清空操作
        if (hist.isEmpty()) return; // 未传历史时不覆盖，保留后端原有记忆
        // 清空该会话之前保存的旧记忆，准备写入前端提供的历史消息
        chatMemory.clear(chatId);
        // 将前端提供的历史消息写入 chatMemory，完成记忆重建
        chatMemory.add(chatId, hist);
    }

    /**
     * 解析前端传来的 history JSON（[{"role","content"},...]）为 Spring AI 消息列表。
     * 只识别 role 为 user 和 assistant 的消息，其他角色或无效数据会被忽略。
     *
     * @param history JSON 格式的历史消息字符串
     * @return 解析后的 Spring AI Message 列表；如果输入为空或解析失败，返回空列表
     */
    private List<Message> parseHistory(String history) {
        // 如果传入的 history 为 null 或空白字符串，直接返回不可变空列表，避免后续解析异常
        if (history == null || history.isBlank()) return List.of();
        try {
            // 使用 Jackson 将 history JSON 字符串反序列化为 List<Map<String, String>>
            // TypeReference 用于保留完整的泛型信息，避免类型擦除导致反序列化失败
            List<Map<String, String>> list = objectMapper.readValue(history, new TypeReference<List<Map<String, String>>>() {});
            // 创建可变列表，用于存储解析后的 Spring AI 消息对象
            List<Message> msgs = new ArrayList<>();
            // 遍历解析出的每一个历史消息 Map
            for (Map<String, String> m : list) {
                // 跳过 null 元素，避免处理过程中出现空指针异常
                if (m == null) continue;
                // 从 Map 中获取消息角色
                String role = m.get("role");
                // 从 Map 中获取消息内容
                String content = m.get("content");
                // 如果内容为 null 或空白字符串，跳过该消息，因为空白消息没有实际意义
                if (content == null || content.isBlank()) continue;
                // 如果角色是 user，则封装为 UserMessage
                if ("user".equals(role)) {
                    // 创建 UserMessage 并添加到消息列表
                    msgs.add(new UserMessage(content));
                } else if ("assistant".equals(role)) {
                    // 如果角色是 assistant，则封装为 AssistantMessage
                    msgs.add(new AssistantMessage(content));
                }
                // 其他角色（如 system）不处理，直接忽略
            }
            // 返回所有解析成功的消息列表
            return msgs;
        } catch (Exception e) {
            // 解析过程中出现任何异常，记录警告日志并返回空列表，避免影响主流程
            log.warn("history 解析失败，忽略", e);
            return List.of();
        }
    }

    public Flux<String> doChatByStream(String message, String chatId, String history) {
        rebuildMemory(chatId, history);
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


    //@Resource
    //private Advisor loveAppRagCloudAdvisor;

    @Resource
    private VectorStore pgVectorVectorStore;

    //@Resource
    //rivate JdbcTemplate jdbcTemplate;
    @Resource
    private QueryRewriter queryRewriter;
    //RAG检索增强生成（向量库已停用：为避免启动时调用嵌入 API 产生费用，相关注入与使用代码全部注释）
    @Resource
    private VectorStore loveAppVectorStore;

    public String doChatWithRag(String message, String chatId) {
        String rewriteMessage = queryRewriter.rewrite(message);
        AtomicReference<List<Document>> retrievedDocs = new AtomicReference<>(List.of());
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
                .advisors(LoveAppRagCustomAdvisorFactory.createLoveAppRagCustomAdvisor(loveAppVectorStore, "单身", retrievedDocs))
                // 应用增强检索服务（云知识库服务）
                //.advisors(loveAppRagCloudAdvisor)
                //应用增强检索服务（PgVector服务）
                //.advisors(new QuestionAnswerAdvisor(pgVectorVectorStore))
                .call()
                .chatResponse();
        String content = chatResponse.getResult().getOutput().getText();
        log.info("content: {}", content);
        return content + formatRetrievedReferences(retrievedDocs.get());
    }

    /**
     * RAG 检索增强对话（SSE 流式）：与同步版相同链路，返回实时文本流
     *
     * @param message 用户输入
     * @param chatId  会话ID
     * @param status  知识库文档状态过滤条件
     * @return AI回复文本流
     */
    public Flux<String> doChatWithRagByStream(String message, String chatId, String status, String history) {
        rebuildMemory(chatId, history);
        // 大模型查询重写，提升向量检索命中率
        String rewriteMessage = queryRewriter.rewrite(message);
        // 使用 AtomicReference 收集检索到的文档，以便流式响应结束后追加参考资料
        // 线程安全容器：RAG 顾问在检索完成后会写入命中的 Document 列表，
        // 流式响应结束后通过 retrievedDocs.get() 读取并拼接到参考资料区。
        // 为什么用 AtomicReference：
        // RAG 顾问在检索完成后会整体替换命中的文档列表，而后续 Flux.defer 是在流结束后延迟读取。
        // 如果这里用普通 List，一旦顾问写入和读取不在同一线程，可能出现可见性问题或读到中间状态。
        // AtomicReference 保证引用替换的原子性和跨线程可见性，初始用不可变的空列表兜底。
        AtomicReference<List<Document>> retrievedDocs = new AtomicReference<>(List.of());

        // 手动预检索：命中则走 RAG 流式并附参考资料；未命中直接返回固定说辞（不调大模型，
        // 避免把兜底文案当 prompt 丢给模型让它生成而拒不配合）
        Filter.Expression statusExpr = new FilterExpressionBuilder().eq("status", status).build();
        DocumentRetriever preRetriever = VectorStoreDocumentRetriever.builder()
                .vectorStore(loveAppVectorStore)
                .filterExpression(statusExpr)
                .similarityThreshold(0.5)
                .topK(3)
                .build();
        List<Document> matchedDocs = preRetriever.retrieve(new Query(rewriteMessage));
        if (matchedDocs == null || matchedDocs.isEmpty()) {
            // 知识库未命中：直接输出固定说辞（不走模型，确保原样返回）
            String fallback = "抱歉，我只能回答知识库已包含的情感相关问题，别的没办法帮到您哦，\n"
                    + "有问题可以联系网站主理人：-若世有神明-\n"
                    + "联系电话：19177777777";
            log.info("RAG 未命中，返回固定说辞。message={}, status={}", message, status);
            return Flux.just(fallback);
        }
        retrievedDocs.set(matchedDocs);

        return chatClient
                .prompt()
                .user(rewriteMessage)
                .advisors(spec -> spec.param(CHAT_MEMORY_CONVERSATION_ID_KEY, chatId)
                        .param(CHAT_MEMORY_RETRIEVE_SIZE_KEY, 10))
                .advisors(new MyLoggerAdvisor())
                .advisors(LoveAppRagCustomAdvisorFactory.createLoveAppRagCustomAdvisor(loveAppVectorStore, status, retrievedDocs))
                .stream()
                .content()
                // AI 回答结束后，追加本次检索到的参考资料（按相关度从高到低排序）
                .concatWith(Flux.defer(() -> {
                    String references = formatRetrievedReferences(retrievedDocs.get());
                    return references.isEmpty() ? Flux.empty() : Flux.just(references);
                }));
    }

    /**
     * 将检索到的文档格式化为"参考资料"段落：
     * 每个文档附上相关度分数，并按分数从高到低排序；无文档时返回空串。
     */
    // 将检索到的文档列表格式化为“参考资料”文本，用于追加在 AI 回答末尾展示
    // 方法接收的 documents 可能为 null 或空列表，需要先进行防御性判断
    private String formatRetrievedReferences(List<Document> documents) {
        // 如果传入的文档列表为 null 或没有任何文档
        if (documents == null || documents.isEmpty()) {
            // 直接返回空字符串，表示没有参考资料可提供
            return "";
        }
        // 将文档列表转换为 Stream 流，便于进行排序操作
        List<Document> sorted = documents.stream()
                // 使用 Comparator.comparingDouble 按文档的相关度分数（double 类型）进行排序
                .sorted(Comparator.comparingDouble(
                        // Lambda 表达式：提取每个文档的相关度分数
                        // 如果分数为 null，则使用 Double.MIN_VALUE 作为最小占位值，让空分数排在最后
                        (Document doc) -> doc.getScore() == null ? Double.MIN_VALUE : doc.getScore())
                        // 默认排序是升序，这里反转成降序，确保相关度高的文档排在前面
                        .reversed())
                // 将排序后的 Stream 收集为不可变 List，方便后续通过索引遍历
                .toList();
        // 创建 StringBuilder，用于高效拼接参考资料字符串
        // 初始内容包含两个换行和分隔线标题，用于与前面的 AI 回答内容隔开
        StringBuilder sb = new StringBuilder("\n\n────────── 参考资料（按相关度从高到低排序）──────────");
        // 遍历排序后的文档列表，i 用于生成条目序号
        for (int i = 0; i < sorted.size(); i++) {
            // 获取当前遍历位置的文档对象
            Document doc = sorted.get(i);
            // 获取当前文档的相关度分数；如果为 null 则使用 0.0 作为默认值
            double score = doc.getScore() == null ? 0.0 : doc.getScore();
            // 获取当前文档的文本内容；如果为 null 则使用空字符串，防止输出 "null"
            String text = doc.getText() == null ? "" : doc.getText();
            // 开始拼接当前文档条目：先追加两个换行和【序号】标记，序号 = i+1，从 1 开始显示
            sb.append("\n\n【").append(i + 1).append("】相关度：")
                    // 将相关度分数格式化为四位小数，追加到 StringBuilder
                    .append(String.format("%.4f", score))
                    // 追加一个换行符，让分数和文档内容显示在不同行
                    .append('\n')
                    // 追加文档的正文文本
                    .append(text);
        }
        // 返回拼接完成的参考资料字符串
        return sb.toString();
    }
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


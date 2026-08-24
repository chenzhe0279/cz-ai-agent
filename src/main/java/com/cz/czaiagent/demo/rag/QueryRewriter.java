package com.cz.czaiagent.demo.rag;

import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;

/**
 * 查询重写器：调用 DashScope 大模型将原始查询重写为更适合向量检索的短查询。
 *
 * 注意：这里不再使用 Spring AI 内置的 {@code RewriteQueryTransformer} —— 它内部会强制注入
 * 一个仅含 temperature 的通用 ChatOptions，而 DashScopeChatOptions.multiModel 的默认值是
 * false（非 null），Spring AI 的 options 合并只填充 null 字段，因此多模态开关会被覆盖成
 * false，请求误走文本生成端点；多模态模型（如 qwen3.6-flash）在该端点会返回
 * 400 "url error, please check url"。此处直接以显式 DashScopeChatOptions 调用模型，
 * 保证 multi-model=true 始终生效。
 */
@Component
public class QueryRewriter {

    private static final String REWRITE_TEMPLATE = """
            Given a user query, rewrite it to provide better results when querying a {target}.
            Remove any irrelevant information, and ensure the query is concise and specific.

            Original query:
            {query}

            Rewritten query:
            """;

    private static final String TARGET = "vector store";

    private final ChatModel chatModel;

    /**
     * 构造函数，初始化查询重写器
     *
     * @param dashscopeChatModel 注入的阿里云 DashScope 聊天模型实例
     */
    public QueryRewriter(ChatModel dashscopeChatModel){
        this.chatModel = dashscopeChatModel;
    }

    /**
     * 对输入的提示词（查询语句）进行重写
     *
     * @param prompt 原始的用户查询或提示词
     * @return 重写后的查询文本；重写结果为空时原样返回原始查询
     */
    public String rewrite(String prompt){
        String rewritten = renderRewrite(prompt);
        if (!StringUtils.hasText(rewritten)) {
            // 与框架默认行为一致：重写失败/为空时使用原始查询
            return prompt;
        }
        return rewritten.trim();
    }

    /**
     * 构造重写 prompt 并调用大模型（非流式），显式携带 multi-model 开关
     */
    private String renderRewrite(String prompt) {
        String userText = new PromptTemplate(REWRITE_TEMPLATE, Map.of(
                "target", TARGET,
                "query", prompt == null ? "" : prompt
        )).render();

        ChatResponse response = chatModel.call(new Prompt(
                List.of(new UserMessage(userText)),
                DashScopeChatOptions.builder()
                        .withTemperature(0.0)
                        // qwen3.6-flash 为多模态模型，必须走 multimodal-generation 端点
                        .withMultiModel(true)
                        .build()
        ));
        if (response == null || response.getResults() == null || response.getResults().isEmpty()) {
            return null;
        }
        return response.getResults().get(0).getOutput().getText();
    }
}

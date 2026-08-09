package com.cz.czaiagent.demo.rag;


import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.preretrieval.query.transformation.QueryTransformer;
import org.springframework.ai.rag.preretrieval.query.transformation.RewriteQueryTransformer;
import org.springframework.stereotype.Component;

/**
 * 查询重写器
 */
@Component
public class QueryRewriter {

    /**
     * 查询转换器，用于对原始查询进行重写和优化
     */
    private final QueryTransformer queryTransformer;

    /**
     * 构造函数，初始化查询重写器
     *
     * @param dashscopeChatModel 注入的阿里云 DashScope 聊天模型实例
     */
    public QueryRewriter(ChatModel dashscopeChatModel){
        // 使用传入的聊天模型构建 ChatClient.Builder
        ChatClient.Builder builder = ChatClient.builder(dashscopeChatModel);
        
        // 构建 RewriteQueryTransformer 实例，用于执行查询重写逻辑
        queryTransformer = RewriteQueryTransformer.builder()
                .chatClientBuilder(builder) // 设置聊天客户端构建器
                .build(); // 完成构建
    }

    /**
     * 对输入的提示词（查询语句）进行重写
     *
     * @param prompt 原始的用户查询或提示词
     * @return 重写后的查询文本
     */
    public String rewrite(String prompt){
        // 将原始字符串提示词封装为 Query 对象
        Query query = new Query(prompt);
        
        // 调用转换器对 Query 对象进行重写转换
        Query transformedQuery = queryTransformer.transform(query);
        
        // 返回重写后的查询文本内容
        return transformedQuery.text();
    }
}

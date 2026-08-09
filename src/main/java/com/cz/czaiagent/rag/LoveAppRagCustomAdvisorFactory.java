package com.cz.czaiagent.rag;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.rag.retrieval.search.DocumentRetriever;
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;

@Slf4j
public class LoveAppRagCustomAdvisorFactory {

    /**
     * 创建带有自定义过滤条件的RAG检索增强Advisor
     * <p>
     * 根据指定的状态过滤条件，从向量存储中检索相似度最高的文档，
     * 并将其封装为RetrievalAugmentationAdvisor用于增强AI对话的上下文检索能力。
     *
     * @param vectorStore 向量存储，用于文档的相似度检索
     * @param status      文档状态过滤条件，仅检索匹配该状态的文档
     * @return 配置了自定义文档检索器的 {@link Advisor} 实例
     */
    public static Advisor createLoveAppRagCustomAdvisor(VectorStore vectorStore, String status) {
        // 构建基于status字段的过滤表达式，用于限定文档检索范围
        Filter.Expression expression = new FilterExpressionBuilder()
                .eq("status", status)
                .build();
        // 配置文档检索器，结合向量存储、过滤条件、相似度阈值和返回数量进行检索
        DocumentRetriever documentRetriever = VectorStoreDocumentRetriever.builder()
                .vectorStore(vectorStore)
                .filterExpression(expression) // 过滤条件
                .similarityThreshold(0.5) // 相似度阈值
                .topK(3) // 返回文档数量
                .build();
        // 将文档检索器封装为RetrievalAugmentationAdvisor并返回
        // 构建并返回 RetrievalAugmentationAdvisor 实例，用于实现检索增强生成（RAG）
        return RetrievalAugmentationAdvisor.builder()
                // 设置文档检索器，使用前面配置好的带有过滤条件的检索器
                .documentRetriever(documentRetriever)
                // 设置查询增强器，使用自定义的上下文查询增强器工厂创建的实例
                .queryAugmenter(LoveAppContextualQueryAugmenterFactory.createInstance())
                // 完成构建并返回 Advisor 实例
                .build();
    }
}

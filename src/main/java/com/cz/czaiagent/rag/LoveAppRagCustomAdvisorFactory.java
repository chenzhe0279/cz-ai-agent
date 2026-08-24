package com.cz.czaiagent.rag;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.retrieval.search.DocumentRetriever;
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
public class LoveAppRagCustomAdvisorFactory {

   /* *//**
     * 创建带有自定义过滤条件的RAG检索增强Advisor
     * <p>
     * 根据指定的状态过滤条件，从向量存储中检索相似度最高的文档，
     * 若向量库未命中则降级查询MySQL知识库，
     * 并将其封装为RetrievalAugmentationAdvisor用于增强AI对话的上下文检索能力。
     *
     * @param vectorStore 向量存储，用于文档的相似度检索
     * @param status      文档状态过滤条件，仅检索匹配该状态的文档
     * @return 配置了自定义文档检索器的 {@link Advisor} 实例
     */
    public static Advisor createLoveAppRagCustomAdvisor(VectorStore vectorStore,String status) {
        return createLoveAppRagCustomAdvisor(vectorStore, status, null);
    }

    /**
     * 创建带有自定义过滤条件的RAG检索增强Advisor，并捕获本次实际检索到的文档。
     * <p>
     * 与 {@link #createLoveAppRagCustomAdvisor(VectorStore, String)} 逻辑一致，
     * 区别在于会将被 {@code VectorStoreDocumentRetriever} 检索到的文档（含相似度分数）
     * 写入调用方提供的 {@code retrievedDocs}，供接口在回答末尾展示"参考资料 + 相关度"。
     *
     * @param vectorStore   向量存储，用于文档的相似度检索
     * @param status        文档状态过滤条件，仅检索匹配该状态的文档
     * @param retrievedDocs 用于回传本次检索结果文档列表的引用（可为 null，表示不捕获）
     * @return 配置了自定义文档检索器的 {@link Advisor} 实例
     */
    public static Advisor createLoveAppRagCustomAdvisor(VectorStore vectorStore, String status,
                                                       AtomicReference<List<Document>> retrievedDocs) {
        // 构建基于status字段的过滤表达式，用于限定文档检索范围
        Filter.Expression expression = new FilterExpressionBuilder()
                .eq("status", status)
                .build();
        // 配置向量库文档检索器，结合向量存储、过滤条件、相似度阈值和返回数量进行检索
        DocumentRetriever documentRetriever = VectorStoreDocumentRetriever.builder()
                .vectorStore(vectorStore)
                .filterExpression(expression) // 过滤条件
                .similarityThreshold(0.5) // 相似度阈值
                .topK(3) // 返回文档数量
                .build();
        // 包装检索器：检索完成后把文档列表（含相似度分数）回传给调用方
        // 创建一个带结果捕获能力的文档检索器：使用 Lambda 表达式实现 DocumentRetriever 接口，
        // 在原始向量库检索器执行完检索后，将命中的文档列表通过 retrievedDocs 引用回传给调用方。
        DocumentRetriever capturingRetriever = query -> {
            // 调用底层向量库文档检索器，根据查询条件获取相似文档列表（文档中会包含相似度分数）
            List<Document> documents = documentRetriever.retrieve(query);
            // 判断调用方是否传入了用于接收检索结果的 AtomicReference 引用，避免空指针异常
            if (retrievedDocs != null) {
                // 将本次检索到的文档列表安全地写入 AtomicReference，供外部接口在回答末尾展示参考资料和相关度
                retrievedDocs.set(documents);
            }
            // 返回本次检索到的文档列表，以便后续 RAG 流程将文档上下文拼接给大模型使用
            return documents;
        };
        // 构建并返回 RetrievalAugmentationAdvisor 实例，用于实现检索增强生成（RAG）
        return RetrievalAugmentationAdvisor.builder()
                // 设置文档检索器，使用前面配置好的带有过滤条件的检索器
                .documentRetriever(capturingRetriever)
                // 设置查询增强器，使用自定义的上下文查询增强器工厂创建的实例
                .queryAugmenter(LoveAppContextualQueryAugmenterFactory.createInstance())
                // 完成构建并返回 Advisor 实例
                .build();
    }
    /*public static Advisor createLoveAppRagCustomAdvisor(VectorStore vectorStore,String status) {
        // 构建基于status字段的过滤表达式，用于限定文档检索范围
        Filter.Expression expression = new FilterExpressionBuilder()
                .eq("status", status)
                .build();
        // 配置向量库文档检索器，结合向量存储、过滤条件、相似度阈值和返回数量进行检索
        DocumentRetriever documentRetriever = VectorStoreDocumentRetriever.builder()
                .vectorStore(vectorStore)
                .filterExpression(expression) // 过滤条件
                .similarityThreshold(0.5) // 相似度阈值
                .topK(3) // 返回文档数量
                .build();
        // 构建组合检索器：优先走向量库，无结果时降级查询MySQL
        //DocumentRetriever compositeRetriever = new LoveAppCompositeDocumentRetriever(documentRetriever, jdbcTemplate, status);
        // 构建并返回 RetrievalAugmentationAdvisor 实例，用于实现检索增强生成（RAG）
        return RetrievalAugmentationAdvisor.builder()
                // 设置文档检索器，使用前面配置好的带有过滤条件的检索器
                .documentRetriever(documentRetriever)
                // 设置查询增强器，使用自定义的上下文查询增强器工厂创建的实例
                .queryAugmenter(LoveAppContextualQueryAugmenterFactory.createInstance())
                // 完成构建并返回 Advisor 实例
                .build();
    }*/
}

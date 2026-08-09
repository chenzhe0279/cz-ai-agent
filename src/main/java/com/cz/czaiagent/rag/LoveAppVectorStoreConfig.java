package com.cz.czaiagent.rag;


import com.alibaba.cloud.ai.dashscope.embedding.DashScopeEmbeddingModel;
import jakarta.annotation.Resource;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class LoveAppVectorStoreConfig {
    @Resource
    private LoveAppDocumentLoader loveAppDocumentLoader;

    @Resource
    private MyTokenTextSplitter myTokenTextSplitter;

    @Resource
    private MyKeywordEnricher myKeywordEnricher;

    /**
     * 创建并配置一个基于内存的向量存储（VectorStore），用于 RAG 检索增强生成。
     * 该方法会加载本地 Markdown 文件，利用嵌入模型将文档内容转换为向量并存入向量存储中，
     * 供后续语义检索使用。
     *
     * @param dashscopeEmbeddingModel 用于文本向量化的嵌入模型实例
     * @return 已初始化并加载文档数据的 VectorStore 实例
     */
    @Bean
    public VectorStore loveAppVectorStore(EmbeddingModel dashscopeEmbeddingModel) {
        //构建一个向量数据库
        SimpleVectorStore simpleVectorStore = SimpleVectorStore.builder(dashscopeEmbeddingModel).build();
        //获取markdown文件
        List<Document> documents = loveAppDocumentLoader.loadMarkDowns();
        //自主切分
        //List<Document> splitDocuemnts = myTokenTextSplitter.splitCustomized(documents);
        // 自动补充关键词元信息
        List<Document> enrichedDocuments = myKeywordEnricher.enrichDocuments(documents);
        // 将文档数据写入向量存储，完成向量化索引
        simpleVectorStore.add(enrichedDocuments);
        return simpleVectorStore;
    }
}

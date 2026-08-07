package com.cz.czaiagent.rag;

import jakarta.annotation.Resource;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class PgVectorVectorStoreConfigTest {
    @Resource
    VectorStore pgVectorVectorStore;
    @Resource
    JdbcTemplate jdbcTemplate;

    @Test
    void test() {
        jdbcTemplate.update("DELETE FROM vector_store");

        List<Document> documents = List.of(
                new Document("陈爱国是一个编程爱好者，他正在自学编程!", Map.of("meta1", "自学编程")),
                new Document("陈爱国想找个对象"),
                new Document("陈爱国目前正在学习AI编程方面的知识", Map.of("meta2", "AI编程")));
        // 添加文档
        pgVectorVectorStore.add(documents);
        // 相似度查询
        List<Document> results = pgVectorVectorStore.similaritySearch(SearchRequest.builder().query("编程").topK(2).build());
        Assertions.assertNotNull(results);

        for (Document doc : results) {
            System.out.println("内容: " + doc.getText());
            System.out.println("元数据: " + doc.getMetadata());
            Object distance = doc.getMetadata().get("distance");
            if (distance instanceof Number) {
                double similarity = 1.0 - ((Number) distance).doubleValue();
                System.out.println("距离: " + distance + ", 相似度: " + similarity);
            }
            System.out.println("---");
        }
    }


}
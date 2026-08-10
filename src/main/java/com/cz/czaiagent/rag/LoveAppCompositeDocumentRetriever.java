package com.cz.czaiagent.rag;


import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.retrieval.search.DocumentRetriever;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 组合文档检索器：优先从向量库检索，无结果时降级查询 MySQL 的 love_knowledge 表
 */
@Slf4j
public class LoveAppCompositeDocumentRetriever implements DocumentRetriever {

    //向量文档检索器，用于优先从向量库中检索文档
    private final DocumentRetriever vectorRetriever;

    private final JdbcTemplate jdbcTemplate;

    private final String status;
    public LoveAppCompositeDocumentRetriever(DocumentRetriever vectorRetriever,JdbcTemplate jdbcTemplate,String status){
        this.vectorRetriever = vectorRetriever;
        this.jdbcTemplate = jdbcTemplate;
        this.status = status;
    }
    @Override
    /**
     * 执行文档检索逻辑。
     * 优先从向量数据库中检索相关文档；若未命中，则降级使用 MySQL 进行关键词模糊匹配。
     *
     * @param query 用户的查询对象
     * @return 检索到的文档列表，若均未命中则返回空列表
     */
    public List<Document> retrieve(Query query) {
        // 1. 优先查询向量数据库
        List<Document> documents = vectorRetriever.retrieve(query);
        
        // 2. 若向量库命中相关文档，则直接返回结果
        if (documents != null && !documents.isEmpty()) {
            log.info("向量库命中 {} 条文档", documents.size());
            return documents;
        }
        
        // 3. 向量数据库未查询到结果，触发降级策略：按 status 从 MySQL 随机查询
        log.info("向量库未命中，降级查询 MySQL love_knowledge 表，status：{}", status);

        String sql = "SELECT content, tags FROM love_knowledge WHERE status = ? ORDER BY RAND() LIMIT 3";
        List<Map<String, Object>> dataList = jdbcTemplate.queryForList(sql, status);
        
        List<Document> mysqlDocs = new ArrayList<>();
        for (Map<String, Object> row : dataList) {
            String content = (String) row.get("content");
            String tags = (String) row.get("tags");
            if (tags == null) {
                tags = "";
            }
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("source", "mysql");
            metadata.put("status", status);
            metadata.put("tags", tags);
            mysqlDocs.add(new Document(content, metadata));
        }

        // 4. 若 MySQL 降级查询命中结果，则返回
        if (!mysqlDocs.isEmpty()) {
            log.info("MySQL 降级命中 {} 条文档（status={}）", mysqlDocs.size(), status);
            return mysqlDocs;
        }

        // 5. 向量库和 MySQL 均未命中任何文档
        log.info("向量库和 MySQL 均未命中，将触发拒答 prompt");
        return List.of();
    }
}

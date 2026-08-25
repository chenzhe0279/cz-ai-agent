package com.cz.czaiagent.rag;


import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.markdown.MarkdownDocumentReader;
import org.springframework.ai.reader.markdown.config.MarkdownDocumentReaderConfig;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Document加载类，负责读取本地markdown文件并解析成Document列表
 */
@Component
@Slf4j
public class LoveAppDocumentLoader {
    
    /**
     * Spring 资源模式解析器，用于根据通配符模式（如 classpath*:*.md）解析和加载资源文件。
     * 声明为 final 以确保引用不可变，符合依赖注入的最佳实践，同时保证线程安全。
     */
    private final ResourcePatternResolver resourcePatternResolver;
    
    /**
     * 构造函数，通过构造器注入的方式初始化 ResourcePatternResolver。
     * Spring 容器会自动将 ResourcePatternResolver 的实例注入到该构造器中，
     * 以便后续用于读取和匹配本地 markdown 文件资源。
     *
     * @param resourcePatternResolver Spring 提供的资源模式解析器实例
     */
    public LoveAppDocumentLoader(ResourcePatternResolver resourcePatternResolver){
        this.resourcePatternResolver = resourcePatternResolver;
    }

    /**
     * 加载 classpath 下 document 目录中的所有 Markdown 文件，并将其解析为 Document 列表。
     * 每个 Markdown 文件会根据配置被拆分为多个 Document 对象，
     * 同时会将文件名作为元数据附加到每个 Document 上。
     *
     * @return 解析后的 Document 列表，若读取失败则返回空列表
     */
    public List<Document> loadMarkDowns(){
        //创建一个Document类型的列表，用于存储解析后的文档
        List<Document> allDocuments = new ArrayList<>();
        try {
            // 扫描 classpath 下 document 目录中所有 .md 文件
            Resource[] resources = resourcePatternResolver.getResources("classpath*:document/*.md");
            for (Resource resource : resources) {
                //获取markdown文件名
                String fileName = resource.getFilename();
                // 提取文档倒数第 3 和第 2 个字作为标签
                String status = fileName.substring(fileName.length() - 6, fileName.length() - 4);
                // 构建 Markdown 读取配置：按水平分割线拆分为多个 Document，排除代码块和引用块，并附加文件名元数据
                MarkdownDocumentReaderConfig config = MarkdownDocumentReaderConfig.builder()
                        .withHorizontalRuleCreateDocument(true)
                        .withIncludeCodeBlock(false)
                        .withIncludeBlockquote(false)
                        .withAdditionalMetadata("filename", fileName)
                        .withAdditionalMetadata("status", status)
                        .build();
                // 使用配置解析当前 Markdown 文件，并将结果合并到文档列表中
                MarkdownDocumentReader markdownDocumentReader = new MarkdownDocumentReader(resource, config);
                allDocuments.addAll(markdownDocumentReader.get());
            }
        }catch (IOException e){
            log.error("Markdown文件读取失败",e);
        }
        return allDocuments;
    }
}

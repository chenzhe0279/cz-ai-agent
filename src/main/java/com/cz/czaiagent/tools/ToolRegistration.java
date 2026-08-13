package com.cz.czaiagent.tools;

import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbacks;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSender;

/**
 * 工具注册类
 * <p>
 * 职责：统一管理和注册所有 AI Agent 可用的工具。
 * 就像"工具箱管理员"，把所有工具（文件操作、搜索、邮件、数据库等）
 * 集中创建并注册为 ToolCallback 数组，供 AI 模型调用。
 * <p>
 * 依赖注入说明：
 * - searchApiKey：通过 @Value 从配置文件读取
 * - mailSender：通过构造函数注入 Spring 自动创建的邮件发送器
 * - jdbcTemplate：通过构造函数注入 Spring 自动创建的 JDBC 模板
 */
@Configuration
public class ToolRegistration {

    /**
     * 搜索引擎 API 密钥，从 application-local.yaml 中读取
     */
    @Value("${search-api.api-key}")
    private String searchApiKey;

    /**
     * 邮件发件人地址，从 application-local.yaml 的 spring.mail.username 中读取
     */
    @Value("${spring.mail.username}")
    private String mailUsername;

    /**
     * Spring 邮件发送器，用于 EmailTool 发送邮件
     */
    //private final JavaMailSender mailSender;

    /**
     * Spring JDBC 模板，用于 DatabaseTool 执行 SQL
     */
    //private final JdbcTemplate jdbcTemplate;

    /**
     * 构造函数：注入邮件发送器和 JDBC 模板
     * <p>
     * Spring 启动时会自动将容器中已创建的 JavaMailSender 和 JdbcTemplate 传入。
     *
     * @param mailSender   邮件发送器
     * @param jdbcTemplate JDBC 模板
     */
    /*public ToolRegistration(JavaMailSender mailSender, JdbcTemplate jdbcTemplate) {
        this.mailSender = mailSender;
        this.jdbcTemplate = jdbcTemplate;
    }*/

    /**
     * 注册所有工具并返回 ToolCallback 数组
     * <p>
     * 工作流程：
     * 第一步：逐个创建工具实例（无依赖的直接 new，有依赖的传入构造参数）
     * 第二步：通过 ToolCallbacks.from() 把所有工具打包成 ToolCallback 数组
     * 第三步：Spring 容器会自动将这个数组注册为 Bean，供 AI Agent 使用
     *
     * @return 包含所有工具的 ToolCallback 数组
     */
    @Bean
    public ToolCallback[] allTools() {
        // 无外部依赖的工具，直接 new 即可
        FileOperationTool fileOperationTool = new FileOperationTool();
        WebSearchTool webSearchTool = new WebSearchTool(searchApiKey);
        WebScrapingTool webScrapingTool = new WebScrapingTool();
        ResourceDownloadTool resourceDownloadTool = new ResourceDownloadTool();
        TerminalOperationTool terminalOperationTool = new TerminalOperationTool();
        PDFGenerationTool pdfGenerationTool = new PDFGenerationTool();
        // 需要 Spring Bean 依赖的工具，通过构造函数注入
        //EmailTool emailTool = new EmailTool(mailSender, mailUsername);
        DateTimeTool dateTimeTool = new DateTimeTool();
        //DatabaseTool databaseTool = new DatabaseTool(jdbcTemplate);
        // 将所有工具统一注册为 ToolCallback 数组
        return ToolCallbacks.from(
            fileOperationTool,
            webSearchTool,
            webScrapingTool,
            resourceDownloadTool,
            terminalOperationTool,
            pdfGenerationTool,
            //emailTool,
            dateTimeTool
            //databaseTool
        );
    }
}

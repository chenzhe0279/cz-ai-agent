package com.cz.czaiagent.tools;

import com.cz.czaiagent.service.HumanInteractionService;
import org.springframework.ai.model.function.FunctionCallback;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbacks;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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
     * 搜索引擎 API 密钥，从 application-local.yml 中读取
     */
    @Value("${search-api.api-key}")
    private String searchApiKey;

    /**
     * 邮件发件人地址，从 application-local.yml 的 spring.mail.username 中读取
     */
    @Value("${spring.mail.username}")
    private String mailUsername;

    /**
     * Spring 邮件发送器，用于 EmailTool 发送邮件
     */
    //private JavaMailSender mailSender;

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
     * 第二步：通过 ToolCallbacks.from() 把所有本地工具打包成 ToolCallback 数组
     * 第三步：合并 MCP 客户端提供的远程工具（如图片搜索 searchImage）
     * 第四步：Spring 容器会自动将合并后的数组注册为 Bean，供 AI Agent 使用
     *
     * @param mcpToolCallbackProvider MCP 客户端自动配置的工具提供者（参数由 Spring 自动注入）
     * @return 包含本地工具 + MCP 工具的 ToolCallback 数组
     */
    @Bean
    public ToolCallback[] allTools(ToolCallbackProvider mcpToolCallbackProvider , JavaMailSender mailSender, HumanInteractionService humanInteractionService) {
        // 无外部依赖的工具，直接 new 即可
        FileOperationTool fileOperationTool = new FileOperationTool();
        WebSearchTool webSearchTool = new WebSearchTool(searchApiKey);
        WebScrapingTool webScrapingTool = new WebScrapingTool();
        ResourceDownloadTool resourceDownloadTool = new ResourceDownloadTool();
        //TerminalOperationTool terminalOperationTool = new TerminalOperationTool();
        PDFGenerationTool pdfGenerationTool = new PDFGenerationTool();
        // 创建人类交互工具实例：无外部依赖（内部直接使用 System.in），直接 new 即可
        AskHumanTool askHumanTool = new AskHumanTool(humanInteractionService);
        // 需要 Spring Bean 依赖的工具，通过构造函数注入
        EmailTool emailTool = new EmailTool(mailSender, mailUsername);
        DateTimeTool dateTimeTool = new DateTimeTool();
        //DatabaseTool databaseTool = new DatabaseTool(jdbcTemplate);
        // 将所有本地工具统一注册为 ToolCallback 数组
        TerminateTool terminateTool = new TerminateTool();
        ToolCallback[] localTools = ToolCallbacks.from(
            fileOperationTool,
            webSearchTool,
            webScrapingTool,
            resourceDownloadTool,
            //terminalOperationTool,
            pdfGenerationTool,
            emailTool,
            terminateTool,
            dateTimeTool,
            // 注册人类交互工具，注册后大模型即可在 think() 阶段自主决定调用 askHuman
            askHumanTool
            //databaseTool
        );
        // 获取 MCP 客户端提供的远程工具（如图片搜索 searchImage）
        // 注意：getToolCallbacks() 返回 FunctionCallback[]，需手动拷贝合并到 ToolCallback[] 中
        FunctionCallback[] mcpTools = mcpToolCallbackProvider.getToolCallbacks();
        // 初始化合并后的工具数组，总容量为本地工具数量与 MCP 远程工具数量之和
        ToolCallback[] mergedTools = new ToolCallback[localTools.length + mcpTools.length];
        // 将本地工具数组 (localTools) 的全部元素，从源索引 0 开始拷贝到目标数组 (mergedTools) 的起始位置 (索引 0)
        System.arraycopy(localTools, 0, mergedTools, 0, localTools.length);
        // 将 MCP 工具数组 (mcpTools) 的全部元素，从源索引 0 开始拷贝到目标数组 (mergedTools) 中紧接本地工具之后的位置 (索引 localTools.length)
        System.arraycopy(mcpTools, 0, mergedTools, localTools.length, mcpTools.length);
        return mergedTools;
    }
}

package com.cz.czaiagent.tools;

import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mail.javamail.JavaMailSender;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class EmailToolTest {
    /**
     * 注入 Spring 邮件发送器，用于构造 EmailTool
     */
    @Resource
    private JavaMailSender mailSender;

    /**
     * 测试发送纯文本邮件
     * <p>
     * 验证点：调用 sendEmail 方法，确保返回值不为空。
     * 如果 SMTP 配置正确，会收到一封纯文本邮件。
     */
    @Test
    public void testSendEmail() {
        // 使用注入的 mailSender 和配置的发件人地址创建工具实例
        EmailTool tool = new EmailTool(mailSender, "17771873239@163.com");
        // 调用发送纯文本邮件方法
        String result = tool.sendEmail(
                "3302076969@qq.com",
                "测试邮件",
                "你好，这是一封来自 AI Agent 的测试邮件。"
        );
        // 打印发送结果
        System.out.println(result);
        // 断言返回值不为空
        assertNotNull(result);
    }

    /**
     * 测试发送 HTML 格式邮件
     * <p>
     * 验证点：调用 sendHtmlEmail 方法，确保返回值不为空。
     * 如果 SMTP 配置正确，会收到一封带 HTML 格式的邮件（包含标题和加粗文字）。
     */
    @Test
    public void testSendHtmlEmail() {
        // 创建工具实例
        EmailTool tool = new EmailTool(mailSender, "17771873239@163.com");
        // 构造 HTML 格式的邮件正文
        String htmlContent = "<h1>欢迎</h1><p>这是一封 <b>HTML 格式</b> 的测试邮件。</p>";
        // 调用发送 HTML 邮件方法
        String result = tool.sendHtmlEmail(
                "3302076969@qq.com",
                "HTML测试邮件",
                htmlContent
        );
        // 打印发送结果
        System.out.println(result);
        // 断言返回值不为空
        assertNotNull(result);
    }
}
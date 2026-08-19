package com.cz.czaiagent.tools;

import jakarta.mail.internet.MimeMessage;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;

import java.io.File;

/**
 *  * 邮件发送工具
 *  * 功能说明：为 AI Agent 提供邮件发送能力，支持两种邮件格式：
 *  * 1. 纯文本邮件 —— 适用于简单的通知类消息，如"你的验证码是 1234"
 *  * 2. HTML 富文本邮件 —— 适用于需要排版的消息，如带表格、链接、图片的邮件
 */
public class EmailTool {
    /**
     * Spring 提供的邮件发送器，内部已配置好 SMTP 服务器地址、端口、账号等信息
     */
    private final JavaMailSender mailSender;

    /**
     * 发件人邮箱地址，即"从哪封邮箱发出"，配置在 application-local.yaml 中
     */
    private final String fromEmail;

    /**
     * 构造函数：通过构造注入邮件发送器和发件人邮箱
     * <p>
     * 为什么不直接用 @Autowired？因为工具类不是 Spring Bean，
     * 它是在 ToolRegistration 中手动 new 出来的，所以需要手动传入依赖。
     *
     * @param mailSender Spring 邮件发送器，由 Spring 容器自动创建
     * @param fromEmail  发件人邮箱地址
     */
    public EmailTool(JavaMailSender mailSender, String fromEmail) {
        this.mailSender = mailSender;
        this.fromEmail = fromEmail;
    }

    /**
     * 发送纯文本邮件
     * <p>
     * 工作流程：
     * 第一步：创建一封"简单邮件"对象（SimpleMailMessage），相当于拿出一张空白信纸
     * 第二步：填写发件人、收件人、主题、正文，相当于在信封和信纸上写内容
     * 第三步：调用 mailSender.send() 把邮件发出去，相当于把信投进邮筒
     *
     * @param to      收件人邮箱地址，例如 "user@example.com"
     * @param subject 邮件主题，例如 "欢迎注册"
     * @param body    邮件正文内容，纯文本格式
     * @return 发送结果描述，成功返回成功信息，失败返回错误原因
     */
    @Tool(description = "Send a plain text email to a recipient")
    public String sendEmail(
            @ToolParam(description = "Recipient email address") String to,
            @ToolParam(description = "Email subject") String subject,
            @ToolParam(description = "Email body content") String body) {
        try {
            // 第一步：创建简单邮件消息对象
            SimpleMailMessage message = new SimpleMailMessage();
            // 第二步：设置发件人地址
            message.setFrom(fromEmail);
            // 第三步：设置收件人地址
            message.setTo(to);
            // 第四步：设置邮件主题
            message.setSubject(subject);
            // 第五步：设置邮件正文
            message.setText(body);
            // 第六步：发送邮件
            mailSender.send(message);
            return "Email sent successfully to: " + to;
        } catch (Exception e) {
            // 捕获异常并返回错误信息，避免程序崩溃
            return "Error sending email: " + e.getMessage();
        }
    }

    /**
     * 发送 HTML 格式的富文本邮件
     * <p>
     * 与纯文本邮件的区别：
     * - 纯文本邮件只能发文字，HTML 邮件可以发带格式的内容（加粗、链接、表格等）
     * - 需要使用 MimeMessage 而不是 SimpleMailMessage，就像"普通信件"和"带附件的挂号信"的区别
     * <p>
     * 工作流程：
     * 第一步：通过 mailSender 创建一个 MIME 格式的邮件对象
     * 第二步：用 MimeMessageHelper 包装它，简化设置操作（类似"信封填写助手"）
     * 第三步：设置发件人、收件人、主题
     * 第四步：设置 HTML 内容，第二个参数 true 表示"这是 HTML 格式"
     * 第五步：发送邮件
     *
     * @param to       收件人邮箱地址
     * @param subject  邮件主题
     * @param htmlBody HTML 格式的邮件正文，可以包含 HTML 标签如 &lt;h1&gt;, &lt;p&gt;, &lt;a&gt; 等
     * @return 发送结果描述
     */
    @Tool(description = "Send an HTML format email to a recipient, supports rich text with HTML tags")
    public String sendHtmlEmail(
            @ToolParam(description = "Recipient email address") String to,
            @ToolParam(description = "Email subject") String subject,
            @ToolParam(description = "HTML content of the email, can include HTML tags like <h1>, <p>, <a> etc.") String htmlBody) {
        try {
            // 第一步：创建 MIME 格式的邮件消息对象（支持富文本内容）
            MimeMessage message = mailSender.createMimeMessage();
            // 第二步：创建辅助工具类，简化 MIME 邮件的设置操作
            // 参数 true 表示支持多部分内容（如附件），"UTF-8" 确保中文不会乱码
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            // 第三步：设置发件人地址
            helper.setFrom(fromEmail);
            // 第四步：设置收件人地址
            helper.setTo(to);
            // 第五步：设置邮件主题
            helper.setSubject(subject);
            // 第六步：设置邮件正文，第二个参数 true 表示内容是 HTML 格式
            helper.setText(htmlBody, true);
            // 第七步：发送邮件
            mailSender.send(message);
            return "HTML email sent successfully to: " + to;
        } catch (Exception e) {
            // 捕获异常并返回错误信息
            return "Error sending HTML email: " + e.getMessage();
        }
    }

    /**
     * 发送带附件的邮件
     *
     * @param to             收件人邮箱地址
     * @param subject        邮件主题
     * @param body           邮件正文内容
     * @param attachmentPath 附件文件的本地路径，例如 PDF 文件的完整路径
     * @return 发送结果描述
     */
    @Tool(description = "Send an email with a file attachment to a recipient")
    public String sendEmailWithAttachment(
            @ToolParam(description = "Recipient email address") String to,
            @ToolParam(description = "Email subject") String subject,
            @ToolParam(description = "Email body content") String body,
            @ToolParam(description = "Local file path of the attachment to send") String attachmentPath) {
        try {
            File attachmentFile = new File(attachmentPath);
            if (!attachmentFile.exists()) {
                return "Error sending email: attachment file not exist: " + attachmentPath;
            }
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromEmail);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(body);
            // 添加附件，使用文件本身的名称作为附件名
            helper.addAttachment(attachmentFile.getName(), attachmentFile);
            mailSender.send(message);
            return "Email with attachment sent successfully to: " + to;
        } catch (Exception e) {
            return "Error sending email with attachment: " + e.getMessage();
        }
    }
}

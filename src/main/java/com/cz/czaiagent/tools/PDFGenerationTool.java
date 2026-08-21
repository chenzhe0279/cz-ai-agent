package com.cz.czaiagent.tools;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import com.cz.czaiagent.constant.FileConstant;
import com.itextpdf.io.image.ImageDataFactory;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Image;
import com.itextpdf.layout.element.Paragraph;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * PDF生成工具
 */
public class PDFGenerationTool {

    /**
     * 匹配正文中的图片占位行，格式：[图片:本地文件路径]
     * 例如：[图片:D:\soft\javaProject\cz-ai-agent\tmp\download\jingan_park_1.jpeg]
     */
    private static final Pattern IMAGE_LINE_PATTERN = Pattern.compile("^\\s*\\[图片[:：](.+)]\\s*$");

    @Tool(description = "Generate a PDF file with given content, SUPPORTS embedding images. " +
            "Use this tool when the content needs to contain images. " +
            "To embed an image at a specific position, write an independent line in the content with format: " +
            "[图片:image local file path]. " +
            "For example, after writing one location's introduction, immediately add a line [图片:path] to show its image.")
    public String generatePDFWithImage(
            @ToolParam(description = "Name of the file to save the generated PDF") String fileName,
            @ToolParam(description = "Content to be included in the PDF. Images are referenced inline as [图片:local file path], and will be inserted at the exact position they appear") String content) {
        String fileDir = FileConstant.FILE_SAVE_DIR + "/pdf";
        String filePath = fileDir + "/" + fileName;
        try {
            FileUtil.mkdir(fileDir);
            try (PdfWriter writer = new PdfWriter(filePath);
                 PdfDocument pdf = new PdfDocument(writer);
                 Document document = new Document(pdf)) {
                PdfFont font = PdfFontFactory.createFont("STSongStd-Light", "UniGB-UCS2-H");
                document.setFont(font);
                // 逐行解析正文：普通行作为段落，图片标记行则插入对应图片
                String[] lines = content.split("\\n");
                for (String line : lines) {
                    Matcher matcher = IMAGE_LINE_PATTERN.matcher(line);
                    if (matcher.matches()) {
                        // 图片标记行：提取路径并插入图片
                        String imagePath = matcher.group(1).trim();
                        if (FileUtil.exist(imagePath)) {
                            Image image = new Image(ImageDataFactory.create(imagePath));
                            image.setWidth(400);
                            document.add(image);
                        } else {
                            document.add(new Paragraph("（图片文件不存在：" + imagePath + "）"));
                        }
                    } else if (StrUtil.isNotBlank(line)) {
                        // 普通文本行：作为段落写入
                        document.add(new Paragraph(line));
                    }
                }
            }
            return "PDF generated successfully to: " + filePath;
        } catch (IOException e) {
            return "Error generating PDF: " + e.getMessage();
        }
    }

    /**
     * 将缓冲区中累积的文本作为一个段落写入文档，然后清空缓冲区。
     * 段落设置首行缩进 24（约两个中文字符），符合中文排版习惯。
     */
    private void flushParagraph(Document document, StringBuilder buffer) {
        if (buffer.length() > 0) {
            document.add(new Paragraph(buffer.toString()).setFirstLineIndent(24));
            buffer.setLength(0);
        }
    }

    @Tool(description = "Generate a plain-text PDF file with given content, WITHOUT any image support. " +
            "Use this tool only when the content contains pure text and does not need images. " +
            "If the content needs to embed images, use generatePDFWithImage instead.", returnDirect = false)
    public String generatePDF(
            @ToolParam(description = "Name of the file to save the generated PDF") String fileName,
            @ToolParam(description = "Plain text content to be included in the PDF") String content) {
        String fileDir = FileConstant.FILE_SAVE_DIR + "/pdf";
        String filePath = fileDir + "/" + fileName;
        try {
            // 创建目录
            FileUtil.mkdir(fileDir);
            // 创建 PdfWriter 和 PdfDocument 对象
            try (PdfWriter writer = new PdfWriter(filePath);
                 PdfDocument pdf = new PdfDocument(writer);
                 Document document = new Document(pdf)) {
                // 自定义字体（需要人工下载字体文件到特定目录）
//                String fontPath = Paths.get("src/main/resources/static/fonts/simsun.ttf")
//                        .toAbsolutePath().toString();
//                PdfFont font = PdfFontFactory.createFont(fontPath,
//                        PdfFontFactory.EmbeddingStrategy.PREFER_EMBEDDED);
                // 使用内置中文字体
                PdfFont font = PdfFontFactory.createFont("STSongStd-Light", "UniGB-UCS2-H");
                document.setFont(font);
                // 创建段落
                Paragraph paragraph = new Paragraph(content);
                // 添加段落并关闭文档
                document.add(paragraph);
            }
            return "PDF generated successfully to: " + filePath;
        } catch (IOException e) {
            return "Error generating PDF: " + e.getMessage();
        }
    }

    /**
     * 下面的代码是生成 PDF 文件并上传至对象存储的示例，返回可访问的 URL
     */
    /*@Resource
    private ObjectStorageService objectStorageService; // 注入对象存储服务（如阿里云OSS、MinIO）

    @Tool(description = "生成 PDF 文件并上传至对象存储，返回可访问的 URL",,returnDirect = true)
    public String generatePDF(
            @ToolParam(description = "PDF 文件名（不含路径）") String fileName,
            @ToolParam(description = "PDF 内容") String content) {

        try {
            // 1. 在内存中生成 PDF 字节数组
            byte[] pdfBytes = generatePdfBytes(content);

            // 2. 构建存储路径（使用 UUID 防止重名）
            String objectKey = "pdf/" + UUID.randomUUID() + "_" + fileName;

            // 3. 上传至对象存储，并获取访问 URL
            String fileUrl = objectStorageService.uploadFile(objectKey, pdfBytes, "application/pdf");

            // 4. 直接返回 URL（无需大模型二次生成）
            return "PDF 生成并上传成功，访问地址：" + fileUrl;

        } catch (IOException e) {
            return "PDF 生成失败：" + e.getMessage();
        }
    }

    *//**
     * 生成 PDF 字节数组（纯内存操作）
     *//*
    private byte[] generatePdfBytes(String content) throws IOException {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             PdfWriter writer = new PdfWriter(baos);
             PdfDocument pdf = new PdfDocument(writer);
             Document document = new Document(pdf)) {

            // 支持中文的内置字体
            PdfFont font = PdfFontFactory.createFont("STSongStd-Light", "UniGB-UCS2-H");
            document.setFont(font);

            // 添加内容
            document.add(new Paragraph(content));
            document.close();

            return baos.toByteArray();
        }
    }*/
}

package com.cz.czaiagent.tools;

import cn.hutool.core.io.FileUtil;
import com.cz.czaiagent.constant.FileConstant;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Paragraph;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.io.IOException;

/**
 * PDF生成工具
 */
public class PDFGenerationTool {

    @Tool(description = "Generate a PDF file with given content")
    public String generatePDF(
            @ToolParam(description = "Name of the file to save the generated PDF") String fileName,
            @ToolParam(description = "Content to be included in the PDF") String content) {
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

    @Tool(description = "生成 PDF 文件并上传至对象存储，返回可访问的 URL")
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

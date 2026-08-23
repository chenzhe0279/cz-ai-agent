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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;

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
            "For example, after writing one location's introduction, immediately add a line [图片:path] to show its image. " +
            "Invalid or unrecognizable images will NOT break the whole PDF: they are replaced by a placeholder line, " +
            "and the returned result reports how many images were embedded successfully and which ones failed.")
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
                int embeddedOk = 0;
                List<String> failedImages = new ArrayList<>();
                // 逐行解析正文：普通行作为段落，图片标记行则插入对应图片
                String[] lines = content.split("\\n");
                for (String line : lines) {
                    Matcher matcher = IMAGE_LINE_PATTERN.matcher(line);
                    if (matcher.matches()) {
                        // 图片标记行：提取路径并插入图片
                        String imagePath = matcher.group(1).trim();
                        // 1. 解析真实文件（gzip 包裹的伪图片自动解压到临时文件）
                        File imageFile = resolveRealImage(imagePath);
                        if (imageFile == null || !isSupportedImage(imageFile)) {
                            // 2. 文件不存在或不是有效图片：降级为占位文本，不影响整份 PDF
                            failedImages.add(imagePath);
                            document.add(new Paragraph("（图片文件无效：" + imagePath + "）"));
                            continue;
                        }
                        try {
                            // 3. 单张图片独立容错：这一张失败只占位，继续生成后面内容
                            Image image = new Image(ImageDataFactory.create(imageFile.getAbsolutePath()));
                            image.setWidth(400);
                            document.add(image);
                            embeddedOk++;
                        } catch (Exception e) {
                            failedImages.add(imagePath);
                            document.add(new Paragraph("（图片无法识别：" + imagePath + "）"));
                        }
                    } else if (StrUtil.isNotBlank(line)) {
                        // 普通文本行：作为段落写入
                        document.add(new Paragraph(sanitizeForCjk(line)));
                    }
                }
                // 4. 返回统计信息，让 AI 知道哪些图片没有嵌入成功
                StringBuilder result = new StringBuilder("PDF generated successfully to: " + filePath);
                result.append("（图片嵌入成功 ").append(embeddedOk).append(" 张");
                if (!failedImages.isEmpty()) {
                    result.append("，失败 ").append(failedImages.size()).append(" 张：").append(String.join("、", failedImages));
                }
                result.append("）");
                return result.toString();
            }
        } catch (IOException e) {
            return "Error generating PDF: " + e.getMessage();
        }
    }

    /**
     * 解析图片标记中的路径为真实可用的图片文件：
     * - 文件不存在或不是普通文件时返回 null；
     * - 文件是 gzip 压缩流（1F 8B）时，自动解压到临时文件后返回（常见于下载接口被压缩的情况）；
     * - 其余情况原样返回。
     */
    private File resolveRealImage(String path) {
        File file = new File(path);
        if (!file.exists() || !file.isFile()) {
            return null;
        }
        byte[] head = readHead(file, 2);
        boolean gzip = head.length >= 2 && head[0] == 0x1F && head[1] == (byte) 0x8B;
        if (!gzip) {
            return file;
        }
        try {
            File tmp = File.createTempFile("czai-img-", ".img");
            tmp.deleteOnExit();
            try (InputStream in = new GZIPInputStream(new FileInputStream(file));
                 OutputStream out = new FileOutputStream(tmp)) {
                in.transferTo(out);
            }
            return tmp;
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * 按文件头魔数识别常见图片格式，避免把 HTML/JSON 等非图片文件误当成图片：
     * JPEG(FF D8 FF)、PNG(89 50 4E 47)、GIF(GIF8)、BMP(BM)、WebP(RIFF....WEBP)。
     */
    private boolean isSupportedImage(File file) {
        byte[] h = readHead(file, 16);
        if (h.length < 4) {
            return false;
        }
        // JPEG
        if ((h[0] & 0xFF) == 0xFF && (h[1] & 0xFF) == 0xD8 && (h[2] & 0xFF) == 0xFF) {
            return true;
        }
        // PNG
        if ((h[0] & 0xFF) == 0x89 && h[1] == 0x50 && h[2] == 0x4E && h[3] == 0x47) {
            return true;
        }
        // GIF
        if ("GIF8".equals(new String(h, 0, 4, StandardCharsets.US_ASCII))) {
            return true;
        }
        // BMP
        if (h[0] == 0x42 && h[1] == 0x4D) {
            return true;
        }
        // WebP: RIFF + WEBP
        if (h.length >= 12
                && h[0] == 'R' && h[1] == 'I' && h[2] == 'F' && h[3] == 'F'
                && h[8] == 'W' && h[9] == 'E' && h[10] == 'B' && h[11] == 'P') {
            return true;
        }
        return false;
    }

    /**
     * 读取文件头部最多 len 个字节，用于格式识别。
     */
    private byte[] readHead(File file, int len) {
        try (InputStream in = new FileInputStream(file)) {
            byte[] buf = new byte[len];
            int read = in.read(buf);
            return read > 0 ? Arrays.copyOf(buf, read) : new byte[0];
        } catch (IOException e) {
            return new byte[0];
        }
    }

    /**
     * 去除文本中超出 BMP（基本多文种平面）的字符（如 emoji 🏯📍 等），
     * 因为内置中文字体 STSongStd-Light（UniGB-UCS2-H）仅支持 UCS-2/BMP 编码，
     * 直接写入会导致 "This encoder only accepts BMP codepoints" 异常。
     */
    private String sanitizeForCjk(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        StringBuilder sb = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            if (cp <= 0xFFFF) {
                sb.appendCodePoint(cp);
            }
            i += Character.charCount(cp);
        }
        return sb.toString();
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
                Paragraph paragraph = new Paragraph(sanitizeForCjk(content));
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

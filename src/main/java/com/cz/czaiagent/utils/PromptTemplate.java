package com.cz.czaiagent.utils;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 *  定义提示词模板工具类，用于加载和渲染模板文件
 */
@Slf4j
public class PromptTemplate {

    // 定义静态常量正则表达式，用于匹配形如 {key} 的占位符，key 必须由字母或下划线开头，后跟字母、数字或下划线
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([a-zA-Z_][a-zA-Z0-9_]*)}");

    // 定义私有不可变变量，用于存储模板文件在 classpath 中的相对路径
    private final String templatePath;

    // 定义私有变量，用于存储从文件中读取到的模板原始文本内容
    private String content;

    // 定义公共构造函数，接收模板文件路径作为参数
    public PromptTemplate(String templatePath) {
        // 将传入的模板路径赋值给当前对象的 templatePath 成员变量
        this.templatePath = templatePath;
        // 调用私有的 load 方法加载模板文件内容，并赋值给 content 成员变量
        this.content = load(templatePath);
    }

    /**
     * 渲染模板，将 {key} 替换为 variables 中对应的值
     *
     * @param variables 变量映射，key 为占位符名称，value 为替换值
     * @return 渲染后的文本
     */
    // 定义公共方法 render，接收变量映射 Map，返回替换占位符后渲染完成的字符串
    public String render(Map<String, String> variables) {
        // 使用预编译的正则表达式对模板内容创建匹配器，用于查找所有占位符
        Matcher matcher = PLACEHOLDER.matcher(content);
        // 创建 StringBuilder 对象，用于高效拼接渲染后的字符串结果
        StringBuilder sb = new StringBuilder();
        // 循环查找模板内容中所有匹配正则表达式的占位符
        while (matcher.find()) {
            // 获取当前匹配到的占位符中的第一个捕获组，即大括号内的变量名
            String key = matcher.group(1);
            // 从 variables 中获取 key 对应的值，若不存在则使用原始占位符字符串作为默认值
            String value = variables.getOrDefault(key, matcher.group(0));
            // 将匹配到的占位符替换为 value 并追加到 StringBuilder 中，使用 quoteReplacement 防止特殊字符导致异常
            matcher.appendReplacement(sb, Matcher.quoteReplacement(value));
        }
        // 将最后一个匹配项之后的剩余模板内容追加到 StringBuilder 的末尾
        matcher.appendTail(sb);
        // 将 StringBuilder 转换为 String 并返回最终渲染完成的模板文本
        return sb.toString();
    }

    /**
     * 获取模板原始内容（不替换变量）
     */
    // 定义公共方法 getRawContent，用于获取未经变量替换的模板原始内容
    public String getRawContent() {
        // 直接返回成员变量 content 中存储的原始模板文本
        return content;
    }

    /**
     * 从 classpath 加载模板文件内容
     */
    // 定义私有静态方法 load，根据传入的路径读取模板文件并返回其内容字符串
    private static String load(String path) {
        // 使用 try-with-resources 创建 BufferedReader，确保流在使用后自动关闭
        try (BufferedReader reader = new BufferedReader(
                // 创建 InputStreamReader，用于将字节流转换为字符流
                new InputStreamReader(
                        // 使用 Spring 的 ClassPathResource 获取资源输入流
                        new ClassPathResource(path).getInputStream(),
                        // 指定字符流使用的字符集为 UTF-8，确保正确读取多字节字符
                        StandardCharsets.UTF_8))) {
            // 创建 StringBuilder 对象，用于逐行拼接读取到的文件内容
            StringBuilder sb = new StringBuilder();
            // 声明字符串变量，用于临时存储每次从文件中读取的一行文本
            String line;
            // 循环调用 readLine 方法读取文件，直到返回 null 表示到达文件末尾
            while ((line = reader.readLine()) != null) {
                // 判断 StringBuilder 中是否已有内容，即当前读取的不是第一行
                if (sb.length() > 0) {
                    // 如果不是第一行，则先追加一个换行符，以保留原文件的换行格式
                    sb.append("\n");
                }
                // 将当前读取到的一行文本追加到 StringBuilder 中
                sb.append(line);
            }
            // 将 StringBuilder 转换为 String 并返回完整的模板文件内容
            return sb.toString();
        } catch (IOException e) {
            // 捕获读取文件时可能抛出的 IO 异常，并包装为运行时异常抛出
            throw new RuntimeException("模板文件加载失败: " + path, e);
        }
    }
}

package com.cz.czimagesearchmcpserver;

import com.cz.czimagesearchmcpserver.tools.ImageSearchTool;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class CzImageSearchMcpServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(CzImageSearchMcpServerApplication.class, args);
    }

    @Bean
    /**
     * 创建图像搜索工具的 Bean，用于将自定义工具注册到 AI Agent 的上下文中。
     * 该 Bean 利用 Spring AI 的 MethodToolCallbackProvider，
     * 自动扫描并暴露 ImageSearchTool 类中的方法作为可调用工具。
     * 
     * @param imageSearchTool 注入的图像搜索工具实例，包含具体的搜索逻辑实现
     * @return ToolCallbackProvider 接口实现，提供对工具元数据和执行逻辑的访问
     */
    public ToolCallbackProvider imageSearchTools(ImageSearchTool imageSearchTool) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(imageSearchTool)
                .build();
    }
}

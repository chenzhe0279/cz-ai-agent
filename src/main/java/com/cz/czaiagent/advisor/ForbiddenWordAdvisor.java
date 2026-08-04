package com.cz.czaiagent.advisor;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.advisor.api.*;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Slf4j
public class ForbiddenWordAdvisor implements CallAroundAdvisor, StreamAroundAdvisor {

    private final Set<String> forbiddenWords = new HashSet<>();

    public ForbiddenWordAdvisor() {
        loadForbiddenWords();
    }


/**
     * 从文件系统加载违禁词文件 forbidden-words.txt 到内存集合
     * 若文件不存在则自动创建，逐行读取并跳过空行和以 # 开头的注释行
     * 文件读取异常时仅记录警告日志，不阻断程序运行*/


    private void loadForbiddenWords() {
        // 获取当前项目工作目录下的违禁词文件
        File file = new File(System.getProperty("user.dir"), "forbidden-words.txt");
        // 文件不存在时自动创建
        if (!file.exists()) {
            try {
                String defaultContent = """
                        # 违禁词列表，每行一个，# 开头为注释
                        暴力
                        色情
                        赌博
                        毒品
                        诈骗
                        """;
                Files.writeString(file.toPath(), defaultContent, StandardCharsets.UTF_8);
                log.info("违禁词文件不存在，已自动创建: {}", file.getAbsolutePath());
            } catch (IOException e) {
                log.warn("违禁词文件创建失败: {}", e.getMessage());
                return;
            }
        }
        // 以 UTF-8 编码读取违禁词文件
        try (BufferedReader reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (!trimmed.isEmpty() && !trimmed.startsWith("#")) {
                    forbiddenWords.add(trimmed);
                }
            }
            log.info("违禁词库加载完成，共 {} 个词", forbiddenWords.size());
        } catch (IOException e) {
            log.warn("违禁词文件加载失败: {}", e.getMessage());
        }
    }

    @Override
    public String getName() {
        return this.getClass().getSimpleName();
    }

    @Override
    public int getOrder() {
        return -10;
    }

    private AdvisedRequest before(AdvisedRequest request) {
        String userText = request.userText();
        for (String word : forbiddenWords) {
            if (userText.contains(word)) {
                log.warn("检测到违禁词: {}", word);
                throw new RuntimeException("输入包含违规内容，请修改后重试");
            }
        }
        return request;
    }

    @Override
    public AdvisedResponse aroundCall(AdvisedRequest advisedRequest, CallAroundAdvisorChain chain) {
        advisedRequest = this.before(advisedRequest);
        return chain.nextAroundCall(advisedRequest);
    }

    @Override
    public Flux<AdvisedResponse> aroundStream(AdvisedRequest advisedRequest, StreamAroundAdvisorChain chain) {
        advisedRequest = this.before(advisedRequest);
        return chain.nextAroundStream(advisedRequest);
    }
}

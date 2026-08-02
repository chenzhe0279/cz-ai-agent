/*
package com.cz.czaiagent.advisor;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.advisor.api.*;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Slf4j
public class ForbiddenWordAdvisor implements CallAroundAdvisor, StreamAroundAdvisor {

    private final Set<String> forbiddenWords = new HashSet<>();

    public ForbiddenWordAdvisor() {
        loadForbiddenWords();
    }

    // ... existing code ...

    */
/**
     * 从 classpath 资源文件 forbidden-words.txt 中加载违禁词到内存集合
     * 逐行读取文件，跳过空行和以 # 开头的注释行，将有效词条加入 forbiddenWords 集合
     * 文件加载失败时仅记录警告日志，不阻断程序运行
     *//*

    private void loadForbiddenWords() {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(
                        getClass().getClassLoader().getResourceAsStream("forbidden-words.txt"),
                        StandardCharsets.UTF_8))) {
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

// ... existing code ...


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
*/

package com.cz.czaiagent.advisor;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.advisor.api.*;
import org.springframework.ai.chat.model.MessageAggregator;
import reactor.core.publisher.Flux;

/**
 * 自定义日志 Advisor
 * 打印 info 级别日志、只输出单次用户提示词和 AI 回复的文本
 * 参考官方文档和内置的SimpleLoggerAdvisor源码，结合2者并略做修改，开发一个更精简的I，
 * 可自定义级别的日志记录器.默认打印info级别日志、并且只输出单次用户提示词和AI回复的文本。
 */
@Slf4j
public class MyLoggerAdvisor implements CallAroundAdvisor, StreamAroundAdvisor {

    @Override
    public String getName() {
        return this.getClass().getSimpleName();
    }

    @Override
    public int getOrder() {
        return 0;
    }

    private AdvisedRequest before(AdvisedRequest request) {
        log.info("AI Request: {}", request.userText());
        return request;
    }

    private void observeAfter(AdvisedResponse advisedResponse) {
        log.info("AI Response: {}", advisedResponse.response().getResult().getOutput().getText());
    }

    public AdvisedResponse aroundCall(AdvisedRequest advisedRequest, CallAroundAdvisorChain chain) { // 同步调用环绕通知方法，接收请求和调用链
        advisedRequest = this.before(advisedRequest); // 请求前处理：记录用户输入日志
        AdvisedResponse advisedResponse = chain.nextAroundCall(advisedRequest); // 继续执行调用链，获取 AI 响应
        this.observeAfter(advisedResponse); // 响应后处理：记录 AI 回复日志
        return advisedResponse; // 返回最终响应
    } // aroundCall 方法结束

    public Flux<AdvisedResponse> aroundStream(AdvisedRequest advisedRequest, StreamAroundAdvisorChain chain) { // 流式调用环绕通知方法，接收请求和流式调用链
        advisedRequest = this.before(advisedRequest); // 请求前处理：记录用户输入日志
        Flux<AdvisedResponse> advisedResponses = chain.nextAroundStream(advisedRequest); // 继续执行流式调用链，获取响应流
        return (new MessageAggregator()).aggregateAdvisedResponse(advisedResponses, this::observeAfter); // 聚合流式响应，并在聚合完成后记录完整 AI 回复日志
    } // aroundStream 方法结束
}

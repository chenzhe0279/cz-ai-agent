package com.cz.czaiagent.controller;

import com.cz.czaiagent.agent.CzManus;
import com.cz.czaiagent.app.LoveApp;
import com.cz.czaiagent.service.HumanInteractionService;
import jakarta.annotation.Resource;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

import java.io.IOException;

@RestController
@RequestMapping("/ai")
public class AiController {

    @Resource
    private LoveApp loveApp;

    @Resource
    private ToolCallback[] allTools;

    @Resource
    private ChatModel dashscopeChatModel;

    @Resource
    private HumanInteractionService humanInteractionService;

    @GetMapping("/love_app/chat/sync")
    public String doChatWithLoveAppSync(String message, String chatId) {
        return loveApp.doChat(message, chatId);
    }

    @GetMapping(value = "/love_app/chat/sse", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> doChatWithLoveAppSSE(String message, String chatId) {
        return loveApp.doChatByStream(message, chatId);
    }

    @GetMapping(value = "/love_app/chat/sent_event")
    public Flux<ServerSentEvent<String>> doChatWithLoveAppSentEvent(String message, String chatId) {
        return loveApp.doChatByStream(message, chatId)
                .map(chunk -> ServerSentEvent.<String>builder()
                        .data(chunk)
                        .build());
    }

    @GetMapping("/love_app/chat/sse/emitter")
    public SseEmitter doChatWithLoveAppSseEmitter(String message, String chatId) {
        // 创建一个超时时间较长的 SseEmitter
        SseEmitter emitter = new SseEmitter(180000L); // 3分钟超时
        // 获取 Flux 数据流并直接订阅
        loveApp.doChatByStream(message, chatId)
                .subscribe(
                        // 处理每条消息
                        chunk -> {
                            try {
                                emitter.send(chunk);
                            } catch (IOException e) {
                                emitter.completeWithError(e);
                            }
                        },
                        // 处理错误
                        emitter::completeWithError,
                        // 处理完成
                        emitter::complete
                );
        // 返回emitter
        return emitter;
    }

    /**
     * 流式调用 Manus 超级智能体
     *
     * @param message
     * @return
     */
    @GetMapping("/manus/chat")
    public SseEmitter doChatWithManus(String message) {
        CzManus czManus = new CzManus(allTools, dashscopeChatModel,humanInteractionService);
        return czManus.runStream(message);
    }


    /**
     * 人类回答请求参数实体 (Java Record)。
     *
     * 【代码解释】
     * 使用 Java 14+ 引入的 record 关键字定义不可变的数据载体，自动生成构造器、getter、equals、hashCode 和 toString 方法。
     * - requestId: 关联的 AI 交互请求 ID，用于在 HumanInteractionService 中匹配并唤醒对应的阻塞任务。
     * - answer: 人类提供的具体回答内容，将作为 AI 工具的输入继续执行。
     *
     * @param requestId 关联的 AI 交互请求 ID
     * @param answer    人类提供的具体回答内容
     */
    public record HumanAnswerRequest(String requestId, String answer) {
    }

    /**
     * 提交人类对 AI 交互请求的回答。
     * 
     * 【代码解释】
     * 1. 接口定义：这是一个 POST 请求接口，路径为 "/manus/human-answer"，用于接收客户端提交的人类回答。
     * 2. 参数接收：通过 @RequestBody 接收 JSON 格式的请求体，反序列化为 HumanAnswerRequest 记录类。
     * 3. 业务逻辑：调用 humanInteractionService.submitAnswer 方法，将 requestId 和 answer 传递给底层服务，
     *    以唤醒等待该回答的 AI 智能体 (Manus) 线程或流程。
     * 4. 响应处理：根据服务层返回的 boolean 结果决定 HTTP 状态码。
     *    - 如果 accepted 为 true（找到对应的 requestId 并成功提交），返回 200 OK。
     *    - 如果 accepted 为 false（requestId 不存在或已失效），返回 404 Not Found。
     *
     * @param request 包含请求ID和人类回答内容的请求体
     * @return HTTP 响应状态
     */
    @PostMapping("/manus/human-answer")
    public ResponseEntity<Void> submitHumanAnswer(@RequestBody HumanAnswerRequest request) {
        boolean accepted = humanInteractionService.submitAnswer(
                request.requestId(),
                request.answer()
        );

        return accepted
                ? ResponseEntity.ok().build()
                : ResponseEntity.notFound().build();
    }

    /**
     * RAG 检索增强对话（SSE 流式）——供前端新增 AI 聊天框调用
     *
     * @param message 用户输入
     * @param chatId  会话ID
     * @param status  知识库文档状态过滤条件，默认"单身"（可选：单身/恋爱/已婚）
     * @return SSE 流
     */
    @GetMapping("/rag/chat/sse")
    public SseEmitter doChatWithRagSse(String message, String chatId,
                                       @RequestParam(defaultValue = "单身") String status) {
        // 创建一个超时时间较长的 SseEmitter
        SseEmitter emitter = new SseEmitter(180000L); // 3分钟超时
        // 获取 RAG 增强的 Flux 数据流并直接订阅
        loveApp.doChatWithRagByStream(message, chatId, status)
                .subscribe(
                        // 处理每条消息
                        chunk -> {
                            try {
                                emitter.send(chunk);
                            } catch (IOException e) {
                                emitter.completeWithError(e);
                            }
                        },
                        // 处理错误
                        emitter::completeWithError,
                        // 处理完成
                        emitter::complete
                );
        // 返回emitter
        return emitter;
    }

}


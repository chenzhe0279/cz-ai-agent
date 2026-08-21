package com.cz.czaiagent.service;

import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Service
public class HumanInteractionService {

    // 定义等待人类回答的超时时间常量：180 秒（3 分钟），超时后降级处理，避免智能体永久卡死
    private static final long ANSWER_TIMEOUT_SECONDS = 180;

    // 线程本地变量：存储"当前线程"绑定的会话 ID
    // 作用：智能体在异步线程中运行，askHuman 工具调用时无需层层传参即可拿到本会话 ID
    private final ThreadLocal<String> currentSessionId = new ThreadLocal<>();

    // 会话注册表：会话 ID -> 该会话对应的 SSE 推送器（线程安全）
    // 一个 SSE 连接对应一个 SseEmitter，通过它可以向前端推送提问事件
    private final Map<String, SseEmitter> sessionEmitters = new ConcurrentHashMap<>();

    // 待回答注册表：请求 ID -> 对应的 Future（线程安全）
    // askHuman 阻塞在这个 Future 上，submitAnswer 通过 complete() 唤醒它，实现跨线程传递答案
    private final Map<String, CompletableFuture<String>> pendingAnswers = new ConcurrentHashMap<>();

    /**
     * 在 Agent 的异步运行线程中绑定当前 SSE 会话。
     */
    // 打开一个新会话：由 Controller 在启动智能体异步任务前调用，传入 SSE 推送器
    public String openSession(SseEmitter emitter) {
        // 生成一个全局唯一的会话 ID（UUID 保证不重复）
        String sessionId = UUID.randomUUID().toString();
        // 将"会话 ID -> 推送器"的映射存入注册表，后续 askHuman 凭此 ID 找到推送器
        sessionEmitters.put(sessionId, emitter);
        // 把会话 ID 绑定到当前线程（ThreadLocal），同一线程内的 askHuman 可直接取用
        currentSessionId.set(sessionId);
        // 返回会话 ID，供调用方（如 Controller）在任务结束时调用 closeCurrentSession 使用
        return sessionId;
    }

    /**
     * Agent 运行结束时释放当前会话资源。
     */
    // 关闭当前会话：智能体运行结束后必须调用，防止内存泄漏
    public void closeCurrentSession() {
        // 从 ThreadLocal 中取出当前线程绑定的会话 ID
        String sessionId = currentSessionId.get();
        // 如果会话 ID 存在（即本线程确实开启过会话）
        if (sessionId != null) {
            // 从会话注册表中移除该会话的推送器映射，释放引用
            sessionEmitters.remove(sessionId);
        }
        // 清除 ThreadLocal 中的值，防止线程池复用线程时出现脏数据（内存泄漏隐患）
        currentSessionId.remove();
    }

    /**
     * askHuman 工具调用此方法：推送提问事件，并阻塞等待前端回答。
     */
    // 核心方法：askHuman 工具的入口。推送问题给前端，然后阻塞当前线程等待人类回答
    public String askHuman(String question) {
        // 从 ThreadLocal 取出当前线程绑定的会话 ID
        String sessionId = currentSessionId.get();
        // 根据会话 ID 从注册表取出对应的 SSE 推送器
        SseEmitter emitter = sessionEmitters.get(sessionId);

        // 降级判断一：如果没有会话 ID 或找不到推送器（例如测试环境、非 SSE 调用场景）
        if (sessionId == null || emitter == null) {
            // 直接返回降级提示文本，让大模型基于已有信息继续，而不是报错中断任务
            return "当前没有可用的前端会话，请基于已有信息继续完成任务。";
        }

        // 为本次提问生成唯一的请求 ID，前端回答时需要原样带回，用于匹配是哪个问题
        String requestId = UUID.randomUUID().toString();
        // 创建一个 Future 作为"答案容器"，askHuman 线程阻塞等待它被填充
        CompletableFuture<String> answerFuture = new CompletableFuture<>();
        // 把"请求 ID -> Future"注册进等待表，供 submitAnswer 按 ID 查找并唤醒
        pendingAnswers.put(requestId, answerFuture);

        try {
            // 封装提问事件对象，包含请求 ID（供前端回传）和问题内容
            HumanQuestionEvent event = new HumanQuestionEvent(requestId, question);

            // 通过 SSE 向前端推送一个名为 "human_question" 的事件，数据体为上面的事件对象
            // 前端监听该事件名，收到后弹窗展示问题并收集用户输入
            emitter.send(
                    SseEmitter.event()
                            .name("human_question")  // 自定义事件名，前端 EventSource 按此名称监听
                            .data(event)               // 事件数据，会被序列化为 JSON 发送
            );

            // 关键阻塞点：当前线程在此等待，最多等 180 秒
            // 前端提交答案后 submitAnswer 调用 complete()，此方法立即返回答案内容
            String answer = answerFuture.get(ANSWER_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            // 如果答案是 null 或空白（用户跳过/未填写）
            if (answer == null || answer.isBlank()) {
                // 返回降级提示，让模型基于合理假设继续，保证任务流程不中断
                return "人类暂时没有提供更多信息，请基于已有信息和合理假设继续完成任务。";
            }

            // 正常情况：把人类的回答拼成文本返回给大模型，作为工具调用结果
            return "人类的回答是：" + answer.trim();
        } catch (IOException e) {
            // SSE 推送失败（如前端连接已断开），抛出运行时异常终止本次工具调用
            throw new IllegalStateException("向前端发送提问事件失败", e);
        } catch (Exception e) {
            // 捕获其余异常（主要是 get() 的超时 TimeoutException、中断异常等）
            // 降级处理：不抛出异常，返回提示文本让模型自行继续，保证智能体不会卡死
            return "等待人类回答超时或发生异常，请基于已有信息和合理假设继续完成任务。";
        } finally {
            // 无论成功失败，都要从等待表中移除该请求，防止内存泄漏和脏数据残留
            pendingAnswers.remove(requestId);
        }
    }

    /**
     * 由 /ai/manus/human-answer 接口调用，唤醒正在等待的 askHuman 工具。
     */
    // 提交答案入口：前端填完答案后 POST 到后端接口，接口再调用本方法
    public boolean submitAnswer(String requestId, String answer) {
        // 根据前端带回的请求 ID，从等待表中找到对应的 Future
        CompletableFuture<String> answerFuture = pendingAnswers.get(requestId);

        // 如果找不到（可能已超时被清理，或 ID 无效），返回 false 告知调用方提交失败
        if (answerFuture == null) {
            return false;
        }

        // 用答案填充 Future 并标记完成：阻塞在 askHuman 中的线程会被立即唤醒并拿到答案
        // 返回值表示是否填充成功（若 Future 已被其他途径完成则返回 false）
        return answerFuture.complete(answer);
    }

    // 使用 Java record 定义不可变的提问事件对象：包含请求 ID 和问题内容两个字段
    // record 自动生成构造器、getter、equals/hashCode/toString，作为 SSE 事件数据序列化发给前端
    public record HumanQuestionEvent(String requestId, String question) {
    }
}
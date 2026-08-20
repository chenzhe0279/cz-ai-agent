package com.cz.czaiagent.agent;

import com.cz.czaiagent.agent.model.AgentState;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import org.jsoup.internal.StringUtil;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.UserMessage;

import java.util.ArrayList;
import java.util.List;

/**
 * 抽象基础代理类，用于管理代理状态和执行流程。
 *
 * 提供状态转换、内存管理和基于步骤的执行循环的基础功能。
 * 子类必须实现step方法。
 */
@Data
@Slf4j
public abstract class BaseAgent {

    // 核心属性
    private String name;

    //系统预设提示词
    private String systemPrompt;

    //下一步提示词
    private String nextStepPrompt;

    //智能体初始状态
    private AgentState state = AgentState.IDLE;

    //执行控制，智能体最大步数和当前步数
    private int maxSteps = 10;
    private int currentStep = 0;

    // ========== 循环检测与处理机制相关属性 ==========

    //循环检测：相同内容在助手消息中重复出现的次数阈值（不含最后一条本身），达到即判定陷入循环
    //默认值 2 表示：最后一条助手消息的内容，在此前的助手消息中至少再出现 2 次（即同一内容共出现 3 次）才判定为循环
    //该值越小检测越敏感；@Data 会自动生成 getter/setter，子类（如 CzManus）可按需调整灵敏度
    private int duplicateThreshold = 2;

    //检测到循环时注入的干预提示词
    //声明为 static final 常量：所有智能体实例共享同一份文案，且运行期不可修改
    //其作用是提醒大模型"你正在重复同样的输出"，引导它放弃已尝试过的无效路径、改用新策略
    private static final String STUCK_PROMPT = "观察到重复响应。请考虑采用新的策略，避免重复已尝试过的无效路径。";

    //注入干预提示词前的原始下一步提示词，用于 cleanup 时恢复，避免污染下一轮对话
    //背景：CzManus 是单例 Bean，若 run() 结束后 nextStepPrompt 中残留干预提示词，
    //下一次 run() 会在第一轮思考时就把它发给大模型，因此必须先备份、结束后还原
    private String originalNextStepPrompt;

    //是否已注入过干预提示词，防止持续 stuck 时提示词无限膨胀
    //若不加此标记，每一步检测到循环都会再往 nextStepPrompt 前面拼一次 STUCK_PROMPT，
    //导致上下文越来越长、浪费 token 甚至撑爆模型上下文窗口
    private boolean stuckPromptInjected = false;

    //大模型(LLM)
    private ChatClient chatClient;

    // Memory（需要自主维护会话上下文）
    private List<Message> messageList = new ArrayList<>();

    /**
     * 运行代理
     *
     * @param userPrompt 用户提示词
     * @return 执行结果
     */
    public String run(String userPrompt){
        //智能体状态判断
        if(this.state != AgentState.IDLE){
            throw new RuntimeException("Cannot run agent from state: " + this.state);
        }
        //用户输入提示词状态判断
        if(StringUtil.isBlank(userPrompt)){
            throw new RuntimeException("Cannot run agent with empty user prompt");
        }
        //以上判断都通过，表示agent开始运行，需修改状态
        state = AgentState.RUNNING;
        //把用户提示词记录到消息上下文
        messageList.add(new UserMessage(userPrompt));
        //定义一个保存agent运行的结果列表
        List<String> results = new ArrayList<>();
        //开始运行
        try {
            for (int i = 0; i < maxSteps && state != AgentState.FINISHED; i++) {
                int stepNumber = i + 1;
                //设置当前步数
                currentStep = stepNumber;
                log.info("Executing step " + stepNumber + "/" + maxSteps);
                //单步执行
                String stepResult = step();
                //每一步执行完检查是否陷入循环，若陷入则注入干预提示词引导大模型更换策略
                //检测时机说明：放在 step() 之后，此时本步产生的助手消息/工具响应已写入 messageList，
                //能基于最新上下文判断；注入的干预提示词将在下一步 think() 中生效
                if (isStuck()) {
                    handleStuckState();
                }
                String result = "Step " + stepNumber + ": " + stepResult;
                results.add(result);
            }
            //检查是否超出了最大步数限制
            if(currentStep >= maxSteps){
                state = AgentState.FINISHED;
                results.add("Terminated: Reached max steps (" + maxSteps + ")");
            }
            //返回结果
            return String.join("\n", results);
        } catch (Exception e) {
            //如果发生异常，修改状态为ERROR
            state = AgentState.ERROR;
            log.error("Error executing agent", e);
            return "执行错误" + e.getMessage();
        }finally {
            // 清理资源
            this.cleanup();
        }
    }

    /**
     * 执行单个步骤
     *
     * @return 步骤执行结果
     */
    public abstract String step();

    /**
     * 检查智能体是否陷入循环
     * 从消息上下文末尾向前定位最后一条内容非空的助手（ASSISTANT）消息，
     * 统计其内容在更早的助手消息中重复出现的次数，达到阈值即判定为陷入循环。
     * 说明：工具执行后末尾可能是 ToolResponseMessage，因此不能直接取最后一条消息判断，
     * 需要向前找到最后一条助手消息，这样"重复最终回答"和"重复工具调用"两种循环都能检出。
     *
     * @return 是否陷入循环
     */
    protected boolean isStuck() {
        //取出当前消息上下文（即智能体的"记忆"），后续所有重复性判断都基于它
        List<Message> messages = messageList;
        //消息总数不足 2 条时不可能构成"重复"（至少需要一条参照 + 一条重复），直接返回未陷入循环
        if (messages.size() < 2) {
            return false;
        }

        //从末尾向前查找最后一条内容非空的助手消息
        //-1 是"未找到"的哨兵值
        int lastAssistantIndex = -1;
        //倒序遍历：从最新消息往回找，遇到的第一条满足条件的即为"最后一条助手消息"
        for (int i = messages.size() - 1; i >= 0; i--) {
            Message msg = messages.get(i);
            //两个筛选条件：
            //1. 消息类型必须是 ASSISTANT（大模型的回复），用户消息和系统提示词不参与重复统计
            //2. 文本内容必须非空——携带工具调用请求的助手消息文本常为 null/空串，无比较意义
            if (msg.getMessageType() == MessageType.ASSISTANT
                    && msg.getText() != null && !msg.getText().isEmpty()) {
                //记录下标后立即跳出循环，保证只取"最后一条"
                lastAssistantIndex = i;
                break;
            }
        }
        //整个上下文中都找不到有内容的助手消息，说明还没有可供比较的输出，不算循环
        //（例如刚执行完工具、末尾只有 ToolResponseMessage 且此前的助手消息都是空文本的情况）
        if (lastAssistantIndex == -1) {
            return false;
        }

        //计算相同内容在更早的助手消息中出现的次数
        //取出最后一条助手消息的文本，作为重复比对的基准内容
        String lastContent = messages.get(lastAssistantIndex).getText();
        //重复计数器：统计基准内容在历史助手消息中出现的次数
        int duplicateCount = 0;
        //从基准消息的前一条开始继续倒序遍历，只与"更早"的消息比较，避免自己和自己比较
        for (int i = lastAssistantIndex - 1; i >= 0; i--) {
            Message msg = messages.get(i);
            //同样是只看助手消息；用 equals 做全等比较，内容完全一致才算一次重复
            //（lastContent 已保证非空，调用 equals 不会空指针）
            if (msg.getMessageType() == MessageType.ASSISTANT
                    && lastContent.equals(msg.getText())) {
                duplicateCount++;
            }
        }

        //重复次数达到阈值即判定陷入循环
        //例：duplicateThreshold=2 时，同一内容累计出现 3 次（历史 2 次 + 最新 1 次）触发
        return duplicateCount >= duplicateThreshold;
    }

    /**
     * 处理陷入循环的状态
     * 将干预提示词前置注入 nextStepPrompt，下一轮 think() 会把它作为 UserMessage
     * 发给大模型，引导其放弃重复路径、尝试新策略
     */
    protected void handleStuckState() {
        //已注入过则跳过，避免持续 stuck 时提示词无限膨胀
        //只记录日志提示当前仍处于循环中，不再修改 nextStepPrompt
        if (stuckPromptInjected) {
            log.error(name + " 仍处于循环状态（干预提示词已注入，跳过重复注入）");
            return;
        }
        //备份原始提示词，供 cleanup() 恢复
        //必须在覆盖 nextStepPrompt 之前执行，否则原始内容将永久丢失
        originalNextStepPrompt = nextStepPrompt;
        //把干预提示词拼接到 nextStepPrompt 最前面（原有内容跟在后面，可能为 null 时用空串兜底）
        //前置的原因：让大模型在读到本轮引导语时，第一眼看到的是"换策略"的指令，权重更高
        //注入后无需立即发送给模型——下一轮 think() 开头会自动把 nextStepPrompt 包装成
        //UserMessage 追加进 messageList，随 Prompt 一起发给大模型
        nextStepPrompt = STUCK_PROMPT + "\n" + (nextStepPrompt != null ? nextStepPrompt : "");
        //置位注入标记，后续步骤即使再次检测到循环也不会重复拼接
        stuckPromptInjected = true;
        log.error(name + " 检测到循环状态，已注入干预提示词: " + STUCK_PROMPT);
    }

    /**
     * 清理资源
     * 在 run() 的 finally 中调用，无论正常结束还是异常都会执行：
     * 1. 清空消息上下文，释放本轮对话占用的内存
     * 2. 重置状态为 IDLE、步数归零，使单例智能体可以被再次 run()
     * 3. 恢复原始的下一步提示词，避免循环干预提示词污染下一轮对话
     */
    protected void cleanup() {
        messageList.clear();
        currentStep = 0;
        state = AgentState.IDLE;
        //仅在本轮确实注入过干预提示词时才执行恢复逻辑，未触发循环时保持 nextStepPrompt 原样
        if (stuckPromptInjected) {
            //用备份的原始提示词覆盖掉含干预内容的 nextStepPrompt，保证单例智能体下次 run() 干净启动
            nextStepPrompt = originalNextStepPrompt;
            //备份字段置空，释放引用、避免陈旧数据干扰下一轮
            originalNextStepPrompt = null;
            //复位注入标记，使下一轮对话重新具备"检测-注入"能力
            stuckPromptInjected = false;
        }
        log.info(name + " 资源清理完成，状态已重置为 IDLE");
    }
}

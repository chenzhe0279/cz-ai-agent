package com.cz.czaiagent.tools;

// 导入 Spring AI 的 @Tool 注解：标记方法为可被大模型调用的工具
import org.springframework.ai.tool.annotation.Tool;
// 导入 Spring AI 的 @ToolParam 注解：为工具方法的参数生成描述信息，帮助大模型理解参数用途
import org.springframework.ai.tool.annotation.ToolParam;

// 导入 Scanner 类：用于从标准输入流（控制台键盘）读取人类的回答
import java.util.Scanner;

/**
 * 人类交互工具（AskHuman）
 * 功能说明：为 AI Agent 提供"向人类求助"的能力，实现交互式执行（Human-in-the-loop）。
 * 当 AI 在执行任务过程中遇到以下情况时，可以自主决定调用本工具：
 * 1. 关键信息不足 —— 例如不知道用户的预算、具体时间、收件人等
 * 2. 需求存在歧义 —— 用户指令有多种理解方式，需要确认
 * 3. 需要用户偏好或确认 —— 例如多个方案中让用户选择、执行敏感操作前征得同意
 * 工作流程：AI 把问题传入 inquire 参数 → 控制台展示问题 → 人类输入回答 →
 * 回答作为工具结果返回给大模型 → 大模型基于回答继续完成任务
 */
public class AskHumanTool {

    // 全局共享的 Scanner 实例，绑定标准输入流 System.in（即控制台键盘输入）
    // 声明为 static final 的原因：
    // 1. System.in 全 JVM 只有一个，重复包装没有意义
    // 2. 避免多次 new Scanner(System.in) 后关闭流导致后续无法读取
    private static final Scanner SCANNER = new Scanner(System.in);

    /**
     * 向人类提问并等待回答
     * <p>
     * 该方法会被大模型以工具调用的形式触发（工具名为方法名 askHuman）。
     * 方法会阻塞当前执行线程，直到人类在控制台输入一行内容并回车。
     * 人类的回答将作为返回值传回给大模型，进入消息上下文供后续推理使用。
     *
     * @param inquire AI 想向人类提出的问题，例如"你的预算大概是多少？"
     * @return 人类的回答；若无法获取输入则返回引导 AI 自行决策的兜底提示
     */
    // @Tool 注解的 description 是给大模型看的"工具说明书"：
    // 明确告诉大模型本工具的用途和使用时机，让它能自主判断何时需要求助人类
    @Tool(description = """
            Use this tool to ask human for help when you lack critical information, \
            face ambiguous requirements, or need user preferences or confirmation to proceed. \
            Do not guess blindly when important details are missing.
            """)
    public String askHuman(
            // @ToolParam 注解描述参数含义，等价于参考实现中 parameters 里的 inquire 属性（string、必填）
            @ToolParam(description = "The question you want to ask human.") String inquire) {
        // ========== 第一步：在控制台展示 AI 的问题 ==========
        // 打印分隔标题，让人类一眼注意到"AI 正在等待我的输入"
        System.out.println("\n========== AI 向人类求助 ==========");
        // 输出大模型传入的问题内容
        System.out.println("AI 的问题：" + inquire);
        // 提示人类操作方式：输入回答后按回车提交
        System.out.println("请在控制台输入你的回答后回车：");

        // ========== 第二步：阻塞等待人类输入 ==========
        // 先定义回答变量并置空，用于后续判空兜底
        String answer = null;
        // hasNextLine() 会阻塞线程，直到控制台读入一行输入（或输入流被关闭）
        // 判断它主要是防御"无控制台/输入流被关闭"的自动化运行环境
        if (SCANNER.hasNextLine()) {
            // nextLine() 读取人类输入的一整行内容（不含换行符）
            answer = SCANNER.nextLine();
        }

        // ========== 第三步：输入兜底处理 ==========
        // 两种情况视为"人类未提供有效信息"：
        // 1. answer 为 null —— 输入流被关闭，根本读不到内容
        // 2. answer 去除首尾空白后为空 —— 人类直接回车跳过
        if (answer == null || answer.trim().isEmpty()) {
            // 返回兜底提示，引导大模型不要干等，基于已有信息和合理假设继续任务
            return "人类暂时没有提供更多信息，请基于已有信息和合理假设继续完成任务。";
        }

        // ========== 第四步：把人类回答返回给大模型 ==========
        // trim() 去掉首尾多余空白，避免干扰大模型理解
        // 该返回值会被封装为 ToolResponseMessage 写入消息上下文，
        // 下一轮 think() 时大模型就能看到人类的回答并据此继续规划
        return "人类的回答是：" + answer.trim();
    }
}
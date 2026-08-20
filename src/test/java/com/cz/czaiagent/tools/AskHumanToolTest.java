package com.cz.czaiagent.tools;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;


@SpringBootTest
class AskHumanToolTest {

    /**
     * 测试向人类提问并获取回答
     */
    @Test
    public void testAskHuman() {
        // 手动创建工具实例（与生产环境 ToolRegistration 中的创建方式一致，工具本身无外部依赖）
        AskHumanTool tool = new AskHumanTool();
        // 模拟大模型传入的问题：询问用户的预算偏好
        String inquire = "为了制定约会计划，请问你的预算大概是多少？有什么特别的偏好吗？";
        // 调用工具：控制台会打印问题并等待输入，输入回答后继续执行
        String result = tool.askHuman(inquire);
        // 输出工具返回值，观察"人类的回答是：xxx"格式是否正确
        System.out.println(result);
        // 断言返回值非空：无论是否输入内容，工具都会返回有效结果（回答或兜底提示）
        assertNotNull(result);
    }
}
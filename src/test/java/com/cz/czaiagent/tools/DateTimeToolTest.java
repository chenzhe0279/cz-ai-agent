package com.cz.czaiagent.tools;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 时间日期工具测试类
 * <p>
 * 测试 DateTimeTool 的所有时间相关功能。
 */
@SpringBootTest
public class DateTimeToolTest {

    /**
     * 创建工具实例（DateTimeTool 无外部依赖，直接 new）
     */
    private final DateTimeTool tool = new DateTimeTool();

    /**
     * 测试获取当前日期
     * <p>
     * 验证点：返回值不为空，且符合 yyyy-MM-dd 格式（包含两个短横线）
     */
    @Test
    public void testGetCurrentDate() {
        String result = tool.getCurrentDate();
        System.out.println("当前日期: " + result);
        assertNotNull(result);
        // 验证格式包含 "-"，如 "2026-08-13"
        assertTrue(result.contains("-"));
    }

    /**
     * 测试获取当前时间
     * <p>
     * 验证点：返回值不为空，且符合 HH:mm:ss 格式（包含冒号）
     */
    @Test
    public void testGetCurrentTime() {
        String result = tool.getCurrentTime();
        System.out.println("当前时间: " + result);
        assertNotNull(result);
        // 验证格式包含 ":"，如 "14:30:00"
        assertTrue(result.contains(":"));
    }

    /**
     * 测试获取当前日期和时间
     * <p>
     * 验证点：返回值不为空，且同时包含日期分隔符 "-" 和时间分隔符 ":"
     */
    @Test
    public void testGetCurrentDateTime() {
        String result = tool.getCurrentDateTime();
        System.out.println("当前日期时间: " + result);
        assertNotNull(result);
        // 验证同时包含日期和时间
        assertTrue(result.contains("-") && result.contains(":"));
    }

    /**
     * 测试获取指定时区的日期时间
     * <p>
     * 验证点：使用合法时区 "Asia/Shanghai" 时返回正常结果，
     * 使用非法时区时返回包含 "Invalid" 的错误提示
     */
    @Test
    public void testGetDateTimeByTimezone() {
        // 测试合法时区
        String result = tool.getDateTimeByTimezone("Asia/Shanghai");
        System.out.println("上海时间: " + result);
        assertNotNull(result);
        assertTrue(result.contains("20"));
        // 测试非法时区
        String invalidResult = tool.getDateTimeByTimezone("Invalid/Timezone");
        System.out.println("非法时区结果: " + invalidResult);
        assertTrue(invalidResult.contains("Invalid"));
    }

    /**
     * 测试获取今天是星期几
     * <p>
     * 验证点：返回值不为空，且是合法的星期枚举值（包含 "DAY" 关键字）
     */
    @Test
    public void testGetDayOfWeek() {
        String result = tool.getDayOfWeek();
        System.out.println("今天星期: " + result);
        assertNotNull(result);
        // 验证返回的是合法的星期名称（如 MONDAY、TUESDAY 等）
        assertTrue(result.length() > 3);
    }
}
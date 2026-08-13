package com.cz.czaiagent.tools;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 时间日期工具
 * 功能说明：为 AI Agent 提供时间感知能力，让 AI 知道"现在几点"、"今天星期几"、
 * "纽约现在几点"、"7天后是几号"等信息。
 * 包含以下功能：
 * 1. 获取当前日期（年-月-日）
 * 2. 获取当前时间（时:分:秒）
 * 3. 获取当前日期+时间
 * 4. 获取指定时区的日期时间（如"东京现在几点"）
 * 5. 获取今天是星期几
 */
public class DateTimeTool {

    /**
     * 获取当前日期
     * <p>
     * 使用 LocalDate.now() 获取服务器当前日期，格式为 yyyy-MM-dd
     * 就像看日历上的"今天几月几号"
     *
     * @return 当前日期字符串，例如 "2026-08-13"
     */
    @Tool(description = "Get the current date in yyyy-MM-dd format")
    public String getCurrentDate() {
        // LocalDate.now() 获取当前日期，toString() 默认输出 yyyy-MM-dd 格式
        return LocalDate.now().toString();
    }

    /**
     * 获取当前时间
     * <p>
     * 使用 LocalTime.now() 获取当前时刻，格式化为 HH:mm:ss
     * 就像看钟表上的"现在几点几分几秒"
     *
     * @return 当前时间字符串，例如 "14:30:00"
     */
    @Tool(description = "Get the current time in HH:mm:ss format")
    public String getCurrentTime() {
        // 定义时间格式为 时:分:秒
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm:ss");
        // LocalTime.now() 获取当前时间，format() 按指定格式输出
        return LocalTime.now().format(formatter);
    }

    /**
     * 获取当前日期和时间
     * <p>
     * 同时获取"几月几号"和"几点几分"，格式为 yyyy-MM-dd HH:mm:ss
     * 就像同时看日历和钟表
     *
     * @return 当前日期时间字符串，例如 "2026-08-13 14:30:00"
     */
    @Tool(description = "Get the current date and time in yyyy-MM-dd HH:mm:ss format")
    public String getCurrentDateTime() {
        // 定义日期时间格式
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        // LocalDateTime.now() 同时获取日期和时间
        return LocalDateTime.now().format(formatter);
    }

    /**
     * 获取指定时区的当前日期时间
     * <p>
     * 地球分为 24 个时区，不同城市时间不同。
     * 比如北京是 UTC+8，纽约是 UTC-5，当北京是晚上 8 点时，纽约还是早上 7 点。
     * 时区 ID 格式遵循 IANA 标准，常见值：
     * - Asia/Shanghai（北京时间）
     * - America/New_York（纽约时间）
     * - Europe/London（伦敦时间）
     * - UTC（世界协调时间）
     *
     * @param timezone 时区 ID，例如 "Asia/Shanghai"、"America/New_York"
     * @return 指定时区的日期时间字符串，包含时区缩写；如果时区无效则返回错误信息
     */
    @Tool(description = "Get the current date and time for a specific timezone, e.g. Asia/Shanghai, America/New_York")
    public String getDateTimeByTimezone(
            @ToolParam(description = "Timezone ID, e.g. Asia/Shanghai, America/New_York, Europe/London, UTC") String timezone) {
        try {
            // 第一步：根据传入的时区名称创建 ZoneId 对象
            ZoneId zoneId = ZoneId.of(timezone);
            // 第二步：获取该时区的当前日期时间（包含时区信息）
            ZonedDateTime zdt = ZonedDateTime.now(zoneId);
            // 第三步：按 "年-月-日 时:分:秒 时区缩写" 格式输出
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z");
            return zdt.format(formatter);
        } catch (Exception e) {
            // 时区 ID 不合法时（如拼写错误），会抛出异常
            return "Invalid timezone: " + timezone + ". Error: " + e.getMessage();
        }
    }

    /**
     * 获取今天是星期几
     * <p>
     * 返回英文的星期名称，如 MONDAY、TUESDAY 等。
     * 就像看日历顶部的"星期几"。
     *
     * @return 星期名称，例如 "MONDAY"、"WEDNESDAY"
     */
    @Tool(description = "Get the day of week for today")
    public String getDayOfWeek() {
        // LocalDate.now().getDayOfWeek() 返回 DayOfWeek 枚举值
        return LocalDate.now().getDayOfWeek().toString();
    }

    /**
     * 计算从今天起若干天后的日期
     * <p>
     * 正数表示往后推（未来），负数表示往前推（过去）。
     * 比如 +7 就是 7 天后，-3 就是 3 天前。
     * 就像在日历上"往后翻几页"或"往前翻几页"。
     *
     * @param days 要加减的天数，正数表示未来，负数表示过去。例如 7 表示 7 天后，-3 表示 3 天前
     * @return 计算后的日期及描述信息
     */
   /* @Tool(description = "Calculate a date by adding or subtracting days from today, use negative number to subtract")
    public String calculateDate(
            @ToolParam(description = "Number of days to add (negative to subtract), e.g. 7 means 7 days later, -3 means 3 days ago") int days) {
        // 第一步：基于今天的日期进行加减计算
        LocalDate result = LocalDate.now().plusDays(days);
        // 第二步：根据正负值选择描述词（after 表示未来，ago 表示过去）
        String operation = days >= 0 ? "after" : "ago";
        // 第三步：拼接可读的返回信息，取绝对值避免显示"-7 days after"
        return Math.abs(days) + " days " + operation + " from today is: " + result.toString();
    }*/
}
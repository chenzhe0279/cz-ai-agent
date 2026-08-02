package com.cz.czaiagent.chatmemory;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.*;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j // 使用 Lombok 的 @Slf4j 注解，自动生成名为 log 的日志对象，方便进行日志记录
public class MysqlChatMemory implements ChatMemory { // 定义 MysqlChatMemory 类，实现 Spring AI 提供的 ChatMemory 接口

    // 声明一个 final 修饰的 JdbcTemplate 实例，用于执行 MySQL 数据库的 CRUD 操作
    private final JdbcTemplate jdbcTemplate;

    // 构造函数，通过 Spring 的依赖注入机制接收 JdbcTemplate 实例
    public MysqlChatMemory(JdbcTemplate jdbcTemplate) {
        // 将传入的 JdbcTemplate 赋值给当前类的成员变量
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override // 标记该方法为重写自 ChatMemory 接口的方法
    public void add(String conversationId, Message message) { // 重写单条消息添加方法
        // 将单条消息包装成 List，并委托给批量添加方法处理，复用代码逻辑
        add(conversationId, List.of(message));
    }

    @Override // 标记该方法为重写自 ChatMemory 接口的方法
    public void add(String conversationId, List<Message> messages) { // 重写批量消息添加方法
        // 定义插入数据的 SQL 语句，使用 ? 作为占位符以防止 SQL 注入
        String sql = "INSERT INTO chat_memory (conversation_id, message_type, content) VALUES (?, ?, ?)";
        // 使用 Stream API 将 Message 列表转换为批量更新所需的参数列表
        List<Object[]> batchArgs = messages.stream()
                // 将每条 Message 映射为 Object 数组，包含会话ID、消息类型枚举的名称、消息文本内容
                .map(msg -> new Object[]{conversationId, msg.getMessageType().name(), msg.getText()})
                // 将 Stream 收集为不可变的 List 集合
                .toList();
        // 调用 JdbcTemplate 的 batchUpdate 方法执行批量插入操作，提高数据库写入性能
        jdbcTemplate.batchUpdate(sql, batchArgs);
        // 使用 log 对象记录 info 级别日志，输出成功保存的消息条数和对应的会话 ID
        log.info("保存 {} 条消息到会话 {}", messages.size(), conversationId);
    }

    @Override // 标记该方法为重写自 ChatMemory 接口的方法
    public List<Message> get(String conversationId, int lastN) { // 重写获取消息方法，获取指定会话最近的 lastN 条消息
        // 使用 Java 15+ 的文本块语法定义查询 SQL 语句
        String sql = """
                SELECT message_type, content FROM chat_memory -- 查询消息类型和消息内容字段
                WHERE conversation_id = ? -- 筛选条件：匹配传入的会话 ID
                ORDER BY id DESC LIMIT ? -- 按主键 ID 降序排列（最新的在前），并限制返回的最大行数为 lastN
                """;
        // 执行 SQL 查询，将结果集映射为 List<Map<String, Object>>，每个 Map 代表数据库中的一行记录
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, conversationId, lastN);
        // 创建一个空的 ArrayList，用于存放从数据库记录转换而来的 Message 对象
        List<Message> messages = new ArrayList<>();
        // 从后向前遍历查询结果（因为 SQL 中使用了 DESC 降序，倒序遍历可恢复消息的时间正序）
        for (int i = rows.size() - 1; i >= 0; i--) {
            // 获取当前索引处的行数据 Map
            Map<String, Object> row = rows.get(i);
            // 从 Map 中取出 "message_type" 字段的值，并强制转换为 String 类型
            String type = (String) row.get("message_type");
            // 从 Map 中取出 "content" 字段的值，并强制转换为 String 类型
            String content = (String) row.get("content");
            // 使用 Java 14+ 的 switch 表达式，根据消息类型实例化对应的 Message 子类，并添加到列表中
            messages.add(switch (type) {
                case "USER" -> new UserMessage(content); // 如果类型是 USER，创建 UserMessage 实例
                case "ASSISTANT" -> new AssistantMessage(content); // 如果类型是 ASSISTANT，创建 AssistantMessage 实例
                case "SYSTEM" -> new SystemMessage(content); // 如果类型是 SYSTEM，创建 SystemMessage 实例
                default -> new UserMessage(content); // 对于未知类型，默认作为 UserMessage 处理以保证兼容性
            });
        }
        // 返回转换完成后的 Message 对象列表
        return messages;
    }

    @Override // 标记该方法为重写自 ChatMemory 接口的方法
    public void clear(String conversationId) { // 重写清空方法，用于删除指定会话的所有历史记忆
        // 执行 DELETE SQL 语句，根据会话 ID 删除 chat_memory 表中的相关记录
        jdbcTemplate.update("DELETE FROM chat_memory WHERE conversation_id = ?", conversationId);
        // 使用 log 对象记录 info 级别日志，提示指定会话的记忆已被清空
        log.info("清空会话 {} 的记忆", conversationId);
    }
}


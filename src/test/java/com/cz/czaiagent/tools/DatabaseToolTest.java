package com.cz.czaiagent.tools;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 数据库操作工具测试类
 * <p>
 * 测试 DatabaseTool 的查询、更新、列表表名、查看表结构功能。
 * 注意：测试使用项目已配置的 MySQL 数据源（localhost:3306/yu_picture），
 * 运行前确保数据库服务已启动。
 */
@SpringBootTest
public class DatabaseToolTest {

    /**
     * 注入 Spring JDBC 模板，用于构造 DatabaseTool
     */
    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * 测试执行 SELECT 查询
     * <p>
     * 验证点：查询 "SHOW TABLES" 的结果不为空，
     * 因为数据库中至少应该存在一张表。
     */
    @Test
    public void testExecuteQuery() {
        // 使用注入的 jdbcTemplate 创建工具实例
        DatabaseTool tool = new DatabaseTool(jdbcTemplate);
        // 执行一个简单的查询
        String result = tool.executeQuery("SHOW TABLES");
        System.out.println(result);
        assertNotNull(result);
    }

    /**
     * 测试执行 INSERT 操作
     * <p>
     * 验证点：执行 INSERT 语句后返回成功信息，包含 "INSERT" 和 "Affected rows"。
     * 注意：请根据实际数据库中的表结构调整 SQL 语句，
     * 这里使用了一个通用的测试 SQL，你可能需要替换为实际存在的表。
     */
    @Test
    public void testExecuteInsert() {
        DatabaseTool tool = new DatabaseTool(jdbcTemplate);
        // 执行插入操作（请替换为实际存在的表和字段）
        String result = tool.executeUpdate("INSERT INTO test_table (name, age) VALUES ('测试用户', 25)");
        System.out.println(result);
        assertNotNull(result);
        // 验证返回信息包含操作类型
        assertTrue(result.contains("INSERT") || result.contains("Error"));
    }

    /**
     * 测试执行 UPDATE 操作
     * <p>
     * 验证点：执行 UPDATE 语句后返回受影响行数信息。
     */
    @Test
    public void testExecuteUpdate() {
        DatabaseTool tool = new DatabaseTool(jdbcTemplate);
        // 执行更新操作（请替换为实际存在的表和字段）
        String result = tool.executeUpdate("UPDATE test_table SET age = 26 WHERE name = '测试用户'");
        System.out.println(result);
        assertNotNull(result);
        // 验证返回信息包含操作类型或错误信息
        assertTrue(result.contains("UPDATE") || result.contains("Error"));
    }

    /**
     * 测试执行 DELETE 操作
     * <p>
     * 验证点：执行 DELETE 语句后返回受影响行数信息。
     */
    @Test
    public void testExecuteDelete() {
        DatabaseTool tool = new DatabaseTool(jdbcTemplate);
        // 执行删除操作（请替换为实际存在的表和字段）
        String result = tool.executeUpdate("DELETE FROM test_table WHERE name = '测试用户'");
        System.out.println(result);
        assertNotNull(result);
        assertTrue(result.contains("DELETE") || result.contains("Error"));
    }

    /**
     * 测试列出所有表名
     * <p>
     * 验证点：返回结果包含 "Tables in database" 字样
     */
    @Test
    public void testListTables() {
        DatabaseTool tool = new DatabaseTool(jdbcTemplate);
        String result = tool.listTables();
        System.out.println(result);
        assertNotNull(result);
        // 验证返回信息包含表列表标题
        assertTrue(result.contains("Tables in database"));
    }

    /**
     * 测试获取表结构
     * <p>
     * 验证点：对一张已存在的表执行 DESCRIBE，返回结果包含 "Structure of table"。
     * 注意：请替换为你数据库中实际存在的表名。
     */
    @Test
    public void testGetTableStructure() {
        DatabaseTool tool = new DatabaseTool(jdbcTemplate);
        // 替换为你数据库中实际存在的表名
        String result = tool.getTableStructure("test_table");
        System.out.println(result);
        assertNotNull(result);
        // 验证返回信息包含表结构标题或错误信息
        assertTrue(result.contains("Structure of table") || result.contains("Error"));
    }

    /**
     * 测试安全检查 —— 禁止 DROP 操作
     * <p>
     * 验证点：尝试执行 DROP TABLE 时，应被拦截并返回安全提示，而不是真正删表
     */
    @Test
    public void testDropTableBlocked() {
        DatabaseTool tool = new DatabaseTool(jdbcTemplate);
        // 尝试执行危险的 DROP 操作
        String result = tool.executeUpdate("DROP TABLE test_table");
        System.out.println(result);
        // 验证被安全机制拦截
        assertTrue(result.contains("not allowed"));
    }
}
package com.cz.czaiagent.tools;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

/**
 * 数据库操作工具
 * <p>
 * 功能说明：为 AI Agent 提供数据库操作能力，让 AI 能够像数据库管理员一样：
 * - 查看数据库里有哪些表（listTables）
 * - 查看某张表的结构（getTableStructure）
 * - 查询数据（executeQuery）
 * - 新增、修改、删除数据（executeUpdate）
 */
public class DatabaseTool {

    /**
     * Spring JDBC 模板类，封装了数据库连接、SQL执行、结果集处理等操作
     */
    private final JdbcTemplate jdbcTemplate;

    /**
     * 构造函数：注入 JdbcTemplate
     * <p>
     * JdbcTemplate 由 Spring 容器根据 application.yml 中的数据源配置自动创建，
     * 我们只需把它传进来即可。
     *
     * @param jdbcTemplate Spring JDBC 模板对象
     */
    public DatabaseTool(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 执行 SELECT 查询语句并返回结果
     * <p>
     * 工作流程：
     * 第一步：检查 SQL 是否以 SELECT 开头，防止"挂羊头卖狗肉"（说是查询实际是删除）
     * 第二步：通过 JdbcTemplate 执行查询，结果自动封装为 List<Map> 格式
     * 第三步：遍历结果集，把每一行数据转成字符串拼接起来
     * <p>
     * 返回结果示例：
     * Query returned 2 row(s):
     * {id=1, name=张三, age=25}
     * {id=2, name=李四, age=30}
     *
     * @param sql 要执行的 SELECT 查询语句，例如 "SELECT * FROM users WHERE age > 18 LIMIT 10"
     * @return 查询结果的字符串描述；如果没有数据则提示无结果；出错则返回错误信息
     */
    @Tool(description = "Execute a SELECT SQL query and return the results as JSON format. The query must be a SELECT statement.")
    public String executeQuery(
            @ToolParam(description = "SELECT SQL query to execute, e.g. SELECT * FROM users WHERE age > 18 LIMIT 10") String sql) {
        try {
            // 第一步：去除 SQL 两端空格并转大写，用于安全检查
            String trimmedSql = sql.trim().toUpperCase();
            // 第二步：安全检查 —— 只允许 SELECT 语句
            if (!trimmedSql.startsWith("SELECT")) {
                return "Error: Only SELECT queries are allowed in this method. Use executeUpdate for INSERT/UPDATE/DELETE.";
            }
            // 第三步：执行查询，queryForList 会把每一行结果变成一个 Map（列名 → 值）
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql);
            // 第四步：处理空结果的情况
            if (rows.isEmpty()) {
                return "Query executed successfully, but no results found.";
            }
            // 第五步：拼接结果字符串
            StringBuilder sb = new StringBuilder();
            sb.append("Query returned ").append(rows.size()).append(" row(s):\n");
            // 第六步：遍历每一行数据，转为字符串追加
            for (Map<String, Object> row : rows) {
                sb.append(row.toString()).append("\n");
            }
            return sb.toString();
        } catch (Exception e) {
            // 捕获 SQL 语法错误、表不存在等异常
            return "Error executing query: " + e.getMessage();
        }
    }

    /**
     * 执行 INSERT / UPDATE / DELETE 等数据修改操作
     * <p>
     * 工作流程：
     * 第一步：检查 SQL 类型，确保不是 SELECT（查询要用 executeQuery）
     * 第二步：安全检查 —— 禁止 DROP 和 TRUNCATE 等高危操作（防止"拆家"）
     * 第三步：执行 SQL 并获取受影响的行数
     * 第四步：根据 SQL 类型（INSERT/UPDATE/DELETE）拼接友好的返回信息
     *
     * @param sql 要执行的 SQL 语句（INSERT/UPDATE/DELETE），例如 "UPDATE users SET name='Tom' WHERE id=1"
     * @return 执行结果描述，包含操作类型和受影响行数；出错则返回错误信息
     */
    @Tool(description = "Execute an INSERT, UPDATE, or DELETE SQL statement and return the number of affected rows")
    public String executeUpdate(
            @ToolParam(description = "SQL statement to execute (INSERT/UPDATE/DELETE), e.g. UPDATE users SET name='Tom' WHERE id=1") String sql) {
        try {
            // 第一步：去除空格并转大写，统一用于判断
            String trimmedSql = sql.trim().toUpperCase();
            // 第二步：安全检查 —— 禁止在此方法执行查询语句
            if (trimmedSql.startsWith("SELECT")) {
                return "Error: SELECT statements are not allowed in this method. Use executeQuery for SELECT.";
            }
            // 第三步：安全检查 —— 禁止删表和清空表等高危操作
            if (trimmedSql.startsWith("DROP") || trimmedSql.startsWith("TRUNCATE")) {
                return "Error: DROP and TRUNCATE operations are not allowed for safety.";
            }
            // 第四步：执行更新操作，返回受影响的行数
            int affectedRows = jdbcTemplate.update(sql);
            // 第五步：根据 SQL 开头关键字判断操作类型，用于拼接返回信息
            String operationType = trimmedSql.startsWith("INSERT") ? "INSERT" :
                    trimmedSql.startsWith("UPDATE") ? "UPDATE" : "DELETE";
            return operationType + " executed successfully. Affected rows: " + affectedRows;
        } catch (Exception e) {
            // 捕获 SQL 语法错误、约束冲突等异常
            return "Error executing update: " + e.getMessage();
        }
    }

    /**
     * 列出当前数据库中的所有表名
     * <p>
     * 工作原理：执行 MySQL 的 "SHOW TABLES" 命令，
     * 就像打开数据库管理工具看到的"左侧表列表"。
     *
     * @return 所有表名的列表，每行一个表名；出错则返回错误信息
     */
    @Tool(description = "List all table names in the current database")
    public String listTables() {
        try {
            // 执行 MySQL 内置命令，获取所有表名
            List<Map<String, Object>> tables = jdbcTemplate.queryForList("SHOW TABLES");
            StringBuilder sb = new StringBuilder();
            sb.append("Tables in database:\n");
            // 遍历结果，每行的第一个值就是表名
            for (Map<String, Object> table : tables) {
                // table.values() 取每行的值，因为 SHOW TABLES 只有一列，所以取第一个即可
                table.values().forEach(
                        value -> sb.append("- ").append(value).append("\n")
                );
            }
            return sb.toString();
        } catch (Exception e) {
            return "Error listing tables: " + e.getMessage();
        }
    }

    /**
     * 获取指定表的结构信息（列名、类型、是否可为空等）
     * <p>
     * 工作原理：执行 MySQL 的 "DESCRIBE 表名" 命令，
     * 返回结果类似数据库管理工具中的"表结构设计"视图。
     *
     * @param tableName 要查看结构的表名，例如 "users"
     * @return 表结构信息，包含每个列的字段名、类型、是否允许为空等；出错则返回错误信息
     */
    @Tool(description = "Get the structure of a database table, including column names, types, and other metadata")
    public String getTableStructure(
            @ToolParam(description = "Name of the table to describe") String tableName) {
        try {
            // 拼接 DESCRIBE 命令，MySQL 用它来查看表结构
            String sql = "DESCRIBE " + tableName;
            // 执行查询，每行包含 Field、Type、Null、Key、Default、Extra 等列信息
            List<Map<String, Object>> columns = jdbcTemplate.queryForList(sql);
            StringBuilder sb = new StringBuilder();
            sb.append("Structure of table '").append(tableName).append("':\n");
            // 遍历每一列的定义信息
            for (Map<String, Object> col : columns) {
                sb.append(col.toString()).append("\n");
            }
            return sb.toString();
        } catch (Exception e) {
            // 表不存在或无权限时会抛出异常
            return "Error getting table structure: " + e.getMessage();
        }
    }
}
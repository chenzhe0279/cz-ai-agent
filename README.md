# cz-ai-agent

基于 Spring Boot 与 Spring AI 的 Java 21 AI Agent 示例项目。主服务提供恋爱咨询 RAG、工具调用和多步 ReAct Agent 的实现；仓库中的 `cz-image-search-mcp-server` 是一个可由主服务通过 MCP（stdio）调用的图片检索子服务。

> 这是一个偏学习/实验性质的工程：目前仅暴露健康检查 HTTP 接口，`LoveApp`、`CzManus` 等核心能力以 Spring 组件和测试用例形式提供，尚未封装为完整的业务 API。

## 功能概览

- 恋爱咨询：基于通义千问（DashScope）的普通对话、带聊天记忆的对话及结构化恋爱报告。
- RAG：读取 `src/main/resources/document/` 中的恋爱知识 Markdown，在内存向量库中检索；支持查询改写、检索增强和 MySQL `love_knowledge` 表降级检索。
- Agent：`CzManus` 继承 ReAct 代理框架，支持最大步数、状态管理和重复响应检测/干预。
- 工具调用：本地文件读写、网络搜索与抓取、资源下载、终端命令、PDF 生成、邮件、日期时间、人工确认、终止任务等；可合并 MCP 提供的远程工具。
- MCP 图片搜索：子项目通过 Pexels 搜索图片，默认作为 stdio MCP Server 被主服务拉起。
- 基础设施：MySQL 聊天记忆实现、统一响应/异常处理、CORS、Knife4j / OpenAPI 文档与 Sa-Token 依赖。

## 技术栈

- Java 21、Maven、Spring Boot 3.4.x
- Spring AI 1.0.0-M6 / MCP Server 1.0.0-M7
- Alibaba DashScope（通义千问）、LangChain4j、Ollama（依赖已引入）
- MySQL、可选 PostgreSQL + pgvector（当前 pgvector 配置类已禁用）
- iText 9、Jsoup、Hutool、JavaMail、Knife4j

## 项目结构

```text
.
├── src/main/java/com/cz/czaiagent/
│   ├── agent/             # BaseAgent、ReActAgent、ToolCallAgent、CzManus
│   ├── app/               # LoveApp 和启动时的 Spring AI 演示 ITApp
│   ├── advisor/           # 日志、敏感词、重复阅读等 ChatClient Advisor
│   ├── chatmemory/        # 文件与 MySQL 聊天记忆
│   ├── controller/        # 健康检查接口
│   ├── rag/               # 文档加载、向量检索、查询转换与 RAG Advisor
│   ├── tools/             # Agent 本地工具及统一注册
│   └── common/ exception/ model/ config/ ...
├── src/main/resources/
│   ├── document/          # 恋爱知识库 Markdown
│   ├── prompts/           # 恋爱专家与报告提示词模板
│   ├── application*.yaml  # 主服务配置
│   └── mcp-image-servers.json
├── cz-image-search-mcp-server/ # 独立的 Pexels MCP 图片搜索服务
├── sql/create_table.sql   # MySQL 建表及恋爱知识示例数据
└── src/test/              # Agent、RAG、工具等示例/测试
```

## 快速开始

### 1. 前置条件

- JDK 21
- MySQL 8.x（默认连接 `localhost:3306`）
- Maven 3.9+，或直接使用仓库的 Maven Wrapper
- DashScope API Key
- 如启用邮件、网页搜索、图片搜索：相应服务的账号与 API Key

### 2. 初始化数据库

主服务默认使用名为 `yu_picture` 的 MySQL 数据库。执行建表脚本：

```powershell
mysql -u root -p < sql/create_table.sql
```

脚本会创建聊天记忆、用户、图片、空间和恋爱知识等表，并插入部分 `love_knowledge` 示例数据。

### 3. 配置本地密钥

`application.yaml` 默认激活 `local` Profile。请在本机创建或修改 `src/main/resources/application-local.yaml`，使用自己的凭据，切勿提交真实密钥。示例：

```yaml
spring:
  ai:
    dashscope:
      api-key: ${DASHSCOPE_API_KEY}
      chat:
        options:
          model: qwen-max
  mail:
    host: smtp.example.com
    port: 465
    username: ${MAIL_USERNAME}
    password: ${MAIL_PASSWORD}

baidu:
  translate:
    app-id: ${BAIDU_TRANSLATE_APP_ID}
    security-key: ${BAIDU_TRANSLATE_SECRET}

search-api:
  api-key: ${SEARCH_API_KEY}
```

并按需设置环境变量：

```powershell
$env:DASHSCOPE_API_KEY = "your-key"
$env:MAIL_USERNAME = "your-email"
$env:MAIL_PASSWORD = "your-mail-password"
$env:SEARCH_API_KEY = "your-search-key"
```

### 4. 构建图片 MCP 子服务

主服务的 `mcp-image-servers.json` 会执行子项目构建产物，因此先执行：

```powershell
./mvnw.cmd -f cz-image-search-mcp-server/pom.xml clean package
```

在 `cz-image-search-mcp-server/src/main/resources/application.yaml` 中配置 Pexels API Key，或将其改为由环境变量读取。可通过 `IMAGE_SEARCH_LIMIT` 控制单次返回的图片数，默认由 MCP 配置设为 `5`。

### 5. 启动主服务

```powershell
./mvnw.cmd spring-boot:run
```

默认地址为 `http://localhost:8123/api`。

## 可用接口与文档

| 项目 | 地址 |
| --- | --- |
| 健康检查 | `GET /api/helth`（项目源码中的路径拼写即为 `helth`） |
| Swagger UI | `http://localhost:8123/api/swagger-ui.html` |
| OpenAPI JSON | `http://localhost:8123/api/v3/api-docs` |

目前没有将 `LoveApp` 或 `CzManus` 对外暴露为 REST Controller；可参考 `src/test/java` 中的测试用例，或在新的 Controller/Service 中注入这些组件。

## 核心组件

| 组件 | 作用 |
| --- | --- |
| `LoveApp` | 普通对话、结构化报告、RAG、MySQL 降级检索、工具调用与 MCP 调用入口。 |
| `LoveAppVectorStoreConfig` | 启动时加载本地恋爱 Markdown 并建立内存 `SimpleVectorStore`。 |
| `MysqlChatMemory` / `FileBaseChatMemory` | 两种聊天记忆实现；`LoveApp` 当前实际使用内存记忆，MySQL 实现保留为可切换选项。 |
| `CzManus` | 使用工具调用模型的多步 ReAct Agent。 |
| `ToolRegistration` | 汇总本地工具与 MCP Client 返回的工具回调。 |
| `ImageSearchTool` | MCP 子服务暴露的 `searchImage` 工具，调用 Pexels API 并返回中等尺寸图片 URL。 |

## 安全提示

- 当前仓库的配置文件中已经存在数据库密码、模型/搜索/翻译/Pexels API Key 和邮件凭据。请立即在各服务端撤销或轮换这些凭据，并迁移到环境变量、密钥管理服务或未提交的本地配置文件。
- `TerminalOperationTool` 可执行终端命令，`FileOperationTool` 可读写文件，`DatabaseTool` 虽默认未注册但代码允许执行 SQL 写操作。对外提供 Agent 能力前，应加入身份认证、路径/命令白名单、最小权限及审计。
- `ITApp` 是 `CommandLineRunner`，应用启动会自动发起三次模型调用；生产启动前建议移除、注释或增加 Profile 条件，避免额外费用和启动副作用。

## 测试

```powershell
./mvnw.cmd test
./mvnw.cmd -f cz-image-search-mcp-server/pom.xml test
```

部分测试和组件依赖数据库、模型服务、邮件或外部 API；在未配置凭据或网络受限的环境中可能失败。可按模块或类单独执行测试。

## 后续建议

- 将恋爱咨询与 Agent 能力封装为经过鉴权和限流保护的 REST API。
- 将 `application-local.yaml` 从版本控制中移除，改用环境变量或密钥服务。
- 为 MCP 服务增加独立的部署方式与健康检查；主服务在调用前校验子服务 JAR 是否已构建。
- 根据数据规模启用并完善 PostgreSQL + pgvector 持久化向量库配置。


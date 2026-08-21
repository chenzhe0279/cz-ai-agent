# cz-ai-agent

基于 **Java 21、Spring Boot 3、Spring AI** 的 AI Agent 工程。主服务提供恋爱咨询对话（LoveApp）、多步 ReAct 超级智能体（CzManus）、丰富的本地工具调用、RAG 检索增强与 Human-in-the-loop（人机交互）能力，并通过 MCP（stdio）集成图片检索子服务 `cz-image-search-mcp-server`。仓库还包含一个 Vue 3 前端 `cz-ai-agent-frontend`，以 SSE 流式方式与后端对话，并支持智能体运行过程中向前端弹窗提问。

> 当前代码整体处于**教学/演示/原型阶段**：认证（Sa-Token / AuthCheck / AuthAdvisor）相关代码基本处于注释状态，AI 接口未做鉴权与限流；部分配置文件曾包含真实凭据，其中 `application-local.yaml` 已移出版本控制（详见[安全注意事项](#安全注意事项)），对外部署前必须处理。

## 仓库组成

| 子项目 | 说明 | 端口 |
| --- | --- | --- |
| `cz-ai-agent`（本目录） | Spring Boot 主服务：LoveApp 对话、CzManus 智能体、工具系统、RAG、MCP Client、SSE 接口 | `8123`，上下文路径 `/api` |
| `cz-ai-agent-frontend` | Vue 3 + Vite 单页应用：聊天工作台（恋爱大师 / 超级智能体），SSE 流式渲染与人机交互弹窗 | `5173` |
| `cz-image-search-mcp-server` | 独立 MCP Server：基于 Pexels API 的图片搜索工具 `searchImage`，默认以 stdio 方式被主服务拉起 | SSE 模式 `8127`；stdio 模式无端口 |

## 功能特性

- **恋爱大师（LoveApp）**：基于通义千问（DashScope）的恋爱咨询对话，支持多轮聊天记忆（当前默认使用 `InMemoryChatMemory`，另有 MySQL、文件两种实现）、结构化恋爱报告、RAG 检索增强，以及向量检索无果时降级查询 MySQL `love_knowledge` 表。
- **超级智能体（CzManus）**：ReAct 多步推理代理，最大步数 30，具备思考-行动循环、循环检测与干预、资源清理，并通过 SSE 实时推送思考正文、步骤结果与人工提问事件。
- **工具系统**：文件读写、网页搜索、网页抓取、资源下载、终端命令、PDF 生成（支持嵌入图片）、邮件发送（纯文本 / HTML / 附件）、日期时间、人工确认（`askHuman`）、任务终止；并自动合并 MCP 提供的远程工具（图片搜索）。
- **人机交互（Human-in-the-loop）**：智能体缺失关键信息、需求不明确或需确认时，通过 `askHuman` 工具经 SSE 向前端推送提问事件，阻塞等待用户回答（180 秒超时，超时自动降级），实现可中断、可补充信息的交互式执行。
- **RAG 检索增强**：启动时将 `src/main/resources/document/` 下的 Markdown 知识文档向量化到内存向量库（`SimpleVectorStore`），支持状态元数据过滤、LLM 查询改写、百度翻译查询转换、DashScope 云端知识库、向量库 + MySQL 组合检索降级等多种策略。
- **MCP 集成**：主服务作为 MCP Client（stdio）拉起图片搜索子服务，并保留高德地图 MCP 示例配置；子服务同时支持 stdio 与 SSE 两种模式。
- **基础设施**：统一响应/异常处理、全局 CORS、Knife4j / OpenAPI 接口文档、Long 型 JSON 精度保护、Sa-Token 依赖（未启用）、循环检测防呆机制。

## 技术栈

| 分类 | 组件 | 版本 |
| --- | --- | --- |
| 语言 / 构建 | Java、Maven（仓库自带 Wrapper 3.9.16） | 21 / 3.9+ |
| 后端框架 | Spring Boot（主服务） | 3.4.4 |
| 后端框架 | Spring Boot（MCP 子服务） | 3.4.5 |
| AI 框架 | Spring AI（Alibaba DashScope Starter / Ollama Starter / MCP Client / pgvector-store / markdown reader） | 1.0.0-M6 / M6.1 |
| 模型 | 阿里云 DashScope 通义千问（对话默认 `qwen-max`）、DashScope Embedding（1536 维） | DashScope SDK 2.19.1 |
| 其他 AI | LangChain4j（DashScope 社区模型） | 1.0.0-beta2 |
| 数据库 | MySQL（默认库 `yu_picture`）、可选 PostgreSQL + pgvector（当前未启用） | MySQL 8.x |
| 工具库 | Hutool、Jsoup、iText 9（PDF）、Spring Mail、Kryo（文件记忆序列化）、victools jsonschema（结构化输出） | 5.8.37 / 1.19.1 / 9.1.0 / 5.6.2 / 4.38.0 |
| 文档 / 鉴权 | Knife4j（OpenAPI3）、Sa-Token（未启用） | 4.4.0 / 1.44.0 |
| 前端 | Vue 3、Vite、Axios（SSE 使用浏览器原生 `fetch` 流式读取） | 均未锁定版本（`latest`） |

## 项目结构

```text
.
├── src/main/java/com/cz/czaiagent/
│   ├── agent/                  # 智能体框架：BaseAgent / ReActAgent / ToolCallAgent / CzManus
│   │   └── model/AgentState.java
│   ├── app/                    # LoveApp（恋爱大师业务入口，ITApp 演示类已注释）
│   ├── advisor/                # ChatClient Advisor：日志、违禁词、Re2 重读、鉴权（后两者/鉴权未启用）
│   ├── chatmemory/             # 聊天记忆：内存（默认）、MySQL、Kryo 文件三种实现
│   ├── controller/             # AiController（AI 对话/智能体/人类回答）、HelthController（健康检查）
│   ├── rag/                    # 文档加载、向量库、查询转换、组合检索、RAG Advisor 配置
│   ├── service/                # HumanInteractionService（人机交互会话与答案唤醒）
│   ├── tools/                  # 本地工具与统一注册 ToolRegistration
│   ├── demo/                   # 大模型调用演示（HTTP / SDK / Spring AI / LangChain4j）与 RAG 演示
│   ├── common/ exception/ model/ config/ annotation/ aop/
│   │                           # 统一响应、异常处理、DTO/VO/枚举、CORS/JSON、鉴权注解与切面（未启用）
│   └── constant/               # FileConstant（tmp 目录）、UserConstant
├── src/main/resources/
│   ├── application.yaml        # 主配置：端口/上下文、数据源、MCP Client、pgvector 参数、文档配置
│   ├── application-local.yaml  # 本地密钥（DashScope / 邮件 / 百度翻译 / 搜索 API）——已加入 .gitignore 并取消追踪，clone 后需自行创建
│   ├── document/               # 恋爱知识库 Markdown（单身 / 恋爱 / 已婚）
│   ├── prompts/                # 恋爱专家与恋爱报告提示词模板
│   ├── mcp-image-servers.json  # MCP stdio 子服务启动配置（实际启用）
│   └── mcp-servers.json        # 高德地图 MCP 示例配置（未被 application.yaml 引用）
├── cz-ai-agent-frontend/       # Vue 3 + Vite 前端（src/ 源码 + dist/ 构建产物）
├── cz-image-search-mcp-server/ # Pexels 图片搜索 MCP Server（stdio / SSE 双模式）
├── sql/create_table.sql        # MySQL 建表脚本 + 恋爱知识示例数据
├── tmp/                        # 运行时文件目录：file/ download/ pdf/ chat-memory/（已 gitignore）
└── src/test/                   # SpringBootTest 集成测试（部分依赖数据库/模型/外部 API）
```

## 核心机制

### 1. 智能体框架

智能体采用继承链 `BaseAgent → ReActAgent → ToolCallAgent → CzManus`：

- `BaseAgent`：管理状态机、最大步数、消息上下文、SSE 推送与循环检测；提供同步 `run()` 与流式 `runStream()`（`SseEmitter`，超时 5 分钟）。
- `ReActAgent`：实现"思考（think）→ 行动（act）"的 ReAct 模式，`step()` 每次先思考再执行。
- `ToolCallAgent`：禁用 Spring AI 内置自动工具调用（`proxyToolCalls=true`），手动维护对话上下文；通过 `ToolCallingManager` 执行工具并把工具请求/响应写回消息历史；检测到 `doTerminate` 工具时置状态为 `FINISHED`。
- `CzManus`：配置 Agent 名称、系统提示词、下一步提示词、最大步数（30）与 `ChatClient`，通过构造注入全部工具、DashScope 模型与 `HumanInteractionService`。

### 2. 智能体状态机

`AgentState` 定义 `IDLE → RUNNING → FINISHED / ERROR → IDLE`：

| 状态 | 含义 |
| --- | --- |
| `IDLE` | 空闲，可启动新任务 |
| `RUNNING` | 运行中（思考/执行工具循环） |
| `FINISHED` | 正常结束（达到最大步数或调用终止工具） |
| `ERROR` | 执行异常 |

每次 `run()` / `runStream()` 结束（含异常）都会执行 `cleanup()`：清空消息上下文、步数归零、状态复位为 `IDLE`，并恢复被循环干预污染的下轮提示词，保证单例 Bean 可重复使用。

### 3. 循环检测与干预

`BaseAgent` 每一步执行后检测"最后一条非空助手消息"在更早助手消息中是否重复出现：`duplicateThreshold` 默认 `2`，即同一内容累计出现 3 次时判定陷入循环。触发后向下一步提示词前置注入干预文案（"观察到重复响应，请考虑采用新的策略…"），且每轮只注入一次，避免提示词无限膨胀；本轮结束后自动还原。

### 4. SSE 流式协议

| 接口 | 事件名 | 数据内容 |
| --- | --- | --- |
| `GET /api/ai/manus/chat` | `assistant_message` | 大模型本轮思考正文（纯文本） |
| 同上 | `human_question` | JSON：`{"requestId":"...","question":"..."}` |
| 同上 | 默认 `message` | `Step N: ...` 步骤结果、`执行结束: 达到最大步骤 (30)`、`执行错误: ...` 等 |
| `GET /api/ai/love_app/chat/sse/emitter` | 默认 `message` | 恋爱大师回复文本分片（连续追加，形成打字机效果） |

前端通过 `fetch` 读取响应流，按空行切分 SSE 事件块并解析 `event:` / `data:` 字段；`Step N` 强制独立气泡，`human_question` 触发弹窗。

### 5. 人机交互（Human-in-the-loop）

1. 智能体在异步线程中运行，`runStream()` 启动时通过 `HumanInteractionService.openSession(emitter)` 绑定"会话 ID → SSE 推送器"（`ThreadLocal` 记录当前线程会话）。
2. 大模型调用 `askHuman` 工具时，服务端生成 `requestId`，通过 SSE 推送 `human_question` 事件，并在 `CompletableFuture` 上阻塞等待（最多 180 秒）。
3. 前端弹窗收集回答，`POST /api/ai/manus/human-answer`（请求体 `{requestId, answer}`）；服务端通过 `submitAnswer()` 唤醒阻塞线程，将"人类的回答是：xxx"作为工具结果返回给模型。
4. 降级策略：无前端会话、回答为空、超时或异常时均返回提示文本，让模型基于已有信息与合理假设继续，避免任务卡死。

### 6. RAG 检索链路

当前 LoveApp 内部实现了多种检索增强方案（未全部暴露为 HTTP 接口）：

- **内存向量库**（`LoveAppVectorStoreConfig`）：启动时用 `MarkdownDocumentReader` 加载 `document/*.md`，按水平分割线切分，并从文件名提取 `status` 元数据（单身 / 恋爱 / 已婚），用 DashScope Embedding 写入 `SimpleVectorStore`。
- **自定义 RAG Advisor**（`LoveAppRagCustomAdvisorFactory`）：`VectorStoreDocumentRetriever` + 状态过滤 + 相似度阈值 0.5 + TopK 3，未命中时输出兜底拒答文案。
- **组合检索降级**（`LoveAppCompositeDocumentRetriever`）：优先向量库，未命中时按 `status` 从 MySQL `love_knowledge` 表随机取 3 条作为降级上下文。
- **查询改写 / 转换**：`QueryRewriter`（LLM 改写）与 `TranslationQueryTransformer`（百度翻译，中译英，失败回退原文）。
- **云端知识库**（`LoveAppRagCloudAdvisorConfig`）：DashScope 云端索引"恋爱大师"（`DashScopeDocumentRetriever`），当前在 `doChatWithRag` 中处于注释状态。
- **pgvector**：`PgVectorVectorStoreConfig`（HNSW + 余弦距离 + 1536 维）整体被注释，主应用也排除了 `PgVectorStoreAutoConfiguration`，当前未启用。

## 快速开始

### 1. 环境要求

- JDK 21、Maven 3.9+（或直接使用仓库 `mvnw` / `mvnw.cmd` Wrapper）
- Node.js 18+（运行前端）
- MySQL 8.x（默认连接 `localhost:3306`，库名 `yu_picture`）
- 阿里云 DashScope（通义千问）API Key（对话与向量化均需要）
- 可选：SearchAPI（`search-api.api-key`）、百度翻译、SMTP 邮箱、Pexels API Key、Ollama

### 2. 初始化数据库

```powershell
mysql -u root -p < sql/create_table.sql
```

脚本会创建库 `yu_picture`，并创建 `chat_memory`、`user`、`picture`、`space`、`space_user`、`love_knowledge` 等表及索引，插入恋爱知识库示例数据（其中用户/图片/空间相关表为图片项目遗留，当前 AI Agent 核心使用 `chat_memory` 与 `love_knowledge`）。

### 3. 配置本地密钥

主服务 `application.yaml` 默认激活 `local` Profile。本地密钥文件 `src/main/resources/application-local.yaml` 已加入 `.gitignore` 并取消 git 追踪，**clone 仓库后不会存在，需按下面的模板自行创建**；切勿把真实密钥提交到版本库：

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
    properties:
      mail:
        smtp:
          auth: true
          ssl:
            enable: true

baidu:
  translate:
    app-id: ${BAIDU_TRANSLATE_APP_ID}
    security-key: ${BAIDU_TRANSLATE_SECRET}

search-api:
  api-key: ${SEARCH_API_KEY}
```

图片搜索 MCP 子服务的 `cz-image-search-mcp-server/src/main/resources/application.yaml` 中需配置 Pexels API Key（`Pexels.apiKey`）。

### 4. 构建图片搜索 MCP 子服务

主服务的 `mcp-image-servers.json` 会直接执行子项目构建产物（`java -jar cz-image-search-mcp-server/target/cz-image-search-mcp-server-0.0.1-SNAPSHOT.jar`），因此**必须先打包**，否则主服务启动时无法拉起 MCP 子进程：

```powershell
./mvnw.cmd -f cz-image-search-mcp-server/pom.xml clean package
```

单次返回图片数量由环境变量 `IMAGE_SEARCH_LIMIT` 控制（MCP 配置默认 `5`；工具内部未设置时的兜底值为 `10`）。

### 5. 启动主服务

```powershell
./mvnw.cmd spring-boot:run
```

默认地址为 `http://localhost:8123/api`。启动时会在内存中完成恋爱知识文档的向量化（需要 DashScope API Key 与网络）。

### 6. 启动前端

```powershell
cd cz-ai-agent-frontend
npm install
npm run dev
```

前端默认运行在 `http://localhost:5173`，后端地址硬编码在 `src/services/http.js`（`http://localhost:8123/api`）。首页可在「AI 恋爱大师」与「AI 超级智能体」之间切换，支持 SSE 流式输出与智能体提问弹窗；生产构建执行 `npm run build`（产物已提交在 `dist/`）。

### 7. 访问地址汇总

| 地址 | 说明 |
| --- | --- |
| `http://localhost:5173` | 前端工作台 |
| `http://localhost:8123/api/helth` | 健康检查（源码拼写即 `helth`），返回 `OK!` |
| `http://localhost:8123/api/doc.html` | Knife4j 文档 |
| `http://localhost:8123/api/swagger-ui.html` | Swagger UI |
| `http://localhost:8123/api/v3/api-docs` | OpenAPI JSON |

## 配置说明

| 配置项 | 位置 | 说明 |
| --- | --- | --- |
| `spring.profiles.active` | `application.yaml` | 默认 `local`，加载本地密钥文件（`application-local.yaml` 已 gitignore，需本地自行创建） |
| `spring.datasource.*` | `application.yaml` | MySQL 连接（默认 `localhost:3306/yu_picture`，账号密码为开发值，已提交） |
| `server.port` / `server.servlet.context-path` | `application.yaml` | `8123` / `/api` |
| `spring.ai.dashscope.api-key` | `application-local.yaml` | 通义千问 API Key |
| `spring.ai.dashscope.chat.options.model` | `application-local.yaml` | 对话模型，默认 `qwen-max` |
| `spring.mail.*` | `application-local.yaml` | SMTP 邮件（示例：163 邮箱，465 端口 SSL） |
| `baidu.translate.app-id` / `security-key` | `application-local.yaml` | 百度翻译（`TranslationQueryTransformer`） |
| `search-api.api-key` | `application-local.yaml` | SearchAPI（`WebSearchTool`，Baidu 引擎） |
| `spring.ai.mcp.client.stdio.servers-configuration` | `application.yaml` | MCP stdio 服务配置，指向 `mcp-image-servers.json` |
| `spring.ai.vectorstore.pgvector.*` | `application.yaml` | pgvector 参数（HNSW / 1536 维 / 余弦距离），当前实际未启用 |
| `Pexels.apiKey` | MCP 子服务 `application.yaml` | Pexels 图片搜索密钥 |
| `IMAGE_SEARCH_LIMIT` | MCP 子服务环境变量 | 单次返回图片数（MCP 配置默认 5） |

## API 接口

所有接口均在 `AiController` / `HelthController` 中定义，路径前缀为 `/api`。

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/helth` | 健康检查，返回 `OK!` |
| GET | `/ai/love_app/chat/sync?message=&chatId=` | 恋爱大师同步对话，返回纯文本 |
| GET | `/ai/love_app/chat/sse?message=&chatId=` | 恋爱大师流式对话（`Flux<String>`，`text/event-stream`） |
| GET | `/ai/love_app/chat/sent_event?message=&chatId=` | 恋爱大师 SSE 事件流（`Flux<ServerSentEvent<String>>`） |
| GET | `/ai/love_app/chat/sse/emitter?message=&chatId=` | 恋爱大师 SSE 对话（`SseEmitter`，超时 180 秒，前端实际使用） |
| GET | `/ai/manus/chat?message=` | 超级智能体流式对话（`SseEmitter`，超时 300 秒；每次请求创建新的 `CzManus` 实例） |
| POST | `/ai/manus/human-answer` | 提交人类回答，请求体 `{"requestId":"...","answer":"..."}`；找到请求返回 200，未找到返回 404 |

说明：

- 恋爱大师通过 `chatId` 维持独立会话（前端每次进入应用生成 UUID），`MessageChatMemoryAdvisor` 每轮注入最近 10 条记忆。
- `LoveApp` 内部的 RAG / 组合检索 / 工具 / MCP 对话方法（`doChatWithRag`、`doChatWithFallbackSearch`、`doChatWithTools`、`doChatWithMcp`、`doChatWithReport`）目前**未暴露为 HTTP 接口**，仅供测试与内部调用。
- 接口目前未做鉴权；统一异常处理（`GlobalExceptionHandler`）会将 Sa-Token 未登录/无权限、业务异常、运行时异常包装为 `BaseResponse` JSON。

## 工具清单

`ToolRegistration.allTools` 将本地工具与 MCP 远程工具合并为一个 `ToolCallback[]`，供 LoveApp 与 CzManus 使用。当前注册的工具：

| 工具类 | 工具方法 | 能力 | 产物目录 |
| --- | --- | --- | --- |
| `FileOperationTool` | `readFile` / `writeFile` | 读写本地文本文件 | `tmp/file` |
| `WebSearchTool` | `searchWeb` | SearchAPI 百度搜索，返回前 5 条 | - |
| `WebScrapingTool` | `scrapeWebPage` | Jsoup 抓取网页正文 | - |
| `ResourceDownloadTool` | `downloadResource` | 下载远程资源 | `tmp/download` |
| `TerminalOperationTool` | `executeTerminalCommand` | 执行终端命令（Windows `cmd.exe /c`） | - |
| `PDFGenerationTool` | `generatePDFWithImage` / `generatePDF` | iText 9 生成 PDF，支持 `[图片:本地路径]` 内嵌图片与中文排版 | `tmp/pdf` |
| `EmailTool` | `sendEmail` / `sendHtmlEmail` / `sendEmailWithAttachment` | 纯文本 / HTML / 附件邮件 | - |
| `DateTimeTool` | `getCurrentDate` / `getCurrentTime` / `getCurrentDateTime` / `getDateTimeByTimezone` / `getDayOfWeek` | 日期时间与时区感知 | - |
| `AskHumanTool` | `askHuman` | 向前端提问并等待回答（180 秒超时） | - |
| `TerminateTool` | `doTerminate` | 结束智能体任务循环 | - |
| MCP `ImageSearchTool` | `searchImage` | Pexels 图片搜索，返回逗号分隔的中等尺寸图片 URL | - |

`DatabaseTool`（`executeQuery` / `executeUpdate` / `listTables` / `getTableStructure`）已实现并自带安全限制（查询仅允许 SELECT，写操作禁止 DROP/TRUNCATE），但**默认未注册**（`ToolRegistration` 中已注释）。

## MCP 集成

### 图片搜索子服务（启用中）

- 主服务通过 `spring-ai-mcp-client-spring-boot-starter`（1.0.0-M6）以 **stdio** 方式启动子服务，配置见 `mcp-image-servers.json`：`java -Dspring.ai.mcp.server.stdio=true -Dspring.main.web-application-type=none -jar cz-image-search-mcp-server/target/cz-image-search-mcp-server-0.0.1-SNAPSHOT.jar`。
- 子服务基于 `spring-ai-starter-mcp-server-webmvc`（1.0.0-M7），提供同步工具 `searchImage(query)`，调用 Pexels `GET /v1/search` 并返回 `src.medium` 图片 URL 列表。
- 子服务默认激活 `stdio` Profile（关闭 Web 容器、关闭横幅）；`sse` Profile 下以 WebMVC 方式监听 `8127` 端口，可被 SSE 模式的 MCP Client 连接。
- 依赖 `java` 命令位于 PATH，且主服务须从仓库根目录启动（JAR 路径为相对路径）。

### 高德地图 MCP（示例，未启用）

`mcp-image-servers.json` 同级存在 `mcp-servers.json`，内含高德地图 MCP（`@amap/amap-maps-mcp-server`）的启动示例与 API Key，但 `application.yaml` 当前只引用了 `mcp-image-servers.json`，因此该配置**未生效**；如需启用，将 `spring.ai.mcp.client.stdio.servers-configuration` 指向该文件并调整 `command` 为环境中的 `npx` 路径。

## 数据库设计

| 表 | 用途 | 说明 |
| --- | --- | --- |
| `chat_memory` | 会话消息持久化 | `conversation_id` + `message_type`（USER/ASSISTANT/SYSTEM）+ `content`，供 `MysqlChatMemory` 使用 |
| `love_knowledge` | 恋爱知识库 | `content` + `status`（单身/恋爱/已婚）+ `tags`，RAG 降级检索与示例数据来源 |
| `user` | 用户（图片项目遗留） | 含账号、密码、角色、VIP 扩展字段 |
| `picture` / `space` / `space_user` | 图片/空间（图片项目遗留） | 与当前 AI Agent 核心逻辑无直接关系 |

## 测试

```powershell
# 主服务测试
./mvnw.cmd test

# MCP 子服务测试
./mvnw.cmd -f cz-image-search-mcp-server/pom.xml test
```

测试覆盖：应用上下文与 LoveApp 对话（`CzAiAgentApplicationTests`）、CzManus 端到端任务（`CzManusTest`）、RAG 组件（`LoveAppTest`、`PgVectorVectorStoreConfigTest`）、各工具（文件、搜索、抓取、下载、终端、PDF、邮件、日期、数据库、WebSearch）。

注意：多数测试是 `@SpringBootTest` 集成测试，**依赖 MySQL、DashScope、邮件 SMTP 或外部 API**；未配置凭据或网络受限时可能失败，建议按类单独执行。

## 安全注意事项

> 以下问题在对外发布前必须处理。

- **`application-local.yaml` 已移出版本控制**：已加入 `.gitignore`（同时覆盖 `.yml` / `.yaml` 两种后缀）并执行 `git rm --cached`，后续提交不会再包含该文件。但该文件**曾进入过提交历史**，其中的密钥可能已暴露，仍建议**撤销/轮换**（DashScope Key、163 邮箱账号与授权码、百度翻译 AppID/密钥、SearchAPI Key）。
- **其余真实凭据仍被版本库追踪**（具体值不在 README 中展示），涉及文件：
  - `src/main/java/com/cz/czaiagent/demo/invoke/TestApiKey.java`：DashScope Key；
  - `cz-image-search-mcp-server/src/main/resources/application.yaml`：Pexels API Key；
  - `src/main/resources/mcp-servers.json`：高德地图 API Key；
  - `src/main/resources/application.yaml`：MySQL 默认账号密码（及被注释的 PostgreSQL 连接串）。
  请立即**撤销/轮换**这些密钥，并迁移到环境变量、密钥管理服务或未提交的本地配置。
- AI 接口目前**无鉴权、无限流**；`AuthAdvisor`、`AuthInterceptor`、Sa-Token 相关代码均处于注释状态。
- `TerminalOperationTool` 可执行任意终端命令、`FileOperationTool` 可读写文件；对外提供 Agent 能力前应增加命令白名单、路径约束、权限隔离与审计。
- `mcp-image-servers.json` 的 stdio 配置由主服务直接执行 JAR，部署时需确认 JAR 来源可信、路径可控。

## 已知问题与注意事项

- 健康检查路径拼写为 `/helth`（非 `health`），属于源码现状，README 按实际路径记录。
- LoveApp 当前使用 `InMemoryChatMemory`，重启后对话记忆丢失；`MysqlChatMemory` / `FileBaseChatMemory` 已实现但需手动切换。
- pgvector 相关配置（`application.yaml` 参数、`PgVectorVectorStoreConfig`、`PgVectorStoreAutoConfiguration` 排除项）尚未打通，启用需同步调整数据源与依赖。
- 前端依赖使用 `latest`，未锁定版本，构建结果可能随依赖升级变化；`dist/` 构建产物已提交。
- 根目录存在一个空的 `package-lock.json`（`packages: {}`），疑似残留，无实际用途。
- `ITApp` 演示类已整体注释，不会在启动时自动调用模型；若恢复启用会在启动时产生模型调用费用。
- MCP 子服务需在启动主服务前完成打包；主服务目录下 `tmp/` 为运行时产物（gitignore），其中包含历史下载与 PDF 文件。

## 后续规划

- 为恋爱咨询与智能体接口补充鉴权（启用 Sa-Token / `AuthCheck`）、限流与审计日志。
- `application-local.yaml` 已移出版本控制；继续清理其余已提交的凭据（`TestApiKey.java`、`mcp-servers.json`、MCP 子服务 `application.yaml`、主 `application.yaml` 数据源），必要时重写 git 历史清除旧密钥，并全面切换环境变量/密钥服务。
- 将对话记忆切换为 MySQL 持久化，并设计会话清理策略。
- 打通 PostgreSQL + pgvector 持久化向量库，替代启动时内存建库的方案。
- 完善用户体系（注册/登录/VIP 兑换接口已建模但未暴露）。
- 为 MCP 子服务增加独立部署、健康检查与主服务启动前置校验。
- 将 LoveApp 的 RAG / 工具 / MCP / 报告等内部能力暴露为受控 HTTP 接口，并补充单元测试。

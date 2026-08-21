# cz-ai-agent

基于 Spring Boot 3 与 Spring AI 的 Java 21 AI Agent 工程。主服务提供恋爱咨询 RAG 对话、多步 ReAct 超级智能体（CzManus）、丰富的本地工具调用，以及通过 MCP（stdio）集成的图片检索子服务 `cz-image-search-mcp-server`。仓库还包含一个 Vue 3 前端 `cz-ai-agent-frontend`，以 SSE 流式方式与后端对话，并支持智能体在运行中向用户提问（Human-in-the-loop）。

## 功能概览

- **恋爱大师（LoveApp）**：基于通义千问（DashScope）的对话，支持多轮聊天记忆（内存/MySQL 两种实现）、结构化恋爱报告、RAG 检索增强、向量检索无果时降级查询 MySQL `love_knowledge` 表。
- **超级智能体（CzManus）**：ReAct 多步推理代理，支持最大步数（30）控制、状态管理、工具编排，并通过 SSE 实时推送思考与执行过程。
- **工具系统**：文件读写、网页搜索与抓取、资源下载、终端命令、PDF 生成（支持嵌入图片）、邮件发送（支持附件）、日期时间、人工确认（askHuman）、终止任务等；自动合并 MCP 提供的远程工具（图片搜索）。
- **人机交互（Human-in-the-loop）**：智能体信息不足时通过 `askHuman` 工具向前端弹出提问，阻塞等待用户回答（180 秒超时，超时自动降级），实现可中断、可补充信息的交互式执行。
- **MCP 图片搜索**：子项目 `cz-image-search-mcp-server` 通过 Pexels API 检索图片，默认以 stdio MCP Server 方式由主服务拉起，返回图片数量可由环境变量 `IMAGE_SEARCH_LIMIT` 控制（默认 5）。
- **基础设施**：MySQL 聊天记忆、统一响应/异常处理、CORS、Knife4j / OpenAPI 文档、Sa-Token 依赖。

## 技术栈

- Java 21、Maven、Spring Boot 3.4.4
- Spring AI 1.0.0-M6 / Spring AI Alibaba（DashScope）1.0.0-M6.1 / MCP Client 1.0.0-M6
- 阿里云 DashScope（通义千问）、LangChain4j、Ollama（依赖已引入）
- MySQL、可选 PostgreSQL + pgvector（当前 pgvector 配置未启用）
- iText 9（PDF）、Jsoup、Hutool、Spring Mail、Kryo、Knife4j、Sa-Token
- 前端：Vue 3、Vite、Axios（SSE 流式渲染）

## 项目结构

```text
.
├── src/main/java/com/cz/czaiagent/
│   ├── agent/             # BaseAgent / ReActAgent / ToolCallAgent / CzManus 与状态模型
│   ├── app/               # LoveApp（恋爱大师）与启动演示 ITApp
│   ├── advisor/           # 日志、敏感词、重读等 ChatClient Advisor
│   ├── chatmemory/        # 文件与 MySQL 聊天记忆
│   ├── controller/        # AiController（AI 对话/智能体接口）、HelthController（健康检查）
│   ├── rag/               # 文档加载、向量检索、查询改写与 RAG Advisor
│   ├── service/           # HumanInteractionService（人机交互）
│   ├── tools/             # Agent 本地工具及统一注册 ToolRegistration
│   └── common/ exception/ model/ config/ annotation/ aop/ ...
├── src/main/resources/
│   ├── document/          # 恋爱知识库 Markdown
│   ├── prompts/           # 恋爱专家与报告提示词模板
│   ├── application*.yaml  # 主服务配置（默认激活 local）
│   └── mcp-image-servers.json   # MCP stdio 子服务启动配置
├── cz-ai-agent-frontend/  # Vue 3 + Vite 聊天前端（恋爱大师 / 超级智能体）
├── cz-image-search-mcp-server/  # 独立的 Pexels MCP 图片搜索服务
├── sql/create_table.sql   # MySQL 建表及恋爱知识示例数据
└── src/test/              # Agent、RAG、工具等测试用例
```

## 快速开始

### 1. 前置条件

- JDK 21、Maven 3.9+（或使用仓库 Maven Wrapper）
- Node.js 18+（运行前端）
- MySQL 8.x（默认连接 `localhost:3306`）
- DashScope API Key
- 如启用邮件、网页搜索、图片搜索：相应服务的账号与 API Key

### 2. 初始化数据库

主服务默认使用 MySQL 数据库 `yu_picture`，执行建表脚本：

```powershell
mysql -u root -p < sql/create_table.sql
```

脚本会创建聊天记忆（`chat_memory`）、恋爱知识库（`love_knowledge`）等表并插入示例数据（同时包含图片项目的用户/图片/空间相关表）。

### 3. 配置本地密钥

`application.yaml` 默认激活 `local` Profile。请在 `src/main/resources/application-local.yaml` 中填入自己的凭据，切勿提交真实密钥：

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

### 4. 构建图片 MCP 子服务

主服务的 `mcp-image-servers.json` 会直接执行子项目构建产物，先打包：

```powershell
./mvnw.cmd -f cz-image-search-mcp-server/pom.xml clean package
```

在该子服务的 `application.yaml` 中配置 Pexels API Key。可通过环境变量 `IMAGE_SEARCH_LIMIT` 控制单次返回图片数量（MCP 配置默认 `5`）。

### 5. 启动主服务

```powershell
./mvnw.cmd spring-boot:run
```

默认地址为 `http://localhost:8123/api`。

### 6. 启动前端

```powershell
cd cz-ai-agent-frontend
npm install
npm run dev
```

前端默认运行在 `http://localhost:5173`，已内置后端地址 `http://localhost:8123/api`（见 `src/services/http.js`）。首页可在「AI 恋爱大师」与「AI 超级智能体」两个应用间切换，支持 SSE 流式输出与智能体提问弹窗。

## 可用接口

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/helth` | 健康检查（源码拼写即为 `helth`） |
| GET | `/api/ai/love_app/chat/sync` | 恋爱大师同步对话，参数 `message`、`chatId` |
| GET | `/api/ai/love_app/chat/sse` | 恋爱大师 SSE 流式对话（`Flux<String>`） |
| GET | `/api/ai/love_app/chat/sent_event` | 恋爱大师 SSE 事件流（`Flux<ServerSentEvent>`） |
| GET | `/api/ai/love_app/chat/sse/emitter` | 恋爱大师 SSE 对话（`SseEmitter`，前端实际使用） |
| GET | `/api/ai/manus/chat` | 超级智能体流式对话（`SseEmitter`），参数 `message` |
| POST | `/api/ai/manus/human-answer` | 提交人类回答，唤醒等待中的 `askHuman`，请求体 `{requestId, answer}` |

| 项目 | 地址 |
| --- | --- |
| Knife4j 文档 | `http://localhost:8123/api/doc.html` |
| Swagger UI | `http://localhost:8123/api/swagger-ui.html` |
| OpenAPI JSON | `http://localhost:8123/api/v3/api-docs` |

## 人机交互（Human-in-the-loop）

智能体在缺失关键信息或需要确认时，会调用 `askHuman` 工具：

1. `HumanInteractionService` 通过 SSE 向前端推送 `human_question` 事件（含 `requestId` 与问题内容）。
2. 前端弹窗收集用户回答，POST 到 `/api/ai/manus/human-answer` 并带回 `requestId`。
3. 服务端通过 `CompletableFuture` 唤醒阻塞中的智能体线程，将回答作为工具结果继续执行。
4. 无前端会话、回答为空或等待超时（180 秒）时均会降级，返回提示让模型基于已有信息继续，避免任务卡死。

## 核心组件

| 组件 | 作用 |
| --- | --- |
| `LoveApp` | 对话、结构化报告、RAG、MySQL 降级检索、工具调用与 MCP 调用入口。 |
| `AiController` | 对外暴露恋爱大师与超级智能体的同步/流式接口及人类回答提交接口。 |
| `CzManus` | 多步 ReAct 超级智能体（继承 `ToolCallAgent`），最大步数 30。 |
| `ToolCallAgent` / `ReActAgent` / `BaseAgent` | 思考-行动循环框架，手动维护上下文并支持 SSE 事件推送。 |
| `ToolRegistration` | 汇总本地工具与 MCP 远程工具为统一 `ToolCallback[]`。 |
| `HumanInteractionService` | 管理 SSE 会话与待答请求，实现跨线程传递人类回答。 |
| `AskHumanTool` | 暴露给大模型的人工确认工具。 |
| `ImageSearchTool` | MCP 子服务的 `searchImage` 工具，调用 Pexels API 返回中等尺寸图片 URL。 |

## 安全提示

- 当前仓库配置文件中已包含数据库密码、模型/搜索/翻译/Pexels API Key 及邮件凭据，请立即撤销或轮换，并迁移到环境变量、密钥管理服务或未提交的本地配置。
- `TerminalOperationTool` 可执行终端命令、`FileOperationTool` 可读写文件、`DatabaseTool`（默认未注册）允许 SQL 写操作。对外提供 Agent 能力前，应加入身份认证、路径/命令白名单、最小权限与审计。
- `ITApp` 为启动时自动调用模型的演示类，生产启动前建议移除、注释或增加 Profile 条件，避免额外费用和启动副作用。

## 测试

```powershell
./mvnw.cmd test
./mvnw.cmd -f cz-image-search-mcp-server/pom.xml test
```

部分测试依赖数据库、模型服务、邮件或外部 API，在未配置凭据或网络受限环境中可能失败，可按模块或类单独执行。

## 后续建议

- 为恋爱咨询与 Agent 能力增加鉴权与限流保护。
- 将 `application-local.yaml` 移出版本控制，改用环境变量或密钥服务。
- 为 MCP 服务增加独立部署方式与健康检查，主服务调用前校验子服务 JAR 是否已构建。
- 根据数据规模启用并完善 PostgreSQL + pgvector 持久化向量库配置。
- 完善前端用户体系与权限控制，接入 Sa-Token 认证。

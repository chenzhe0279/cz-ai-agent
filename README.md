# cz-ai-agent

基于 **Java 21、Spring Boot 3、Spring AI** 的 AI Agent 工程。主服务提供**通用 AI 智能助手**（后端类名与接口沿用旧命名 LoveApp、`/love_app/*`）、多步 ReAct 超级智能体（CzManus）、**AI 情感专家**（RAG 知识检索对话，`/ai/rag/chat/sse`，按情感状态过滤知识库）、丰富的本地工具调用、RAG 检索增强、Human-in-the-loop（人机交互）与完整的用户体系（注册/登录/个人中心/邮箱验证/找回密码/VIP/管理员），并通过 MCP（stdio）集成图片检索子服务 `cz-image-search-mcp-server`。仓库还包含一个 Vue 3 前端 `cz-ai-agent-frontend`，以 SSE 流式方式与后端对话；界面以**三套可切换的动态背景**（彗星日落视频 / 星空流星 / 彗星蓝天·你的名字，右下角按钮切换并支持自动轮播）为基调，主页采用完全透明、登录/注册/找回密码与聊天页、个人中心采用毛玻璃通透风格。

## 仓库组成

| 子项目 | 说明 | 端口 |
| --- | --- | --- |
| `cz-ai-agent`（本目录） | Spring Boot 主服务：AI 对话、智能体、工具系统、RAG、用户体系、MCP Client、SSE 接口 | `8123`，上下文路径 `/api` |
| `cz-ai-agent-frontend` | Vue 3 + Vite 单页应用：聊天工作台（智能助手 / 超级智能体 / AI 情感专家三个聊天框）、登录（双模式）/注册、找回密码、个人中心（管理员面板），动态视频背景（主页透明 + 其余页面毛玻璃） | `5173` |
| `cz-image-search-mcp-server` | 独立 MCP Server：基于 Pexels API 的图片搜索工具 `searchImage`，默认以 stdio 方式被主服务拉起 | SSE 模式 `8127`；stdio 模式无端口 |

## 功能特性

- **AI 智能助手（LoveApp）**：基于通义千问（DashScope）的通用 AI 对话助手，**游客可直接使用**；可回答学习、工作、生活、编程、情感等各领域问题，支持多轮聊天记忆（当前默认 `InMemoryChatMemory`，另有 MySQL、文件两种实现）、结构化对话总结报告。
- **AI 情感专家（RAG 检索增强对话）**：**需登录后使用**；独立聊天应用，基于情感知识库做检索增强问答，按情感状态（单身/恋爱/已婚）过滤文档，围绕各阶段情感问题给出有依据的解答。
- **超级智能体（CzManus）**：ReAct 多步推理代理，**需登录后使用**；最大步数 30，具备思考-行动循环、循环检测与干预、资源清理，并通过 SSE 实时推送思考正文、步骤结果与人工提问事件。
- **工具系统**：文件读写、网页搜索、网页抓取、资源下载、终端命令、PDF 生成（支持嵌入图片）、邮件发送（纯文本 / HTML / 附件）、日期时间、人工确认（`askHuman`）、任务终止；并自动合并 MCP 提供的远程工具（图片搜索）。PDF 工具具备**坏图容错**：嵌入前按文件头魔数校验真实格式（JPEG/PNG/GIF/BMP/WebP）、自动解压 gzip 包裹的伪图片、单张图片失败仅降级为占位文本而不中断整份 PDF，并在返回结果中报告"成功/失败张数与失败路径"；文本写入前还会过滤 emoji 等非 BMP 字符，避免内置中文字体编码报错。
- **人机交互（Human-in-the-loop）**：智能体缺失关键信息、需求不明确或需确认时，通过 `askHuman` 工具经 SSE 向前端推送提问事件，阻塞等待用户回答（180 秒超时，超时自动降级），实现可中断、可补充信息的交互式执行。
- **RAG 检索增强**：启动时将 `src/main/resources/document/` 下的 Markdown 知识文档向量化到内存向量库（`SimpleVectorStore`），支持状态元数据过滤、LLM 查询改写、百度翻译查询转换、DashScope 云端知识库、向量库 + MySQL 组合检索降级等多种策略。
- **MCP 集成**：主服务作为 MCP Client（stdio）拉起图片搜索子服务，并保留高德地图 MCP 示例配置；子服务同时支持 stdio 与 SSE 两种模式。
- **用户体系**：
  - 注册：**注册时即绑定邮箱并校验验证码**；
  - 登录：**账号密码 / 邮箱验证码两种方式**（Sa-Token 令牌）；
  - 个人中心：资料编辑、修改密码、**本地图片上传头像**、邮箱绑定与验证；
  - 找回密码：通过绑定邮箱 + 验证码重置密码；
  - VIP：管理员生成兑换码，用户兑换后升级为 vip 并记录会员有效期；
  - 管理员：用户分页查询 / 新增 / 软删除 / 修改角色 / 批量生成兑换码；
  - 鉴权：`/user/**` 与 `/ai/manus/**` 需登录，管理员接口需 `admin` 角色，AI 智能助手对游客开放。
- **基础设施**：统一响应/异常处理、全局 CORS、Knife4j / OpenAPI 接口文档、Long 型 JSON 精度保护、Sa-Token 鉴权、循环检测防呆机制。

## 技术栈

| 分类 | 组件 | 版本 |
| --- | --- | --- |
| 语言 / 构建 | Java、Maven（仓库自带 Wrapper） | 21 / 3.9.16 |
| 后端框架 | Spring Boot（主服务） | 3.4.4 |
| 后端框架 | Spring Boot（MCP 子服务） | 3.4.5 |
| AI 框架 | Spring AI（Alibaba DashScope Starter / Ollama Starter / MCP Client / pgvector-store / markdown reader） | 1.0.0-M6 / M6.1 |
| 模型 | 阿里云 DashScope 通义千问（对话默认 `qwen-max`）、DashScope Embedding（1536 维） | DashScope SDK 2.19.1 |
| 其他 AI | LangChain4j（DashScope 社区模型） | 1.0.0-beta2 |
| 数据库 | MySQL（默认库 `yu_picture`）、可选 PostgreSQL + pgvector（当前未启用） | MySQL 8.x |
| 工具库 | Hutool、Jsoup、iText 9（PDF）、Spring Mail、Kryo（文件记忆序列化）、victools jsonschema（结构化输出） | 5.8.37 / 1.19.1 / 9.1.0 / 5.6.2 / 4.38.0 |
| 文档 / 鉴权 | Knife4j（OpenAPI3）、Sa-Token（已启用：登录拦截 + `@SaCheckRole` 角色校验） | 4.4.0 / 1.44.0 |
| 前端 | Vue 3、Vite、Axios（SSE 使用浏览器原生 `fetch` 流式读取） | 均未锁定版本（`latest`） |

## 项目结构

```text
.
├── src/main/java/com/cz/czaiagent/
│   ├── agent/                  # 智能体框架：BaseAgent / ReActAgent / ToolCallAgent / CzManus
│   │   └── model/AgentState.java
│   ├── app/                    # LoveApp（AI 智能助手业务入口，类名沿用旧命名，ITApp 演示类已注释）
│   ├── advisor/                # ChatClient Advisor：日志、违禁词、Re2 重读、鉴权（后两者/鉴权未启用）
│   ├── chatmemory/             # 聊天记忆：内存（默认）、MySQL、Kryo 文件三种实现
│   ├── controller/             # AiController、UserController（用户/鉴权/邮箱）、FileController（头像文件）、HelthController
│   ├── rag/                    # 文档加载、向量库、查询转换、组合检索、RAG Advisor 配置
│   ├── service/                # HumanInteractionService（人机交互）、UserService/UserServiceImpl（用户）
│   ├── tools/                  # 本地工具与统一注册 ToolRegistration
│   ├── demo/                   # 大模型调用演示（HTTP / SDK / Spring AI / LangChain4j）与 RAG 演示
│   ├── common/                 # BaseResponse、ResultUtils、PageRequest、DeleteRequest、PageResult
│   ├── exception/              # ErrorCode、BusinessException、GlobalExceptionHandler、ThrowUtils
│   ├── model/                  # entity/User、dto/user/*（含邮箱/找回密码 DTO）、vo/*（含 LoginResponse）、enums/UserRoleEnum
│   ├── config/                 # CorsConfig、JsonConfig、SaTokenConfig（登录拦截）、StpInterfaceImpl（角色数据源）
│   ├── annotation/ aop/        # @AuthCheck 注解与切面（未启用，可作 Sa-Token 之外的可选方案）
│   └── constant/               # FileConstant（tmp 目录）、UserConstant
├── src/main/resources/
│   ├── application.yaml        # 主配置：端口/上下文、数据源、MCP Client、Sa-Token、multipart、文档配置
│   ├── application-local.yaml  # 本地密钥（DashScope / 邮件 / 百度翻译 / 搜索 API）——已 gitignore 并取消追踪，clone 后需自行创建
│   ├── document/               # 旧恋爱知识库 Markdown（通用化后未启用，可删除）
│   ├── prompts/                # AI 助手系统提示词与对话总结报告模板（文件名沿用 love-*）
│   ├── mcp-image-servers.json  # MCP stdio 子服务启动配置（实际启用）
│   └── mcp-servers.json        # 高德地图 MCP 示例配置（未被 application.yaml 引用）
├── cz-ai-agent-frontend/       # Vue 3 + Vite 前端
│   ├── public/background/      # 全局动态背景资源：comet.mp4（彗星日落视频）+ stars.webp / starry-eyes.webp（星空流星 / 彗星蓝天·你的名字图片）+ 首帧占位图
│   ├── src/App.vue             # 视图入口：首页 / 登录 / 注册 / 找回密码 / 个人中心 / 聊天
│   ├── src/components/         # LoginView（双模式）/ RegisterView / ForgotPasswordView / ProfileView
│   ├── src/store/auth.js       # 全局登录态（token 与用户信息）
│   ├── src/services/           # http.js（axios + token 拦截）、user.js、chat.js（SSE）
│   ├── src/styles.css / styles-auth.css  # 基础样式 + 深空主题样式（含透明/毛玻璃方案）
│   └── dist/                   # 生产构建产物（已提交）
├── cz-image-search-mcp-server/ # Pexels 图片搜索 MCP Server（stdio / SSE 双模式）
├── sql/create_table.sql        # 建表脚本 + 旧恋爱知识数据（遗留）+ VIP 兑换码/邮箱验证码表 + 初始管理员
├── tmp/                        # 运行时文件目录：file/ download/ pdf/ chat-memory/ avatar/（已 gitignore）
└── src/test/                   # SpringBootTest 集成测试（部分依赖数据库/模型/外部 API）
```

## 核心机制

### 1. 智能体框架

智能体采用继承链 `BaseAgent → ReActAgent → ToolCallAgent → CzManus`：

- `BaseAgent`：管理状态机、最大步数、消息上下文、SSE 推送与循环检测；提供同步 `run()` 与流式 `runStream()`（`SseEmitter`，超时 10 分钟）。
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

`BaseAgent` 每一步执行后检测"最后一条非空助手消息"在更早助手消息中是否重复出现：`duplicateThreshold` 默认 `2`，即同一内容累计出现 3 次时判定陷入循环。触发后向下一步提示词前置注入干预文案，且每轮只注入一次，避免提示词无限膨胀；本轮结束后自动还原。

### 4. SSE 流式协议

| 接口 | 事件名 | 数据内容 |
| --- | --- | --- |
| `GET /api/ai/manus/chat` | `assistant_message` | 大模型本轮思考正文（纯文本） |
| 同上 | `human_question` | JSON：`{"requestId":"...","question":"..."}` |
| 同上 | 默认 `message` | `Step N: ...` 步骤结果、`执行结束: 达到最大步骤 (30)`、`执行错误: ...` 等 |
| `GET /api/ai/love_app/chat/sse/emitter` | 默认 `message` | AI 智能助手回复文本分片（连续追加，形成打字机效果） |

前端通过 `fetch` 读取响应流，按空行切分 SSE 事件块并解析 `event:` / `data:` 字段；`Step N` 强制独立气泡，`human_question` 触发弹窗。

说明：客户端在任务结束前断开（点击「■ 停止」、刷新或关闭页面）时，智能体会在下一次 SSE 推送时检测到连接已结束并**安全提前结束循环**（仅 WARN 日志），不再抛出 `ResponseBodyEmitter has already completed`；此前已完成的业务（如邮件发送）不受影响。

### 5. 人机交互（Human-in-the-loop）

1. 智能体在异步线程中运行，`runStream()` 启动时通过 `HumanInteractionService.openSession(emitter)` 绑定"会话 ID → SSE 推送器"（`ThreadLocal` 记录当前线程会话）。
2. 大模型调用 `askHuman` 工具时，服务端生成 `requestId`，通过 SSE 推送 `human_question` 事件，并在 `CompletableFuture` 上阻塞等待（最多 180 秒）。
3. 前端弹窗收集回答，`POST /api/ai/manus/human-answer`（请求体 `{requestId, answer}`）；服务端通过 `submitAnswer()` 唤醒阻塞线程，将"人类的回答是：xxx"作为工具结果返回给模型。
4. 降级策略：无前端会话、回答为空、超时或异常时均返回提示文本，让模型基于已有信息与合理假设继续，避免任务卡死。
5. 健壮性：提问事件以**显式 JSON 字符串**发送（前端可直接 `JSON.parse`），避免对象序列化行为差异导致前端弹窗收不到；无可用会话时记录 WARN 日志并降级，便于排查。

### 6. RAG 检索链路

当前 LoveApp 内部保留了多种**旧恋爱知识 RAG 检索增强**方案（通用助手模式下默认未启用，未全部暴露为 HTTP 接口）：

- **内存向量库**（`LoveAppVectorStoreConfig`）：启动时用 `MarkdownDocumentReader` 加载 `document/*.md`，按水平分割线切分，并从文件名提取 `status` 元数据（单身 / 恋爱 / 已婚），用 DashScope Embedding 写入 `SimpleVectorStore`。
- **自定义 RAG Advisor**（`LoveAppRagCustomAdvisorFactory`）：`VectorStoreDocumentRetriever` + 状态过滤 + 相似度阈值 0.5 + TopK 3，未命中时输出兜底拒答文案。
- **组合检索降级**（`LoveAppCompositeDocumentRetriever`）：优先向量库，未命中时按 `status` 从 MySQL `love_knowledge` 表随机取 3 条作为降级上下文。
- **查询改写 / 转换**：`QueryRewriter`（LLM 改写）与 `TranslationQueryTransformer`（百度翻译，中译英，失败回退原文）。
- **云端知识库**（`LoveAppRagCloudAdvisorConfig`）：DashScope 云端索引旧恋爱知识库（`DashScopeDocumentRetriever`），当前在 `doChatWithRag` 中处于注释状态。
- **pgvector**：`PgVectorVectorStoreConfig`（HNSW + 余弦距离 + 1536 维）整体被注释，主应用也排除了 `PgVectorStoreAutoConfiguration`，当前未启用。

### 7. 用户鉴权与邮箱验证机制

- **令牌**：登录（账号密码 / 邮箱验证码）成功后返回 `{user, token}`，令牌为 **Sa-Token 无状态 JWT**（`token-style: jwt` + `StpLogicJwtForStateless`），有效期为 30 天；身份与过期时间由令牌自身携带，**不依赖后端内存会话**，因此刷新页面、后端重启后登录态依然有效。前端将令牌存 `localStorage`（键 `cz_ai_token`），所有请求通过请求头 `satoken: <token>` 携带；生产环境必须通过环境变量覆盖 `sa-token.jwt-secret-key`。
- **登录拦截**：`SaTokenConfig` 注册 `SaInterceptor`，`/user/**` 与 `/ai/manus/**` 需要登录；公开放行：`/user/register`、`/user/register/code`、`/user/login/**`、`/user/password/**`，以及 CORS 预检 `OPTIONS`。
- **角色校验**：`StpInterfaceImpl` 从数据库读取用户角色，管理员接口通过 `@SaCheckRole("admin")` 校验（`/user/add`、`/user/delete`、`/user/update/role`、`/user/list`、`/user/vip/code/generate`）。
- **密码安全**：注册/改密/管理员建号均使用 Hutool BCrypt 加盐哈希；修改密码或重置密码后强制重新登录；管理员新建用户初始密码 `12345678`。
- **邮箱验证码**：用途分为 `register`（注册）、`login`（邮箱登录）、`bind`（绑定邮箱）、`reset`（找回密码）；6 位数字、10 分钟有效、一次性使用、同一邮箱同用途 60 秒冷却，通过 `spring.mail` SMTP 发送。
- **会话管理**：无状态 JWT 模式下服务端无法主动吊销令牌，`StpUtil.kickout()` 会抛出 `ApiDisabledException`（代码已容错忽略）；删除用户后数据库 `isDelete=1`，其后续请求在读取用户时会按“未登录”处理；修改角色后权限实时按数据库角色校验。前端“退出登录”会同时清除本地令牌与用户缓存。
- **错误码补充**：非管理员访问管理接口抛出 `NotRoleException`，由 `GlobalExceptionHandler` 统一映射为 `40101 无权限`。
- **错误码**：未登录 `40100`、无权限 `40101`，统一由 `GlobalExceptionHandler` 包装为 `BaseResponse`。

## 快速开始

### 1. 环境要求

- JDK 21、Maven 3.9+（或直接使用仓库 `mvnw` / `mvnw.cmd` Wrapper）
- Node.js 18+（运行前端）
- MySQL 8.x（默认连接 `localhost:3306`，库名 `yu_picture`）
- 阿里云 DashScope（通义千问）API Key（对话与向量化均需要）
- 可选：SearchAPI（`search-api.api-key`）、百度翻译、SMTP 邮箱（邮箱验证码/找回密码必需）、Pexels API Key、Ollama

### 2. 初始化数据库

```powershell
mysql -u root -p < sql/create_table.sql
```

脚本会创建库 `yu_picture`，并创建 `chat_memory`、`user`、`picture`、`space`、`space_user`、`love_knowledge`、`vip_code`、`email_verify_code` 等表及索引，插入旧恋爱知识库示例数据（遗留），并写入初始管理员账号 **`admin` / `admin123456`**（BCrypt 加密，账号已存在时自动跳过）。`user` 表需包含 `vipExpireTime`、`vipCode`、`vipNumber`、`email` 等列（脚本通过幂等 ALTER 补齐）；若你的库是旧版本，请重新执行脚本末尾追加的用户体系段落（VIP 兑换码 / 邮箱验证码两段均为幂等操作）。

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

默认地址为 `http://localhost:8123/api`。若启用旧的恋爱知识 RAG，启动时会进行知识文档向量化（需要 DashScope API Key 与网络）；通用助手模式下该链路默认未启用。

### 6. 启动前端

```powershell
cd cz-ai-agent-frontend
npm install
npm run dev
```

前端默认运行在 `http://localhost:5173`，后端地址硬编码在 `src/services/http.js`（`http://localhost:8123/api`）。游客可直接使用「AI 智能助手」；「AI 情感专家」「AI 超级智能体」、个人中心等功能需先注册/登录。生产构建执行 `npm run build`（产物已提交在 `dist/`）。

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
| `spring.mvc.async.request-timeout` | `application.yaml` | 异步/SSE 请求超时（毫秒，当前 `600000`，即 10 分钟），与 `SseEmitter` 超时配合防止长任务连接被断开 |
| `spring.servlet.multipart.*` | `application.yaml` | 文件上传限制（单文件与总请求均 5MB），头像上传使用 |
| `spring.mail.*` | `application-local.yaml` | SMTP 邮件（示例：163 邮箱，465 端口 SSL），邮箱验证码/找回密码依赖 |
| `spring.ai.dashscope.api-key` / `model` | `application-local.yaml` | 通义千问 API Key 与模型（默认 `qwen-max`） |
| `spring.ai.dashscope.chat.options.multi-model` | `application-local.yaml` | 多模态模型（如 `qwen3.7-plus`）必须设为 `true`，否则 SDK 走文本端点会报 `400 url error`；`ToolCallAgent` 代码中也已硬编码开启 |
| `baidu.translate.app-id` / `security-key` | `application-local.yaml` | 百度翻译（`TranslationQueryTransformer`） |
| `search-api.api-key` | `application-local.yaml` | SearchAPI（`WebSearchTool`，Baidu 引擎） |
| `spring.ai.mcp.client.stdio.servers-configuration` | `application.yaml` | MCP stdio 服务配置，指向 `mcp-image-servers.json` |
| `spring.ai.vectorstore.pgvector.*` | `application.yaml` | pgvector 参数（HNSW / 1536 维 / 余弦距离），当前实际未启用 |
| `sa-token.token-name` / `sa-token.timeout` / `sa-token.token-style` / `sa-token.jwt-secret-key` | `application.yaml` | 令牌名 `satoken`、有效期 30 天、`jwt` 无状态模式与签名密钥；前端通过请求头 `satoken` 携带，生产环境请用环境变量覆盖 `jwt-secret-key` |
| `Pexels.apiKey` / `IMAGE_SEARCH_LIMIT` | MCP 子服务 | Pexels 密钥与单次返回图片数（默认 5） |

## API 接口

接口定义于 `AiController` / `UserController` / `FileController` / `HelthController`，路径前缀为 `/api`。

### AI 对话

| 方法 | 路径 | 鉴权 | 说明 |
| --- | --- | --- | --- |
| GET | `/helth` | 公开 | 健康检查，返回 `OK!` |
| GET | `/ai/love_app/chat/sync?message=&chatId=` | 公开 | AI 智能助手同步对话，返回纯文本 |
| GET | `/ai/love_app/chat/sse?message=&chatId=` | 公开 | AI 智能助手流式对话（`Flux<String>`） |
| GET | `/ai/love_app/chat/sent_event?message=&chatId=` | 公开 | AI 智能助手 SSE 事件流（`Flux<ServerSentEvent<String>>`） |
| GET | `/ai/love_app/chat/sse/emitter?message=&chatId=` | 公开 | AI 智能助手 SSE 对话（`SseEmitter`，超时 180 秒，前端实际使用） |
| GET | `/ai/manus/chat?message=` | 登录 | 超级智能体流式对话（`SseEmitter`，超时 600 秒；每次请求创建新的 `CzManus` 实例） |
| POST | `/ai/manus/human-answer` | 登录 | 提交人类回答，请求体 `{"requestId":"...","answer":"..."}`；找到请求返回 200，未找到返回 404 |

### 用户

| 方法 | 路径 | 鉴权 | 说明 |
| --- | --- | --- | --- |
| POST | `/user/register` | 公开 | 注册，请求体 `{userAccount, userPassword, checkPassword, email, verifyCode}`，注册时绑定邮箱并校验验证码 |
| POST | `/user/register/code` | 公开 | 发送注册邮箱验证码，请求体 `{email}`（60 秒冷却，10 分钟有效） |
| POST | `/user/login` | 公开 | 账号密码登录，请求体 `{userAccount, userPassword}`，返回 `{user, token}` |
| POST | `/user/login/code/send` | 公开 | 发送登录邮箱验证码，请求体 `{email}`（需邮箱已绑定账号） |
| POST | `/user/login/code` | 公开 | 邮箱验证码登录，请求体 `{email, verifyCode}`，返回 `{user, token}` |
| POST | `/user/logout` | 登录 | 退出登录 |
| GET | `/user/current` | 登录 | 获取当前登录用户（脱敏信息） |
| POST | `/user/update` | 登录 | 更新资料（仅更新传入的非空字段：`userName` / `userAvatar` / `userProfile`） |
| POST | `/user/update/password` | 登录 | 修改密码，请求体 `{oldPassword, newPassword, checkPassword}`，成功后强制重新登录 |
| POST | `/user/email/send-code` | 登录 | 发送邮箱绑定验证码，请求体 `{email}` |
| POST | `/user/email/bind` | 登录 | 绑定邮箱，请求体 `{email, verifyCode}` |
| POST | `/user/password/reset/code` | 公开 | 发送找回密码验证码，请求体 `{email}`（需该邮箱已绑定账号） |
| POST | `/user/password/reset` | 公开 | 通过验证码重置密码，请求体 `{email, verifyCode, newPassword, checkPassword}` |
| POST | `/user/avatar/upload` | 登录 | 上传头像（`multipart/form-data`，字段名 `file`，支持 jpg/png/gif/webp ≤5MB），返回相对访问路径并自动更新头像 |
| POST | `/user/add` | 管理员 | 新增用户，请求体 `{userAccount, userName, userAvatar, userProfile, userRole}`（初始密码 `12345678`） |
| POST | `/user/delete` | 管理员 | 删除用户（软删除），请求体 `{id}` |
| POST | `/user/update/role` | 管理员 | 修改角色，请求体 `{id, userRole}` |
| GET | `/user/list` | 管理员 | 分页查询用户，参数 `current/pageSize/userAccount/userName/userRole` |
| POST | `/user/vip/code/generate` | 管理员 | 生成 VIP 兑换码，请求体 `{count, durationDays}`，返回兑换码列表 |

### 文件

| 方法 | 路径 | 鉴权 | 说明 |
| --- | --- | --- | --- |
| GET | `/file/avatar/{filename}` | 公开 | 访问已上传的头像文件（带 7 天缓存，文件名白名单防路径穿越） |

说明：

- AI 智能助手通过 `chatId` 维持独立会话（前端每次进入应用生成 UUID），`MessageChatMemoryAdvisor` 每轮注入最近 10 条记忆。
- `LoveApp` 的 RAG 检索增强对话已通过 **`GET /ai/rag/chat/sse`** 暴露（`doChatWithRagByStream`，前端「AI 情感专家」使用），按 `status`（单身/恋爱/已婚）过滤知识库并维持 `chatId` 会话记忆；其余组合检索 / 工具 / MCP 对话方法（`doChatWithRag`、`doChatWithFallbackSearch`、`doChatWithTools`、`doChatWithMcp`、`doChatWithReport`）仍**未暴露为 HTTP 接口**，仅供测试与内部调用。
- 登录态通过请求头 `satoken: <token>` 传递；未登录返回 `40100`，无权限返回 `40101`，统一由 `GlobalExceptionHandler` 包装为 `BaseResponse` JSON。

## 工具清单

`ToolRegistration.allTools` 将本地工具与 MCP 远程工具合并为一个 `ToolCallback[]`，供 LoveApp 与 CzManus 使用。当前注册的工具：

| 工具类 | 工具方法 | 能力 | 产物目录 |
| --- | --- | --- | --- |
| `FileOperationTool` | `readFile` / `writeFile` | 读写本地文本文件 | `tmp/file` |
| `WebSearchTool` | `searchWeb` | SearchAPI 百度搜索，返回前 5 条 | - |
| `WebScrapingTool` | `scrapeWebPage` | Jsoup 抓取网页正文 | - |
| `ResourceDownloadTool` | `downloadResource` | 下载远程资源 | `tmp/download` |
| `TerminalOperationTool` | `executeTerminalCommand` | 执行终端命令（Windows `cmd.exe /c`） | - |
| `PDFGenerationTool` | `generatePDFWithImage` / `generatePDF` | iText 9 生成 PDF：支持 `[图片:本地路径]` 内嵌图片、中文排版；内嵌前做图片魔数校验（JPEG/PNG/GIF/BMP/WebP）并自动解压 gzip 伪图片，单张坏图降级为占位文本且返回失败统计；文本写入前过滤 emoji 等非 BMP 字符 | `tmp/pdf` |
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
| `love_knowledge` | 旧恋爱知识库（遗留） | `content` + `status`（单身/恋爱/已婚）+ `tags`，旧 RAG 降级检索与示例数据来源，通用助手模式下未使用 |
| `user` | 用户（核心） | 含账号、BCrypt 密码、昵称/头像/简介、**邮箱**（唯一）、角色（user/vip/admin）、VIP 扩展字段；`isDelete` 软删除 |
| `vip_code` | VIP 兑换码 | `code` 唯一、`duration_days` 时长、`is_used` 使用状态、`used_by` 使用人、`created_by` 生成管理员 |
| `email_verify_code` | 邮箱验证码 | `email` + `purpose`（register/login/bind/reset）+ 6 位验证码 + `expire_time` + `is_used`；一次性使用 |
| `picture` / `space` / `space_user` | 图片/空间（图片项目遗留） | 与当前 AI Agent 核心逻辑无直接关系 |

## 前端说明

- **页面结构**：单页应用通过视图状态切换（无 vue-router）：首页 → 登录（双模式）/ 注册 / 找回密码 → 聊天 → 个人中心。当前页面同步到 URL hash（如 `#/chat/love`、`#/chat/manus`、`#/chat/rag`），**刷新页面后停留在原页面**，并支持浏览器前进/后退；AI 智能助手与 AI 情感专家会话会记住 `chatId`（延续后端会话记忆），草稿与历史消息由 `localStorage` 持久化，刷新后原地恢复。
- **首页**：高端落地页结构——大字 Hero（渐变描边标题）+「开始对话 / 创建账号」双 CTA + 三大 AI 应用展示卡 + 能力特性条（实时流式 / 多会话管理 / 三大 AI 伙伴 / 账号体系），整体直接铺在动态视频背景上（原极光/星尘/网格装饰层已隐藏以完全透出背景）。
- **多会话管理**：聊天页左侧会话列表支持新建、切换、**重命名**（点击 ✎ 内联编辑，Enter/失焦保存、Esc 取消）与删除（自定义确认弹框，**至少保留一个会话**）；新建对话默认名为"新对话"，可随时改名；会话、草稿与消息均持久化到 `localStorage`，刷新/关闭浏览器后保留；移动端会话列表为抽屉式。
- **聊天框固定高度**：聊天框固定为视口高度（`100vh`/`100dvh`），消息区内部滚动——内容再多也不会撑长页面，新消息到达时历史对话自动上移并滚到底部；消息区与输入框的滚动条均已隐藏（仅保留滚动能力），通过**鼠标滚轮/触控板**翻看历史，或使用左侧**对话活动轨道**点击跳转任意气泡。
- **背景切换**：右下角悬浮控件支持在「彗星日落（视频）/ 星空流星 / 彗星蓝天·你的名字」三套背景间切换（‹ › 或点击名称），并可通过 ▶/❚❚ 按钮开启/关闭**自动轮播**（10 秒切换一次）；选择与轮播开关持久化到 `localStorage`，刷新后保持。星空流星与彗星蓝天为静态壁纸，前端以缓慢缩放平移（Ken Burns）动效呈现流动感。
- **登录页**：「账号密码」与「邮箱验证码」两个选项卡；邮箱模式支持发送验证码（60 秒倒计时）；「立即注册」「忘记密码」入口两种模式均可直达。
- **注册页**：账号 + 密码 + 邮箱 + 验证码，验证码发送成功后才能提交，注册成功后自动登录。
- **找回密码**：输入绑定邮箱 → 发送验证码（成功发送后才可进入下一步）→ 设置新密码 → 重新登录。
- **登录态**：token 存 `localStorage`（键 `cz_ai_token`），用户信息另缓存于 `cz_ai_user`；刷新页面时先用本地缓存恢复用户（不闪烁、不掉登录），再向后端 `/user/current` 校验令牌，仅明确收到 `40100/40101` 或 HTTP 401/403 才清空登录态（网络/服务瞬时异常不会误登出）；`http.js` 请求拦截自动附加 `satoken` 头，响应拦截统一解包 `BaseResponse`；SSE 请求（`chat.js`）同样携带令牌。
- **游客与登录**：游客可进入「AI 智能助手」聊天；「AI 情感专家」「AI 超级智能体」在未登录时点击会跳转登录页（卡片上显示"登录后可用"），刷新后未登录也不会停留在这两个页面。
- **AI 情感专家（RAG 知识检索对话）**：独立的第三个聊天应用（`#/chat/rag`），**需登录后使用**，基于知识库做检索增强问答；与智能助手一样按 `chatId` 维持后端会话记忆，支持多会话/重命名/删除、深度思考提示、生成中断与修改重发、对话活动轨道等全部聊天能力；聊天气泡上方提供「用户婚恋状态」选择器（**单身 / 恋爱 / 已婚**），按用户婚恋状态过滤知识库文档，选择持久化到 `localStorage`；每次回答末尾会附带本次检索到的**参考资料**，每个文档标注**相关度分数**（余弦相似度），并按分数从高到低排序展示。
- **智能体提问超时**：`askHuman` 提问弹窗带 180 秒倒计时（与后端 `HumanInteractionService` 超时一致）；超时未回复时自动关闭弹窗，并在对话气泡中展示"待确认问题 + 由于该问题人类并没有给出相关回复，我将基于自己的理解进行思考……"。
- **深度思考提示**：超级智能体在工具执行/思考间隙、AI 智能助手与 AI 情感专家在等待首个响应分片时，对话气泡内显示"正在深度思考中……"（带动态圆点），提升等待体验。
- **对话活动轨道（Conversation Activity Rail）**：聊天区左侧的竖向轮次标记条，**一个气泡 = 一个标记**（用户问题、超级智能体的每一个 step 气泡都各占一根短线），整体**限制在聊天框消息区范围内**；短线采用**居中分布**——气泡少时整组垂直居中，历史增多后最早的气泡线逐渐上移、新的向下排，直至铺满整轨；**最早期的标记接近顶部边界时逐渐渐隐**（顶部 64px 渐隐区）；鼠标在轨道上快速上下移动时，高亮以**弹簧物理跟随（Spring-physics follow）**滞后、过冲回弹，并带**连锁波浪缩放**（高斯波浪轮廓随弹簧移动），悬停同时弹出 Tooltip 预览气泡内容；**鼠标滚轮放在轨道上滚动即可上下翻阅历史**；**点击轨道任意位置**（不限于细短线，会自动吸附最近的对话记录）以弹性波浪缓动平滑滚动跳转到该气泡并短暂高亮，随滚动自动更新当前气泡，三个聊天框（智能助手 / 超级智能体 / AI 情感专家）行为一致；移动端自动隐藏。
- **生成中断与修改重发**：AI 生成过程中输入框发送键变为「■ 停止」按钮，点击即中断生成（清掉本次未完成的回复，回到用户提问处）；随后可**直接在输入框输入新问题**，或**点击用户气泡下的 ✎ 修改之前发出去的提问**，确认后自动按新提示词重新生成回复（智能助手、超级智能体与 AI 情感专家均支持）。
- **个人中心**：资料编辑（昵称/简介）、修改密码（改后需重新登录）、邮箱绑定（发送验证码 + 60 秒倒计时）、VIP 兑换、管理员面板（用户分页/搜索、改角色、删除、新建用户、批量生成兑换码）；头像通过 `+` 按钮选择本地图片上传，即时预览，主页/聊天页右上角同步显示头像。
- **视觉风格**：全站以**三套可切换动态背景**为主角（彗星日落为视频、星空流星与彗星蓝天·你的名字为 Ken Burns 动效图片，全局固定一层、所有页面共用，右下角按钮切换）；主页为**轻玻璃**（8~10px 轻微模糊 + 极淡底色，兼顾通透与可读性），登录/注册/找回密码、聊天页、个人中心为**毛玻璃通透**（轻微 6~12px 模糊 + 极淡底色）；深空星点、极光、行星等装饰层已弱化以透出背景。

## 测试

```powershell
# 主服务测试
./mvnw.cmd test

# MCP 子服务测试
./mvnw.cmd -f cz-image-search-mcp-server/pom.xml test
```

测试覆盖：应用上下文与 LoveApp 对话（`CzAiAgentApplicationTests`）、CzManus 端到端任务（`CzManusTest`）、RAG 组件（`LoveAppTest`、`PgVectorVectorStoreConfigTest`）、各工具（文件、搜索、抓取、下载、终端、PDF、邮件、日期、数据库、WebSearch）。

注意：多数测试是 `@SpringBootTest` 集成测试，**依赖 MySQL、DashScope、邮件 SMTP 或外部 API**；未配置凭据或网络受限时可能失败，建议按类单独执行。

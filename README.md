# AI Character Play

Spring Boot 3 / Java 21 + Vue 3，支持会话隔离的文字和语音角色对话。

## Docker 本地启动

安装 Docker Desktop（Linux containers），在项目根目录用 PowerShell 执行：

```powershell
Copy-Item .env.example .env
powershell -ExecutionPolicy Bypass -File scripts/download-model.ps1
docker compose --profile local-llm --profile local-speech up -d --build
powershell -ExecutionPolicy Bypass -File scripts/smoke.ps1
```

打开 http://127.0.0.1:5173 ，自行注册账号。首次构建需要下载 Maven/npm 依赖和模型。本地 LLM 使用官方 llama.cpp CPU 服务与 Qwen 官方 `Qwen2.5-0.5B-Instruct-GGUF` 的 Q4_K_M 文件（约 491 MB），下载脚本校验官方仓库记录的 SHA256。这个小模型仅用于演示闭环，中文角色表现有限。模型保存在忽略的 `docker/models` 目录；未完成下载时 LLM 无法启动。

下载需代理时可执行 `scripts/download-model.ps1 -Proxy http://127.0.0.1:7890`。CPU 镜像来自 `ghcr.io/ggml-org/llama.cpp:server`，不需下载数 GB 的 GPU 运行库。模型采用 [Apache-2.0](https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct-GGUF) 许可证，llama.cpp 使用 [MIT](https://github.com/ggml-org/llama.cpp/blob/master/LICENSE) 许可证。仓库不包含模型权重。

使用已有兼容 OpenAI 的服务时，在 `.env` 设置 `LLM_BASE_URL`、`LLM_API_KEY`、`LLM_MODEL`，执行 `docker compose up -d --build` 即可，无需 local-llm profile。容器访问宿主机服务请使用 `host.docker.internal`。

Web 与 API/WS/SSE 走同源 Nginx；backend 也可通过 http://127.0.0.1:8123/api/health 查看健康。MySQL 和 Redis 不暴露宿主机端口。项目名固定为 `ai-character-play`，数据保存在自己的命名卷，不使用 CRM/CMS 容器或数据库。

`.env.example` 中的数据库密码仅为本地开发默认值。供应商密钥写在忽略的 `.env`，不要上传。浏览器语音识别/朗读是否可用取决于浏览器、系统音色与权限；浏览器朗读不等于已验证云端合成。DashScope/SiliconFlow/Azure 等需要有效凭据，免费试用政策与实际额度以各供应商为准。

可选本地识别与机械音合成：`docker compose --profile local-speech up -d --build local-speech`。首次运行下载 faster-whisper tiny 模型，`/health` 在下载完成前返回 503；模型保存在 `speech-models` 命名卷。下载需代理时在 `.env` 设置 `SPEECH_DOWNLOAD_PROXY=http://host.docker.internal:7890`；缓存后可清空代理并设置 `WHISPER_LOCAL_FILES_ONLY=true`。客户端选择 local 供应商才会调用此服务，中文 espeak-ng 是机械音演示，不能当作自然人声音质。供应商端点、模型和音色变量见 `.env.example` 与 [语音供应商说明](docs/voice-providers.md)。

## 日常命令与排障

```powershell
docker compose ps
docker compose logs --tail 100 backend
docker compose --profile local-llm logs --tail 50 llm
docker compose --profile local-llm stop
docker compose --profile local-llm up -d
```

首次初始化只导入干净表结构、苏格拉底和福尔摩斯两个角色，不导入仓库 SQL 中的历史用户和对话；角色归属保留系统 ID 0，不包含默认登录账号。注册后可直接聊天。

已有数据库请先备份，再查看 `scripts/upgrade-db.ps1` 输出，执行 `scripts/upgrade-db.ps1 -Apply`。脚本检查并跳过已存在的列与索引，可重复运行；原始 SQL 文件适合一次性人工升级。迁移仅增加会话/轮次字段、索引和无原文的反馈表，不清空原表。初始化 SQL 只在空 MySQL 卷首次启动时执行，修改初始化文件不会自动改变已有表。不要通过删除卷修复升级问题。

如 Docker Hub 在当前网络过慢，先手动从可信镜像源拉取并 tag 为官方名称，或通过 `.env` 的 `*_IMAGE` 变量指定镜像源；无需修改 Docker daemon 全局配置。`MAVEN_IMAGE`、`JAVA_IMAGE`、`NODE_IMAGE`、`NGINX_IMAGE` 都支持构建参数。

烟雾脚本调用真实 HTTP 注册、登录、session 与精选角色列表，会创建一个独立测试用户。

完整本地语音链路回归（另建测试用户，选择local识别与合成）：

```powershell
powershell -ExecutionPolicy Bypass -File scripts/voice-smoke.ps1 -LongContext
```

该脚本检验未登录WS拒绝、真实人声PCM→Whisper→LLM→WAV、反馈确认、打断重发和四轮长中文上下文；不会调用麦克风或云端。日常完整启动/停止请同时带`--profile local-llm --profile local-speech`。

会话上下文以SQL为真源，按用户/角色/会话隔离；完整生成后才保存AI回复。模型HTTP随打断取消。默认上下文最多12000 UTF-8字节，系统提示上限4000字节，优先保留当前消息与最近历史；用`LLM_CONTEXT_BYTE_BUDGET`调整时须匹配模型窗口及输出预算。默认llama总窗口32768、双槽每槽16384。RAG默认关闭；启用需配置`RAG_ENABLED=true`及`EMBEDDING_API_KEY`（或`DASHSCOPE_API_KEY`），资料从打包classpath按角色加载，真实云embedding未联调。验证细节见[验证记录](docs/validation.md)。

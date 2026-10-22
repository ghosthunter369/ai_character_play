# 本地验证记录

日期：2026-10-22。目标分支develop；master由用户单独处理。

## 已验证

- Docker六服务健康：web、backend、MySQL8、Redis7、llama.cpp、local-speech；独立命名卷。
- Maven完整32项通过：真实MyBatis/H2历史隔离、实际AppService+HTTP SSE取消与成功落库、真实TCP取消、截断和HTTP失败、上下文预算、语音协议适配、分句与慢TTS并行。
- 前端生产状态机16项通过；Vite生产构建通过（仍有大bundle提示）。
- 本地语音13项HTTP测试通过，包含真实eSpeak、官方Whisper JFK人声三短语断言、并发429；缓存模型在无网络容器内实际推理通过。
- `scripts/smoke.ps1`：真实注册/登录、Redis session、角色列表。
- `scripts/voice-smoke.ps1 -LongContext`：未登录WS拒绝；登录Cookie鉴权；requestId匹配；真实PCM→Whisper→Qwen→WAV；反馈确认；打断立即重发；四轮额外长中文输入无上下文溢出。
- SQL读回反馈记录，首个协议回归会话用户消息4条、AI消息3条，取消的模型回复未落库。
- 将WEB_PORT临时设为5174仅重建backend配置，以Origin=http://127.0.0.1:5174走8123真实WS完成整链路，通过后恢复默认5173配置。
- 浏览器注册/登录、角色选择、语音模式、本地供应商、文字提交→真实回复→WAV播放回调→就绪，角色反馈显示“反馈已记录”；单次首音581ms。截图保存在忽略目录test-results/voice-browser.png。
- 独立代码审查发现并修复：LangChain Reactor取消不关闭HTTP、迟到turn.started使旧回复复活、自定义端口CORS、长上下文超出本地模型槽位。修复复查无遗留确认P0/P1/P2。

## 限制与未验证

- 未提供云端key，DashScope/SiliconFlow/Azure只做官方协议核对与本地HTTP契约测试；不可声称云端试用额度或声音实际可用。讯飞旧入口保留，新网关尚未统一接入；腾讯/火山仅调研。
- 无真人麦克风录音、扬声器声学或中文识别准确率数据集验证。浏览器播放事件说明音频调度完成，不证明听感；Whisper tiny中文误识别明显、eSpeak为机械音、Qwen0.5B角色遵循有限，仅用于低门槛演示。
- 旧页面完整vue-tsc仍有44项类型错误；新增语音模块不在这些报错中。Vite构建成功不等于整个仓库类型检查通过，未将该检查称为通过。
- RAG打包资源可读取已测，但开启后的真实云embedding未联调。
- 当前是按轮发言与整句音频块，非全双工、非VAD、非供应商原生流式TTS。完整生成但尚未播完的文字会留在历史中。

复现命令和配置见[README](../README.md)、[供应商说明](voice-providers.md)与[完整时序](voice-architecture.md)。

# Docker 与多供应商语音对话设计

用户已授权执行、提交、创建 PR、审查并合并到 develop；master 保持不变。

## 目标
保留 Spring Boot/Vue，实现 Docker 启动、多供应商选择、明确轮次、可取消的分句语音回复、用户与会话隔离、反馈和时延记录。云端试用不等于永久免费；无凭据不得宣称真实云端验证通过。

## 协议
新增 /api/ws/voice，复用登录 session，验证 appId。上行二进制为16kHz单声道PCM16，audio.commit明确提交本轮，最多30秒；text.submit支持文字/浏览器识别。二者必须携带UUID requestId，turn.started回显，前端只接受当前请求对应的新turnId，防止取消后迟到started复活旧回复。会话建立包含conversationId（UUID）、asrProvider、ttsProvider。

下行JSON包含type、turnId、seq；事件为session.ready、asr.final、reply.delta、reply.done、reply.sentence、audio.chunk、turn.done、turn.cancelled、error。audio.chunk包含Base64 data和mimeType，初版以完整短句为音频单位（非逐token合成、非供应商原生音频流）。reply.done代表文字结束，turn.done代表合成队列结束，浏览器播放状态独立。上行还支持turn.cancel、playback.started、feedback。

每轮取消旧任务；取消覆盖LLM订阅、HTTP调用和浏览器播放，迟到结果按turnId丢弃。TTS失败保留文字并明确提示。前端用onended判断播放结束。文字与音频使用一个轮次身份。

## 供应商与运行
SpeechGateway接入DashScope、SiliconFlow、Azure与本地服务；保留原讯飞接口兼容入口，新网关暂不接讯飞。浏览器speechSynthesis可无需云密钥输出，中文音色取决于浏览器。默认本地LLM为llama.cpp/Qwen0.5B，local-speech为Whisper tiny/eSpeak NG机械音。官方免费政策记录来源日期，不自动跨付费供应商回退。

SQL是上下文真源，按user/app/conversation取历史并按UTF8预算裁剪，当前输入只加入一次。仅完整SSE收到[DONE]才存AI回复；截断、错误和取消不存半截回复。ChatCompletionClient直接持有OkHttp Call，取消会中止真实HTTP。Redis用于登录session；旧记忆工厂保留兼容但不用于新对话路径。RAG默认关闭，可单独启用。

独立compose项目与命名卷，不复用或删除CRM/CMS数据。Web绑定127.0.0.1:5173，MySQL/Redis只在内部网络。密钥放忽略的.env，不使用Git历史旧凭据。原SQL dump含历史数据，默认只生成干净表结构和精选角色种子。

## 验收
真实HTTP注册/登录/角色查询、WS鉴权及事件顺序；真实服务覆盖跨用户/会话隔离、分句早于LLM结束、取消后无旧音频、外部超时。供应商契约由本地HTTP协议服务器测试；真实云调用有密钥后单独验证。浏览器验证页面与播放。未验证项如实记录。

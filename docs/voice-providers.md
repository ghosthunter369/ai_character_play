# 语音供应商配置与试用边界

核查日期：2026-10-22。以下为官方协议核对；本地 HTTP 契约测试不等于真实云端成功。没有真实密钥时不得声称云端识别、音色或免费额度已经验证。

## 网关配置

通过 Spring Environment 读取 `voice.<provider>.api-key/base-url/asr-url/tts-url/asr-model/tts-model/voice`。所有 URL 都可配置；优先具体 asr-url/tts-url，然后 base-url，最后供应商默认地址。云端 key 为空时立即报错，不发请求，不自动切换其他供应商。密钥仅使用环境变量，不放入源码和浏览器。

| provider | 默认地址/模型 | 环境变量 |
|---|---|---|
| dashscope | ASR `https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions`，`qwen3-asr-flash`；TTS `https://dashscope.aliyuncs.com/api/v1/services/aigc/multimodal-generation/generation`，`qwen3-tts-flash`，音色 `Cherry` | `DASHSCOPE_API_KEY`、`DASHSCOPE_ASR_URL`、`DASHSCOPE_TTS_URL`、`DASHSCOPE_ASR_MODEL`、`DASHSCOPE_TTS_MODEL`、`DASHSCOPE_VOICE` |
| siliconflow | `https://api.siliconflow.cn/v1`；ASR `FunAudioLLM/SenseVoiceSmall`；TTS `FunAudioLLM/CosyVoice2-0.5B`，音色 `FunAudioLLM/CosyVoice2-0.5B:alex` | `SILICONFLOW_API_KEY/BASE_URL/ASR_URL/TTS_URL/ASR_MODEL/TTS_MODEL/VOICE` |
| azure | ASR `https://eastasia.stt.speech.microsoft.com/speech/recognition/conversation/cognitiveservices/v1?language=zh-CN`；TTS `https://eastasia.tts.speech.microsoft.com/cognitiveservices/v1`，音色 `zh-CN-XiaoxiaoNeural` | `AZURE_SPEECH_KEY`、`AZURE_SPEECH_REGION`、`AZURE_SPEECH_ASR_URL`、`AZURE_SPEECH_TTS_URL`、`AZURE_SPEECH_VOICE` |
| local | `http://local-speech:8000/v1`；OpenAI multipart `/audio/transcriptions` 与 JSON `/audio/speech`；模型默认 `whisper-1` / `tts-1` | `LOCAL_SPEECH_BASE_URL/ASR_URL/TTS_URL/ASR_MODEL/TTS_MODEL/VOICE`；不需 key |
| browser | 浏览器 `speechSynthesis`，由客户端播放 | 网关只列出能力，不执行服务端合成 |
| xunfei | 原讯飞 TTS 兼容入口保留 | 新网关暂不接入，能力显示未配置 |

`voice.http.timeout-ms` / `SPEECH_HTTP_TIMEOUT_MS` 控制整次 HTTP 调用超时（默认20秒），连接超时最多5秒。输入音频最多5MiB，单次合成最多600个Unicode字符，响应最多8MiB。CallScope取消会中断已注册以及并发晚注册的HTTP调用。capabilities中的configured只表示凭据/配置就绪，不是网络健康检查。

## 接口与计费

### 阿里百炼（优先）

ASR 使用 Bearer key，chat/completions 的 input_audio.data 输入 `data:audio/wav;base64,...`，直接读 `choices[0].message.content`，无需公网音频URL。Qwen3-ASR-Flash支持最多5分钟、编码后10MB；本项目另有更严格的输入上限。北京地域免费10小时，有效期90天，自开通/模型发布/审批中较晚日期起算。超额按量计费；建议控制台开启“免费额度用完即停”。北京业务空间专属域名现为 `{WorkspaceId}.cn-beijing.maas.aliyuncs.com`，旧dashscope域名仍支持。

TTS的 input 包含text/voice/language_type，非流式返回output.audio.url（24小时有效）；部分响应可含audio.data Base64。网关先读data，否则仅下载HTTPS且命中内置阿里DashScope结果OSS域名白名单的URL，禁止重定向，限制响应体积，不记录URL。Qwen3-TTS-Flash北京地域免费1万字符/90天，之后0.8元/万字符；额度以账户控制台为准。

来源：[ASR API](https://help.aliyun.com/zh/model-studio/qwen-asr-api-reference)、[TTS API](https://help.aliyun.com/zh/model-studio/qwen-tts-api)、[模型价格](https://help.aliyun.com/zh/model-studio/model-pricing)、[免费规则](https://help.aliyun.com/zh/model-studio/new-free-quota)。

### SiliconFlow（存在模型可用性冲突）

ASR `POST /audio/transcriptions`：Bearer鉴权，multipart上传file和model，返回JSON.text；TTS `POST /audio/speech`：JSON model/input/voice/response_format，返回音频二进制，官方支持流式，但本项目每次合成完整短句。

官方当前价格页将SenseVoiceSmall列为免费，API参考仍列该模型；同时2025-02-27公告将其列入2025-03-06下线名单。因此默认模型只是协议配置示例，必须持key通过GET `/v1/models?sub_type=speech-to-text` 和真实识别确认，不能保证当前账号可用。CosyVoice2收费，不能称永久免费。当前价格页抓取的0.05未展示单位，不沿用旧文章报价。平台需实名认证；活动代金券按资格发放，金额和有效期以控制台为准。

来源：[ASR](https://docs.siliconflow.cn/docs/api/audio-transcriptions-post)、[TTS](https://docs.siliconflow.cn/docs/api/audio-speech-post)、[价格](https://siliconflow.cn/pricing)、[模型公告](https://docs.siliconflow.cn/docs/release-notes/overview)。

### Azure Speech

ASR采用短音频REST，直接上传16kHz单声道PCM WAV，返回DisplayText与RecognitionStatus；最长60秒，只返回最终文本。TTS采用SSML及Ocp-Apim-Subscription-Key，返回WAV。地区和资源key必须匹配；配置自定义URL可使用控制台提供的资源专属endpoint。

F0资源每月5小时实时识别、50万字符神经TTS；F0不支持Batch。需要Azure账号和Speech F0资源，不能把S0付费资源误认为自动享有F0额度。超额行为应按所选资源层级确认，升级S0后按量收费。

来源：[短音频ASR](https://learn.microsoft.com/en-us/azure/ai-services/speech-service/rest-speech-to-text-short)、[TTS](https://learn.microsoft.com/en-us/azure/ai-services/speech-service/rest-text-to-speech)、[F0定价](https://azure.microsoft.com/en-us/pricing/details/speech/)。

## 其他可选渠道

七牛LLM支持 `https://api.qnaigc.com/v1/chat/completions` 的OpenAI兼容协议、Bearer与SSE。新用户有300万tokens共享资源包，但key文档和计费文档分别写“创建key激活”与“首次计费后约1小时发放”；有效期查看账户资源包，用尽自动按量收费。七牛语音是独立Dora产品：旧一句话识别可Base64，需工单开通，不能声称LLM资源包抵扣ASR/TTS。

来源：[七牛LLM接入](https://developer.qiniu.com/aitokenapi/13379/real-time-ai-interface-api)、[免费与计费](https://developer.qiniu.com/aitokenapi/12898/ai-token-api-pricing)、[语音工单限制](https://developer.qiniu.com/dora/8336/a-word-recognition)。

腾讯ASR SentenceRecognition可Base64（60秒，编码后3MB），每月5000次免费；TC3签名宜用官方Java SDK。TTS TextToVoice返回Base64音频，中文单次150字；基础/精品800万、大模型10万、超自然2万字符免费包，领取后3个月、每账号一次。免费用尽后能否停服与付费开通状态有关，需控制台确认。尚未接入本项目。

来源：[腾讯ASR](https://cloud.tencent.com/document/api/1093/35646)、[免费ASR](https://cloud.tencent.com/document/product/1093/35693)、[腾讯TTS](https://cloud.tencent.com/document/api/1073/37995)、[TTS计费](https://cloud.tencent.com/document/product/1073/34112)。

火山豆包新版TTS提供HTTP Chunked返回Base64音频，可通过X-Api-Key鉴权；录音识别极速版当前新版文档只明确audio.url，不据旧资料假定支持Base64。免费试用需控制台手动开通，额度与有效期按控制台，正式服务超额计费。尚未接入本项目。

来源：[豆包TTS](https://docs.volcengine.com/docs/DoubaoVoice/unidirectional-streaming-text-to-speech-http?lang=zh)、[极速ASR](https://docs.volcengine.com/docs/DoubaoVoice/recording-file-recognition-lite-http?lang=zh)、[计费](https://docs.volcengine.com/docs/DoubaoVoice/BillingOverview-15?lang=zh)。

## 本地运行说明

默认已部署llama.cpp CPU服务与Qwen2.5-0.5B-Instruct Q4_K_M模型，通过兼容OpenAI的SSE接口输出。也可以配置Ollama或其他兼容服务。模型需下载，许可证按模型确认，无云端调用费用。

已打包`local-speech/`兼容服务：faster-whisper tiny CPU/int8识别，eSpeak NG中文机械音合成。使用`--profile local-speech`启动，模型首次下载后可设置`WHISPER_LOCAL_FILES_ONLY=true`离线运行。已通过真实HTTP与官方JFK人声样本识别，再经真实WS完成ASR→LLM→WAV闭环；这不是中文准确率或自然音质评测。tiny中文表现有限，eSpeak音色机械，适合零云密钥验证链路。Piper未接入；后续采用须确认模型许可证与协议转换。浏览器TTS可无需key体验，音色依操作系统和浏览器而异。详见[本地服务](../local-speech/README.md)。

来源：[Ollama API](https://docs.ollama.com/api/chat)、[faster-whisper](https://github.com/SYSTRAN/faster-whisper)、[Piper](https://github.com/OHF-Voice/piper1-gpl)、[Piper HTTP](https://raw.githubusercontent.com/OHF-Voice/piper1-gpl/main/docs/API_HTTP.md)。

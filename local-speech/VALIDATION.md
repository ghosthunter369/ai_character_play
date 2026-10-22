# 本地语音实际验证记录

2026-10-22，Docker Linux / Python 3.11，faster-whisper 1.2.1，官方Systran tiny多语种，CPU/int8，eSpeak NG 1.52。

- 首轮pytest RED：Uvicorn无法导入尚未实现的app。
- 实现后11个真实HTTP/eSpeak/输入边界测试通过，未载入ASR明确503。
- 官方tiny模型下载到compose命名卷`ai-character-play_speech-models:/models`，实际初始化成功。
- 重新启动服务，`--network none`、模型卷只读、`WHISPER_LOCAL_FILES_ONLY=true`，health实际报告ready=true，证明运行阶段不依赖云key、代理或外部下载。
- ASR并发4个真实HTTP请求，返回200和429，单路识别限制有效。

真实样本结果（eSpeak生成，非真人录音）：

| 输入 | tiny识别结果 |
|---|---|
| This is a local speech recognition test. | This is a local speech recognition test. |
| Please open the door. | Please open the door. |
| The quick brown fox jumps over the lazy dog. | The quick roundfax jumps over the rainy dog. |
| Hello world. This is a test. | The long world, this is my test. |
| 你好，这是本地语音测试。 | 你好 傻傻的原因 傻傻傻 |

英文机械语音最初的严格fox断言实际失败，该失败保留为质量限制。最终推理接口烟测改为官方Whisper测试使用的JFK人声录音，并沿用官方的三个明确短语断言（my fellow americans / your country / do for you），不放宽该人声测试断言。机械合成语音不作为人声准确率基准。上述13项实际HTTP测试通过，包含官方人声真实推理和并发429验证；这只证明接口闭环，**真人中文、口音、噪声环境准确率尚未评测**。tiny对机械中文样本有严重误识别，不能称中文准确率验收通过。中文eSpeak输出是可播放的低质量机械音；需要自然音色或更高准确率时使用云供应商或另选本地模型。

官方人声实际结果：And so, my fellow Americans, ask not what your country can do for you, ask what you can do for your country.

样本来源：https://github.com/openai/whisper/blob/main/tests/jfk.flac；对应官方测试：https://github.com/openai/whisper/blob/main/tests/test_transcribe.py。转换后的16kHz单声道PCM16 WAV在tests/fixtures/jfk-16k.wav，原始FLAC与WAV校验值记录在同目录source.json。

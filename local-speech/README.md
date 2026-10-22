# 本地语音服务

这是不需云端key的基础语音服务：ASR运行官方Systran faster-whisper tiny多语种模型，CPU/int8；TTS调用容器内 `espeak-ng -v cmn` 输出真实中文WAV。eSpeak是质量较低的机械音，不是神经音色，也不克隆声音。本次没有引入Piper模型。Whisper tiny中文准确率有限，需要更好识别时可在确认机器资源后调整模型并重新预载。

## 启动与缓存

```powershell
docker build -t ai-character-local-speech local-speech
docker volume create ai-character-local-speech-models
docker run --rm -v ai-character-local-speech-models:/models ai-character-local-speech python download_model.py
docker run -d --name ai-character-local-speech -p 127.0.0.1:18000:8000 -e WHISPER_LOCAL_FILES_ONLY=true -v ai-character-local-speech-models:/models ai-character-local-speech
```

应用compose内服务名应为`local-speech`，后端使用 `LOCAL_SPEECH_BASE_URL=http://local-speech:8000/v1`。不需要把8000暴露到公网。模型命名卷应一直挂在`/models`，更换服务容器不必重新下载。

如果首次HuggingFace下载被当前网络阻断，仅为下载容器增加 `-e HTTPS_PROXY=http://host.docker.internal:7890`，完成后运行服务不需要代理。不要修改系统代理。下载脚本会实际初始化模型以检查缓存完整性。

默认`WHISPER_PRELOAD=true`，启动后后台载入模型，缺缓存时尝试首次下载。`WHISPER_LOCAL_FILES_ONLY=true`强制只用缓存；未下载时会明确失败。`/health`返回如下状态：

- 模型未载入/下载载入中/载入失败：HTTP503，`ready=false`，`asr.status`分别为`not_loaded/loading/failed`。
- ASR模型与eSpeak均可用：HTTP200，`ready=true`、`asr.status=ready`。
- TTS可在ASR尚未载入时单独调用，健康响应中`tts.ready`单独显示。

首次下载可能超过Docker健康检查的5分钟启动宽限期；容器不会因此删除缓存，完成载入后可恢复健康。下载失败后先运行预载命令，再重启服务。

| 环境变量 | 默认值 | 说明 |
|---|---|---|
| WHISPER_MODEL | tiny | 仅启动时选择模型；请求不能触发任意模型下载 |
| WHISPER_DOWNLOAD_ROOT | /models | 模型缓存目录，挂命名卷 |
| WHISPER_PRELOAD | true | 启动后台下载/载入；测试可false |
| WHISPER_LOCAL_FILES_ONLY | false | true时严格离线只读取缓存 |
| WHISPER_CPU_THREADS | 2 | CPU工作线程 |

## API与边界

- `GET /health`：分开报告模型与TTS状态，不把未载入模型说成ready。
- `POST /v1/audio/transcriptions`：multipart的`file`为PCM WAV、`model`支持`whisper-1`或已配置的模型名；返回`{"text":"..."}`。最多30秒/5MiB，单声道或双声道、16位PCM、8–48kHz。原上传文件名被忽略。识别单路并发，繁忙返回429，未载入返回503，空白音频可返回空文字。上传整体大小在multipart解析前有限制。
- `POST /v1/audio/speech`：JSON `{"model":"tts-1","input":"你好","voice":"alloy","response_format":"wav"}`；返回`audio/wav`。`alloy/cmn/zh`都映射固定中文机械音；不允许用户指定程序参数。文字1–600字符，只支持WAV。

临时文件使用固定服务临时根目录中的独立请求目录，目录内文件名固定为`input.wav/output.wav`，不会使用客户端路径；请求结束删除。eSpeak使用无shell的参数列表与stdin，最多运行20秒；API错误不泄露内部路径。服务只应位于compose内部网络，当前不提供独立公网鉴权。

## 测试

```powershell
docker build --target test -t ai-character-local-speech:test local-speech
docker run --rm ai-character-local-speech:test
```

默认pytest实际启动Uvicorn通过HTTP验证接口和真正eSpeak WAV，不使用假模型冒充ASR。真实ASR测试需要先下载模型并启动服务：

```powershell
docker run --rm --network container:ai-character-local-speech -e SPEECH_TEST_URL=http://127.0.0.1:8000 ai-character-local-speech:test pytest -q tests -m model
```

模型测试通过真实HTTP识别官方Whisper的JFK人声样本（tests/fixtures/jfk-16k.wav），检查与官方测试相同的三个明确短语；这属于真实推理接口烟测，不是准确率评测。prepare_sample.py可从已核实的官方仓库下载原始FLAC并转换16k PCM WAV，source.json记录原始与转换后SHA-256。tiny对eSpeak合成英语有错字，对合成中文也存在严重误识别；不把机械合成语音当作人声准确率基准，真人中文、口音、噪声环境准确率未评测。详细结果见[VALIDATION.md](VALIDATION.md)。

官方来源：[faster-whisper](https://github.com/SYSTRAN/faster-whisper)、[1.2.1发行版](https://pypi.org/project/faster-whisper/1.2.1/)、[Systran tiny模型](https://huggingface.co/Systran/faster-whisper-tiny)、[eSpeak NG](https://github.com/espeak-ng/espeak-ng)。

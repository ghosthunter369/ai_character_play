package com.character.service.impl;

import com.character.service.ITTSService;
import com.character.service.AudioDataWithXunfeiSeq;
import com.character.util.TTSUtil;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * TTS 语音合成服务实现 - 简化版本，每次新建连接使用完即销毁
 */
@Service
public class TTSServiceImpl implements ITTSService {

    private static final Logger logger = LoggerFactory.getLogger(TTSServiceImpl.class);
    
    // 讯飞TTS配置 - 根据官方文档
    private static final String HOST_URL = "https://cbm01.cn-huabei-1.xf-yun.com/v1/private/mcd9m97e6";
    private static final String VCN = "x5_lingfeiyi_flow"; // 发音人参数

    @Value("${xunfei.app-id}")
    private String appId;

    @Value("${xunfei.access-key-id}")
    private String apiKey;

    @Value("${xunfei.access-key-secret}")
    private String apiSecret;

    private final OkHttpClient client;
    private final ConcurrentHashMap<String, Sinks.Many<AudioDataWithXunfeiSeq>> sessionAudioSinks = new ConcurrentHashMap<>();

    public TTSServiceImpl() {
        this.client = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(10, TimeUnit.SECONDS)
                .build();
    }

    @Override
    public Flux<byte[]> streamTextToSpeech(String sessionId, Flux<String> textFlux, Long appId) {
        logger.info("启动流式TTS，会话ID: {}, appId: {}", sessionId, appId);

        // 创建音频数据流
        Sinks.Many<AudioDataWithXunfeiSeq> audioSink = Sinks.many().multicast().onBackpressureBuffer();
        sessionAudioSinks.put(sessionId, audioSink);

        // 创建新的TTS连接
        TTSConnection connection = null;
        try {
            connection = createNewConnection(sessionId, audioSink);
            final TTSConnection finalConnection = connection;
            
            // 处理文本流
            textFlux.subscribe(
                text -> {
                    if (text != null && !text.trim().isEmpty()) {
                        finalConnection.sendText(text, false);
                    }
                },
                error -> {
                    logger.error("文本流处理错误，会话ID: {}", sessionId, error);
                    finalConnection.close();
                    cleanupSession(sessionId);
                },
                () -> {
                    logger.info("文本流结束，发送结束标记，会话ID: {}", sessionId);
                    finalConnection.sendEndMarker();
                    // 注意：连接会在收到完成响应后自动关闭
                }
            );

            return audioSink.asFlux()
                    .map(AudioDataWithXunfeiSeq::getAudioData)
                    .doOnTerminate(() -> {
                        logger.info("音频流结束，清理会话: {}", sessionId);
                        finalConnection.close();
                        cleanupSession(sessionId);
                    });

        } catch (Exception e) {
            logger.error("创建TTS连接失败，会话ID: {}", sessionId, e);
            if (connection != null) {
                connection.close();
            }
            cleanupSession(sessionId);
            return Flux.error(e);
        }
    }

    @Override
    public void closeTTSSession(String sessionId) {
        logger.info("关闭TTS会话: {}", sessionId);
        cleanupSession(sessionId);
    }



    private void cleanupSession(String sessionId) {
        Sinks.Many<AudioDataWithXunfeiSeq> sink = sessionAudioSinks.remove(sessionId);
        if (sink != null) {
            sink.tryEmitComplete();
        }
    }

    private TTSConnection createNewConnection(String sessionId, Sinks.Many<AudioDataWithXunfeiSeq> audioSink) throws Exception {
        String authUrl = TTSUtil.getAuthUrl(HOST_URL, apiKey, apiSecret);
        String wsUrl = authUrl.replace("http://", "ws://").replace("https://", "wss://");
        
        TTSConnection connection = new TTSConnection(wsUrl, sessionId, audioSink);
        if (connection.connect()) {
            logger.info("成功创建TTS连接，会话ID: {}", sessionId);
            return connection;
        } else {
            throw new Exception("TTS连接建立失败，会话ID: " + sessionId);
        }
    }

    /**
     * TTS连接封装类 - 简化版本，用完即销毁
     */
    private class TTSConnection {
        private final String wsUrl;
        private final String sessionId;
        private final Sinks.Many<AudioDataWithXunfeiSeq> audioSink;
        private WebSocket webSocket;
        private volatile boolean connected = false;
        private final AtomicInteger audioSequenceNumber = new AtomicInteger(0);

        public TTSConnection(String wsUrl, String sessionId, Sinks.Many<AudioDataWithXunfeiSeq> audioSink) {
            this.wsUrl = wsUrl;
            this.sessionId = sessionId;
            this.audioSink = audioSink;
        }

        public boolean connect() throws Exception {
            CountDownLatch connectionLatch = new CountDownLatch(1);
            final boolean[] connectionSuccess = {false};

            Request request = new Request.Builder().url(wsUrl).build();
            
            webSocket = client.newWebSocket(request, new WebSocketListener() {
                @Override
                public void onOpen(WebSocket webSocket, Response response) {
                    connected = true;
                    connectionSuccess[0] = true;
                    connectionLatch.countDown();
                    logger.debug("TTS WebSocket连接已建立，会话ID: {}", sessionId);
                    sendInitMessage();
                }

                @Override
                public void onMessage(WebSocket webSocket, String responseText) {
                    super.onMessage(webSocket, responseText);
                    //处理返回数据
                    logger.info("receive=>" + responseText);
                    try {
                        JsonObject jsonParse = JsonParser.parseString(responseText).getAsJsonObject();
                        
                        // 检查错误码
                        if (jsonParse.has("code")) {
                            int code = jsonParse.get("code").getAsInt();
                            if (code != 0) {
                                logger.error("发生错误，错误码为：{}, 会话ID: {}", code, sessionId);
                                if (jsonParse.has("sid")) {
                                    logger.error("本次请求的sid为：{}", jsonParse.get("sid").getAsString());
                                }
                                audioSink.tryEmitError(new RuntimeException("TTS服务错误，错误码: " + code));
                                close();
                                return;
                            }
                        }

                        // 处理音频数据
                        if (jsonParse.has("payload") && jsonParse.get("payload").getAsJsonObject().has("audio") &&
                                jsonParse.get("payload").getAsJsonObject().get("audio").getAsJsonObject().has("audio")) {
                            try {
                                JsonObject payload = jsonParse.get("payload").getAsJsonObject();
                                JsonObject audioObj = payload.get("audio").getAsJsonObject();
                                
                                byte[] audioData = Base64.getDecoder().decode(audioObj.get("audio").getAsString());
                                int seq = audioObj.get("seq").getAsInt(); // 获取讯飞返回的序号
                                int status = audioObj.get("status").getAsInt(); // 获取数据状态
                                
                                //发送二进制音频数据到前端，包含序号和状态信息
                                AudioDataWithXunfeiSeq audioPacket = new AudioDataWithXunfeiSeq(audioData, seq, status);
                                audioSink.tryEmitNext(audioPacket);
                                
                                logger.info("接收到TTS音频数据，会话ID: {}, 讯飞序号: {}, 状态: {}, 长度: {} 字节", 
                                        sessionId, seq, status, audioData.length);
                            } catch (Exception e) {
                                logger.error("解码TTS音频数据失败，会话ID: {}", sessionId, e);
                            }
                        }

                        // 检查是否完成
                        if (jsonParse.has("header") && jsonParse.get("header").getAsJsonObject().get("status").getAsInt() == 2) {
                            logger.info("TTS合成完成，会话ID: {}", sessionId);
                            audioSink.tryEmitComplete();
                            close(); // 立即关闭连接
                        }
                    } catch (Exception e) {
                        logger.error("处理TTS响应数据失败，会话ID: {}, 响应内容: {}", sessionId, responseText, e);
                        audioSink.tryEmitError(e);
                        close();
                    }
                }

                @Override
                public void onFailure(WebSocket webSocket, Throwable t, Response response) {
                    connected = false;
                    connectionLatch.countDown();
                    logger.error("TTS WebSocket连接失败，会话ID: {}", sessionId, t);
                    audioSink.tryEmitError(new RuntimeException("TTS连接失败", t));
                }

                @Override
                public void onClosed(WebSocket webSocket, int code, String reason) {
                    connected = false;
                    logger.debug("TTS WebSocket连接已关闭，会话ID: {}, 原因: {}", sessionId, reason);
                }
            });

            // 等待连接建立
            if (!connectionLatch.await(10, TimeUnit.SECONDS)) {
                if (webSocket != null) {
                    webSocket.close(1000, "连接超时");
                }
                throw new Exception("TTS WebSocket连接超时，会话ID: " + sessionId);
            }

            if (!connectionSuccess[0]) {
                throw new Exception("TTS WebSocket连接建立失败，会话ID: " + sessionId);
            }

            return true;
        }

        public void sendText(String text, boolean isLast) {
            if (!connected || webSocket == null) {
                logger.warn("TTS连接未建立，无法发送文本，会话ID: {}", sessionId);
                return;
            }

            JsonObject requestData = buildTTSRequest(text, isLast);
            String requestJson = requestData.toString();
            webSocket.send(requestJson);
            logger.debug("发送TTS文本，会话ID: {}, 文本: {}", sessionId, text.isEmpty() ? "[结束标记]" : text);
        }

        public void sendEndMarker() {
            sendText("", true);
        }

        private void sendInitMessage() {
            if (!connected || webSocket == null) {
                logger.warn("TTS连接未建立，无法发送初始化请求，会话ID: {}", sessionId);
                return;
            }

            // 发送初始化请求，包含一个空的开始标记
            JsonObject requestData = buildInitRequest();
            String requestJson = requestData.toString();
            webSocket.send(requestJson);
            logger.debug("发送TTS初始化请求，会话ID: {}", sessionId);
            logger.debug("TTS初始化请求JSON: {}", requestJson);
        }

        private JsonObject buildInitRequest() {
            JsonObject requestData = new JsonObject();
            JsonObject header = new JsonObject();
            JsonObject parameter = new JsonObject();
            JsonObject payload = new JsonObject();

            // 填充header - 初始化请求
            header.addProperty("app_id", appId);
            header.addProperty("status", 0); // 0表示开始

            // 填充parameter
            JsonObject tts = new JsonObject();
            tts.addProperty("vcn", VCN);
            tts.addProperty("speed", 55); // 稍微提升语速，更自然
            tts.addProperty("volume", 60); // 提升音量，减少噪音
            tts.addProperty("pitch", 50);
            tts.addProperty("bgs", 0);
            tts.addProperty("reg", 0);
            tts.addProperty("rdn", 0);
            tts.addProperty("rhy", 1); // 启用韵律优化

            JsonObject audio = new JsonObject();
            audio.addProperty("encoding", "raw"); // PCM原始格式
            audio.addProperty("sample_rate", 16000);
            audio.addProperty("channels", 1);
            audio.addProperty("bit_depth", 16);
            audio.addProperty("frame_size", 1024); // 修正为讯飞支持的最大帧大小

            tts.add("audio", audio);
            parameter.add("tts", tts);

            // 填充payload - 初始化请求，发送一个空的开始标记
            JsonObject textObj = new JsonObject();
            textObj.addProperty("encoding", "utf8");
            textObj.addProperty("compress", "raw");
            textObj.addProperty("format", "json");
            textObj.addProperty("status", 0); // 0表示开始
            textObj.addProperty("seq", audioSequenceNumber.getAndIncrement());
            // 发送一个占位符文本作为初始化，避免空文本错误
            try {
                textObj.addProperty("text", Base64.getEncoder().encodeToString("\\".getBytes(StandardCharsets.UTF_8)));
            } catch (Exception e) {
                logger.error("初始化文本编码失败", e);
            }

            payload.add("text", textObj);
            requestData.add("header", header);
            requestData.add("parameter", parameter);
            requestData.add("payload", payload);

            return requestData;
        }

        private JsonObject buildTTSRequest(String text, boolean isLast) {
            JsonObject requestData = new JsonObject();
            JsonObject header = new JsonObject();
            JsonObject parameter = new JsonObject();
            JsonObject payload = new JsonObject();

            // 填充header
            header.addProperty("app_id", appId);
            header.addProperty("status", isLast ? 2 : 1);

            // 填充parameter
            JsonObject tts = new JsonObject();
            tts.addProperty("vcn", VCN);
            tts.addProperty("speed", 55); // 稍微提升语速，更自然
            tts.addProperty("volume", 60); // 提升音量，减少噪音
            tts.addProperty("pitch", 50);
            tts.addProperty("bgs", 0);
            tts.addProperty("reg", 0);
            tts.addProperty("rdn", 0);
            tts.addProperty("rhy", 1); // 启用韵律优化

            JsonObject audio = new JsonObject();
            audio.addProperty("encoding", "raw"); // PCM原始格式
            audio.addProperty("sample_rate", 16000);
            audio.addProperty("channels", 1);
            audio.addProperty("bit_depth", 16);
            audio.addProperty("frame_size", 1024); // 修正为讯飞支持的最大帧大小

            tts.add("audio", audio);
            parameter.add("tts", tts);

            // 填充payload
            JsonObject textObj = new JsonObject();
            textObj.addProperty("encoding", "utf8");
            textObj.addProperty("compress", "raw");
            textObj.addProperty("format", "json");
            textObj.addProperty("status", isLast ? 2 : 1);
            textObj.addProperty("seq", audioSequenceNumber.getAndIncrement());

            if (!text.isEmpty()) {
                try {
                    textObj.addProperty("text", Base64.getEncoder().encodeToString(text.getBytes("utf8")));
                } catch (Exception e) {
                    logger.error("文本编码失败", e);
                }
            }

            payload.add("text", textObj);
            requestData.add("header", header);
            requestData.add("parameter", parameter);
            requestData.add("payload", payload);

            return requestData;
        }

        public void close() {
            connected = false;
            if (webSocket != null) {
                webSocket.close(1000, "正常关闭");
                webSocket = null;
                logger.debug("TTS连接已关闭，会话ID: {}", sessionId);
            }
        }

        public boolean isConnected() {
            return connected;
        }
    }
}
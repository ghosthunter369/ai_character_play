package com.character.service.impl;

import com.character.controller.AiChatController;
import com.character.model.entity.User;
import com.character.service.ASRService;
import com.character.service.TTSService;
import com.character.service.XunfeiConnectionPool;
import jakarta.annotation.Resource;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.nio.ByteBuffer;
import java.util.Base64;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 语音识别服务实现
 */
@Service
public class ASRServiceImpl implements ASRService {

    private static final Logger logger = LoggerFactory.getLogger(ASRServiceImpl.class);
    private static final int AUDIO_FRAME_SIZE = 1280;
    private static final int FRAME_INTERVAL_MS = 40;

    @Resource
    private XunfeiConnectionPool connectionPool;
    @Resource
    private AiChatController aiChatController;
    @Autowired
    private TTSService ttsService;

    private final ConcurrentHashMap<String, Sinks.Many<String>> sessionSinks = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, XunfeiConnectionPool.XunfeiConnection> sessionConnections = new ConcurrentHashMap<>();

    @Override
    public Flux<String> startASR(String sessionId, Long appId, User loginUser) {
        logger.info("开始语音识别，会话ID: {}", sessionId);

        Sinks.Many<String> sink = Sinks.many().multicast().onBackpressureBuffer();
        sessionSinks.put(sessionId, sink);

        try {
            XunfeiConnectionPool.XunfeiConnection connection = connectionPool.getConnection();
            sessionConnections.put(sessionId, connection);
            connection.sendStartMessage();

            connection.setMessageHandler(message -> {
                try {
                    String result = processXunfeiMessage(message);
                    if (result != null && !result.isEmpty()) {
                        sink.tryEmitNext(result);
                        
                        if (result.startsWith("FINAL:")) {
                            String finalText = result.substring(6);
                            logger.info("触发AI回复，输入: [{}]", finalText);
                            
                            handleAIReply(finalText, appId, loginUser, sink, sessionId);
                        }
                    }
                } catch (Exception e) {
                    logger.error("处理讯飞消息失败", e);
                    sink.tryEmitError(e);
                }
            });

            return sink.asFlux()
                    .doOnCancel(() -> cleanupSession(sessionId))
                    .doOnComplete(() -> cleanupSession(sessionId))
                    .doOnError(error -> {
                        logger.error("语音识别流错误，会话ID: " + sessionId, error);
                        cleanupSession(sessionId);
                    });

        } catch (Exception e) {
            logger.error("启动语音识别失败", e);
            sink.tryEmitError(e);
            sessionSinks.remove(sessionId);
            return sink.asFlux();
        }
    }

    @Override
    public void sendAudioData(String sessionId, ByteBuffer audioData, Long appId, User loginUser) {
        try {
            XunfeiConnectionPool.XunfeiConnection connection = sessionConnections.get(sessionId);
            if (connection == null) {
                logger.warn("会话连接不存在，会话ID: {}", sessionId);
                return;
            }

            byte[] audioBytes = new byte[audioData.remaining()];
            audioData.get(audioBytes);
            processAudioFrames(sessionId, audioBytes, connection);

        } catch (Exception e) {
            logger.error("发送音频数据失败，会话ID: " + sessionId, e);
            Sinks.Many<String> sink = sessionSinks.get(sessionId);
            if (sink != null) {
                sink.tryEmitError(e);
            }
        }
    }

    @Override
    public void sendSegmentEnd(String sessionId, Long appId, User loginUser) {
        logger.info("段落结束检测，会话ID: {}", sessionId);
        // 让讯飞根据音频静音自然检测语音结束
    }

    @Override
    public void endASR(String sessionId, Long appId, User loginUser) {
        logger.info("结束语音识别，会话ID: {}", sessionId);
        
        XunfeiConnectionPool.XunfeiConnection connection = sessionConnections.get(sessionId);
        if (connection != null) {
            connection.sendEndMessage();
            sessionConnections.remove(sessionId);
            connectionPool.returnConnection(connection);
        }
    }

    public void cleanupSession(String sessionId) {
        logger.info("清理会话资源，会话ID: {}", sessionId);

        Sinks.Many<String> sink = sessionSinks.remove(sessionId);
        if (sink != null && !sink.tryEmitComplete().isSuccess()) {
            logger.warn("无法正常关闭结果流，会话ID: {}", sessionId);
        }

        XunfeiConnectionPool.XunfeiConnection connection = sessionConnections.remove(sessionId);
        if (connection != null) {
            try {
                connection.sendEndMessage();
                connectionPool.returnConnection(connection);
            } catch (Exception e) {
                logger.error("归还连接失败，会话ID: " + sessionId, e);
            }
        }
    }

    private void handleAIReply(String finalText, Long appId, User loginUser, Sinks.Many<String> sink, String sessionId) {
        try {
            Flux<String> replyFlux = aiChatController.voiceChatWithUser(appId, finalText, loginUser);
            StringBuilder fullReplyBuilder = new StringBuilder();
            
            replyFlux.subscribe(
                reply -> {
                    sink.tryEmitNext("REPLY:" + reply);
                    fullReplyBuilder.append(reply);
                },
                err -> logger.error("AI回复流错误，会话ID: " + sessionId, err),
                () -> {
                    String fullReply = fullReplyBuilder.toString();
                    logger.info("AI回复完成，开始TTS转换，文本长度: {}", fullReply.length());
                    
                    if (!fullReply.trim().isEmpty()) {
                        java.util.concurrent.CompletableFuture.runAsync(() -> {
                            try {
                                byte[] audioData = ttsService.textToSpeech(fullReply);
                                String audioMessage = "AUDIO:" + Base64.getEncoder().encodeToString(audioData);
                                sink.tryEmitNext(audioMessage);
                                logger.info("TTS转换完成，会话ID: {}, 音频大小: {} 字节", sessionId, audioData.length);
                            } catch (Exception e) {
                                logger.error("TTS转换失败，会话ID: " + sessionId, e);
                                sink.tryEmitNext("ERROR:TTS转换失败: " + e.getMessage());
                            }
                        });
                    }
                }
            );
        } catch (Exception e) {
            logger.error("AI回复失败，会话ID: {}", sessionId, e);
        }
    }

    private void processAudioFrames(String sessionId, byte[] audioData, XunfeiConnectionPool.XunfeiConnection connection) {
        int totalFrames = (audioData.length + AUDIO_FRAME_SIZE - 1) / AUDIO_FRAME_SIZE;

        java.util.concurrent.CompletableFuture.runAsync(() -> {
            try {
                for (int i = 0; i < totalFrames; i++) {
                    int start = i * AUDIO_FRAME_SIZE;
                    int end = Math.min(start + AUDIO_FRAME_SIZE, audioData.length);
                    byte[] frame = new byte[end - start];
                    System.arraycopy(audioData, start, frame, 0, end - start);

                    if (connection.isConnected()) {
                        connection.sendAudioFrame(frame);
                    }

                    if (i < totalFrames - 1) {
                        Thread.sleep(FRAME_INTERVAL_MS);
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.warn("音频帧发送被中断，会话ID: {}", sessionId);
            } catch (Exception e) {
                logger.error("音频帧处理异常，会话ID: " + sessionId, e);
            }
        });
    }

    private String processXunfeiMessage(String message) {
        try {
            JSONObject json = new JSONObject(message);

            if (!"result".equals(json.optString("msg_type"))) {
                return null;
            }

            if (json.has("data")) {
                JSONObject data = json.getJSONObject("data");
                if (data.has("cn")) {
                    JSONObject cn = data.getJSONObject("cn");
                    if (cn.has("st")) {
                        JSONObject st = cn.getJSONObject("st");
                        int type = st.optInt("type", 0);
                        boolean isPartial = (type == 1);
                        
                        if (st.has("rt")) {
                            JSONArray rt = st.getJSONArray("rt");
                            StringBuilder result = new StringBuilder();
                            
                            for (int i = 0; i < rt.length(); i++) {
                                JSONObject rtItem = rt.getJSONObject(i);
                                if (rtItem.has("ws")) {
                                    JSONArray ws = rtItem.getJSONArray("ws");
                                    for (int j = 0; j < ws.length(); j++) {
                                        JSONObject wsItem = ws.getJSONObject(j);
                                        if (wsItem.has("cw")) {
                                            JSONArray cw = wsItem.getJSONArray("cw");
                                            for (int k = 0; k < cw.length(); k++) {
                                                JSONObject cwItem = cw.getJSONObject(k);
                                                String word = cwItem.optString("w", "");
                                                if (!word.isEmpty()) {
                                                    result.append(word);
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            String finalResult = result.toString().trim();
                            if (!finalResult.isEmpty()) {
                                return isPartial ? "PARTIAL:" + finalResult : "FINAL:" + finalResult;
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            logger.warn("解析讯飞消息失败: {}", e.getMessage());
        }
        return null;
    }
}
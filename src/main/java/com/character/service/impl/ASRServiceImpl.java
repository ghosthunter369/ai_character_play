package com.character.service.impl;

import com.character.controller.AiChatController;
import com.character.model.entity.User;
import com.character.service.ASRService;
import com.character.service.AudioStorageService;
import com.character.service.ITTSService;
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
import java.util.concurrent.TimeUnit;

/**
 * 实时语音识别服务实现（impl 目录只放实现类）
 */
@Service
public class ASRServiceImpl implements ASRService {

    private static final Logger logger = LoggerFactory.getLogger(ASRServiceImpl.class);

    // 音频帧配置
    private static final int AUDIO_FRAME_SIZE = 1280;  // 每帧字节数
    private static final int FRAME_INTERVAL_MS = 40;   // 帧间隔(毫秒)

    @Resource
    private XunfeiConnectionPool connectionPool;
    @Resource
    private AiChatController aiChatController;
    @Autowired
    private ITTSService ttsService;
    @Autowired
    private AudioStorageService audioStorageService;

    // 存储每个会话的结果流和连接
    private final ConcurrentHashMap<String, Sinks.Many<String>> sessionSinks = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, XunfeiConnectionPool.XunfeiConnection> sessionConnections = new ConcurrentHashMap<>();

    @Override
    public Flux<String> startASR(String sessionId, Long appId, User loginUser) {
        logger.info("开始实时语音识别，会话ID: {}, appId: {}, userId: {}", sessionId, appId, loginUser != null ? loginUser.getId() : null);

        Sinks.Many<String> sink = Sinks.many().multicast().onBackpressureBuffer();
        sessionSinks.put(sessionId, sink);

        try {
            // 获取讯飞连接
            XunfeiConnectionPool.XunfeiConnection connection = connectionPool.getConnection();
            sessionConnections.put(sessionId, connection);

            // 发送开始识别消息
            connection.sendStartMessage();

            // 设置消息处理器
            connection.setMessageHandler(message -> {
                try {
                    String result = processXunfeiMessage(message);
                    if (result != null && !result.isEmpty()) {
                        // 识别文本推送到前端
                        sink.tryEmitNext(result);
                        // 只有FINAL结果才触发AI回复
                        if (result.startsWith("FINAL:")) {
                            String finalText = result.substring(6); // 去掉"FINAL:"前缀
                            logger.info("触发AI回复，输入: [{}]", finalText);
                            try {
                                Flux<String> replyFlux = aiChatController.voiceChatWithUser(appId, finalText, loginUser);
                            
                                // 收集完整的AI回复文本
                                StringBuilder fullReplyBuilder = new StringBuilder();
                                
                                // 订阅AI回复流，收集完整文本并推送到前端显示
                                replyFlux.subscribe(
                                    reply -> {
                                        // 将 AI 回复文本推送到前端（用于实时显示）
                                        sink.tryEmitNext("REPLY:" + reply);
                                        // 同时收集完整文本用于TTS
                                        fullReplyBuilder.append(reply);
                                    },
                                    err -> {
                                        logger.error("AI 回复流错误，会话ID: " + sessionId, err);
                                    },
                                    () -> {
                                        // AI回复完成，开始同步TTS处理
                                        String fullReply = fullReplyBuilder.toString();
                                        logger.info("AI回复完成，开始TTS转换，文本长度: {}", fullReply.length());
                                        
                                        if (!fullReply.trim().isEmpty()) {
                                            // 异步处理TTS，避免阻塞主流程
                                            java.util.concurrent.CompletableFuture.runAsync(() -> {
                                                try {
                                                    // 使用同步TTS服务生成完整音频
                                                    byte[] audioData = ttsService.syncTextToSpeech(fullReply);
                                                    
                                                    // 将完整音频推送到前端
                                                    String audioMessage = "AUDIO:" + Base64.getEncoder().encodeToString(audioData);
                                                    sink.tryEmitNext(audioMessage);
                                                    
                                                    logger.info("TTS转换完成并推送到前端，会话ID: {}, 音频大小: {} 字节", 
                                                              sessionId, audioData.length);
                                                    
                                                } catch (Exception e) {
                                                    logger.error("TTS转换失败，会话ID: " + sessionId, e);
                                                    sink.tryEmitNext("ERROR:TTS转换失败: " + e.getMessage());
                                                }
                                            });
                                        }
                                    }
                                );
                            } catch (Exception e) {
                                logger.error("AI回复失败，会话ID: {}, err={}", sessionId, e.getMessage());
                            }
                        }
                    }
                } catch (Exception e) {
                    logger.error("处理讯飞消息失败", e);
                    sink.tryEmitError(e);
                }
            });

            logger.info("讯飞连接已建立，会话ID: {}", sessionId);

            return sink.asFlux()
                    .doOnCancel(() -> {
                        logger.info("取消语音识别流，会话ID: {}", sessionId);
                        cleanupSession(sessionId);
                    })
                    .doOnComplete(() -> {
                        logger.info("语音识别流完成，会话ID: {}", sessionId);
                        cleanupSession(sessionId);
                    })
                    .doOnError(error -> {
                        logger.error("语音识别流错误，会话ID: " + sessionId, error);
                        cleanupSession(sessionId);
                    });

        } catch (Exception e) {
            logger.error("启动实时语音识别失败", e);
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
            processAudioFrames(sessionId, audioBytes);

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
        logger.info("段落结束检测，等待讯飞自然返回最终结果（不发送end消息），会话ID: {}", sessionId);
        
        // 不发送end消息，让讯飞根据音频静音自然检测语音结束
        // 这样可以避免频繁的end/start消息导致的连接问题
        // 讯飞会在检测到足够长的静音后自动返回最终识别结果
        
        XunfeiConnectionPool.XunfeiConnection connection = sessionConnections.get(sessionId);
        if (connection != null) {
            logger.info("ASR连接保持活跃，等待讯飞自然检测语音结束，会话ID: {}", sessionId);
        } else {
            logger.warn("未找到ASR连接，会话ID: {}", sessionId);
        }
    }

    @Override
    public void endASR(String sessionId, Long appId, User loginUser) {
        logger.info("结束语音识别，会话ID: {}, appId: {}, userId: {}", sessionId, appId, loginUser != null ? loginUser.getId() : null);

        XunfeiConnectionPool.XunfeiConnection connection = sessionConnections.get(sessionId);
        if (connection != null) {
            connection.sendEndMessage();
        }

        // 不在这里发送结束消息，由WebSocketHandler统一处理
        logger.info("语音识别会话已结束，会话ID: {}", sessionId);

        // 只清理讯飞连接，保留WebSocket会话
        if (connection != null) {
            sessionConnections.remove(sessionId);
            connectionPool.returnConnection(connection);
            logger.debug("连接已归还到连接池，会话ID: {}", sessionId);
        }
    }

    private void processAudioFrames(String sessionId, byte[] audioData) {
        XunfeiConnectionPool.XunfeiConnection connection = sessionConnections.get(sessionId);
        if (connection == null) {
            logger.warn("会话连接不存在，会话ID: {}", sessionId);
            return;
        }

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
                        
                        // 获取结果类型：0-最终结果，1-中间结果
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
                                // 为partial结果添加标识前缀
                                String resultWithType = isPartial ? "PARTIAL:" + finalResult : "FINAL:" + finalResult;
                                return resultWithType;
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

    /**
     * 完全清理会话资源（仅在WebSocket连接关闭时调用）
     */
    public void cleanupSessionCompletely(String sessionId) {
        logger.info("完全清理会话资源，会话ID: {}", sessionId);

        Sinks.Many<String> sink = sessionSinks.remove(sessionId);
        if (sink != null && !sink.tryEmitComplete().isSuccess()) {
            logger.warn("无法正常关闭结果流，会话ID: {}", sessionId);
        }

        XunfeiConnectionPool.XunfeiConnection connection = sessionConnections.remove(sessionId);
        if (connection != null) {
            try {
                connection.sendEndMessage();
                connectionPool.returnConnection(connection);
                logger.debug("连接已归还到连接池，会话ID: {}", sessionId);
            } catch (Exception e) {
                logger.error("归还连接失败，会话ID: " + sessionId, e);
            }
        }
    }

    private void cleanupSession(String sessionId) {
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
                logger.debug("连接已归还到连接池，会话ID: {}", sessionId);
            } catch (Exception e) {
                logger.error("归还连接失败，会话ID: " + sessionId, e);
            }
        }
    }
}
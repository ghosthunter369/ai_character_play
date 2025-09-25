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
import java.util.concurrent.atomic.AtomicBoolean;

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
                    logger.info("处理讯飞消息结果: [{}]", result);
                    if (result != null && !result.isEmpty()) {
                        // 识别文本推送到前端
                        sink.tryEmitNext(result);
                        logger.info("ASR识别结果已发送到前端: [{}]", result);

                        // 将识别文本进入 AI 回复链路（voiceChat），把 AI 回复也以文本流推给前端
                        logger.info("开始调用AI对话服务，输入文本: [{}]", result);
                        try {
                            Flux<String> replyFlux = aiChatController.voiceChatWithUser(appId, result, loginUser);
                            
                            // 使用 share() 让流可以被多次订阅
                            Flux<String> sharedReplyFlux = replyFlux.share();
                            // 将回复流传入讯飞TTS，生成音频流
                            logger.info("开始调用TTS服务，会话ID: {}", sessionId);
                            
                            // 开始音频会话
                            audioStorageService.startAudioSession(sessionId);
                            
                            Flux<byte[]> audioFlux = ttsService.streamTextToSpeech(sessionId , sharedReplyFlux, appId);
                            logger.info("TTS服务调用完成，开始订阅音频流，会话ID: {}", sessionId);
                            Flux<byte[]> share = audioFlux.share();
                            
                            // 订阅音频流，将音频数据以特殊格式推送到前端（区别于文本）
                            share.subscribe(
                                    audioData -> {
                                        // 添加音频片段到存储服务并获取序号
                                        int sequenceNumber = audioStorageService.addAudioChunk(sessionId, audioData);
                                        
                                        logger.info("收到TTS音频数据，会话ID: {}, 序号: {}, 数据大小: {} 字节", 
                                                   sessionId, sequenceNumber, audioData.length);
                                        
                                        // 将音频数据编码为Base64并添加序号信息推送到前端
                                        String audioMessage = String.format("AUDIO:%d:%s", 
                                                                           sequenceNumber, 
                                                                           Base64.getEncoder().encodeToString(audioData));
                                        
                                        logger.debug("发送音频消息到前端，会话ID: {}, 序号: {}, Base64长度: {}", 
                                                    sessionId, sequenceNumber, audioMessage.length());
                                        sink.tryEmitNext(audioMessage);
                                    },
                                    err -> {
                                        logger.error("TTS音频流错误，会话ID: " + sessionId, err);
                                        logger.error("错误类型: {}, 错误消息: {}", err.getClass().getSimpleName(), err.getMessage());
                                        // 清理音频会话
                                        audioStorageService.cleanupSession(sessionId);
                                    },
                                    () -> {
                                        logger.info("TTS音频流完成，会话ID: {}", sessionId);
                                        // 完成音频会话并保存文件
                                        String savedFilePath = audioStorageService.finishAudioSession(sessionId);
                                        if (savedFilePath != null) {
                                            logger.info("音频文件已保存: {}", savedFilePath);
                                        }
                                    }
                            );
                            
                            // 同时将AI回复文本也推送到前端（用于显示）
                            sharedReplyFlux.subscribe(
                                    reply -> {
                                        // 将 AI 回复文本推送（前端可区分显示）
                                        sink.tryEmitNext("REPLY:" + reply);
                                    },
                                    err -> logger.error("AI 回复流错误，会话ID: " + sessionId, err)
                            );
                        } catch (Exception e) {
                            logger.warn("调用 voiceChat 或 TTS 出错，会话ID: {}, err={}", sessionId, e.getMessage());
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

            logger.debug("收到音频数据，会话ID: {}, 数据长度: {} 字节, appId: {}", sessionId, audioBytes.length, appId);

            // 分帧处理音频数据
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
        logger.debug("处理音频帧，会话ID: {}, 总数据长度: {} 字节", sessionId, audioData.length);

        XunfeiConnectionPool.XunfeiConnection connection = sessionConnections.get(sessionId);
        if (connection == null) {
            logger.warn("会话连接不存在，会话ID: {}", sessionId);
            return;
        }

        int totalFrames = (audioData.length + AUDIO_FRAME_SIZE - 1) / AUDIO_FRAME_SIZE;
        logger.debug("音频将分为 {} 帧处理", totalFrames);

        java.util.concurrent.CompletableFuture.runAsync(() -> {
            try {
                for (int i = 0; i < totalFrames; i++) {
                    int start = i * AUDIO_FRAME_SIZE;
                    int end = Math.min(start + AUDIO_FRAME_SIZE, audioData.length);
                    byte[] frame = new byte[end - start];
                    System.arraycopy(audioData, start, frame, 0, end - start);

                    if (connection.isConnected()) {
                        connection.sendAudioFrame(frame);
                        logger.debug("发送音频帧 {}/{}, 大小: {} 字节", i + 1, totalFrames, frame.length);
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
                                logger.debug("识别结果: {}", finalResult);
                                return finalResult;
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
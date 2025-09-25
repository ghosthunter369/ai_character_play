package com.character.service.impl;

import com.character.service.ITTSService;
import com.character.service.XunfeiTTSConnectionPool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * TTS 语音合成服务实现
 */
@Service
public class TTSServiceImpl implements ITTSService {

    private static final Logger logger = LoggerFactory.getLogger(TTSServiceImpl.class);

    @Autowired
    private XunfeiTTSConnectionPool connectionPool;

    // 存储每个会话的音频流和连接
    private final ConcurrentHashMap<String, Sinks.Many<byte[]>> sessionAudioSinks = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, XunfeiTTSConnectionPool.XunfeiTTSConnection> sessionTTSConnections = new ConcurrentHashMap<>();

    @Override
    public Flux<byte[]> streamTextToSpeech(String sessionId, Flux<String> textFlux, Long appId) {
        logger.info("启动流式TTS，会话ID: {}, appId: {}", sessionId, appId);
        
        // 检查是否已有相同会话的TTS连接，如果有则复用
        XunfeiTTSConnectionPool.XunfeiTTSConnection ttsConnection = sessionTTSConnections.get(sessionId);
        Sinks.Many<byte[]> audioSink = sessionAudioSinks.get(sessionId);
        
        if (ttsConnection != null && ttsConnection.isConnected() && audioSink != null) {
            logger.info("复用现有TTS连接，会话ID: {}", sessionId);
            // 复用现有连接和音频流 - multicast支持多次订阅
            return processTextWithExistingConnection(sessionId, textFlux, ttsConnection, audioSink);
        } else {
            logger.info("创建新的TTS连接，会话ID: {}", sessionId);
            // 清理可能存在的无效连接
            if (ttsConnection != null) {
                cleanupTTSSession(sessionId);
            }
            
            // 创建新的音频数据流 - 使用multicast支持多个订阅者
            audioSink = Sinks.many().multicast().onBackpressureBuffer();
            sessionAudioSinks.put(sessionId, audioSink);

            try {
                // 获取新的TTS连接
                ttsConnection = connectionPool.getConnection();
                sessionTTSConnections.put(sessionId, ttsConnection);
                logger.info("新TTS连接已建立，会话ID: {}", sessionId);

                // 设置音频数据处理器
                final Sinks.Many<byte[]> finalAudioSink = audioSink;
                ttsConnection.setHandlers(
                        audioData -> {
                            // 接收到音频数据，推送到流中
                            logger.debug("TTS 收到TTS音频数据，会话ID: {}, 大小: {} 字节", sessionId, audioData.length);
                            finalAudioSink.tryEmitNext(audioData);
                        },
                        error -> {
                            logger.error("TTS处理错误，会话ID: {}, 错误: {}", sessionId, error);
                            finalAudioSink.tryEmitError(new RuntimeException(error));
                            cleanupTTSSession(sessionId);
                        },
                        () -> {
                            logger.info("TTS合成完成，会话ID: {}", sessionId);
                            finalAudioSink.tryEmitComplete();
                            cleanupTTSSession(sessionId);
                        }
                );
                
                return processTextWithExistingConnection(sessionId, textFlux, ttsConnection, audioSink);
                
            } catch (Exception e) {
                logger.error("启动TTS流失败，会话ID: " + sessionId, e);
                audioSink.tryEmitError(e);
                sessionAudioSinks.remove(sessionId);
                return audioSink.asFlux();
            }
        }
    }
    
    /**
     * 使用现有连接处理文本流
     */
    private Flux<byte[]> processTextWithExistingConnection(String sessionId, Flux<String> textFlux, 
                                                          XunfeiTTSConnectionPool.XunfeiTTSConnection ttsConnection,
                                                          Sinks.Many<byte[]> audioSink) {

        // 订阅文本流并发送到TTS（使用现有连接）
        textFlux
                .filter(text -> text != null && !text.trim().isEmpty()) // 过滤空文本
                .doOnComplete(
                        () -> {
                            logger.info("文本流完成，发送结束标记，会话ID: {}", sessionId);
                            ttsConnection.sendEndMessage(); // 确保发送结束标记
                        }
                )
                .subscribe(
                        text -> {
                            logger.info("发送文本到TTS，会话ID: {}, 文本: [{}]", sessionId, text);
                            if (!text.trim().isEmpty()) {
                                //我怎么知道最后发送的是哪个
                                ttsConnection.sendText(text, false); // 中间片段，不是最后一个
                            } else {
                                logger.warn("⚠跳过空文本，会话ID: {}", sessionId);
                            }
                        },
                        error -> {
                            logger.error(" 文本流错误，会话ID: " + sessionId, error);
                            ttsConnection.sendEndMessage(); // 发送结束标记
                            audioSink.tryEmitError(error);
                        }
//                        () -> {
//                            logger.info("文本流完成，会话ID: {}", sessionId);
//                            ttsConnection.sendEndMessage(); // 发送结束标记
//                        }
                )

        ;

        return audioSink.asFlux()
                .doOnCancel(() -> {
                    logger.info("取消TTS流，会话ID: {}", sessionId);
                    // 注意：这里不立即清理连接，保持连接供后续使用
                })
                .doOnError(error -> {
                    logger.error("TTS流错误，会话ID: " + sessionId, error);
                    cleanupTTSSession(sessionId);
                });
    }

    @Override
    public void closeTTSSession(String sessionId) {
        logger.info("手动关闭TTS会话，会话ID: {}", sessionId);
        cleanupTTSSession(sessionId);
    }


    private void cleanupTTSSession(String sessionId) {
        logger.debug("清理TTS会话资源，会话ID: {}", sessionId);

        Sinks.Many<byte[]> audioSink = sessionAudioSinks.remove(sessionId);
        if (audioSink != null && !audioSink.tryEmitComplete().isSuccess()) {
            logger.warn("无法正常关闭TTS音频流，会话ID: {}", sessionId);
        }

        XunfeiTTSConnectionPool.XunfeiTTSConnection ttsConnection = sessionTTSConnections.remove(sessionId);
        if (ttsConnection != null) {
            try {
                connectionPool.returnConnection(ttsConnection);
                logger.debug("TTS连接已归还到连接池，会话ID: {}", sessionId);
            } catch (Exception e) {
                logger.error("归还TTS连接失败，会话ID: " + sessionId, e);
            }
        }
    }
}
package com.character.service.impl;

import com.character.service.AudioDataWithXunfeiSeq;
import com.character.service.ITTSService;
import com.character.service.XunfeiTTSConnectionPool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * TTS 语音合成服务实现
 */
@Service
public class TTSServiceImpl implements ITTSService {

    private static final Logger logger = LoggerFactory.getLogger(TTSServiceImpl.class);

    @Autowired
    private XunfeiTTSConnectionPool connectionPool;

    // 存储每个会话的音频流和连接
    private final ConcurrentHashMap<String, Sinks.Many<AudioDataWithXunfeiSeq>> sessionAudioSinks = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, XunfeiTTSConnectionPool.XunfeiTTSConnection> sessionTTSConnections = new ConcurrentHashMap<>();

    @Override
    public Flux<byte[]> streamTextToSpeech(String sessionId, Flux<String> textFlux, Long appId) {
        logger.info("启动流式TTS，会话ID: {}, appId: {}", sessionId, appId);
        
        // 检查是否已有相同会话的TTS连接，如果有则复用
        XunfeiTTSConnectionPool.XunfeiTTSConnection ttsConnection = sessionTTSConnections.get(sessionId);
        Sinks.Many<AudioDataWithXunfeiSeq> audioSink = sessionAudioSinks.get(sessionId);
        
        if (ttsConnection != null && ttsConnection.isConnected() && audioSink != null) {
            logger.info("复用现有TTS连接，会话ID: {}", sessionId);
            // 复用现有连接和音频流 - multicast支持多次订阅
            return processTextWithExistingConnection(sessionId, textFlux, ttsConnection, audioSink)
                    .map(AudioDataWithXunfeiSeq::getAudioData); // 转换为byte[]
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
                final Sinks.Many<AudioDataWithXunfeiSeq> finalAudioSink = audioSink;
                ttsConnection.setHandlers(
                        audioPacket -> {
                            // 接收到音频数据，推送到流中
                            byte[] audioData = audioPacket.getAudioData();
                            int seq = audioPacket.getSeq();
                            int status = audioPacket.getStatus();
                            
                            logger.debug("TTS 收到TTS音频数据，会话ID: {}, 讯飞序号: {}, 状态: {}, 大小: {} 字节", 
                                        sessionId, seq, status, audioData.length);
                            
                            // 创建包含讯飞序号的音频数据对象
                            AudioDataWithXunfeiSeq audioWithSeq = new AudioDataWithXunfeiSeq(audioData, seq, status);
                            finalAudioSink.tryEmitNext(audioWithSeq);
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
                
                return processTextWithExistingConnection(sessionId, textFlux, ttsConnection, audioSink)
                        .map(AudioDataWithXunfeiSeq::getAudioData); // 转换为byte[]
                
            } catch (Exception e) {
                logger.error("启动TTS流失败，会话ID: " + sessionId, e);
                audioSink.tryEmitError(e);
                sessionAudioSinks.remove(sessionId);
                return audioSink.asFlux().map(AudioDataWithXunfeiSeq::getAudioData);
            }
        }
    }
    
    /**
     * 使用现有连接处理文本流
     */
    private Flux<AudioDataWithXunfeiSeq> processTextWithExistingConnection(String sessionId, Flux<String> textFlux, 
                                                          XunfeiTTSConnectionPool.XunfeiTTSConnection ttsConnection,
                                                          Sinks.Many<AudioDataWithXunfeiSeq> audioSink) {

        // 使用AtomicBoolean跟踪是否已发送结束标记
        AtomicBoolean endMessageSent = new AtomicBoolean(false);
        
        // 收集所有文本片段，进行预处理和过滤，然后一次性发送完整文本
        textFlux
                .filter(text -> text != null && !text.trim().isEmpty()) // 过滤空文本
                .map(this::preprocessText) // 预处理文本：过滤特殊符号
                .collectList() // 收集所有文本片段
                .subscribe(
                        textList -> {
                            // 合并所有文本片段为一个完整的文本
                            String fullText = String.join("", textList);
                            
                            if (!fullText.trim().isEmpty()) {
                                logger.info("收到并处理完整文本，会话ID: {}, 文本长度: {}, 内容: [{}]", 
                                           sessionId, fullText.length(), fullText);
                                
                                // 一次性发送完整文本
                                ttsConnection.sendText(fullText, true);
                            }
                            
                            // 确保发送结束标记
                            if (!endMessageSent.getAndSet(true)) {
                                logger.info("文本发送完成，发送结束标记，会话ID: {}", sessionId);
                                ttsConnection.sendEndMessage();
                            }
                        },
                        error -> {
                            logger.error("文本流错误，会话ID: " + sessionId, error);
                            if (!endMessageSent.getAndSet(true)) {
                                ttsConnection.sendEndMessage(); // 发送结束标记
                            }
                            audioSink.tryEmitError(error);
                        }
                );

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


    /**
     * 预处理文本：过滤特殊符号，保留中文、英文、数字和基本标点符号
     */
    private String preprocessText(String text) {
        if (text == null || text.trim().isEmpty()) {
            return "";
        }
        
        // 定义允许的字符：中文、英文、数字、基本标点符号
        // 保留：中文字符、英文字母、数字、常用标点符号
        String cleanedText = text.replaceAll("[^\\u4e00-\\u9fa5a-zA-Z0-9，。！？；：、\\u201c\\u201d\\u2018\\u2019（）\\s]", "");
        
        // 去除多余的空格
        cleanedText = cleanedText.replaceAll("\\s+", " ").trim();
        
        logger.debug("文本预处理: [{}] -> [{}]", text, cleanedText);
        return cleanedText;
    }
    
    /**
     * 按标点符号智能分词：按照完整语义片段分割文本
     * 例如："我今天吃饭了，非常爽。" -> ["我今天吃饭了，", "非常爽。"]
     */
    private Flux<String> splitTextByPunctuation(String text) {
        if (text == null || text.trim().isEmpty()) {
            return Flux.empty();
        }
        
        // 按照主要标点符号分割，保留标点符号，形成完整的语义片段
        // 分割符号：，。！？；但保留标点符号在片段末尾
        String[] segments = text.split("(?<=[，。！？；])");
        
        List<String> processedSegments = Arrays.stream(segments)
                .map(String::trim)
                .filter(segment -> !segment.isEmpty())
                .filter(segment -> segment.length() >= 2) // 过滤太短的片段
                .collect(Collectors.toList());
        
        // 如果分割后的片段太少或太短，尝试合并相邻的短片段
        List<String> mergedSegments = mergeShortSegments(processedSegments);
        
        logger.debug("文本分词: [{}] -> {} 个完整片段: {}", text, mergedSegments.size(), mergedSegments);
        return Flux.fromIterable(mergedSegments);
    }
    
    /**
     * 合并过短的文本片段，确保每个片段都是完整的语义单元
     */
    private List<String> mergeShortSegments(List<String> segments) {
        if (segments.isEmpty()) {
            return segments;
        }
        
        List<String> merged = new java.util.ArrayList<>();
        StringBuilder currentSegment = new StringBuilder();
        
        for (String segment : segments) {
            // 如果当前累积的片段长度合适（5-30字符），就作为一个完整片段
            if (currentSegment.length() > 0 && 
                (currentSegment.length() + segment.length() > 30 || 
                 segment.matches(".*[。！？]$"))) { // 遇到句号、感叹号、问号就结束
                
                merged.add(currentSegment.toString().trim());
                currentSegment = new StringBuilder(segment);
            } else {
                currentSegment.append(segment);
            }
        }
        
        // 添加最后一个片段
        if (currentSegment.length() > 0) {
            merged.add(currentSegment.toString().trim());
        }
        
        // 确保每个片段都有合理的长度（至少3个字符）
        return merged.stream()
                .filter(s -> s.length() >= 3)
                .collect(Collectors.toList());
    }

    private void cleanupTTSSession(String sessionId) {
        logger.debug("清理TTS会话资源，会话ID: {}", sessionId);

        Sinks.Many<AudioDataWithXunfeiSeq> audioSink = sessionAudioSinks.remove(sessionId);
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
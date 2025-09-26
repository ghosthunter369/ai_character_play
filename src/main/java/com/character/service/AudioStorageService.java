package com.character.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 音频存储服务 - 负责拼接和存储TTS生成的音频数据
 */
@Service
public class AudioStorageService {

    private static final Logger logger = LoggerFactory.getLogger(AudioStorageService.class);
    
    // 音频存储目录
    private static final String AUDIO_STORAGE_DIR = "C:/test/ai_character_play/src/main/resources/audio";
    
    // 存储每个会话的音频数据和序号
    private final ConcurrentHashMap<String, ByteArrayOutputStream> sessionAudioBuffers = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicInteger> sessionSequenceNumbers = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> sessionAudioFiles = new ConcurrentHashMap<>();

    public AudioStorageService() {
        // 确保音频存储目录存在
        try {
            Path audioDir = Paths.get(AUDIO_STORAGE_DIR);
            if (!Files.exists(audioDir)) {
                Files.createDirectories(audioDir);
                logger.info("创建音频存储目录: {}", AUDIO_STORAGE_DIR);
            }
        } catch (Exception e) {
            logger.error("创建音频存储目录失败", e);
        }
    }

    /**
     * 开始新的音频会话
     */
    public void startAudioSession(String sessionId) {
        logger.info("开始音频会话: {}", sessionId);
        
        // 初始化音频缓冲区
        sessionAudioBuffers.put(sessionId, new ByteArrayOutputStream());
        sessionSequenceNumbers.put(sessionId, new AtomicInteger(0));
        
        // 生成音频文件名
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String fileName = String.format("tts_audio_%s_%s.mp3", sessionId.substring(0, 8), timestamp);
        String filePath = AUDIO_STORAGE_DIR + "/" + fileName;
        sessionAudioFiles.put(sessionId, filePath);
        
        logger.info("音频会话已初始化，会话ID: {}, 文件路径: {}", sessionId, filePath);
    }

    /**
     * 添加音频片段到会话缓冲区
     */
    public int addAudioChunk(String sessionId, byte[] audioData) {
        ByteArrayOutputStream buffer = sessionAudioBuffers.get(sessionId);
        AtomicInteger sequenceNumber = sessionSequenceNumbers.get(sessionId);
        
        if (buffer == null || sequenceNumber == null) {
            logger.warn("音频会话不存在，自动创建: {}", sessionId);
            startAudioSession(sessionId);
            buffer = sessionAudioBuffers.get(sessionId);
            sequenceNumber = sessionSequenceNumbers.get(sessionId);
        }
        
        try {
            // 将音频数据写入缓冲区
            buffer.write(audioData);
            int currentSeq = sequenceNumber.incrementAndGet();
            
            logger.debug("添加音频片段，会话ID: {}, 序号: {}, 大小: {} 字节, 总大小: {} 字节", 
                        sessionId, currentSeq, audioData.length, buffer.size());
            
            return currentSeq;
        } catch (IOException e) {
            logger.error("添加音频片段失败，会话ID: " + sessionId, e);
            return -1;
        }
    }

    /**
     * 完成音频会话并保存文件
     */
    public String finishAudioSession(String sessionId) {
        logger.info("完成音频会话: {}", sessionId);
        
        ByteArrayOutputStream buffer = sessionAudioBuffers.remove(sessionId);
        AtomicInteger sequenceNumber = sessionSequenceNumbers.remove(sessionId);
        String filePath = sessionAudioFiles.remove(sessionId);
        
        if (buffer == null || filePath == null) {
            logger.warn("音频会话数据不存在: {}", sessionId);
            return null;
        }
        
        try {
            byte[] completeAudioData = buffer.toByteArray();
            
            if (completeAudioData.length == 0) {
                logger.warn("音频数据为空，不保存文件，会话ID: {}", sessionId);
                return null;
            }
            
            // 保存完整音频文件
            try (FileOutputStream fos = new FileOutputStream(filePath)) {
                fos.write(completeAudioData);
                fos.flush();
            }
            
            int totalChunks = sequenceNumber != null ? sequenceNumber.get() : 0;
            logger.info("音频文件保存成功，会话ID: {}, 文件路径: {}, 总片段数: {}, 文件大小: {} 字节", 
                       sessionId, filePath, totalChunks, completeAudioData.length);
            
            return filePath;
            
        } catch (Exception e) {
            logger.error("保存音频文件失败，会话ID: " + sessionId, e);
            return null;
        } finally {
            // 清理资源
            try {
                buffer.close();
            } catch (IOException e) {
                logger.warn("关闭音频缓冲区失败", e);
            }
        }
    }

    /**
     * 获取当前会话的音频统计信息
     */
    public AudioSessionInfo getSessionInfo(String sessionId) {
        ByteArrayOutputStream buffer = sessionAudioBuffers.get(sessionId);
        AtomicInteger sequenceNumber = sessionSequenceNumbers.get(sessionId);
        String filePath = sessionAudioFiles.get(sessionId);
        
        if (buffer == null) {
            return null;
        }
        
        return new AudioSessionInfo(
            sessionId,
            sequenceNumber != null ? sequenceNumber.get() : 0,
            buffer.size(),
            filePath
        );
    }

    /**
     * 清理会话资源（在异常情况下调用）
     */
    public void cleanupSession(String sessionId) {
        logger.info("清理音频会话资源: {}", sessionId);
        
        ByteArrayOutputStream buffer = sessionAudioBuffers.remove(sessionId);
        sessionSequenceNumbers.remove(sessionId);
        sessionAudioFiles.remove(sessionId);
        
        if (buffer != null) {
            try {
                buffer.close();
            } catch (IOException e) {
                logger.warn("关闭音频缓冲区失败", e);
            }
        }
    }

    /**
     * 获取所有活跃的音频会话
     */
    public java.util.Set<String> getActiveSessions() {
        return sessionAudioBuffers.keySet();
    }

    /**
     * 音频会话信息
     */
    public static class AudioSessionInfo {
        private final String sessionId;
        private final int chunkCount;
        private final int totalSize;
        private final String filePath;

        public AudioSessionInfo(String sessionId, int chunkCount, int totalSize, String filePath) {
            this.sessionId = sessionId;
            this.chunkCount = chunkCount;
            this.totalSize = totalSize;
            this.filePath = filePath;
        }

        public String getSessionId() { return sessionId; }
        public int getChunkCount() { return chunkCount; }
        public int getTotalSize() { return totalSize; }
        public String getFilePath() { return filePath; }

        @Override
        public String toString() {
            return String.format("AudioSessionInfo{sessionId='%s', chunkCount=%d, totalSize=%d, filePath='%s'}", 
                               sessionId, chunkCount, totalSize, filePath);
        }
    }
}
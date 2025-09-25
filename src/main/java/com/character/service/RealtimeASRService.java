package com.character.service;

import com.character.controller.AiChatController;
import jakarta.annotation.Resource;
import org.json.JSONArray;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;
import org.json.JSONObject;

import java.nio.ByteBuffer;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class RealtimeASRService {
    
    private static final Logger logger = LoggerFactory.getLogger(RealtimeASRService.class);
    
    // 音频帧配置
    private static final int AUDIO_FRAME_SIZE = 1280;  // 每帧字节数
    private static final int FRAME_INTERVAL_MS = 40;   // 帧间隔(毫秒)
    
    @Autowired
    private XunfeiConnectionPool connectionPool;
    @Resource
    private AiChatController aiChatController;
    
    // 存储每个会话的结果流和连接
    private final ConcurrentHashMap<String, Sinks.Many<String>> sessionSinks = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, XunfeiConnectionPool.XunfeiConnection> sessionConnections = new ConcurrentHashMap<>();
    
    /**
     * 开始实时语音识别
     * @param sessionId 会话ID
     * @return 流式识别结果
     */
    public Flux<String> startRealtimeASR(String sessionId) {
        logger.info("开始实时语音识别，会话ID: {}", sessionId);
        
        // 创建结果流
        Sinks.Many<String> sink = Sinks.many().multicast().onBackpressureBuffer();
        sessionSinks.put(sessionId, sink);

        try {
            // 获取讯飞连接
            XunfeiConnectionPool.XunfeiConnection connection = connectionPool.getConnection();
            sessionConnections.put(sessionId, connection);
            
            // 设置消息处理器
            connection.setMessageHandler(message -> {
                try {
                    String result = processXunfeiMessage(message);
                    if (result != null && !result.isEmpty()) {
                        sink.tryEmitNext(result);
                        //TODO 传递语音result（用户说的话）至模型,在加上信息解析，就是前端处理AI和用户信息
                        logger.info("用户说：{}",result);
                        //TODO 调用对应任务的chatModel，将返回数据流式返回前端
//                        Flux<String> stringFlux = aiChatController.voiceChat("1",result,);
                        //TODO 调用讯飞ws生成语音流


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
    
    /**
     * 发送音频数据
     * @param sessionId 会话ID
     * @param audioData 音频数据
     */
    public void sendAudioData(String sessionId, ByteBuffer audioData) {
        try {
            XunfeiConnectionPool.XunfeiConnection connection = sessionConnections.get(sessionId);
            if (connection == null) {
                logger.warn("会话连接不存在，会话ID: {}", sessionId);
                return;
            }
            
            byte[] audioBytes = new byte[audioData.remaining()];
            audioData.get(audioBytes);
            
            logger.debug("收到音频数据，会话ID: {}, 数据长度: {} 字节", sessionId, audioBytes.length);
            
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
    
    /**
     * 结束语音识别
     * @param sessionId 会话ID
     */
    public void endASR(String sessionId) {
        logger.info("结束语音识别，会话ID: {}", sessionId);
        
        XunfeiConnectionPool.XunfeiConnection connection = sessionConnections.get(sessionId);
        if (connection != null) {
            connection.sendEndMessage();
        }
        
        Sinks.Many<String> sink = sessionSinks.get(sessionId);
        if (sink != null) {
            sink.tryEmitComplete();
        }
        
        cleanupSession(sessionId);
    }
    
    /**
     * 处理音频帧数据
     */
    private void processAudioFrames(String sessionId, byte[] audioData) {
        logger.debug("处理音频帧，会话ID: {}, 总数据长度: {} 字节", sessionId, audioData.length);
        
        XunfeiConnectionPool.XunfeiConnection connection = sessionConnections.get(sessionId);
        if (connection == null) {
            logger.warn("会话连接不存在，会话ID: {}", sessionId);
            return;
        }
        
        // 计算帧数
        int totalFrames = (audioData.length + AUDIO_FRAME_SIZE - 1) / AUDIO_FRAME_SIZE;
        logger.debug("音频将分为 {} 帧处理", totalFrames);
        
        // 异步处理音频帧，避免阻塞主线程
        java.util.concurrent.CompletableFuture.runAsync(() -> {
            try {
                for (int i = 0; i < totalFrames; i++) {
                    int start = i * AUDIO_FRAME_SIZE;
                    int end = Math.min(start + AUDIO_FRAME_SIZE, audioData.length);
                    byte[] frame = new byte[end - start];
                    System.arraycopy(audioData, start, frame, 0, end - start);
                    
                    // 发送音频帧
                    if (connection.isConnected()) {
                        connection.sendAudioFrame(frame);
                        logger.debug("发送音频帧 {}/{}, 大小: {} 字节", i + 1, totalFrames, frame.length);
                    }
                    
                    // 控制发送节奏（最后一帧不需要等待）
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
    
    /**
     * 处理讯飞返回的消息
     */
    private String processXunfeiMessage(String message) {
        try {
            JSONObject json = new JSONObject(message);
            
            // 只处理识别结果消息
            if (!"result".equals(json.optString("msg_type"))) {
                return null;
            }
            
            // 解析讯飞ASR结果格式
            if (json.has("data")) {
                JSONObject data = json.getJSONObject("data");
                
                // 检查是否有中文识别结果
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
     * 清理会话资源
     */
    private void cleanupSession(String sessionId) {
        logger.info("清理会话资源，会话ID: {}", sessionId);
        
        // 移除结果流
        Sinks.Many<String> sink = sessionSinks.remove(sessionId);
        if (sink != null && !sink.tryEmitComplete().isSuccess()) {
            logger.warn("无法正常关闭结果流，会话ID: {}", sessionId);
        }
        
        // 移除并归还连接
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
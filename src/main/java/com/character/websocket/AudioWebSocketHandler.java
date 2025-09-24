package com.character.websocket;

import com.character.service.RealtimeASRService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.Disposable;

import java.nio.ByteBuffer;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class AudioWebSocketHandler implements WebSocketHandler {
    
    private static final Logger logger = LoggerFactory.getLogger(AudioWebSocketHandler.class);
    
    @Autowired
    private RealtimeASRService realtimeASRService;
    
    // 存储每个WebSocket会话的订阅
    private final ConcurrentHashMap<String, Disposable> sessionSubscriptions = new ConcurrentHashMap<>();
    
    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String sessionId = session.getId();
        logger.info("WebSocket连接建立，会话ID: {}", sessionId);
        
        // 启动实时语音识别流
        Disposable subscription = realtimeASRService.startRealtimeASR(sessionId)
                .subscribe(
                    result -> {
                        // 发送识别结果给前端
                        try {
                            if (session.isOpen()) {
                                session.sendMessage(new TextMessage(result));
                            }
                        } catch (Exception e) {
                            logger.error("发送识别结果失败", e);
                        }
                    },
                    error -> {
                        logger.error("语音识别流错误", error);
                        try {
                            if (session.isOpen()) {
                                session.sendMessage(new TextMessage("ERROR: " + error.getMessage()));
                                session.close();
                            }
                        } catch (Exception e) {
                            logger.error("发送错误消息失败", e);
                        }
                    },
                    () -> {
                        logger.info("语音识别流完成，会话ID: {}", sessionId);
                        try {
                            if (session.isOpen()) {
                                session.close();
                            }
                        } catch (Exception e) {
                            logger.error("关闭WebSocket会话失败", e);
                        }
                    }
                );
        
        sessionSubscriptions.put(sessionId, subscription);
    }
    
    @Override
    public void handleMessage(WebSocketSession session, WebSocketMessage<?> message) throws Exception {
        String sessionId = session.getId();
        
        if (message instanceof BinaryMessage) {
            BinaryMessage binaryMessage = (BinaryMessage) message;
            ByteBuffer payload = binaryMessage.getPayload();
            
            logger.debug("收到二进制音频数据，会话ID: {}, 数据大小: {} 字节", 
                        sessionId, payload.remaining());
            
            // 发送音频数据给语音识别服务
            realtimeASRService.sendAudioData(sessionId, payload);
            
        } else if (message instanceof TextMessage) {
            TextMessage textMessage = (TextMessage) message;
            String payload = textMessage.getPayload();
            
            logger.debug("收到文本消息，会话ID: {}, 内容: {}", sessionId, payload);
            
            // 处理控制消息
            if ("END".equals(payload)) {
                logger.info("收到结束信号，会话ID: {}", sessionId);
                realtimeASRService.endASR(sessionId);
            }
        }
    }
    
    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        String sessionId = session.getId();
        logger.error("WebSocket传输错误，会话ID: " + sessionId, exception);
        
        // 清理资源
        cleanupSession(sessionId);
    }
    
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus closeStatus) throws Exception {
        String sessionId = session.getId();
        logger.info("WebSocket连接关闭，会话ID: {}, 状态: {}", sessionId, closeStatus);
        
        // 清理资源
        cleanupSession(sessionId);
    }
    
    @Override
    public boolean supportsPartialMessages() {
        return false;
    }
    
    /**
     * 清理会话资源
     */
    private void cleanupSession(String sessionId) {
        // 取消语音识别流订阅
        Disposable subscription = sessionSubscriptions.remove(sessionId);
        if (subscription != null && !subscription.isDisposed()) {
            subscription.dispose();
        }
        
        // 结束语音识别
        realtimeASRService.endASR(sessionId);
    }
}
package com.character.websocket;

import com.character.model.entity.User;
import com.character.service.ASRService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import reactor.core.Disposable;

import java.nio.ByteBuffer;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 音频WebSocket处理器
 */
@Component
public class AudioWebSocketHandler implements WebSocketHandler {

    private static final Logger logger = LoggerFactory.getLogger(AudioWebSocketHandler.class);

    @Autowired
    private ASRService asrService;

    @Autowired
    private WebSocketSessionManager sessionManager;

    private final ConcurrentHashMap<String, Disposable> sessionSubscriptions = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String sessionId = session.getId();
        sessionManager.add(sessionId, session);
        logger.info("WebSocket连接建立，会话ID: {}", sessionId);

        Long appId = getAppId(session);
        User loginUser = getLoginUser(session);

        Disposable subscription = asrService.startASR(sessionId, appId, loginUser)
                .subscribe(
                        result -> handleASRResult(session, sessionId, result),
                        error -> handleASRError(session, sessionId, error),
                        () -> logger.info("语音识别流完成，会话ID: {}", sessionId)
                );

        sessionSubscriptions.put(sessionId, subscription);
    }

    @Override
    public void handleMessage(WebSocketSession session, WebSocketMessage<?> message) throws Exception {
        String sessionId = session.getId();

        if (message instanceof BinaryMessage) {
            handleBinaryMessage(session, (BinaryMessage) message);
        } else if (message instanceof TextMessage) {
            handleTextMessage(session, (TextMessage) message);
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        String sessionId = session.getId();
        sessionManager.remove(sessionId);
        logger.error("WebSocket传输错误，会话ID: " + sessionId, exception);

        cleanupSession(session);

        if (session.isOpen()) {
            try {
                session.close(CloseStatus.SERVER_ERROR);
            } catch (Exception e) {
                logger.error("关闭错误的WebSocket会话时发生异常", e);
            }
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus closeStatus) throws Exception {
        String sessionId = session.getId();
        sessionManager.remove(sessionId);
        logger.info("WebSocket连接关闭，会话ID: {}, 状态: {}", sessionId, closeStatus);

        cleanupSession(session);
    }

    @Override
    public boolean supportsPartialMessages() {
        return false;
    }

    private void handleBinaryMessage(WebSocketSession session, BinaryMessage binaryMessage) {
        String sessionId = session.getId();
        ByteBuffer payload = binaryMessage.getPayload();

        Long appId = getAppId(session);
        User loginUser = getLoginUser(session);
        
        asrService.sendAudioData(sessionId, payload, appId, loginUser);
    }

    private void handleTextMessage(WebSocketSession session, TextMessage textMessage) {
        String sessionId = session.getId();
        String payload = textMessage.getPayload();
        Long appId = getAppId(session);
        User loginUser = getLoginUser(session);

        switch (payload) {
            case "END":
                logger.info("收到结束信号，会话ID: {}", sessionId);
                asrService.endASR(sessionId, appId, loginUser);
                break;
            case "START":
                logger.info("收到开始信号，会话ID: {}", sessionId);
                restartASR(session, sessionId, appId, loginUser);
                break;
            default:
                handleControlMessage(session, payload, appId, loginUser);
                break;
        }
    }

    private void handleControlMessage(WebSocketSession session, String payload, Long appId, User loginUser) {
        String sessionId = session.getId();
        try {
            org.json.JSONObject jsonMessage = new org.json.JSONObject(payload);
            String messageType = jsonMessage.optString("type");

            if ("segment_end".equals(messageType)) {
                logger.info("收到段落结束信号，会话ID: {}", sessionId);
                asrService.sendSegmentEnd(sessionId, appId, loginUser);
            }
        } catch (Exception e) {
            // 忽略非JSON格式消息
        }
    }

    private void restartASR(WebSocketSession session, String sessionId, Long appId, User loginUser) {
        Disposable existingSubscription = sessionSubscriptions.get(sessionId);
        if (existingSubscription != null && !existingSubscription.isDisposed()) {
            existingSubscription.dispose();
        }

        Disposable subscription = asrService.startASR(sessionId, appId, loginUser)
                .subscribe(
                        result -> handleASRResult(session, sessionId, result),
                        error -> handleASRError(session, sessionId, error)
                );

        sessionSubscriptions.put(sessionId, subscription);
    }

    private void cleanupSession(WebSocketSession session) {
        String sessionId = session.getId();
        
        Disposable subscription = sessionSubscriptions.remove(sessionId);
        if (subscription != null && !subscription.isDisposed()) {
            subscription.dispose();
        }

        Long appId = getAppId(session);
        User loginUser = getLoginUser(session);
        asrService.endASR(sessionId, appId, loginUser);
    }

    private void handleASRResult(WebSocketSession session, String sessionId, String result) {
        try {
            if (session.isOpen()) {
                if (result.startsWith("AUDIO:")) {
                    handleAudioResult(session, sessionId, result);
                } else {
                    session.sendMessage(new TextMessage(result));
                    logger.debug("发送文本消息到前端，会话ID: {}", sessionId);
                }
            }
        } catch (Exception e) {
            logger.error("发送消息失败，会话ID: " + sessionId, e);
        }
    }

    private void handleAudioResult(WebSocketSession session, String sessionId, String result) throws Exception {
        String[] parts = result.split(":", 3);
        String base64Audio = null;
        
        if (parts.length == 2) {
            base64Audio = parts[1];
        } else if (parts.length == 3) {
            base64Audio = parts[2];
        }
        
        if (base64Audio != null && !base64Audio.isEmpty()) {
            try {
                byte[] audioBytes = java.util.Base64.getDecoder().decode(base64Audio);
                session.sendMessage(new BinaryMessage(audioBytes));
                logger.debug("发送PCM音频数据到前端，会话ID: {}, 大小: {} 字节", sessionId, audioBytes.length);
            } catch (IllegalArgumentException e) {
                logger.warn("Base64解码失败，会话ID: {}", sessionId);
            }
        }
    }

    private void handleASRError(WebSocketSession session, String sessionId, Throwable error) {
        logger.error("语音识别流错误，会话ID: " + sessionId, error);
        try {
            if (session.isOpen()) {
                session.sendMessage(new TextMessage("ERROR: " + error.getMessage()));
            }
        } catch (Exception e) {
            logger.error("发送错误消息失败，会话ID: " + sessionId, e);
        }
    }

    private Long getAppId(WebSocketSession session) {
        Object appIdAttr = session.getAttributes().get(AudioHandshakeInterceptor.ATTR_APP_ID);
        if (appIdAttr instanceof String s && !s.isBlank()) {
            try {
                return Long.parseLong(s);
            } catch (Exception e) {
                return null;
            }
        }
        return null;
    }

    private User getLoginUser(WebSocketSession session) {
        return (User) session.getAttributes().get(AudioHandshakeInterceptor.ATTR_USER);
    }
}
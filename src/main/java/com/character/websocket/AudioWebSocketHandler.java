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

@Component
public class AudioWebSocketHandler implements WebSocketHandler {

    private static final Logger logger = LoggerFactory.getLogger(AudioWebSocketHandler.class);

    @Autowired
    private ASRService aSRService;

    @Autowired
    private WebSocketSessionManager sessionManager;

    // 存储每个WebSocket会话的订阅
    private final ConcurrentHashMap<String, Disposable> sessionSubscriptions = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String sessionId = session.getId();
        sessionManager.add(sessionId, session);
        logger.info("WebSocket连接建立，会话ID: {}", sessionId);

        // 读取握手存入的 appId 与用户信息
        Object appIdAttr = session.getAttributes().get(AudioHandshakeInterceptor.ATTR_APP_ID);
        Long appId = null;
        if (appIdAttr instanceof String s && !s.isBlank()) {
            try {
                appId = Long.parseLong(s);
            } catch (Exception ignore) {
            }
        }
        User loginUser =
                (User) session.getAttributes().get(AudioHandshakeInterceptor.ATTR_USER);

        // 启动实时语音识别流
        Disposable subscription = aSRService.startASR(sessionId, appId, loginUser)
                .subscribe(
                        result -> handleASRResult(session, sessionId, result),
                        error -> handleASRError(session, sessionId, error),
                        () -> {
                            logger.info("语音识别流完成，会话ID: {}", sessionId);
                            // 不关闭WebSocket连接，保持连接以便后续使用
                            try {
                                if (session.isOpen()) {
                                    logger.info("语音识别流完成，但保持WebSocket连接，会话ID: {}", sessionId);
                                }
                            } catch (Exception e) {
                                logger.error("发送完成消息失败，会话ID: " + sessionId, e);
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

//            logger.debug("收到二进制音频数据，会话ID: {}, 数据大小: {} 字节",
//                    sessionId, payload.remaining());

            // 发送音频数据给语音识别服务（传递 appId 与用户信息）
            Object appIdAttr = session.getAttributes().get(AudioHandshakeInterceptor.ATTR_APP_ID);
            Long appId = null;
            if (appIdAttr instanceof String s && !s.isBlank()) {
                try {
                    appId = Long.parseLong(s);
                } catch (Exception ignore) {
                }
            }
            User loginUser =
                    (User) session.getAttributes().get(AudioHandshakeInterceptor.ATTR_USER);
            //前端传输音频交给后端
            aSRService.sendAudioData(sessionId, payload, appId, loginUser);

        } else if (message instanceof TextMessage) {
            TextMessage textMessage = (TextMessage) message;
            String payload = textMessage.getPayload();

            // 处理控制消息
            if ("END".equals(payload)) {
                logger.info("收到结束信号，会话ID: {}", sessionId);
                Object appIdAttr = session.getAttributes().get(AudioHandshakeInterceptor.ATTR_APP_ID);
                Long appId = null;
                if (appIdAttr instanceof String s && !s.isBlank()) {
                    try {
                        appId = Long.parseLong(s);
                    } catch (Exception ignore) {
                    }
                }
                User loginUser = (User) session.getAttributes().get(AudioHandshakeInterceptor.ATTR_USER);
                aSRService.endASR(sessionId, appId, loginUser);
                
            } else if ("START".equals(payload)) {
                logger.info("收到开始信号，会话ID: {}", sessionId);
                Object appIdAttr = session.getAttributes().get(AudioHandshakeInterceptor.ATTR_APP_ID);
                Long appId = null;
                if (appIdAttr instanceof String s && !s.isBlank()) {
                    try {
                        appId = Long.parseLong(s);
                    } catch (Exception ignore) {
                    }
                }
                User loginUser = (User) session.getAttributes().get(AudioHandshakeInterceptor.ATTR_USER);

                // 启动新的语音识别流
                Disposable existingSubscription = sessionSubscriptions.get(sessionId);
                if (existingSubscription != null && !existingSubscription.isDisposed()) {
                    existingSubscription.dispose();
                }

                Disposable subscription = aSRService.startASR(sessionId, appId, loginUser)
                        .subscribe(
                                result -> handleASRResult(session, sessionId, result),
                                error -> handleASRError(session, sessionId, error)
                        );

                sessionSubscriptions.put(sessionId, subscription);
                
            } else {
                // 尝试解析JSON格式的控制消息
                try {
                    org.json.JSONObject jsonMessage = new org.json.JSONObject(payload);
                    String messageType = jsonMessage.optString("type");

                    if ("segment_end".equals(messageType)) {
                        logger.info("收到段落结束信号，会话ID: {}", sessionId);
                        Object appIdAttr = session.getAttributes().get(AudioHandshakeInterceptor.ATTR_APP_ID);
                        Long appId = null;
                        if (appIdAttr instanceof String s && !s.isBlank()) {
                            try {
                                appId = Long.parseLong(s);
                            } catch (Exception ignore) {
                            }
                        }
                        User loginUser = (User) session.getAttributes().get(AudioHandshakeInterceptor.ATTR_USER);
                        aSRService.sendSegmentEnd(sessionId, appId, loginUser);
                    }
                } catch (Exception jsonError) {
                    // 忽略非JSON格式消息
                }
            }
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        String sessionId = session.getId();
        sessionManager.remove(sessionId);
        logger.error("WebSocket传输错误，会话ID: " + sessionId, exception);

        // 清理资源
        cleanupSession(session);

        // 如果会话仍然打开，尝试关闭它
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

        // 清理资源
        cleanupSession(session);
    }

    @Override
    public boolean supportsPartialMessages() {
        return false;
    }

    /**
     * 清理会话资源
     */
    private void cleanupSession(WebSocketSession session) {
        String sessionId = session.getId();
        // 取消语音识别流订阅
        Disposable subscription = sessionSubscriptions.remove(sessionId);
        if (subscription != null && !subscription.isDisposed()) {
            subscription.dispose();
        }

        // 结束语音识别（带上 appId 与用户信息）
        Object appIdAttr = session.getAttributes().get(AudioHandshakeInterceptor.ATTR_APP_ID);
        Long appId = null;
        if (appIdAttr instanceof String s && !s.isBlank()) {
            try {
                appId = Long.parseLong(s);
            } catch (Exception ignore) {
            }
        }
        User loginUser =
                (User) session.getAttributes().get(AudioHandshakeInterceptor.ATTR_USER);

        aSRService.endASR(sessionId, appId, loginUser);
    }

    /**
     * 统一处理ASR结果 - 避免重复代码
     */
    private void handleASRResult(WebSocketSession session, String sessionId, String result) {
        try {
            if (session.isOpen()) {
                if (result.startsWith("AUDIO:")) {
                    // 音频数据：解码Base64并作为二进制消息发送
                    // 格式为 AUDIO:序号:Base64数据
                    String[] parts = result.split(":", 3);
                    if (parts.length == 3) {
                        String base64Audio = parts[2]; // 获取Base64数据部分
                        byte[] audioBytes = java.util.Base64.getDecoder().decode(base64Audio);
                        session.sendMessage(new BinaryMessage(audioBytes));
                        logger.debug("发送PCM音频数据到前端，会话ID: {}, 大小: {} 字节", sessionId, audioBytes.length);
                    } else {
                        logger.warn("音频数据格式不正确: {}", result);
                    }
                } else {
                    // 文本数据：直接作为文本消息发送
                    session.sendMessage(new TextMessage(result));
                    logger.debug("发送文本消息到前端，会话ID: {}, 内容: {}", sessionId, result);
                }
            }
        } catch (Exception e) {
            logger.error("发送消息失败，会话ID: " + sessionId, e);
        }
    }

    /**
     * 统一处理ASR错误 - 避免重复代码
     */
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
}
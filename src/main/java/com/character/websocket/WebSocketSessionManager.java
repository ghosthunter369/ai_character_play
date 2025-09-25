package com.character.websocket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 管理 WebSocketSession，以便服务层按 sessionId 回发消息
 */
@Component
public class WebSocketSessionManager {

    private static final Logger logger = LoggerFactory.getLogger(WebSocketSessionManager.class);

    private final ConcurrentHashMap<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

    /**
     * 添加WebSocket会话
     */
    public void add(String sessionId, WebSocketSession session) {
        sessions.put(sessionId, session);
        logger.info("WebSocket会话已添加: {}, 当前活跃连接数: {}", sessionId, sessions.size());
    }

    /**
     * 移除WebSocket会话
     */
    public void remove(String sessionId) {
        sessions.remove(sessionId);
        logger.info("WebSocket会话已移除: {}, 当前活跃连接数: {}", sessionId, sessions.size());
    }

    /**
     * 获取WebSocket会话
     */
    public WebSocketSession get(String sessionId) {
        return sessions.get(sessionId);
    }

    /**
     * 发送消息到指定会话
     */
    public void sendMessage(String sessionId, String message) {
        WebSocketSession session = sessions.get(sessionId);
        if (session != null && session.isOpen()) {
            try {
                session.sendMessage(new TextMessage(message));
                logger.debug("消息已发送到会话: {}", sessionId);
            } catch (IOException e) {
                logger.error("发送消息失败，会话ID: {}", sessionId, e);
                sessions.remove(sessionId);
            }
        } else {
            logger.warn("会话不存在或已关闭，无法发送消息: {}", sessionId);
        }
    }

    /**
     * 发送二进制消息到指定会话
     */
    public void sendBinaryMessage(String sessionId, byte[] data) {
        WebSocketSession session = sessions.get(sessionId);
        if (session != null && session.isOpen()) {
            try {
                session.sendMessage(new org.springframework.web.socket.BinaryMessage(data));
                logger.debug("二进制消息已发送到会话: {}, 数据长度: {}", sessionId, data.length);
            } catch (IOException e) {
                logger.error("发送二进制消息失败，会话ID: {}", sessionId, e);
                sessions.remove(sessionId);
            }
        } else {
            logger.warn("会话不存在或已关闭，无法发送二进制消息: {}", sessionId);
        }
    }

    /**
     * 广播消息到所有会话
     */
    public void broadcast(String message) {
        sessions.forEach((sessionId, session) -> {
            if (session.isOpen()) {
                try {
                    session.sendMessage(new TextMessage(message));
                } catch (IOException e) {
                    logger.error("广播消息失败，会话ID: {}", sessionId, e);
                    sessions.remove(sessionId);
                }
            }
        });
        logger.info("消息已广播到 {} 个会话", sessions.size());
    }

    /**
     * 获取活跃连接数
     */
    public int getActiveConnectionCount() {
        return sessions.size();
    }
}
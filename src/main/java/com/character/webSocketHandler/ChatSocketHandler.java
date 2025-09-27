package com.character.webSocketHandler;

import com.character.websocket.WebSocketSessionManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;

/**
 * 聊天WebSocket处理器
 */
@Component
@Slf4j
public class ChatSocketHandler extends TextWebSocketHandler {

    @Autowired
    private WebSocketSessionManager sessionManager;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        sessionManager.add(session.getId(), session);
        log.info("Chat WebSocket连接建立: {}", session.getId());
        super.afterConnectionEstablished(session);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        // 处理聊天消息的逻辑可以在这里实现
        super.handleTextMessage(session, message);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        sessionManager.remove(session.getId());
        log.info("Chat WebSocket连接关闭: {}, 状态: {}", session.getId(), status);
        super.afterConnectionClosed(session, status);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        sessionManager.remove(session.getId());
        log.error("Chat WebSocket传输错误: {}", session.getId(), exception);
        
        if (session.isOpen()) {
            try {
                session.close(CloseStatus.SERVER_ERROR);
            } catch (IOException e) {
                log.error("关闭错误的WebSocket会话时发生异常", e);
            }
        }
        
        super.handleTransportError(session, exception);
    }
}
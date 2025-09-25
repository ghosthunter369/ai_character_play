package com.character.websocket;

import com.character.model.entity.User;
import com.character.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

/**
 * 握手拦截器：提取 appId 和用户信息，避免在异步回调中使用已回收的 HttpServletRequest
 */
@Component
public class AudioHandshakeInterceptor implements HandshakeInterceptor {

    private static final Logger logger = LoggerFactory.getLogger(AudioHandshakeInterceptor.class);
    public static final String ATTR_APP_ID = "APP_ID";
    public static final String ATTR_USER = "USER";
    
    @Autowired
    private UserService userService;

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) throws Exception {
        if (request instanceof ServletServerHttpRequest servletReq) {
            HttpServletRequest httpRequest = servletReq.getServletRequest();
            String appIdStr = httpRequest.getParameter("appId");
            if (appIdStr == null || appIdStr.isBlank()) {
                appIdStr = httpRequest.getHeader("X-App-Id");
            }
            attributes.put(ATTR_APP_ID, appIdStr);
            
            // 在握手时提取用户信息，避免后续使用已回收的 request
            try {
                User loginUser = userService.getLoginUser(httpRequest);
                attributes.put(ATTR_USER, loginUser);
                logger.info("握手拦截：appId={}, userId={}, URI={}", appIdStr, loginUser.getId(), httpRequest.getRequestURI());
            } catch (Exception e) {
                logger.warn("获取登录用户失败：{}", e.getMessage());
                return false; // 认证失败，拒绝握手
            }
        }
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
    }
}
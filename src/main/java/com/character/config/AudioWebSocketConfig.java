package com.character.config;

import com.character.websocket.AudioWebSocketHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class AudioWebSocketConfig implements WebSocketConfigurer {

    @Autowired
    private AudioWebSocketHandler audioWebSocketHandler;
    //    @Resource TODO 握手拦截器，校验用户身份
//    private WsHandshakeInterceptor wsHandshakeInterceptor;
    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(audioWebSocketHandler, "/ws/audio")
                .setAllowedOrigins("*"); // 生产环境中应该限制具体域名
    }
}

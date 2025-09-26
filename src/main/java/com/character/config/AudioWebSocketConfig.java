package com.character.config;

import com.character.websocket.AudioHandshakeInterceptor;
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

    @Autowired
    private AudioHandshakeInterceptor audioHandshakeInterceptor;
    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(audioWebSocketHandler, "/ws/audio")
                .addInterceptors(audioHandshakeInterceptor)
                .setAllowedOrigins("*"); // 生产环境中应该限制具体域名
    }
}

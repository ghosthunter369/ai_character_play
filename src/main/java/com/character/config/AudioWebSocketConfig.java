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
    @Autowired
    private com.character.voice.VoiceWebSocketHandler voiceWebSocketHandler;
    @org.springframework.beans.factory.annotation.Value("${app.cors.allowed-origin-patterns:http://localhost:*,http://127.0.0.1:*}")
    private String[] allowedOrigins;
    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(audioWebSocketHandler, "/ws/audio")
                .addInterceptors(audioHandshakeInterceptor)
                .setAllowedOriginPatterns(allowedOrigins);
        registry.addHandler(voiceWebSocketHandler, "/ws/voice")
                .addInterceptors(audioHandshakeInterceptor)
                .setAllowedOriginPatterns(allowedOrigins);
    }
}

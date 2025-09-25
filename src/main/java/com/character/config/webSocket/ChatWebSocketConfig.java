package com.character.config.webSocket;

import com.character.webSocketHandler.ChatSocketHandler;
import jakarta.annotation.Resource;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;


@Configuration
@EnableWebSocket
public class ChatWebSocketConfig implements WebSocketConfigurer {

   @Resource
    private ChatSocketHandler pictureEditHandler;

//    @Resource
//    private WsHandshakeInterceptor wsHandshakeInterceptor;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // websocket
        registry.addHandler(pictureEditHandler, "/ws/ars/edit")
//                .addInterceptors(wsHandshakeInterceptor)
                .setAllowedOrigins("*");
    }
}

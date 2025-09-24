package com.character.config.webSocket;

import com.character.webSocketHandler.PictureEditHandler;
import jakarta.annotation.Resource;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;


@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

   @Resource
    private PictureEditHandler pictureEditHandler;

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

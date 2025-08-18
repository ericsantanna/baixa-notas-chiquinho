package com.ericsantanna.baixaNotasChiquinho.appConfig

import com.ericsantanna.baixaNotasChiquinho.execucao.ExecutionStatusWebSocketHandler
import com.ericsantanna.baixaNotasChiquinho.logs.LogWebSocketHandler
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Configuration
import org.springframework.web.socket.config.annotation.EnableWebSocket
import org.springframework.web.socket.config.annotation.WebSocketConfigurer
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry

@Configuration
@EnableWebSocket
class WebSocketConfig implements WebSocketConfigurer {
    
    @Autowired
    LogWebSocketHandler logWebSocketHandler
    
    @Autowired
    ExecutionStatusWebSocketHandler executionStatusWebSocketHandler
    
    @Override
    void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry
                .addHandler(executionStatusWebSocketHandler, "/execucao")
                .setAllowedOrigins("*")
        
        registry
                .addHandler(logWebSocketHandler, "/logs")
                .setAllowedOrigins("*")
    }
}
package com.ericsantanna.baixaNotasChiquinho.appConfig

import com.ericsantanna.baixaNotasChiquinho.execucao.ExecutionStatusWebSocketHandler
import com.ericsantanna.baixaNotasChiquinho.logs.LogWebSocketHandler
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.reactive.HandlerMapping
import org.springframework.web.reactive.handler.SimpleUrlHandlerMapping
import org.springframework.web.reactive.socket.server.support.WebSocketHandlerAdapter

@Configuration
class WebFluxWebSocketConfig {

    @Bean
    HandlerMapping webSocketHandlerMapping(
            ExecutionStatusWebSocketHandler executionHandler, 
            LogWebSocketHandler logHandler) {
        
        Map<String, Object> map = new HashMap<>()
        map.put("/ws/execucao", executionHandler)
        map.put("/ws/logs", logHandler)

        SimpleUrlHandlerMapping mapping = new SimpleUrlHandlerMapping()
        mapping.setOrder(1)
        mapping.setUrlMap(map)
        return mapping
    }

    @Bean
    WebSocketHandlerAdapter handlerAdapter() {
        return new WebSocketHandlerAdapter()
    }
}
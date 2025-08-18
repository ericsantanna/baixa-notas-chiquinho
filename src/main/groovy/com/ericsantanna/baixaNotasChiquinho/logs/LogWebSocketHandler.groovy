package com.ericsantanna.baixaNotasChiquinho.logs

import org.springframework.stereotype.Component
import org.springframework.web.reactive.socket.WebSocketHandler
import org.springframework.web.reactive.socket.WebSocketSession
import reactor.core.publisher.Mono

@Component
class LogWebSocketHandler implements WebSocketHandler {
    
    @Override
    Mono<Void> handle(WebSocketSession session) {
        return session.receive()
            .map { message -> message.payloadAsText }
            .doOnNext { text ->
                println("Received log message: ${text}")
            }
            .then()
    }
    
    // Add methods to send log messages to clients
    Mono<Void> sendLogMessage(WebSocketSession session, String logJson) {
        return session.send(
            Mono.just(session.textMessage(logJson))
        )
    }
}
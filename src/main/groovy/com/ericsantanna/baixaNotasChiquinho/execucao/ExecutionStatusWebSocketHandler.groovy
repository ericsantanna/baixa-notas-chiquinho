package com.ericsantanna.baixaNotasChiquinho.execucao

import org.springframework.stereotype.Component
import org.springframework.web.reactive.socket.WebSocketHandler
import org.springframework.web.reactive.socket.WebSocketSession
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.concurrent.ConcurrentHashMap

@Component
class ExecutionStatusWebSocketHandler implements WebSocketHandler {
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>()
    
    @Override
    Mono<Void> handle(WebSocketSession session) {
        sessions.put(session.id, session)
        
        return session.receive()
            .doFinally(signalType -> {
                sessions.remove(session.id)
            })
            .then()
    }
    
    Mono<Void> broadcastStatus(String statusJson) {
        return Flux.fromIterable(sessions.values())
            .flatMap(session -> 
                session.send(Mono.just(session.textMessage(statusJson)))
                    .onErrorResume(e -> {
                        sessions.remove(session.id)
                        return Mono.empty()
                    })
            )
            .then()
    }
}
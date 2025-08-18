package com.ericsantanna.baixaNotasChiquinho.logs

import groovy.json.JsonBuilder
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.socket.CloseStatus
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import org.springframework.web.socket.handler.TextWebSocketHandler

import java.util.concurrent.ConcurrentHashMap

@Component
class LogWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(LogWebSocketHandler.class)

    private final Set<WebSocketSession> sessions = ConcurrentHashMap.newKeySet()

    @Override
    void afterConnectionEstablished(WebSocketSession session) throws Exception {
        sessions.add(session)
        log.info("WebSocket connection established: ${session.id}")
        log.info("Active sessions: ${sessions.size()}")
    }

    @Override
    void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        sessions.remove(session)
        log.info("WebSocket connection closed: ${session.id}")
        log.info("Active sessions: ${sessions.size()}")
    }

    void broadcastLog(LogEvent logEvent) {
        if (sessions.isEmpty()) {
            log.debug("No active sessions to broadcast to")
            return
        }

        String logJson = new JsonBuilder(logEvent).toString()
        log.debug("Broadcasting log: ${logJson}")

        // Create iterator to safely remove failed sessions
        Iterator<WebSocketSession> iterator = sessions.iterator()
        while (iterator.hasNext()) {
            WebSocketSession session = iterator.next()
            try {
                if (session.isOpen()) {
                    session.sendMessage(new TextMessage(logJson))
                    log.debug("Sent message to session: ${session.id}")
                } else {
                    log.debug("Removing closed session: ${session.id}")
                    iterator.remove()
                }
            } catch (Exception e) {
                log.error("Failed to send message to session ${session.id}: ${e.message}")
                iterator.remove()
            }
        }
    }
}
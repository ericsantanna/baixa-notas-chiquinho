package com.ericsantanna.baixaNotasChiquinho.execucao

import groovy.json.JsonOutput
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.socket.CloseStatus
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import org.springframework.web.socket.handler.TextWebSocketHandler

import java.util.concurrent.ConcurrentHashMap

@Component
class ExecutionStatusWebSocketHandler extends TextWebSocketHandler {
    
    private static final Logger log = LoggerFactory.getLogger(ExecutionStatusWebSocketHandler.class)
    
    private final Set<WebSocketSession> sessions = ConcurrentHashMap.newKeySet()
    private volatile List<ExecutionStatus> statusList = []

    @Override
    void afterConnectionEstablished(WebSocketSession session) {
        sessions.add(session)
        log.info("Execution status WebSocket connection established: ${session.id}")
        log.info("Active execution status sessions: ${sessions.size()}")
        
        // Send current status to new client
        if (!statusList.isEmpty()) {
            try {
                synchronized (session) {
                    session.sendMessage(new TextMessage(JsonOutput.toJson(statusList)))
                }
            } catch (Exception e) {
                log.error("Failed to send initial status to session ${session.id}: ${e.message}")
            }
        }
    }

    @Override
    void handleTextMessage(WebSocketSession session, TextMessage message) {
        // Not expecting messages from clients, just ignore
    }

    @Override
    void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session)
        log.info("Execution status WebSocket connection closed: ${session.id}")
        log.info("Active execution status sessions: ${sessions.size()}")
    }

    void broadcastStatusList() {
        if (sessions.isEmpty()) {
            log.debug("No active execution status sessions to broadcast to")
            return
        }
        
        log.info("Broadcasting ${statusList.size()} status messages to ${sessions.size()} sessions")
        String json
        try {
            json = JsonOutput.toJson(new ArrayList<ExecutionStatus>(statusList))
        } catch (Throwable e) {
            log.warn("Could not serialize status list: ${e.message}")
            return
        }

        // Create iterator to safely remove failed sessions
        Iterator<WebSocketSession> iterator = sessions.iterator()
        while (iterator.hasNext()) {
            WebSocketSession session = iterator.next()
            try {
                if (session.isOpen()) {
                    synchronized (session) {
                        session.sendMessage(new TextMessage(json))
                    }
                } else {
                    log.debug("Removing closed execution status session: ${session.id}")
                    iterator.remove()
                }
            } catch (Exception e) {
                log.error("Failed to send status to session ${session.id}: ${e.message}")
                iterator.remove()
            }
        }
    }

    void updateStatusList(List<ExecutionStatus> newList) {
        statusList = newList
        broadcastStatusList()
    }
}
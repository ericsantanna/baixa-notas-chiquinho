package com.ericsantanna.baixaNotasChiquinho.logs

import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.AppenderBase
import com.ericsantanna.baixaNotasChiquinho.appConfig.SpringContextHolder

class WebSocketLogAppender extends AppenderBase<ILoggingEvent> {
    
    private boolean enabled = true
    
    @Override
    protected void append(ILoggingEvent event) {
        if (!enabled || !SpringContextHolder.isContextReady()) return
        
        String loggerName = event.loggerName
        
        // Filter out logs from the WebSocket package to prevent infinite loops
        if (loggerName.startsWith('com.ericsantanna.baixaNotasChiquinho.logs')) {
            return // Skip logs from our own logging package
        }
        
        try {
            LogWebSocketHandler handler = SpringContextHolder.getBean(LogWebSocketHandler)
            if (!handler) return
            
            def logEvent = new LogEvent(
                timestamp: event.timeStamp,
                level: event.level.toString(),
                message: event.formattedMessage,
            )
            handler.broadcastLog(logEvent)
            
        } catch (Exception e) {
            // Fail silently to not break logging
        }
    }
    
    void setEnabled(boolean enabled) {
        this.enabled = enabled
    }
}
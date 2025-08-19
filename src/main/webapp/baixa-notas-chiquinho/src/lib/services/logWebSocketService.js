import { browser } from '$app/environment';

export class LogWebSocketService {
    constructor() {
        this.ws = null;
        this.listeners = new Set();
        this.connectionListeners = new Set();
        
        this.shouldReconnect = false;
        this.reconnectInterval = null;
        this.reconnectDelay = 3000;
    }

    connect() {
        if (!browser) return;
        
        this.shouldReconnect = true;
        this.attemptConnection();
    }
    
    attemptConnection() {
        if (!browser || (this.ws && this.ws.readyState === WebSocket.OPEN)) return;
        
        this.clearReconnectTimer();
        
        try {
            console.log('Tentando conectar ao WebSocket de logs...');
            this.ws = new WebSocket('ws://localhost:9001/ws/logs');
            
            this.ws.onopen = () => {
                console.log('Log WebSocket connected');
                this.notifyConnectionChange(true);
            };
            
            this.ws.onmessage = (event) => {
                console.log('Raw WebSocket message:', event.data);
                try {
                    const logEntry = JSON.parse(event.data);
                    
                    if (!logEntry.timestamp || !logEntry.level || !logEntry.message) {
                        console.warn('Invalid log entry received:', logEntry);
                        return;
                    }
                    
                    this.notifyListeners(logEntry);
                } catch (error) {
                    console.error('Failed to parse log message:', error);
                }
            };
            
            this.ws.onclose = (event) => {
                console.log('Log WebSocket disconnected', event);
                this.ws = null;
                this.notifyConnectionChange(false);
                this.scheduleReconnect();
            };
            
            this.ws.onerror = (error) => {
                console.error('Log WebSocket error:', error);
                this.notifyConnectionChange(false);
                // onclose will be called after onerror, so reconnection will be handled there
            };
            
        } catch (error) {
            console.error('Failed to connect log WebSocket:', error);
            this.scheduleReconnect();
        }
    }
    
    scheduleReconnect() {
        if (!this.shouldReconnect || this.reconnectInterval) return;
        
        this.reconnectInterval = setTimeout(() => {
            this.reconnectInterval = null;
            this.attemptConnection();
        }, this.reconnectDelay);
    }
    
    clearReconnectTimer() {
        if (this.reconnectInterval) {
            clearTimeout(this.reconnectInterval);
            this.reconnectInterval = null;
        }
    }
    
    disconnect() {
        this.shouldReconnect = false;
        this.clearReconnectTimer();
        
        if (this.ws) {
            this.ws.close(1000, 'Manual disconnect');
            this.ws = null;
        }
    }
    
    addLogListener(callback) {
        this.listeners.add(callback);
        return () => this.listeners.delete(callback);
    }
    
    addConnectionListener(callback) {
        this.connectionListeners.add(callback);
        return () => this.connectionListeners.delete(callback);
    }
    
    notifyListeners(logEntry) {
        this.listeners.forEach(callback => {
            try {
                callback(logEntry);
            } catch (error) {
                console.error('Error in log listener:', error);
            }
        });
    }
    
    notifyConnectionChange(isConnected) {
        this.connectionListeners.forEach(callback => {
            try {
                callback(isConnected);
            } catch (error) {
                console.error('Error in connection listener:', error);
            }
        });
    }
    
    // Public method to manually trigger reconnection
    forceReconnect() {
        if (this.ws) {
            this.ws.close();
        } else {
            this.attemptConnection();
        }
    }
    
    // Get current connection status
    isConnected() {
        return this.ws && this.ws.readyState === WebSocket.OPEN;
    }
}

export const logWebSocketService = new LogWebSocketService();
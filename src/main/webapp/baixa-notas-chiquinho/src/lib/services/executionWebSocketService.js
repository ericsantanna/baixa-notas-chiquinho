// src/lib/services/executionWebSocketService.js
import { browser } from '$app/environment';

export class ExecutionWebSocketService {
    constructor() {
        this.ws = null;
        this.statusListeners = new Set();
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
            console.log('Tentando conectar ao WebSocket de execução...');
            this.ws = new WebSocket('ws://localhost:8080/execucao');
            
            this.ws.onopen = () => {
                console.log('Conectado ao servidor via WebSocket');
                this.reconnectAttempts = 0;
                this.notifyConnectionChange(true);
            };
            
            this.ws.onmessage = (event) => {
                try {
                    const data = JSON.parse(event.data);
                    if (Array.isArray(data)) {
                        this.notifyStatusListeners(data);
                    }
                } catch (e) {
                    console.error('Erro ao processar dados do socket:', e);
                }
            };
            
            this.ws.onclose = (event) => {
                console.log('WebSocket de execução desconectado', event);
                this.ws = null;
                this.notifyConnectionChange(false);
                this.scheduleReconnect();
            };
            
            this.ws.onerror = (err) => {
                console.error('Erro no WebSocket de execução:', err);
                this.notifyConnectionChange(false);
                // onclose will be called after onerror, so reconnection will be handled there
            };
            
        } catch (error) {
            console.error('Failed to connect execution WebSocket:', error);
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
    
    addStatusListener(callback) {
        this.statusListeners.add(callback);
        return () => this.statusListeners.delete(callback);
    }
    
    addConnectionListener(callback) {
        this.connectionListeners.add(callback);
        return () => this.connectionListeners.delete(callback);
    }
    
    notifyStatusListeners(statusList) {
        this.statusListeners.forEach(callback => {
            try {
                callback(statusList);
            } catch (error) {
                console.error('Error in status listener:', error);
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

export const executionWebSocketService = new ExecutionWebSocketService();
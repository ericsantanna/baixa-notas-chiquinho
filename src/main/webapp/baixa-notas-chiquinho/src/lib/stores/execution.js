// src/lib/stores/execution.js
import { writable } from 'svelte/store';
import { browser } from '$app/environment';
import { executionWebSocketService } from '../services/executionWebSocketService.js';

const FRANQUEADORAS_KEY = 'franqueadoras';

// Session storage helpers
function saveFranqueadoras(franqueadoras) {
    if (browser) {
        sessionStorage.setItem(FRANQUEADORAS_KEY, JSON.stringify(franqueadoras));
        console.log("Franqueadoras saved", franqueadoras.length);
    }
}

function loadFranqueadoras() {
    if (browser) {
        const stored = sessionStorage.getItem(FRANQUEADORAS_KEY);
        console.log("Franqueadoras loaded:", JSON.parse(stored));
        return stored ? JSON.parse(stored) : [];
    }
    return [];
}

function createExecutionStore() {
    const { subscribe, set, update } = writable({
        executionStatus: 'NOT_STARTED',
        franqueadoras: loadFranqueadoras(),
        isConnected: false
    });

    let unsubscribeStatus = null;
    let unsubscribeConnection = null;

    return {
        subscribe,
        
        connect() {
            // Set up WebSocket listeners
            unsubscribeStatus = executionWebSocketService.addStatusListener((statusList) => {
                update(state => {
                    const newState = {
                        ...state,
                        franqueadoras: statusList.length > 0 ? statusList : state.franqueadoras
                    };
                    
                    // Save to session storage
                    saveFranqueadoras(newState.franqueadoras);
                    return newState;
                });
            });
            
            unsubscribeConnection = executionWebSocketService.addConnectionListener((isConnected) => {
                update(state => ({ ...state, isConnected }));
            });
            
            // Connect WebSocket
            executionWebSocketService.connect();
        },
        
        disconnect() {
            // Clean up listeners
            if (unsubscribeStatus) {
                unsubscribeStatus();
                unsubscribeStatus = null;
            }
            
            if (unsubscribeConnection) {
                unsubscribeConnection();
                unsubscribeConnection = null;
            }
            
            // Disconnect WebSocket
            executionWebSocketService.disconnect();
        },
        
        // Manual reconnection method
        reconnect() {
            console.log('Forçando reconexão do WebSocket de execução...');
            executionWebSocketService.forceReconnect();
        },
        
        setExecutionStatus(status) {
            update(state => ({ ...state, executionStatus: status }));
        },
        
        setFranqueadoras(franqueadoras) {
            update(state => {
                const newState = { ...state, franqueadoras };
                saveFranqueadoras(franqueadoras);
                return newState;
            });
        },
        
        updateFranqueadoraStatus(franqueadoraName, status) {
            update(state => {
                const updatedFranqueadoras = state.franqueadoras.map(item =>
                    item.franqueadora === franqueadoraName ? { ...item, status } : item
                );
                saveFranqueadoras(updatedFranqueadoras);
                return { ...state, franqueadoras: updatedFranqueadoras };
            });
        },
        
        clearFranqueadoras() {
            update(state => {
                saveFranqueadoras([]);
                return { ...state, franqueadoras: [] };
            });
        },
        
        // API calls
        async fetchStatus() {
            try {
                const response = await fetch('http://localhost:8080/status');
                const responseJson = await response.json();
                this.setExecutionStatus(responseJson.status);
                return responseJson.status;
            } catch (e) {
                console.error('Erro ao buscar status:', e);
                throw e;
            }
        },
        
        async fetchFranqueadoras() {
            try {
                const response = await fetch('http://localhost:8080/franqueadoras');
                if (!response.ok) {
                    throw new Error(`HTTP error! status: ${response.status}`);
                }
                const responseJson = await response.json();
                console.log('Franqueadoras fetched:', responseJson);
                
                const franqueadoras = responseJson.map(item => ({
                    franqueadora: item,
                    status: 'PENDING'
                }));
                
                this.setFranqueadoras(franqueadoras);
                return franqueadoras;
            } catch (e) {
                console.error('Erro ao buscar franqueadoras:', e);
                throw e;
            }
        },
        
        async executeProcess(usuario, senha, mes, ano) {
            try {
                const response = await fetch('http://localhost:8080/executar', {
                    method: 'POST',
                    headers: {
                        'Content-Type': 'application/json',
                    },
                    body: JSON.stringify({ usuario, senha, mes, ano }),
                });
                return response;
            } catch (e) {
                console.error('Erro ao executar processo:', e);
                throw e;
            }
        },
        
        async interruptProcess() {
            try {
                const response = await fetch('http://localhost:8080/interromper', {
                    method: 'POST'
                });
                
                update(state => {
                    const updatedFranqueadoras = state.franqueadoras.map(item =>
                        item.status === 'PROCESSING' ? { ...item, status: 'PENDING' } : item
                    );
                    saveFranqueadoras(updatedFranqueadoras);
                    return { ...state, franqueadoras: updatedFranqueadoras };
                });
                
                return response;
            } catch (e) {
                console.error('Erro ao interromper processo:', e);
                throw e;
            }
        },
        
        async cleanup() {
            try {
                const response = await fetch('http://localhost:8080/cleanup', {
                    method: 'POST',
                    headers: {
                        'Content-Type': 'application/json'
                    }
                });
                
                if (response.status === 200) {
                    await this.fetchFranqueadoras();
                    console.log('Cleanup successful');
                } else {
                    console.error('Cleanup failed. Check the logs');
                }
                
                return response;
            } catch (error) {
                console.error('Error during cleanup:', error);
                throw error;
            }
        }
    };
}

export const executionStore = createExecutionStore();
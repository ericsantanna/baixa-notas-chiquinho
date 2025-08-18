// src/lib/stores/logs.js
import { writable } from 'svelte/store';
import { browser } from '$app/environment';
import { logWebSocketService } from '../services/logWebSocketService.js';

export const LOG_LEVELS = {
    DEBUG: 'DEBUG',
    INFO: 'INFO',
    WARN: 'WARN',
    ERROR: 'ERROR'
};

// Configuration
const LOGS_STORAGE_KEY = 'app_logs';
const LOGS_METADATA_KEY = 'app_logs_metadata';
const MAX_LOGS = 1000;          // Maximum logs to keep
const MAX_LOGS_TRIM_TO = 800;   // Trim to this amount when limit reached
const MAX_STORAGE_AGE_MS = 24 * 60 * 60 * 1000; // 24 hours

// Session storage helpers
function saveLogs(logs, logCounter, errorCount) {
    if (!browser) return;
    
    try {
        // Save logs data
        const logsData = {
            logs,
            timestamp: Date.now()
        };
        sessionStorage.setItem(LOGS_STORAGE_KEY, JSON.stringify(logsData));
        
        // Save metadata separately (smaller, faster access)
        const metadata = {
            logCounter,
            errorCount,
            lastSaved: Date.now(),
            count: logs.length
        };
        sessionStorage.setItem(LOGS_METADATA_KEY, JSON.stringify(metadata));
        
        console.log(`Logs saved: ${logs.length} entries, counter: ${logCounter}`);
    } catch (error) {
        console.warn('Failed to save logs to session storage:', error);
        // If storage is full, try clearing old data
        if (error.name === 'QuotaExceededError') {
            console.log('Storage quota exceeded, clearing logs...');
            clearStoredLogs();
        }
    }
}

function loadLogs() {
    if (!browser) return { logs: [], logCounter: 0, errorCount: 0 };
    
    try {
        const stored = sessionStorage.getItem(LOGS_STORAGE_KEY);
        const metadata = JSON.parse(sessionStorage.getItem(LOGS_METADATA_KEY) || '{}');
        
        if (!stored) {
            return { logs: [], logCounter: 0, errorCount: 0 };
        }
        
        const logsData = JSON.parse(stored);
        
        // Check if data is too old
        const age = Date.now() - (logsData.timestamp || 0);
        if (age > MAX_STORAGE_AGE_MS) {
            console.log('Stored logs too old, clearing...');
            clearStoredLogs();
            return { logs: [], logCounter: 0, errorCount: 0 };
        }
        
        // Validate data structure
        if (!Array.isArray(logsData.logs)) {
            console.warn('Invalid logs data structure, clearing...');
            clearStoredLogs();
            return { logs: [], logCounter: 0, errorCount: 0 };
        }
        
        const logs = logsData.logs;
        const logCounter = metadata.logCounter || 0;
        const errorCount = logs.filter(log => log.level === LOG_LEVELS.ERROR).length;
        
        console.log(`Logs loaded: ${logs.length} entries, counter: ${logCounter}, errors: ${errorCount}`);
        return { logs, logCounter, errorCount };
        
    } catch (error) {
        console.warn('Failed to load logs from session storage:', error);
        clearStoredLogs();
        return { logs: [], logCounter: 0, errorCount: 0 };
    }
}

function clearStoredLogs() {
    if (!browser) return;
    try {
        sessionStorage.removeItem(LOGS_STORAGE_KEY);
        sessionStorage.removeItem(LOGS_METADATA_KEY);
        console.log('Stored logs cleared');
    } catch (error) {
        console.warn('Failed to clear stored logs:', error);
    }
}

function trimLogs(logs) {
    if (logs.length <= MAX_LOGS) return logs;
    
    console.log(`Trimming logs from ${logs.length} to ${MAX_LOGS_TRIM_TO}`);
    
    // Keep the most recent entries, but ensure we keep all ERROR logs if possible
    const errorLogs = logs.filter(log => log.level === LOG_LEVELS.ERROR);
    const otherLogs = logs.filter(log => log.level !== LOG_LEVELS.ERROR);
    
    // If we have too many error logs alone, keep the most recent ones
    const maxErrorLogs = Math.floor(MAX_LOGS_TRIM_TO * 0.3); // Reserve 30% for errors
    const keepErrorLogs = errorLogs.slice(-maxErrorLogs);
    
    // Fill the rest with other logs (most recent)
    const remainingSpace = MAX_LOGS_TRIM_TO - keepErrorLogs.length;
    const keepOtherLogs = otherLogs.slice(-remainingSpace);
    
    // Combine and sort by ID to maintain chronological order
    const trimmedLogs = [...keepErrorLogs, ...keepOtherLogs]
        .sort((a, b) => a.id - b.id);
    
    console.log(`After trimming: ${trimmedLogs.length} total (${keepErrorLogs.length} errors)`);
    return trimmedLogs;
}

function createLogStore() {
    // Load initial state from session storage
    const initialState = loadLogs();
    
    const { subscribe, set, update } = writable({
        logs: initialState.logs,
        filters: {
            [LOG_LEVELS.DEBUG]: false,
            [LOG_LEVELS.INFO]: true,
            [LOG_LEVELS.WARN]: true,
            [LOG_LEVELS.ERROR]: true
        },
        isConnected: false,
        lastError: initialState.logs.find(log => log.level === LOG_LEVELS.ERROR) || null,
        errorCount: initialState.errorCount
    });

    let logCounter = initialState.logCounter;
    let unsubscribeLog = null;
    let unsubscribeConnection = null;

    return {
        subscribe,
        
        connect() {
            // Set up WebSocket listeners
            unsubscribeLog = logWebSocketService.addLogListener((logEntry) => {
                update(state => {
                    const newLog = {
                        id: ++logCounter,
                        timestamp: logEntry.timestamp,
                        level: logEntry.level,
                        message: logEntry.message
                    };

                    // Add new log and trim if necessary
                    const updatedLogs = trimLogs([...state.logs, newLog]);
                    
                    let newState = {
                        ...state,
                        logs: updatedLogs
                    };

                    // Track errors for notifications
                    if (logEntry.level === LOG_LEVELS.ERROR) {
                        newState.lastError = newLog;
                        newState.errorCount = state.errorCount + 1;
                        console.log('New error logged:', newLog.message);
                    }

                    // Save to session storage
                    saveLogs(newState.logs, logCounter, newState.errorCount);
                    
                    return newState;
                });
            });
            
            unsubscribeConnection = logWebSocketService.addConnectionListener((isConnected) => {
                update(state => ({ ...state, isConnected }));
            });
            
            // Connect WebSocket
            logWebSocketService.connect();
        },
        
        disconnect() {
            // Clean up listeners
            if (unsubscribeLog) {
                unsubscribeLog();
                unsubscribeLog = null;
            }
            
            if (unsubscribeConnection) {
                unsubscribeConnection();
                unsubscribeConnection = null;
            }
            
            // Disconnect WebSocket
            logWebSocketService.disconnect();
        },
        
        // Manual reconnection method
        reconnect() {
            console.log('Forçando reconexão do WebSocket de logs...');
            logWebSocketService.forceReconnect();
        },
        
        toggleFilter(level) {
            update(state => ({
                ...state,
                filters: {
                    ...state.filters,
                    [level]: !state.filters[level]
                }
            }));
        },
        
        clearLogs() {
            update(state => {
                console.log('Clearing logs, had:', state.logs.length);
                logCounter = 0;
                const newState = { 
                    ...state, 
                    logs: [],
                    lastError: null,
                    errorCount: 0
                };
                
                // Clear from session storage
                clearStoredLogs();
                
                return newState;
            });
        },

        // Clear error notification
        clearLastError() {
            update(state => ({
                ...state,
                lastError: null
            }));
        },

        // Get only error logs
        getErrorLogs() {
            let logs = [];
            const unsubscribe = subscribe(state => {
                logs = state.logs.filter(log => log.level === LOG_LEVELS.ERROR);
            });
            unsubscribe();
            return logs;
        },
        
        // Get storage info
        getStorageInfo() {
            if (!browser) return { size: 0, maxAge: MAX_STORAGE_AGE_MS };
            
            try {
                const stored = sessionStorage.getItem(LOGS_STORAGE_KEY);
                const metadata = JSON.parse(sessionStorage.getItem(LOGS_METADATA_KEY) || '{}');
                
                return {
                    size: stored ? stored.length : 0,
                    count: metadata.count || 0,
                    lastSaved: metadata.lastSaved || null,
                    maxLogs: MAX_LOGS,
                    maxAge: MAX_STORAGE_AGE_MS
                };
            } catch (error) {
                return { size: 0, count: 0, maxLogs: MAX_LOGS, maxAge: MAX_STORAGE_AGE_MS };
            }
        },
        
        exportLogs() {
            let logs = [];
            const unsubscribe = subscribe(state => {
                logs = state.logs;
            });
            unsubscribe();
            
            const logText = logs
                .map(log => `[${new Date(log.timestamp).toISOString()}] ${log.level}: ${log.message}`)
                .join('\n');
            
            const blob = new Blob([logText], { type: 'text/plain' });
            const url = URL.createObjectURL(blob);
            const a = document.createElement('a');
            a.href = url;
            a.download = `logs-${new Date().toISOString().split('T')[0]}.txt`;
            document.body.appendChild(a);
            a.click();
            document.body.removeChild(a);
            URL.revokeObjectURL(url);
        }
    };
}

export const logStore = createLogStore();
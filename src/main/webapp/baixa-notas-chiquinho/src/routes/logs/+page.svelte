<script>
    import {tick} from 'svelte';
    import {LOG_LEVELS, logStore} from '$lib/stores/logs.js';

    let logContainer;
    let autoScroll = $state(true);
    let logState = $state({
        logs: [],
        filters: {},
        isConnected: false,
        errorCount: 0
    });
    
    // Subscribe to log store (already connected at root level)
    $effect(() => {
        return logStore.subscribe(state => {
            logState = state;
        });
    });
    
    // Auto-scroll effect
    $effect(() => {
        if (autoScroll && logContainer && logState.logs.length > 0) {
            tick().then(() => {
                logContainer.scrollTop = logContainer.scrollHeight;
            });
        }
    });
    
    // Filter logs based on enabled filters
    let filteredLogs = $derived(
        logState.logs.filter(log => logState.filters[log.level])
    );
    
    // Handle manual scroll to detect if user scrolled up
    function handleScroll() {
        if (!logContainer) return;
        
        const isAtBottom = logContainer.scrollTop + logContainer.clientHeight >=
                          logContainer.scrollHeight - 10; // 10px threshold
        autoScroll = isAtBottom;
    }
    
    function scrollToBottom() {
        autoScroll = true;
        if (logContainer) {
            logContainer.scrollTop = logContainer.scrollHeight;
        }
    }
    
    function formatTimestamp(timestamp) {
        const date = new Date(timestamp);
        return date.toLocaleTimeString('en-US', { 
            hour12: false, 
            hour: '2-digit', 
            minute: '2-digit', 
            second: '2-digit',
            fractionalSecondDigits: 3
        });
    }

    function getLogLevelClass(level) {
        switch (level) {
            case LOG_LEVELS.DEBUG:
                return 'log-debug';
            case LOG_LEVELS.INFO:
                return 'log-info';
            case LOG_LEVELS.WARN:
                return 'log-warn';
            case LOG_LEVELS.ERROR:
                return 'log-error';
            default:
                return 'log-info';
        }
    }
</script>

<!-- Rest of your template remains the same -->
<svelte:head>
    <title>Logs - Baixa Notas Chiquinho</title>
</svelte:head>

<div class="page-container">
    <div class="content-header">
        <h1>Logs da Aplicação</h1>
        
        <div class="connection-status">
            <span class="status-indicator {logState.isConnected ? 'connected' : 'disconnected'}">
                {logState.isConnected ? '🟢' : '🔴'}
            </span>
            <span>{logState.isConnected ? 'Conectado' : 'Desconectado'}</span>
            {#if logState.errorCount > 0}
                <span class="error-count">
                    {logState.errorCount} erro{logState.errorCount > 1 ? 's' : ''}
                </span>
            {/if}
        </div>
    </div>
    
    <div class="logs-controls">
        <div class="filter-controls">
            <span class="filter-label">Filtrar por nível:</span>
            {#each Object.values(LOG_LEVELS) as level}
                <button
                    class="filter-toggle {getLogLevelClass(level)} {logState.filters[level] ? 'active' : ''}"
                    onclick={() => logStore.toggleFilter(level)}
                >
                    {level} ({logState.logs.filter(log => log.level === level).length})
                </button>
            {/each}
        </div>

        <div class="log-controls-divider"></div>
        
        <div class="action-controls">
            <button 
                class="btn-control btn-autoscroll"
                onclick={scrollToBottom}
                disabled={autoScroll}
                title="Ir para o final"
            >
                📍 Auto-scroll
            </button>
            
            <button 
                class="btn-control btn-cleanup"
                onclick={() => logStore.clearLogs()}
                title="Limpar todos os logs"
            >
                🗑️ Limpar
            </button>
            
            <button 
                class="btn-control btn-export"
                onclick={() => logStore.exportLogs()}
                title="Exportar logs para arquivo"
            >
                💾 Exportar
            </button>
        </div>
    </div>
    
    <div class="logs-stats">
        <span>Total: {logState.logs.length} | Visíveis: {filteredLogs.length}</span>
        <span class="auto-scroll-indicator {autoScroll ? 'active' : ''}">
            Auto-scroll: {autoScroll ? 'ATIVO' : 'INATIVO'}
        </span>
    </div>
    
    <div 
        class="logs-container"
        bind:this={logContainer}
        onscroll={handleScroll}
    >
        {#if filteredLogs.length === 0}
            <div class="empty-state">
                {#if logState.logs.length === 0}
                    Nenhum log disponível. {logState.isConnected ? 'Aguardando entradas de log...' : 'Conecte para começar a receber logs.'}
                {:else}
                    Nenhum log corresponde ao filtro atual. Tente habilitar mais níveis de log acima.
                {/if}
            </div>
        {:else}
            {#each filteredLogs as log (log.id)}
                <div class="log-entry {getLogLevelClass(log.level)}">
                    <span class="log-timestamp">{formatTimestamp(log.timestamp)}</span>
                    <span class="log-level">{log.level}</span>
                    <span class="log-message">{log.message}</span>
                </div>
            {/each}
        {/if}
    </div>
</div>

<style>
    /* Base page styling - matching main execution page */
    .page-container {
        background: white;
        color: #333;
        font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
        display: grid;
        grid-template-rows: auto auto auto 1fr; /* header, controls, status-header, logs */
        height: calc(100vh - 72px - 32px);
        overflow: hidden;
    }
    
    /* Header styling */
    .content-header {
        display: flex;
        justify-content: space-between;
        align-items: center;
        margin-bottom: 2rem;
        padding-bottom: 1rem;
        border-bottom: 1px solid #e1e5e9;
    }
    
    .content-header h1 {
        margin: 0;
        color: #333;
        font-size: 1.5rem;
        font-weight: 600;
        line-height: 2.3rem;
    }
    
    .connection-status {
        display: flex;
        align-items: center;
        gap: 0.5rem;
        font-size: 0.9rem;
        color: #666;
        padding: 0.5rem 1rem;
        background: #f8f9fa;
        border-radius: 6px;
        border: 1px solid #e1e5e9;
    }
    
    .status-indicator {
        font-size: 1rem;
    }
    
    /* Controls styling */
    .logs-controls {
        display: flex;
        justify-content: space-between;
        align-items: center;
        padding: 1rem;
        border: 1px solid #e1e5e9;
        border-radius: 6px;
        background: #f8f9fa;
        margin-bottom: 1rem;
        flex-wrap: wrap;
        gap: 1rem;
    }
    
    .filter-controls, .action-controls {
        display: flex;
        align-items: center;
        gap: 0.75rem;
    }

    .log-controls-divider {
        height: 100%;
        border-left: 1px solid #d7d9dd;
    }
    
    .filter-label {
        font-weight: 500;
        color: #333;
        margin-right: 0.5rem;
    }
    
    .filter-toggle {
        padding: 0.5rem 1rem;
        border: 2px solid #e1e5e9;
        border-radius: 6px;
        background: white;
        color: #666;
        cursor: pointer;
        font-size: 0.85rem;
        font-weight: 500;
        transition: all 0.2s ease;
    }
    
    .filter-toggle:hover {
        background: #e4e4e4;
    }
    
    .filter-toggle.active {
        border-color: #4f46e5;
        background: #f0f0ff;
        color: #4f46e5;
    }
    
    /* Log level specific colors - muted for light theme */
    .filter-toggle.log-debug.active {
        border-color: #8b5cf6;
        background: #f3f4f6;
        color: #8b5cf6;
    }
    
    .filter-toggle.log-info.active {
        border-color: #3b82f6;
        background: #eff6ff;
        color: #3b82f6;
    }
    
    .filter-toggle.log-warn.active {
        border-color: #f59e0b;
        background: #fffbeb;
        color: #d97706;
    }
    
    .filter-toggle.log-error.active {
        border-color: #ef4444;
        background: #fef2f2;
        color: #dc2626;
    }
    
    /* Action buttons - matching main page button styles */
    .btn-control {
        padding: 0.5rem 1rem;
        border: none;
        border-radius: 6px;
        cursor: pointer;
        font-size: 0.9rem;
        font-weight: 500;
        transition: background-color 0.2s ease;
    }

    .btn-control:hover {
        background: #e4e4e4;
    }

    .btn-export {
        background: white;
        color: #4f46e5;
        border: 2px solid #4f46e5;
    }
    
    .btn-autoscroll {
        background: white;
        color: #5c5c5c;
        border: 2px solid #5c5c5c;
    }
    
    .btn-autoscroll:disabled {
        background: #d1d5db;
        color: #9ca3af;
        cursor: not-allowed;
        opacity: 0.6;
    }

    .btn-cleanup {
        background: white;
        color: #e74c3c;
        border: 2px solid #e74c3c;
    }

    .btn-cleanup:disabled {
        background: #d1d5db;
        color: #9ca3af;
        cursor: not-allowed;
        opacity: 0.6;
    }
    
    /* Stats bar */
    .logs-stats {
        display: flex;
        justify-content: space-between;
        align-items: center;
        padding: 0.5rem 1rem;
        background: #f8f9fa;
        border: 1px solid #e1e5e9;
        border-radius: 6px;
        font-size: 0.8rem;
        color: #666;
        margin-bottom: 1rem;
    }
    
    .auto-scroll-indicator.active {
        color: #16a34a;
        font-weight: 500;
    }
    
    /* Logs container */
    .logs-container {
        /*height: 60vh;*/
        overflow: auto;
        /*overflow-y: auto;*/
        border: 1px solid #e1e5e9;
        border-radius: 6px;
        background: white;
        scroll-behavior: smooth;
    }
    
    .empty-state {
        display: flex;
        align-items: center;
        justify-content: center;
        height: 100%;
        color: #9ca3af;
        font-style: italic;
        text-align: center;
    }
    
    /* Log entries */
    .log-entry {
        display: flex;
        align-items: flex-start;
        gap: 1rem;
        padding: 0.75rem 1rem;
        border-bottom: 1px solid #f3f4f6;
        font-size: 0.9rem;
        line-height: 1.4;
        transition: background-color 0.1s ease;
        font-family: 'Monaco', 'Menlo', 'Ubuntu Mono', monospace;
    }
    
    .log-entry:hover {
        background: #f9fafb;
    }
    
    .log-entry:last-child {
        border-bottom: none;
    }
    
    .log-timestamp {
        flex-shrink: 0;
        color: #6b7280;
        font-size: 0.8rem;
        width: 90px;
    }
    
    .log-level {
        flex-shrink: 0;
        font-weight: 600;
        text-align: center;
        width: 60px;
        padding: 0.125rem 0.5rem;
        border-radius: 4px;
        font-size: 0.75rem;
    }
    
    .log-message {
        flex: 1;
        word-break: break-word;
        white-space: pre-wrap;
        color: #374151;
    }
    
    /* Log level styling - light theme colors */
    .log-debug .log-level {
        background: #f3f4f6;
        color: #8b5cf6;
        border: 1px solid #e5e7eb;
    }
    
    .log-info .log-level {
        background: #eff6ff;
        color: #3b82f6;
        border: 1px solid #dbeafe;
    }
    
    .log-warn .log-level {
        background: #fffbeb;
        color: #d97706;
        border: 1px solid #fed7aa;
    }
    
    .log-error .log-level {
        background: #fef2f2;
        color: #dc2626;
        border: 1px solid #fecaca;
    }
    
    /* Left border indicators */
    .log-debug {
        border-left: 3px solid #8b5cf6;
    }
    
    .log-info {
        border-left: 3px solid #3b82f6;
    }
    
    .log-warn {
        border-left: 3px solid #f59e0b;
    }
    
    .log-error {
        border-left: 3px solid #ef4444;
    }

    .error-count {
        background: #fef2f2;
        color: #dc2626;
        font-size: 0.75rem;
        padding: 0.25rem 0.5rem;
        border-radius: 12px;
        border: 1px solid #fecaca;
        font-weight: 600;
    }

    /* Responsive design */
    @media (max-width: 768px) {
        .page-container {
            padding: 1rem;
        }
        
        .logs-controls {
            flex-direction: column;
            align-items: stretch;
        }
        
        .filter-controls, .action-controls {
            justify-content: center;
        }
        
        .logs-stats {
            flex-direction: column;
            gap: 0.25rem;
            text-align: center;
        }
        
        .log-entry {
            flex-direction: column;
            gap: 0.25rem;
        }
        
        .log-timestamp, .log-level {
            width: auto;
        }
        
        .logs-container {
            height: calc(100vh - 21rem);
        }
    }
    
    @media (max-width: 640px) {
        .content-header {
            flex-direction: column;
            gap: 1rem;
            align-items: flex-start;
        }
        
        .connection-status {
            align-self: stretch;
            justify-content: center;
        }
    }
</style>
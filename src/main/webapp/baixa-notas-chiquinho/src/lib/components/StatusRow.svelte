<script>
  export let franqueadora = '';
  export let status = '';
  export let errorMessage = '';
  let icon = '';

  $: {
    if (status === 'PROCESSED' || status === 'PREVIOUSLY_PROCESSED') {
      icon = 'check';
    } else if (status === 'PENDING') {
      icon = 'clock';
    } else if (status === 'NOT_PROCESSED') {
      icon = 'cross';
    } else if (status === 'PROCESSING') {
      icon = 'spinner';
    } else {
      icon = '';
    }
  }

  function statusLabel(status) {
    switch (status) {
      case 'PREVIOUSLY_PROCESSED':
        return 'Já processado em execução anterior';
      case 'PROCESSED':
        return 'Processado';
      case 'PENDING':
        return 'Pendente';
      case 'NOT_PROCESSED':
        return 'Não processado';
      case 'PROCESSING':
        return 'Processando...';
      default:
        return status;
    }
  }
</script>

<div class="row">
  <span class="icon">
    {#if icon === 'check'}
      <!-- Check icon -->
      <svg width="32" height="32" viewBox="0 0 32 32" fill="none"><path d="M8 16l6 6 10-10" stroke="#4CAF50" stroke-width="3" fill="none"/></svg>
    {:else if icon === 'clock'}
      <!-- Clock icon -->
      <svg width="32" height="32" viewBox="0 0 32 32" fill="none"><circle cx="16" cy="16" r="14" stroke="#888" stroke-width="3" fill="none"/><path d="M16 10v6l4 4" stroke="#888" stroke-width="3" fill="none"/></svg>
    {:else if icon === 'cross'}
      <!-- Cross icon -->
      <svg width="32" height="32" viewBox="0 0 32 32" fill="none"><line x1="8" y1="8" x2="24" y2="24" stroke="#F44336" stroke-width="3"/><line x1="24" y1="8" x2="8" y2="24" stroke="#F44336" stroke-width="3"/></svg>
    {:else if icon === 'spinner'}
      <!-- Spinner icon -->
      <svg class="spinner" width="32" height="32" viewBox="0 0 32 32"><circle cx="16" cy="16" r="14" stroke="#2196F3" stroke-width="3" fill="none" opacity="0.2"/><circle cx="16" cy="16" r="14" stroke="#2196F3" stroke-width="3" fill="none" stroke-dasharray="22 66" stroke-linecap="round"><animateTransform attributeName="transform" type="rotate" from="0 16 16" to="360 16 16" dur="1s" repeatCount="indefinite"/></circle></svg>
    {/if}
  </span>
  <span class="franqueadora">{franqueadora}</span>
  <span class="status">{statusLabel(status)}</span>
</div>
{#if errorMessage}
  <div class="error-message">{errorMessage}</div>
{/if}

<style>
  .row {
    display: flex;
    align-items: center;
    gap: 1rem;
    margin-bottom: 0.5rem;
  }
  .icon {
    width: 32px;
    height: 32px;
    display: flex;
    align-items: center;
    justify-content: center;
  }
  .spinner {
    animation: spin 1s linear infinite;
  }
  @keyframes spin {
    100% { transform: rotate(360deg); }
  }
  .franqueadora {
    font-size: 1.5rem;
    font-weight: 600;
  }
  .status {
    font-size: 1rem;
    color: #888;
    margin-left: auto;
  }
  .error-message {
    color: #e74c3c;
    background: #fbeaea;
    border: 1px solid #e74c3c;
    border-radius: 4px;
    padding: 0.5rem 1rem;
    font-size: 1rem;
    font-weight: 500;
  }
</style>

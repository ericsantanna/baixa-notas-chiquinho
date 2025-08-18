<script>
  let {
    disabled = false,
    onCleanup
  } = $props();
</script>

<div class="cleanup-control">
  <button 
    class="cleanup-btn" 
    onclick={onCleanup} 
    {disabled}
    title={disabled ? 'Aguarde a execução terminar para limpar' : 'Limpar lista de processados, nao-processaos e o diretório de downloads'}
  >
    <!-- Cleanup/Trash icon -->
    <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
      <polyline points="3 6 5 6 21 6"></polyline>
      <path d="m19 6-2 14a2 2 0 0 1-2 2H9a2 2 0 0 1-2-2L5 6"></path>
      <path d="m10 11 0 6"></path>
      <path d="m14 11 0 6"></path>
      <path d="M5 6l1-2a1 1 0 0 1 1-1h10a1 1 0 0 1 1 1l1 2"></path>
    </svg>
    <span>Limpar</span>
  </button>
</div>

<style>
.cleanup-control {
  display: flex;
  align-items: center;
}

.cleanup-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 0.5rem;
  background: #fff;
  border: 2px solid #e74c3c;
  border-radius: 8px;
  padding: 0.75rem 1.5rem;
  font-size: 1rem;
  font-weight: 600;
  color: #e74c3c;
  cursor: pointer;
  transition: all 0.2s;
  min-height: 48px;
}

.cleanup-btn:hover:not(:disabled) {
  background: #e74c3c;
  color: white;
}

.cleanup-btn:disabled {
  background: #f5f5f5;
  color: #bbb;
  border-color: #ddd;
  cursor: not-allowed;
}

.cleanup-btn:disabled svg {
  stroke: #bbb;
}
</style>
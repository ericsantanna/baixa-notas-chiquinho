<script>
  import ExecControl from '$lib/components/ExecControl.svelte';
  import CleanupControl from '$lib/components/CleanupControl.svelte';
  import StatusRow from '$lib/components/StatusRow.svelte';
  import { onMount, onDestroy } from 'svelte';
  import { browser } from '$app/environment';
  import { executionStore } from '$lib/stores/execution.js';
  import {logStore} from "$lib/stores/logs.js";

  // Subscribe to execution store
  let executionState = $state({
    executionStatus: 'NOT_STARTED',
    franqueadoras: [],
    isConnected: false
  });

  $effect(() => {
    return executionStore.subscribe(state => {
      executionState = state;
    });
  });

  const meses = [
    'Janeiro', 'Fevereiro', 'Março', 'Abril', 'Maio', 'Junho',
    'Julho', 'Agosto', 'Setembro', 'Outubro', 'Novembro', 'Dezembro'
  ];

  let usuario = $state('');
  let senha = $state('');
  let mes = $state(meses[new Date().getMonth()]);
  let ano = $state(new Date().getFullYear());

  // Validation errors
  let errors = $state({});

  function validateForm() {
    const newErrors = {};
    
    if (!usuario.trim()) {
      newErrors.usuario = 'Usuário é obrigatório';
    } else if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(usuario.trim())) {
      newErrors.usuario = 'Email deve ter um formato válido';
    }
    
    if (!senha.trim()) {
      newErrors.senha = 'Senha é obrigatória';
    } else if (senha.trim().length < 3) {
      newErrors.senha = 'Senha deve ter pelo menos 3 caracteres';
    }
    
    if (!mes) {
      newErrors.mes = 'Mês é obrigatório';
    }
    
    if (!ano) {
      newErrors.ano = 'Ano é obrigatório';
    } else if (isNaN(ano) || Number(ano) < 2020 || Number(ano) > 2030) {
      newErrors.ano = 'Ano deve estar entre 2020 e 2030';
    }
    
    errors = newErrors;
    return Object.keys(newErrors).length === 0;
  }

  async function toggleExecution() {
    console.log('Toggling execution status:', executionState.executionStatus);
    
    if (["NOT_STARTED", "COMPLETED", "CANCELLED"].includes(executionState.executionStatus)) {
      if (!validateForm()) {
        return;
      }
      
      if (browser && usuario) {
        localStorage.setItem('usuario', usuario);
      }

      await executionStore.executeProcess(usuario, senha, mes, ano);

      logStore.clearLogs()
      senha = '';
    } else {
      await executionStore.interruptProcess();
    }
    
    await executionStore.fetchStatus();
  }

  async function handleCleanup() {
    try {
      await executionStore.cleanup();
    } catch (error) {
      console.error('Error during cleanup:', error);
    }
  }

  // Clear errors when fields are updated
  $effect(() => {
    if (errors.usuario && usuario.trim()) {
      errors = { ...errors, usuario: undefined };
    }
  });

  $effect(() => {
    if (errors.senha && senha.trim()) {
      errors = { ...errors, senha: undefined };
    }
  });

  $effect(() => {
    if (errors.mes && mes) {
      errors = { ...errors, mes: undefined };
    }
  });

  $effect(() => {
    if (errors.ano && ano) {
      errors = { ...errors, ano: undefined };
    }
  });

  onMount(async () => {
    // Load saved user
    if (browser && localStorage.getItem('usuario')) {
      usuario = localStorage.getItem('usuario');
    }

    // Initialize store data
    await executionStore.fetchStatus();
    
    // Only fetch from API if we don't have stored franqueadoras
    if (executionState.franqueadoras.length === 0) {
      await executionStore.fetchFranqueadoras();
    }

    // Connect WebSocket
    executionStore.connect();
  });

  onDestroy(() => {
    executionStore.disconnect();
  });
</script>

<svelte:head>
  <title>Execução - Baixa Notas Chiquinho</title>
</svelte:head>

<div class="page-container">
  <div class="content-header">
    <h1>Execução</h1>

    <div class="connection-status">
      <span class="status-indicator {executionState.isConnected ? 'connected' : 'disconnected'}">
        {executionState.isConnected ? '🟢' : '🔴'}
      </span>
      <span>{executionState.isConnected ? 'Conectado' : 'Desconectado'}</span>
    </div>
  </div>

  <div class="form-container">
    <div class="execution-form">
      <div class="form-row">
        <div class="form-group">
          <label for="usuario" class="field-title">Usuário</label>
          <p class="field-description">Email de acesso ao sistema ITF Gestor</p>
          <input
            type="email"
            id="usuario"
            bind:value={usuario}
            placeholder="usuario@exemplo.com"
            class="text-input"
            class:error={errors.usuario}
          />
          {#if errors.usuario}
            <span class="error-message">{errors.usuario}</span>
          {/if}
        </div>

        <div class="form-group">
          <label for="senha" class="field-title">Senha</label>
          <p class="field-description">Senha de acesso ao sistema ITF Gestor</p>
          <input
            type="password"
            id="senha"
            bind:value={senha}
            placeholder=""
            class="text-input"
            class:error={errors.senha}
          />
          {#if errors.senha}
            <span class="error-message">{errors.senha}</span>
          {/if}
        </div>
      </div>

      <div class="form-row">
        <div class="form-group">
          <label for="mes" class="field-title">Mês</label>
          <p class="field-description">Mês para busca das notas fiscais</p>
          <select 
            id="mes" 
            bind:value={mes} 
            class="select-input"
            class:error={errors.mes}
          >
            <option value="">Selecione o mês</option>
            {#each meses as mesOption}
              <option value={mesOption}>{mesOption}</option>
            {/each}
          </select>
          {#if errors.mes}
            <span class="error-message">{errors.mes}</span>
          {/if}
        </div>

        <div class="form-group">
          <label for="ano" class="field-title">Ano</label>
          <p class="field-description">Ano para busca das notas fiscais</p>
          <input
            type="number"
            id="ano"
            bind:value={ano}
            min="2020"
            max="2030"
            placeholder="2024"
            class="number-input"
            class:error={errors.ano}
          />
          {#if errors.ano}
            <span class="error-message">{errors.ano}</span>
          {/if}
        </div>
      </div>
    </div>
  </div>

  <!-- Controls container -->
  <div class="controls-container">
    <ExecControl status={executionState.executionStatus} onToggle={toggleExecution} />
    <CleanupControl disabled={executionState.executionStatus === "RUNNING"} onCleanup={handleCleanup} />
  </div>

  <div class="status-section">
    <div class="status-list">
      {#each executionState.franqueadoras as franqueadora}
        <StatusRow franqueadora={franqueadora.franqueadora} status={franqueadora.status} errorMessage={franqueadora.errorMessage} />
      {/each}
    </div>
  </div>
</div>

<style>
  .controls-container {
    display: flex;
    justify-content: space-between;
    border-bottom: 1px solid gray;
    padding-bottom: 1.5rem;
  }

  .page-container {
    background: white;
    color: #333;
    font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
    display: grid;
    grid-template-rows: auto auto auto 1fr; /* header, form, controls, franqueadoras */
    height: calc(100vh - 72px - 30px);
  }

  .content-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 1rem;
    padding-bottom: 1rem;
    border-bottom: 1px solid #e1e5e9;
    line-height: 1.2rem;
  }

  .content-header h1 {
    margin: 0;
    color: #333;
    font-size: 1.5rem;
    font-weight: 600;
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

  .form-container {
    margin-bottom: 2rem;
  }

  .execution-form {
    display: flex;
    flex-direction: column;
    gap: 2rem;
  }

  .form-row {
    display: flex;
    gap: 2rem;
  }

  .form-group {
    flex: 1;
    display: flex;
    flex-direction: column;
    gap: 0.5rem;
  }

  .field-title {
    font-weight: 600;
    color: #333;
    margin: 0;
    font-size: 1rem;
  }

  .field-description {
    font-size: 0.875rem;
    color: #666;
    margin: 0;
    line-height: 1.4;
  }

  /* Input Styles */
  .text-input,
  .number-input,
  .select-input {
    padding: 0.75rem;
    border: 2px solid #e1e5e9;
    border-radius: 6px;
    font-size: 0.9rem;
    transition: border-color 0.2s ease;
    background: white;
    min-width: 20rem;
  }

  .text-input:focus,
  .number-input:focus,
  .select-input:focus {
    outline: none;
    border-color: #4f46e5;
    box-shadow: 0 0 0 3px rgba(79, 70, 229, 0.1);
  }

  .text-input.error,
  .number-input.error,
  .select-input.error {
    border-color: #ef4444;
  }

  .text-input.error:focus,
  .number-input.error:focus,
  .select-input.error:focus {
    box-shadow: 0 0 0 3px rgba(239, 68, 68, 0.1);
  }

  /* Error Message */
  .error-message {
    color: #ef4444;
    font-size: 0.8rem;
    margin-top: 0.25rem;
  }

  /* Status Section */
  .status-section {
    padding-top: 1.5rem;
    padding-bottom: 2rem;
  }

  .status-list {
    display: flex;
    flex-direction: column;
    gap: 0.5rem;
  }

  /* Responsive Design */
  @media (max-width: 640px) {
    .page-container {
      margin: 1rem;
      padding: 1rem;
    }

    .form-container {
      padding: 1.5rem;
    }

    .form-row {
      flex-direction: column;
      gap: 1.5rem;
    }

    .connection-status {
      align-self: stretch;
      justify-content: center;
    }
  }
</style>
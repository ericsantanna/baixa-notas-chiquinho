<script>
  import {logStore} from "$lib/stores/logs.js";

  let {
    status = "NOT_STARTED",
    onToggle
  } = $props();

  let text = $derived.by(() => {
    console.log('Resulting text from status:', status);
    if (["NOT_STARTED", "COMPLETED"].includes(status)) {
      return 'Executar';
    } else if (status === "CANCELLED") {
      return 'Execucão interrompida';
    }
    return 'Execução em progresso';
  });

  let icon = $derived.by(() => {
    if (["NOT_STARTED", "COMPLETED", "CANCELLED"].includes(status)) {
      return 'play';
    }
    return 'stop';
  });

  let messageClass = $derived.by(() => {
    if (status === "CANCELLED") return 'exec-message warning';
    if (status === "RUNNING") return 'exec-message running';
    return 'exec-message';
  });

  $effect(() => {
    return logStore.subscribe(state => {
      if (state.lastError) {
        status = 'CANCELLED';
      }
    });
  });

</script>

<div class="exec-control">
  <button class="exec-btn" onclick={onToggle} title={status === "RUNNING" ? 'Interromper execução' : 'Iniciar execução'}>
    {#if icon === 'play'}
      <!-- Play icon -->
      <svg width="40" height="40" viewBox="0 0 24 24" fill="none" stroke="#1578c9" stroke-width="3" stroke-linecap="round" stroke-linejoin="round">
        <polygon points="5 3 19 12 5 21 5 3" />
      </svg>
    {:else}
      <!-- Stop icon -->
      <svg width="40" height="40" viewBox="0 0 24 24" fill="none" stroke="#e74c3c" stroke-width="3" stroke-linecap="round" stroke-linejoin="round">
        <rect x="6" y="6" width="12" height="12" />
      </svg>
    {/if}
  </button>
  <span class={messageClass}>
    {text}
    {#if status === "RUNNING"}
      <span class="cool-loader">
        <span class="dot"></span>
        <span class="dot"></span>
        <span class="dot"></span>
      </span>
    {/if}
  </span>
</div>

<style>
.exec-control {
  display: flex;
  align-items: center;
  gap: 2rem;
}
.exec-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  background: #e6f0fa;
  border: none;
  border-radius: 8px;
  padding: 1rem 2rem;
  font-size: 1.5rem;
  font-weight: bold;
  color: #1578c9;
  cursor: pointer;
  transition: background 0.2s;
}
.exec-btn:hover {
  background: #d0e6fa;
}
.exec-message {
  font-size: 1.5rem;
  font-weight: 600;
}
.exec-message.warning {
  color: #e67e22;
  background: #fff6e6;
  border: 1px solid #e67e22;
  border-radius: 4px;
  padding: 0.5rem 1rem;
}
.exec-message.running {
  position: relative;
  color: #1578c9;
}
.cool-loader {
  display: inline-flex;
  align-items: center;
  margin-left: 0.5em;
  height: 0.8em;
}
.cool-loader .dot {
  width: 0.8em;
  height: 0.8em;
  margin: 0 0.15em;
  border-radius: 50%;
  background: #1578c9;
  opacity: 0.7;
  animation: cool-bounce 1s infinite alternate;
}
.cool-loader .dot {
  width: 0.5em;
  height: 0.5em;
  margin: 0 0.1em;
}
.cool-loader .dot:nth-child(2) {
  animation-delay: 0.2s;
}
.cool-loader .dot:nth-child(3) {
  animation-delay: 0.4s;
}
@keyframes cool-bounce {
  0% { transform: translateY(0); opacity: 0.7; }
  50% { transform: translateY(-0.5em); opacity: 1; }
  100% { transform: translateY(0); opacity: 0.7; }
}
</style>

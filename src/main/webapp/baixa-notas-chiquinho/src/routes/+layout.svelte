<script>
  import { page } from '$app/stores';
  import { onMount, onDestroy } from 'svelte';
  import { logStore } from '$lib/stores/logs.js';
  import ErrorNotification from '$lib/components/ErrorNotification.svelte';

  // Initialize log store at root level
  onMount(() => {
    console.log('Initializing log store at root level');
    logStore.connect();
  });

  onDestroy(() => {
    console.log('Disconnecting log store at root level');
    logStore.disconnect();
  });
</script>

<!-- Error notification component - shows across all pages -->
<ErrorNotification />

<div class="container">
  <header>
    <h1>Baixa Notas Chiquinho</h1>
  </header>
  <aside class="sidebar">
    <nav>
      <ul>
        <li><a class:active={$page.url.pathname === '/execucao'} href="/execucao">Execução</a></li>
        <li><a class:active={$page.url.pathname === '/configuracoes'} href="/configuracoes">Configurações</a></li>
        <li><a class:active={$page.url.pathname === '/agente'} href="/agente">Agente de IA</a></li>
        <li><a class:active={$page.url.pathname === '/metricas'} href="/metricas">Métricas</a></li>
        <li><a class:active={$page.url.pathname === '/logs'} href="/logs">Logs</a></li>
      </ul>
    </nav>
  </aside>
  <main>
    <slot />
  </main>
</div>

<style>
.container {
  display: flex;
  flex-direction: row;
}
header {
  position: fixed;
  width: 100%;
  top: 0;
  left: 0;
  background: linear-gradient(to bottom, #1578c9 0%, #0b66b1 100%);
  color: #fff;
  padding: 5px 20px;
  z-index: 1;
  font-size: 12px;
}
.sidebar {
  position: fixed;
  top: 72px; /* Adjusted to account for header height */
  left: 0;
  width: 128px;
  background: #3D3D3D;
  padding: 16px;
  height: calc(100vh - 72px - 32px); /* 72px for header, 32px for padding */
}
main {
  margin-top: 72px;
  margin-left: 160px; /* Adjusted to account for sidebar width */
  padding: 15px 20px;
  height: 100px;
}
ul {
  list-style: none;
  padding: 0;
}
li {
  height: 1rem;
  padding: 1rem 5px;
}
a {
  text-decoration: none;
  color: #D9D9D9;
  font-weight: bold;
}
a:hover {
  color: #5ca9e8;
}
.active {
  color: #5ca9e8;
}
</style>
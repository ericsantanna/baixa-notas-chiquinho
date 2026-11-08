<script>
    import { onMount } from 'svelte';
    import { browser } from '$app/environment';

    const API = 'http://localhost:9001/api/agente';

    let tab = $state('instrucoes');
    let isLoading = $state(true);
    let message = $state(null); // { type: 'ok' | 'error', text }

    // ---- Instruções
    let promptPadrao = $state('');
    let promptPersonalizado = $state('');
    let instrucoesExtras = $state('');
    let promptEfetivo = $state('');
    let limiteInstrucoes = $state(4000);
    let limitePrompt = $state(20000);

    // ---- Aprendizado
    let playbook = $state({ exists: false, steps: [] });
    let licoes = $state([]);
    let novaLicao = $state('');
    let editando = $state({}); // id -> texto em edição

    // ---- Tempos
    let tempos = $state({ operacoes: [] });
    let automatico = $state(true);
    let minimo = $state('');
    let maximo = $state('');

    const pendentes = $derived(licoes.filter((l) => l.status === 'PENDING'));
    const aprovadas = $derived(licoes.filter((l) => l.status === 'APPROVED'));
    const rejeitadas = $derived(licoes.filter((l) => l.status === 'REJECTED'));

    const NOMES_OPERACAO = {
        select_franqueadora: 'Selecionar a franqueadora no cabeçalho',
        open_consulta_pdv2_menu: 'Abrir Fiscal > Gerenciamento > Consulta Nota Fiscal Venda PDV2'
    };

    function nomeOperacao(op) {
        return NOMES_OPERACAO[op] || op;
    }

    function descreverPasso(passo) {
        switch (passo.tool) {
            case 'select_franqueadora': return 'Selecionar a franqueadora no cabeçalho';
            case 'open_consulta_pdv2_menu': return 'Abrir Fiscal > Gerenciamento > Consulta Nota Fiscal Venda PDV2';
            case 'go_home': return 'Voltar para a página inicial';
            case 'click':
                if (passo.text === '{{franqueadora}}') return 'Clicar na franqueadora da vez, na lista';
                if (passo.text === '{{header}}') return 'Clicar no widget da franqueadora atual (abre a lista)';
                return `Clicar em "${passo.text}"`;
            default: return passo.tool;
        }
    }

    function avisar(type, text) {
        message = { type, text };
        if (type === 'ok') {
            setTimeout(() => { if (message?.text === text) message = null; }, 4000);
        }
    }

    async function api(path, options = {}) {
        const response = await fetch(`${API}${path}`, {
            headers: { 'Content-Type': 'application/json' },
            ...options
        });
        let body = null;
        try { body = await response.json(); } catch (e) { /* sem corpo */ }
        if (!response.ok) {
            throw new Error(body?.erro || `Erro ${response.status}`);
        }
        return body;
    }

    function aplicarInstrucoes(d) {
        promptPadrao = d.promptPadrao;
        promptPersonalizado = d.promptPersonalizado || '';
        instrucoesExtras = d.instrucoesExtras || '';
        promptEfetivo = d.promptEfetivo;
        limiteInstrucoes = d.limiteInstrucoes;
        limitePrompt = d.limitePrompt;
    }

    function aplicarAprendizado(d) {
        playbook = d.playbook;
        licoes = d.licoes;
    }

    function aplicarTempos(d) {
        tempos = d;
        automatico = d.automatico;
        minimo = d.minimo ?? '';
        maximo = d.maximo ?? '';
    }

    async function carregar() {
        if (!browser) return;
        try {
            isLoading = true;
            const [i, a, t] = await Promise.all([api('/instrucoes'), api('/aprendizado'), api('/tempos')]);
            aplicarInstrucoes(i);
            aplicarAprendizado(a);
            aplicarTempos(t);
        } catch (e) {
            avisar('error', `Não foi possível carregar: ${e.message}`);
        } finally {
            isLoading = false;
        }
    }

    async function executar(fn, okText) {
        try {
            await fn();
            if (okText) avisar('ok', okText);
        } catch (e) {
            avisar('error', e.message);
        }
    }

    const salvarInstrucoes = () => executar(async () => {
        aplicarInstrucoes(await api('/instrucoes', {
            method: 'PUT',
            body: JSON.stringify({ promptPersonalizado, instrucoesExtras })
        }));
    }, 'Instruções salvas. Valem a partir da próxima execução.');

    const restaurarPrompt = () => executar(async () => {
        aplicarInstrucoes(await api('/instrucoes/restaurar', { method: 'POST' }));
    }, 'Prompt padrão restaurado.');

    function carregarPadraoParaEditar() {
        promptPersonalizado = promptPadrao;
    }

    const esquecerPlaybook = () => executar(async () => {
        aplicarAprendizado(await api('/playbook', { method: 'DELETE' }));
    }, 'Playbook esquecido. O agente vai aprender de novo na próxima vez.');

    const adicionarLicao = () => executar(async () => {
        aplicarAprendizado(await api('/licoes', { method: 'POST', body: JSON.stringify({ texto: novaLicao }) }));
        novaLicao = '';
        aplicarInstrucoes(await api('/instrucoes'));
    }, 'Lição adicionada e aprovada.');

    const mudarStatus = (id, acao) => executar(async () => {
        aplicarAprendizado(await api(`/licoes/${id}/${acao}`, { method: 'POST' }));
        aplicarInstrucoes(await api('/instrucoes'));
    });

    const removerLicao = (id) => executar(async () => {
        aplicarAprendizado(await api(`/licoes/${id}`, { method: 'DELETE' }));
        aplicarInstrucoes(await api('/instrucoes'));
    });

    const salvarEdicao = (id) => executar(async () => {
        aplicarAprendizado(await api(`/licoes/${id}`, { method: 'PUT', body: JSON.stringify({ texto: editando[id] }) }));
        const { [id]: _, ...resto } = editando;
        editando = resto;
        aplicarInstrucoes(await api('/instrucoes'));
    }, 'Lição atualizada.');

    const salvarTempos = () => executar(async () => {
        aplicarTempos(await api('/tempos', {
            method: 'PUT',
            body: JSON.stringify({
                automatico,
                minimo: minimo === '' ? null : Number(minimo),
                maximo: maximo === '' ? null : Number(maximo)
            })
        }));
    }, 'Configuração de tempos salva.');

    const esquecerTempos = () => executar(async () => {
        aplicarTempos(await api('/tempos', { method: 'DELETE' }));
    }, 'Histórico de tempos zerado.');

    onMount(carregar);
</script>

<svelte:head>
    <title>Agente de IA - Baixa Notas Chiquinho</title>
</svelte:head>

<div class="page-container">
    <div class="content-header">
        <h1>Agente de IA</h1>
    </div>

    <div class="tabs" role="tablist">
        <button role="tab" aria-selected={tab === 'instrucoes'} class:active={tab === 'instrucoes'} onclick={() => (tab = 'instrucoes')}>Instruções</button>
        <button role="tab" aria-selected={tab === 'aprendizado'} class:active={tab === 'aprendizado'} onclick={() => (tab = 'aprendizado')}>
            Aprendizado {#if pendentes.length}<span class="badge">{pendentes.length}</span>{/if}
        </button>
        <button role="tab" aria-selected={tab === 'tempos'} class:active={tab === 'tempos'} onclick={() => (tab = 'tempos')}>Tempos</button>
    </div>

    {#if message}
        <div class="banner" class:error={message.type === 'error'} role="status">{message.text}</div>
    {/if}

    {#if isLoading}
        <p class="muted">Carregando...</p>
    {:else if tab === 'instrucoes'}
        <section class="section">
            <h2>Instruções adicionais</h2>
            <p class="muted">
                Texto que a IA recebe junto com as instruções padrão, por exemplo: "O menu Fiscal só aparece depois de
                selecionar a franqueadora" ou "Franqueadoras ENCERRADAS ficam no fim da lista".
                Vale a partir da próxima execução.
            </p>
            <textarea rows="6" maxlength={limiteInstrucoes} bind:value={instrucoesExtras} placeholder="Opcional"></textarea>
            <span class="counter">{instrucoesExtras.length} / {limiteInstrucoes}</span>

            <details class="advanced">
                <summary>Avançado: substituir o prompt completo</summary>
                <p class="muted">
                    Deixe vazio para usar o prompt padrão (recomendado). Se você escrever um prompt próprio, ele substitui o
                    padrão, mas as regras de segurança fixas sempre são acrescentadas no final. As ferramentas da IA não mudam.
                </p>
                <textarea rows="14" maxlength={limitePrompt} bind:value={promptPersonalizado} placeholder="(usando o prompt padrão)"></textarea>
                <div class="row">
                    <button class="secondary" onclick={carregarPadraoParaEditar}>Carregar o padrão para editar</button>
                    <button class="secondary" onclick={restaurarPrompt}>Restaurar o padrão</button>
                </div>
            </details>

            <details class="advanced">
                <summary>Ver o prompt completo que a IA vai receber</summary>
                <pre>{promptEfetivo}</pre>
            </details>

            <div class="actions"><button class="primary" onclick={salvarInstrucoes}>Salvar instruções</button></div>
        </section>
    {:else if tab === 'aprendizado'}
        <section class="section">
            <h2>Caminho aprendido</h2>
            <p class="muted">
                Quando a IA consegue chegar na tela de consulta, o caminho é guardado e repetido nas próximas
                franqueadoras e execuções sem gastar tokens. Se parar de funcionar, a IA assume e aprende de novo.
            </p>
            {#if playbook.exists}
                <ol class="steps">
                    {#each playbook.steps as passo}<li>{descreverPasso(passo)}</li>{/each}
                </ol>
                <p class="muted">
                    Aprendido em {playbook.updatedAt?.replace('T', ' ')} por {playbook.source}.
                    {#if playbook.consecutiveFailures > 0}Falhou {playbook.consecutiveFailures} vez(es) seguida(s); é descartado ao chegar em 3.{/if}
                </p>
                <button class="secondary" onclick={esquecerPlaybook}>Esquecer este caminho</button>
            {:else}
                <p><em>Nenhum caminho guardado ainda.</em></p>
            {/if}
        </section>

        <section class="section">
            <h2>Lições</h2>
            <p class="muted">
                Depois de uma recuperação difícil, a IA sugere até 2 lições curtas. <strong>Nada vale até você aprovar</strong>:
                só as lições aprovadas entram nas instruções da IA (no máximo 20).
            </p>

            <h3>Aguardando aprovação ({pendentes.length})</h3>
            {#each pendentes as l (l.id)}
                <div class="lesson pending">
                    {#if editando[l.id] !== undefined}
                        <textarea rows="2" bind:value={editando[l.id]}></textarea>
                        <div class="row">
                            <button class="primary" onclick={() => salvarEdicao(l.id)}>Salvar</button>
                            <button class="secondary" onclick={() => { const { [l.id]: _, ...r } = editando; editando = r; }}>Cancelar</button>
                        </div>
                    {:else}
                        <p>{l.text}</p>
                        <p class="muted small">Sugerida: {l.origem}</p>
                        <div class="row">
                            <button class="primary" onclick={() => mudarStatus(l.id, 'aprovar')}>Aprovar</button>
                            <button class="secondary" onclick={() => (editando = { ...editando, [l.id]: l.text })}>Editar</button>
                            <button class="secondary" onclick={() => mudarStatus(l.id, 'rejeitar')}>Rejeitar</button>
                        </div>
                    {/if}
                </div>
            {:else}
                <p class="muted"><em>Nenhuma sugestão pendente.</em></p>
            {/each}

            <h3>Aprovadas ({aprovadas.length})</h3>
            {#each aprovadas as l (l.id)}
                <div class="lesson approved">
                    {#if editando[l.id] !== undefined}
                        <textarea rows="2" bind:value={editando[l.id]}></textarea>
                        <div class="row">
                            <button class="primary" onclick={() => salvarEdicao(l.id)}>Salvar</button>
                            <button class="secondary" onclick={() => { const { [l.id]: _, ...r } = editando; editando = r; }}>Cancelar</button>
                        </div>
                    {:else}
                        <p>{l.text}</p>
                        <p class="muted small">Origem: {l.origem}</p>
                        <div class="row">
                            <button class="secondary" onclick={() => (editando = { ...editando, [l.id]: l.text })}>Editar</button>
                            <button class="secondary" onclick={() => mudarStatus(l.id, 'pendente')}>Voltar para pendente</button>
                            <button class="secondary" onclick={() => removerLicao(l.id)}>Remover</button>
                        </div>
                    {/if}
                </div>
            {:else}
                <p class="muted"><em>Nenhuma lição aprovada.</em></p>
            {/each}

            {#if rejeitadas.length}
                <details class="advanced">
                    <summary>Rejeitadas ({rejeitadas.length})</summary>
                    {#each rejeitadas as l (l.id)}
                        <div class="lesson rejected">
                            <p>{l.text}</p>
                            <div class="row">
                                <button class="secondary" onclick={() => mudarStatus(l.id, 'pendente')}>Reconsiderar</button>
                                <button class="secondary" onclick={() => removerLicao(l.id)}>Excluir</button>
                            </div>
                        </div>
                    {/each}
                </details>
            {/if}

            <h3>Adicionar lição manualmente</h3>
            <textarea rows="2" maxlength="300" bind:value={novaLicao} placeholder="Ex.: Sempre selecione a franqueadora antes de abrir o menu Fiscal."></textarea>
            <div class="actions"><button class="primary" onclick={adicionarLicao} disabled={novaLicao.trim().length < 10}>Adicionar (já aprovada)</button></div>
        </section>
    {:else}
        <section class="section">
            <h2>Tempos de espera</h2>
            <p class="muted">
                O programa mede quanto cada operação demora e usa esse histórico para escolher o tempo máximo de espera da
                próxima vez: 95% das execuções recentes, mais 50% de folga. Esperas que estouraram contam como o tempo que
                estouraram, então o limite sobe sozinho. Padrão: nunca abaixo do timeout das Configurações ({tempos.configurado}s).
            </p>

            <label class="check"><input type="checkbox" bind:checked={automatico} /> Ajustar automaticamente pelo histórico</label>
            <div class="row">
                <label>Mínimo (s)
                    <input type="number" min="5" bind:value={minimo} placeholder={`${tempos.minimoEfetivo ?? ''}`} />
                </label>
                <label>Máximo (s)
                    <input type="number" min="5" bind:value={maximo} placeholder="120" />
                </label>
            </div>
            <div class="actions"><button class="primary" onclick={salvarTempos}>Salvar</button></div>

            <h3>Medições</h3>
            {#if tempos.operacoes.length}
                <table>
                    <thead>
                        <tr><th>Operação</th><th>Execuções</th><th>Estouros</th><th>Mediana</th><th>95%</th><th>Tempo usado</th></tr>
                    </thead>
                    <tbody>
                        {#each tempos.operacoes as o}
                            <tr>
                                <td>{nomeOperacao(o.operacao)}</td>
                                <td>{o.execucoes}</td>
                                <td>{o.timeouts}</td>
                                <td>{o.medianaSeg ?? '-'}s</td>
                                <td>{o.p95Seg ?? '-'}s</td>
                                <td><strong>{o.timeoutAtualSeg}s</strong>{#if o.amostras < 5} <span class="muted small">(poucos dados: usando o padrão)</span>{/if}</td>
                            </tr>
                        {/each}
                    </tbody>
                </table>
                <button class="secondary" onclick={esquecerTempos}>Zerar histórico</button>
            {:else}
                <p class="muted"><em>Ainda sem medições. Elas aparecem depois da primeira execução.</em></p>
            {/if}
        </section>
    {/if}
</div>

<style>
    .page-container { background: white; color: #333; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; padding-bottom: 3rem; max-width: 900px; }
    .content-header { margin-bottom: 1rem; padding-bottom: 1rem; border-bottom: 1px solid #e1e5e9; }
    .content-header h1 { margin: 0; font-size: 1.5rem; font-weight: 600; }
    .tabs { display: flex; gap: .25rem; border-bottom: 2px solid #e1e5e9; margin-bottom: 1rem; }
    .tabs button { background: none; border: none; padding: .6rem 1rem; cursor: pointer; font-size: .95rem; color: #666; border-bottom: 3px solid transparent; margin-bottom: -2px; }
    .tabs button.active { color: #4f46e5; border-bottom-color: #4f46e5; font-weight: 600; }
    .badge { background: #ef4444; color: #fff; border-radius: 10px; padding: 0 .45rem; font-size: .75rem; margin-left: .25rem; }
    .banner { background: #ecfdf5; border: 1px solid #10b981; color: #065f46; padding: .6rem .9rem; border-radius: 6px; margin-bottom: 1rem; }
    .banner.error { background: #fef2f2; border-color: #ef4444; color: #991b1b; }
    .section { display: flex; flex-direction: column; gap: .6rem; margin-bottom: 2rem; }
    h2 { margin: 0; font-size: 1.15rem; }
    h3 { margin: 1rem 0 0; font-size: 1rem; }
    .muted { color: #666; font-size: .875rem; line-height: 1.4; margin: 0; }
    .small { font-size: .78rem; }
    textarea, input[type='number'] { padding: .6rem; border: 2px solid #e1e5e9; border-radius: 6px; font-size: .9rem; font-family: inherit; }
    textarea { width: 100%; box-sizing: border-box; resize: vertical; }
    textarea:focus, input:focus { outline: none; border-color: #4f46e5; }
    .counter { align-self: flex-end; font-size: .75rem; color: #888; }
    .advanced { border: 1px solid #e1e5e9; border-radius: 6px; padding: .6rem .9rem; }
    .advanced summary { cursor: pointer; font-weight: 600; }
    pre { white-space: pre-wrap; background: #f6f7f9; padding: .8rem; border-radius: 6px; font-size: .78rem; max-height: 24rem; overflow: auto; }
    .row { display: flex; gap: .6rem; flex-wrap: wrap; align-items: flex-end; margin-top: .4rem; }
    .row label { display: flex; flex-direction: column; gap: .25rem; font-size: .85rem; font-weight: 600; }
    .check { display: flex; gap: .5rem; align-items: center; font-weight: 600; }
    .actions { display: flex; margin-top: .5rem; }
    button.primary { background: #4f46e5; color: #fff; border: none; padding: .55rem 1.2rem; border-radius: 6px; cursor: pointer; font-size: .9rem; }
    button.primary:hover:not(:disabled) { background: #4338ca; }
    button.primary:disabled { background: #9ca3af; cursor: not-allowed; }
    button.secondary { background: #fff; color: #333; border: 1px solid #c7ccd4; padding: .45rem .9rem; border-radius: 6px; cursor: pointer; font-size: .85rem; align-self: flex-start; }
    button.secondary:hover { background: #f3f4f6; }
    .steps { margin: .2rem 0; padding-left: 1.4rem; }
    .lesson { border: 1px solid #e1e5e9; border-left-width: 4px; border-radius: 6px; padding: .6rem .9rem; display: flex; flex-direction: column; gap: .3rem; }
    .lesson p { margin: 0; }
    .lesson.pending { border-left-color: #f59e0b; background: #fffbeb; }
    .lesson.approved { border-left-color: #10b981; }
    .lesson.rejected { border-left-color: #9ca3af; opacity: .8; }
    table { border-collapse: collapse; width: 100%; font-size: .875rem; }
    th, td { text-align: left; padding: .45rem .6rem; border-bottom: 1px solid #e1e5e9; }
    th { background: #f6f7f9; font-weight: 600; }
</style>

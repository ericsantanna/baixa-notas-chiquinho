<script>
    import { onMount } from 'svelte';
    import { browser } from '$app/environment';

    const API = 'http://localhost:9001/api/metricas';

    let isLoading = $state(true);
    let message = $state(null); // { type: 'ok' | 'error', text }
    let dados = $state(null);
    let detalhe = $state(null); // { resumo, franqueadoras }
    let carregandoDetalhe = $state(false);
    let filtroDetalhe = $state('problemas');
    let precoEntrada = $state('');
    let precoSaida = $state('');

    const NOMES_DESFECHO = {
        COMPLETED: 'Concluída',
        COMPLETED_WITH_ERRORS: 'Com pendências',
        FAILED: 'Falhou',
        INTERRUPTED: 'Interrompida',
        DESCONHECIDO: 'Sem resultado'
    };
    const NOMES_CATEGORIA = {
        SEM_RESULTADO: 'Sem notas no período',
        MES_ANO_NAO_ENCONTRADO: 'Mês ou ano não disponível',
        FALHA_TECNICA: 'Falha técnica (esgotou as tentativas)',
        EXTRACAO: 'Erro ao extrair/validar o arquivo',
        DOWNLOAD: 'Download não encontrado',
        ABORTADA: 'Execução abortada',
        SEM_RESULTADO_FINAL: 'Sem resultado ao final (verificação)',
        OUTRO: 'Outro motivo'
    };
    const NOMES_STATUS = {
        PROCESSED: 'Processada',
        PREVIOUSLY_PROCESSED: 'Já processada antes',
        NOT_PROCESSED: 'Não processada',
        PENDING: 'Pendente',
        PROCESSING: 'Interrompida no meio'
    };
    const NOMES_VIA = { script: 'Script', agente: 'Agente (IA)', playbook: 'Agente (caminho aprendido)' };
    const NOMES_MODO = { OFF: 'Desligado', FALLBACK: 'Reforço', FULL: 'Completo' };

    const agregado = $derived(dados?.agregado);
    const execucoes = $derived(dados?.execucoes ?? []);
    const cronologico = $derived([...execucoes].reverse()); // oldest first, for the charts
    const maxDuracao = $derived(Math.max(1, ...cronologico.map((e) => e.duracaoSeg || 0)));
    const maxFranq = $derived(Math.max(1, ...cronologico.map((e) => e.processadas + e.semNotas + e.falhasTecnicas)));
    const maxCategoria = $derived(Math.max(1, ...(dados?.porCategoria ?? []).map((c) => c.total)));
    const franqueadorasDetalhe = $derived(
        (detalhe?.franqueadoras ?? []).filter((f) =>
            filtroDetalhe === 'todas' ? true : f.falhaTecnica || f.retentativas > 0 || f.agenteUsado || f.status === 'NOT_PROCESSED'
        )
    );

    function fmtDuracao(seg) {
        if (seg === null || seg === undefined) return '-';
        seg = Math.round(seg);
        const h = Math.floor(seg / 3600);
        const m = Math.floor((seg % 3600) / 60);
        const s = seg % 60;
        if (h > 0) return `${h}h ${String(m).padStart(2, '0')}min`;
        if (m > 0) return `${m}min ${String(s).padStart(2, '0')}s`;
        return `${s}s`;
    }
    function fmtMs(ms) {
        return ms === null || ms === undefined ? '-' : fmtDuracao(ms / 1000);
    }
    function fmtNum(n) {
        return n === null || n === undefined ? '-' : Number(n).toLocaleString('pt-BR');
    }
    function fmtDec(n, casas = 1) {
        return n === null || n === undefined ? '-' : Number(n).toLocaleString('pt-BR', { minimumFractionDigits: casas, maximumFractionDigits: casas });
    }
    function fmtPct(n) {
        return n === null || n === undefined ? '-' : `${fmtDec(n, 1)}%`;
    }
    function fmtUsd(v) {
        if (v === null || v === undefined) return '-';
        return `US$ ${Number(v).toLocaleString('pt-BR', { minimumFractionDigits: 2, maximumFractionDigits: v < 1 ? 4 : 2 })}`;
    }
    function fmtBytes(b) {
        if (!b) return '-';
        const un = ['B', 'KB', 'MB', 'GB'];
        let i = 0;
        let v = b;
        while (v >= 1024 && i < un.length - 1) { v /= 1024; i++; }
        return `${v.toLocaleString('pt-BR', { maximumFractionDigits: 1 })} ${un[i]}`;
    }
    function fmtData(iso) {
        if (!iso) return '-';
        const [d, t] = iso.split('T');
        const [a, m, dia] = d.split('-');
        return `${dia}/${m}/${a} ${t?.slice(0, 5) ?? ''}`;
    }

    function avisar(type, text) {
        message = { type, text };
        if (type === 'ok') setTimeout(() => { if (message?.text === text) message = null; }, 4000);
    }

    async function api(path, options = {}) {
        const response = await fetch(`${API}${path}`, { headers: { 'Content-Type': 'application/json' }, ...options });
        let body = null;
        try { body = await response.json(); } catch (e) { /* sem corpo */ }
        if (!response.ok) throw new Error(body?.erro || `Erro ${response.status}`);
        return body;
    }

    function aplicar(d) {
        dados = d;
        precoEntrada = d.precos.entradaPorMilhao ?? '';
        precoSaida = d.precos.saidaPorMilhao ?? '';
    }

    async function carregar() {
        if (!browser) return;
        try {
            isLoading = true;
            aplicar(await api('/resumo'));
        } catch (e) {
            avisar('error', `Não foi possível carregar: ${e.message}`);
        } finally {
            isLoading = false;
        }
    }

    async function abrirDetalhe(id) {
        if (detalhe?.resumo.id === id) { detalhe = null; return; }
        try {
            carregandoDetalhe = true;
            detalhe = await api(`/execucoes/${id}`);
        } catch (e) {
            avisar('error', e.message);
        } finally {
            carregandoDetalhe = false;
        }
    }

    async function excluir(id) {
        if (!confirm('Excluir esta execução do histórico? Isso não apaga nenhum arquivo baixado.')) return;
        try {
            aplicar(await api(`/execucoes/${id}`, { method: 'DELETE' }));
            if (detalhe?.resumo.id === id) detalhe = null;
            avisar('ok', 'Execução removida do histórico.');
        } catch (e) {
            avisar('error', e.message);
        }
    }

    async function salvarPrecos() {
        try {
            aplicar(await api('/precos', {
                method: 'PUT',
                body: JSON.stringify({
                    entradaPorMilhao: precoEntrada === '' ? null : Number(precoEntrada),
                    saidaPorMilhao: precoSaida === '' ? null : Number(precoSaida)
                })
            }));
            avisar('ok', 'Preços salvos. Os custos foram recalculados.');
        } catch (e) {
            avisar('error', e.message);
        }
    }

    onMount(carregar);
</script>

<svelte:head>
    <title>Métricas - Baixa Notas Chiquinho</title>
</svelte:head>

<div class="page-container">
    <div class="content-header">
        <h1>Métricas</h1>
        <button class="secondary" onclick={carregar}>Atualizar</button>
    </div>

    {#if message}
        <div class="banner" class:error={message.type === 'error'} role="status">{message.text}</div>
    {/if}

    {#if isLoading}
        <p class="muted">Carregando...</p>
    {:else if !agregado || agregado.execucoes === 0}
        <section class="section">
            <p><em>Ainda não há execuções registradas.</em></p>
            <p class="muted">
                A partir da próxima execução, cada uma (concluída, interrompida ou com falha) passa a aparecer aqui com tempo,
                tentativas, uso do agente de IA e custo estimado.
            </p>
        </section>
    {:else}
        <section class="cards">
            <div class="card">
                <span class="label">Execuções registradas</span>
                <strong>{fmtNum(agregado.execucoes)}</strong>
                <span class="hint">{fmtNum(agregado.franqueadorasTentadas)} franqueadoras trabalhadas no total</span>
            </div>
            <div class="card">
                <span class="label">Duração média por execução</span>
                <strong>{fmtDuracao(agregado.duracaoMediaSeg)}</strong>
                <span class="hint">do início ao fim, incluindo a extração dos arquivos</span>
            </div>
            <div class="card">
                <span class="label">Tempo médio por franqueadora</span>
                <strong>{fmtDuracao(agregado.mediaSegPorFranq)}</strong>
                <span class="hint">navegação + download (a extração roda em paralelo e não entra)</span>
            </div>
            <div class="card" class:bad={agregado.falhasTecnicas > 0}>
                <span class="label">Sucesso técnico</span>
                <strong>{fmtPct(agregado.taxaSucessoTecnico)}</strong>
                <span class="hint">{fmtNum(agregado.falhasTecnicas)} falha(s) técnica(s). "Sem notas no período" não é falha.</span>
            </div>
            <div class="card">
                <span class="label">Retentativas por execução</span>
                <strong>{fmtDec(agregado.retentativasPorExecucao, 1)}</strong>
                <span class="hint">{fmtDec(agregado.retentativasPorFranq, 2)} por franqueadora; {fmtNum(agregado.franqSemRetentativa)} acertaram de primeira</span>
            </div>
            <div class="card">
                <span class="label">Tokens gastos pela IA</span>
                <strong>{fmtNum(agregado.tokens)}</strong>
                <span class="hint">entrada {fmtNum(agregado.tokensEntrada)} · saída {fmtNum(agregado.tokensSaida)} · {fmtNum(agregado.chamadasLlm)} chamadas</span>
            </div>
            <div class="card">
                <span class="label">Custo estimado da IA</span>
                <strong>{fmtUsd(agregado.custoTotalUsd)}</strong>
                <span class="hint">
                    {#if agregado.custoTotalUsd === null}informe os preços em "Custo da IA" abaixo
                    {:else}média de {fmtUsd(agregado.custoMedioPorExecucaoUsd)} por execução{/if}
                </span>
            </div>
            <div class="card">
                <span class="label">Agente de IA acionado</span>
                <strong>{fmtPct(agregado.agenteAcionadoPct)}</strong>
                <span class="hint">{fmtNum(agregado.agenteAcionado)} franqueadora(s); chegou na tela em {fmtNum(agregado.agenteNavegou)}</span>
            </div>
            <div class="card">
                <span class="label">Notas baixadas</span>
                <strong>{fmtNum(agregado.xmlTotal)} XMLs</strong>
                <span class="hint">{fmtBytes(agregado.zipBytesTotal)} em arquivos zip</span>
            </div>
        </section>

        <section class="section">
            <h2>Evolução</h2>
            <div class="charts">
                <div class="chart">
                    <h3>Duração de cada execução</h3>
                    <svg viewBox="0 0 {Math.max(cronologico.length * 28, 420)} 110" preserveAspectRatio="xMinYMax meet" role="img" aria-label="Duração por execução">
                        {#each cronologico as e, i}
                            {@const h = ((e.duracaoSeg || 0) / maxDuracao) * 90}
                            <rect x={i * 28 + 4} y={100 - h} width="20" height={h} fill="#4f46e5" rx="2">
                                <title>{fmtData(e.inicio)} · {fmtDuracao(e.duracaoSeg)}</title>
                            </rect>
                        {/each}
                    </svg>
                    <p class="muted small">Mais antiga à esquerda. Passe o mouse sobre uma barra.</p>
                </div>
                <div class="chart">
                    <h3>Resultado por execução</h3>
                    <svg viewBox="0 0 {Math.max(cronologico.length * 28, 420)} 110" preserveAspectRatio="xMinYMax meet" role="img" aria-label="Resultado por execução">
                        {#each cronologico as e, i}
                            {@const hp = (e.processadas / maxFranq) * 90}
                            {@const hs = (e.semNotas / maxFranq) * 90}
                            {@const hf = (e.falhasTecnicas / maxFranq) * 90}
                            <rect x={i * 28 + 4} y={100 - hp} width="20" height={hp} fill="#10b981"><title>{fmtData(e.inicio)} · {e.processadas} processadas</title></rect>
                            <rect x={i * 28 + 4} y={100 - hp - hs} width="20" height={hs} fill="#9ca3af"><title>{fmtData(e.inicio)} · {e.semNotas} sem notas</title></rect>
                            <rect x={i * 28 + 4} y={100 - hp - hs - hf} width="20" height={hf} fill="#ef4444"><title>{fmtData(e.inicio)} · {e.falhasTecnicas} falhas técnicas</title></rect>
                        {/each}
                    </svg>
                    <p class="muted small"><span class="dot ok"></span> processadas <span class="dot gray"></span> sem notas <span class="dot bad"></span> falha técnica</p>
                </div>
            </div>
        </section>

        <section class="section">
            <h2>Histórico de execuções</h2>
            <div class="table-wrap">
                <table>
                    <thead>
                        <tr>
                            <th>Início</th><th>Período</th><th>Desfecho</th><th>Duração</th>
                            <th>Processadas</th><th>Sem notas</th><th>Falhas</th><th>Retentativas</th><th>Tokens</th><th>Custo</th><th></th>
                        </tr>
                    </thead>
                    <tbody>
                        {#each execucoes as e (e.id)}
                            <tr class:selected={detalhe?.resumo.id === e.id}>
                                <td>{fmtData(e.inicio)}</td>
                                <td>{e.mes}/{e.ano}<br /><span class="muted small">IA: {NOMES_MODO[e.modoAgente] ?? '-'}</span></td>
                                <td><span class="pill {e.desfecho}">{NOMES_DESFECHO[e.desfecho] ?? e.desfecho}</span></td>
                                <td class="nowrap">{fmtDuracao(e.duracaoSeg)}</td>
                                <td>{e.processadas}{#if e.jaProcessadas} <span class="muted small">(+{e.jaProcessadas} antes)</span>{/if}</td>
                                <td>{e.semNotas}</td>
                                <td class:badtext={e.falhasTecnicas > 0}>{e.falhasTecnicas}</td>
                                <td>{e.retentativas}</td>
                                <td>{e.tokens ? fmtNum(e.tokens) : '-'}</td>
                                <td>{e.tokens ? fmtUsd(e.custoUsd) : '-'}</td>
                                <td class="actions">
                                    <button class="secondary" onclick={() => abrirDetalhe(e.id)}>{detalhe?.resumo.id === e.id ? 'Fechar' : 'Ver'}</button>
                                    <button class="secondary danger" title="Excluir do histórico" aria-label="Excluir do histórico" onclick={() => excluir(e.id)}>✕</button>
                                </td>
                            </tr>
                        {/each}
                    </tbody>
                </table>
            </div>
            <p class="muted small">
                Retentativa = cada tentativa depois da primeira. "Sem notas" = a franqueadora não tinha notas no mês/ano (não é falha).
                Falha = erro técnico que precisa de atenção. Guardamos as últimas 120 execuções.
            </p>

            {#if carregandoDetalhe}<p class="muted">Carregando detalhes...</p>{/if}
            {#if detalhe}
                <div class="detail">
                    <div class="detail-head">
                        <h3>{detalhe.resumo.mes}/{detalhe.resumo.ano} · {fmtData(detalhe.resumo.inicio)}</h3>
                        <select bind:value={filtroDetalhe}>
                            <option value="problemas">Só as que tiveram problema, retentativa ou agente</option>
                            <option value="todas">Todas as franqueadoras</option>
                        </select>
                    </div>
                    {#if detalhe.resumo.erro}<div class="banner error">Erro da execução: {detalhe.resumo.erro}</div>{/if}
                    <p class="muted small">
                        {detalhe.resumo.total} franqueadoras · tempo médio {fmtDuracao(detalhe.resumo.mediaSegPorFranq)} ·
                        {fmtNum(detalhe.resumo.xmlTotal)} XMLs ({fmtBytes(detalhe.resumo.zipBytesTotal)}) ·
                        {fmtNum(detalhe.resumo.chamadasLlm)} chamadas à IA
                    </p>
                    <div class="table-wrap">
                        <table>
                            <thead>
                                <tr><th>Franqueadora</th><th>Situação</th><th>Tentativas</th><th>Como chegou</th><th>Tempo</th><th>Tokens</th><th>Custo</th><th>XMLs</th><th>Motivo</th></tr>
                            </thead>
                            <tbody>
                                {#each franqueadorasDetalhe as f (f.nome)}
                                    <tr>
                                        <td>{f.nome}</td>
                                        <td><span class="pill {f.status}">{NOMES_STATUS[f.status] ?? f.status}</span></td>
                                        <td>{f.tentativas || '-'}{#if f.retentativas > 0} <span class="muted small">({f.retentativas} retent.)</span>{/if}</td>
                                        <td>{NOMES_VIA[f.via] ?? '-'}</td>
                                        <td>{fmtMs(f.duracaoMs)}</td>
                                        <td>{f.tokensEntrada + f.tokensSaida > 0 ? fmtNum(f.tokensEntrada + f.tokensSaida) : '-'}</td>
                                        <td>{f.tokensEntrada + f.tokensSaida > 0 ? fmtUsd(f.custoUsd) : '-'}</td>
                                        <td>{f.xmlCount ?? '-'}</td>
                                        <td class="motivo">{f.motivo ?? ''}</td>
                                    </tr>
                                {:else}
                                    <tr><td colspan="9" class="muted"><em>Nenhuma franqueadora neste filtro.</em></td></tr>
                                {/each}
                            </tbody>
                        </table>
                    </div>
                </div>
            {/if}
        </section>

        <section class="section two-col">
            <div>
                <h2>Mais lentas</h2>
                <p class="muted small">Tempo médio de navegação + download, entre todas as execuções.</p>
                {#if dados.maisLentas.length}
                    <table>
                        <thead><tr><th>Franqueadora</th><th>Média</th><th>Pior</th><th>Vezes</th></tr></thead>
                        <tbody>
                            {#each dados.maisLentas as f}
                                <tr><td>{f.nome}</td><td>{fmtDuracao(f.mediaSeg)}</td><td>{fmtDuracao(f.maxSeg)}</td><td>{f.execucoes}</td></tr>
                            {/each}
                        </tbody>
                    </table>
                {:else}<p class="muted"><em>Sem dados.</em></p>{/if}
            </div>
            <div>
                <h2>Mais problemáticas</h2>
                <p class="muted small">Falhas técnicas (peso 3) e retentativas (peso 1) somadas entre as execuções.</p>
                {#if dados.maisProblematicas.length}
                    <table>
                        <thead><tr><th>Franqueadora</th><th>Falhas</th><th>Retent.</th><th>Vezes</th></tr></thead>
                        <tbody>
                            {#each dados.maisProblematicas as f}
                                <tr><td>{f.nome}</td><td class:badtext={f.falhasTecnicas > 0}>{f.falhasTecnicas}</td><td>{f.retentativas}</td><td>{f.execucoes}</td></tr>
                            {/each}
                        </tbody>
                    </table>
                {:else}<p class="muted"><em>Nenhuma franqueadora teve problema. 🎉</em></p>{/if}
            </div>
        </section>

        <section class="section two-col">
            <div>
                <h2>Por que não foram processadas</h2>
                {#if dados.porCategoria.length}
                    {#each dados.porCategoria as c}
                        <div class="bar-row">
                            <span class="bar-label">{NOMES_CATEGORIA[c.categoria] ?? c.categoria}</span>
                            <span class="bar"><span class="fill" class:tec={c.tecnica} style="width: {(c.total / maxCategoria) * 100}%"></span></span>
                            <span class="bar-value">{c.total}</span>
                        </div>
                    {/each}
                    <p class="muted small">Em vermelho: motivos técnicos. Em cinza: a franqueadora simplesmente não tinha notas.</p>
                {:else}<p class="muted"><em>Nenhuma franqueadora ficou sem processar.</em></p>{/if}
            </div>
            <div>
                <h2>Como chegaram na tela</h2>
                <div class="bar-row"><span class="bar-label">Script</span><span class="bar"><span class="fill" style="width: {agregado.franqueadorasTentadas ? (agregado.viaScript / agregado.franqueadorasTentadas) * 100 : 0}%"></span></span><span class="bar-value">{agregado.viaScript}</span></div>
                <div class="bar-row"><span class="bar-label">Agente (IA)</span><span class="bar"><span class="fill" style="width: {agregado.franqueadorasTentadas ? (agregado.viaAgente / agregado.franqueadorasTentadas) * 100 : 0}%"></span></span><span class="bar-value">{agregado.viaAgente}</span></div>
                <div class="bar-row"><span class="bar-label">Caminho aprendido</span><span class="bar"><span class="fill" style="width: {agregado.franqueadorasTentadas ? (agregado.viaPlaybook / agregado.franqueadorasTentadas) * 100 : 0}%"></span></span><span class="bar-value">{agregado.viaPlaybook}</span></div>
                <p class="muted small">"Caminho aprendido" = o agente repetiu o que já tinha funcionado, sem gastar tokens.</p>
            </div>
        </section>
    {/if}

    {#if !isLoading}
        <section class="section">
            <h2>Custo da IA</h2>
            <p class="muted">
                O custo é uma <strong>estimativa</strong>: tokens de entrada e de saída vezes o preço por milhão de tokens do modelo.
                Consulte os preços atuais do modelo na página de preços da Anthropic e informe abaixo (em dólares).
                O consumo real aparece no Console da Anthropic.
            </p>
            <div class="row">
                <label>Entrada (US$ por milhão de tokens)
                    <input type="number" min="0" step="0.01" bind:value={precoEntrada} placeholder="ex.: 1,00" />
                </label>
                <label>Saída (US$ por milhão de tokens)
                    <input type="number" min="0" step="0.01" bind:value={precoSaida} placeholder="ex.: 5,00" />
                </label>
                <button class="primary" onclick={salvarPrecos}>Salvar preços</button>
            </div>
        </section>
    {/if}
</div>

<style>
    .page-container { background: white; color: #333; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; padding-bottom: 3rem; max-width: 1100px; }
    .content-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 1rem; padding-bottom: 1rem; border-bottom: 1px solid #e1e5e9; }
    .content-header h1 { margin: 0; font-size: 1.5rem; font-weight: 600; }
    .banner { background: #ecfdf5; border: 1px solid #10b981; color: #065f46; padding: .6rem .9rem; border-radius: 6px; margin-bottom: 1rem; }
    .banner.error { background: #fef2f2; border-color: #ef4444; color: #991b1b; }
    .section { display: flex; flex-direction: column; gap: .6rem; margin-bottom: 2rem; }
    h2 { margin: 0; font-size: 1.15rem; }
    h3 { margin: 0; font-size: .95rem; }
    .muted { color: #666; font-size: .875rem; line-height: 1.4; margin: 0; }
    .small { font-size: .78rem; }
    .cards { display: grid; grid-template-columns: repeat(auto-fit, minmax(210px, 1fr)); gap: .8rem; margin-bottom: 2rem; }
    .card { border: 1px solid #e1e5e9; border-radius: 8px; padding: .8rem 1rem; display: flex; flex-direction: column; gap: .2rem; background: #fafbfc; }
    .card.bad { border-color: #fca5a5; background: #fef2f2; }
    .card .label { font-size: .8rem; color: #555; font-weight: 600; }
    .card strong { font-size: 1.5rem; color: #1f2937; }
    .card .hint { font-size: .75rem; color: #777; line-height: 1.3; }
    .charts { display: grid; grid-template-columns: 1fr 1fr; gap: 1rem; }
    .chart { border: 1px solid #e1e5e9; border-radius: 8px; padding: .8rem 1rem; display: flex; flex-direction: column; gap: .4rem; overflow-x: auto; }
    .chart svg { width: 100%; height: 120px; }
    .dot { display: inline-block; width: .6rem; height: .6rem; border-radius: 50%; margin: 0 .2rem 0 .6rem; }
    .dot.ok { background: #10b981; } .dot.gray { background: #9ca3af; } .dot.bad { background: #ef4444; }
    .table-wrap { overflow-x: auto; }
    table { border-collapse: collapse; width: 100%; font-size: .85rem; }
    th, td { text-align: left; padding: .4rem .45rem; border-bottom: 1px solid #e1e5e9; vertical-align: top; }
    th { background: #f6f7f9; font-weight: 600; white-space: nowrap; }
    tr.selected { background: #eef2ff; }
    td.actions { white-space: nowrap; display: flex; gap: .3rem; align-items: center; }
    td:first-child, td.nowrap { white-space: nowrap; }
    td.motivo { max-width: 22rem; font-size: .78rem; color: #555; }
    .badtext { color: #b91c1c; font-weight: 600; }
    .pill { display: inline-block; padding: .1rem .5rem; border-radius: 10px; font-size: .75rem; background: #e5e7eb; white-space: nowrap; }
    .pill.COMPLETED, .pill.PROCESSED { background: #d1fae5; color: #065f46; }
    .pill.COMPLETED_WITH_ERRORS, .pill.NOT_PROCESSED { background: #fef3c7; color: #92400e; }
    .pill.FAILED { background: #fee2e2; color: #991b1b; }
    .detail { border: 1px solid #c7d2fe; border-radius: 8px; padding: .8rem 1rem; display: flex; flex-direction: column; gap: .5rem; background: #fbfbff; }
    .detail-head { display: flex; justify-content: space-between; align-items: center; gap: 1rem; flex-wrap: wrap; }
    select, input[type='number'] { padding: .45rem .6rem; border: 2px solid #e1e5e9; border-radius: 6px; font-size: .85rem; font-family: inherit; }
    .two-col { display: grid; grid-template-columns: 1fr 1fr; gap: 1.5rem; align-items: start; }
    .two-col > div { display: flex; flex-direction: column; gap: .5rem; }
    .bar-row { display: grid; grid-template-columns: 14rem 1fr 2.5rem; gap: .5rem; align-items: center; font-size: .85rem; }
    .bar { background: #eef0f3; border-radius: 4px; height: .8rem; overflow: hidden; }
    .fill { display: block; height: 100%; background: #9ca3af; }
    .fill.tec { background: #ef4444; }
    .bar-value { text-align: right; font-weight: 600; }
    .row { display: flex; gap: .8rem; flex-wrap: wrap; align-items: flex-end; }
    .row label { display: flex; flex-direction: column; gap: .25rem; font-size: .85rem; font-weight: 600; }
    button.primary { background: #4f46e5; color: #fff; border: none; padding: .55rem 1.2rem; border-radius: 6px; cursor: pointer; font-size: .9rem; }
    button.primary:hover { background: #4338ca; }
    button.secondary { background: #fff; color: #333; border: 1px solid #c7ccd4; padding: .35rem .8rem; border-radius: 6px; cursor: pointer; font-size: .8rem; }
    button.secondary:hover { background: #f3f4f6; }
    button.danger { color: #b91c1c; border-color: #fca5a5; }
    @media (max-width: 900px) { .charts, .two-col { grid-template-columns: 1fr; } .bar-row { grid-template-columns: 9rem 1fr 2rem; } }
</style>

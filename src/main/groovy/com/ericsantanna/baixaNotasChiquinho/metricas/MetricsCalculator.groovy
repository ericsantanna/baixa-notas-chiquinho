package com.ericsantanna.baixaNotasChiquinho.metricas

/**
 * All the numbers shown on the "Métricas" page, computed from the stored execution documents.
 *
 * Definitions (the UI repeats them next to each number):
 *  - tentada: a franchise whose attempts actually ran in that execution (not skipped as already processed)
 *  - retentativa: every attempt after the first one for a franchise (a franchise that failed all 4 attempts = 3)
 *  - sem notas: NOT_PROCESSED for a business reason (no results / month or year not available): not a failure
 *  - falha técnica: any other NOT_PROCESSED: this is what the operator needs to look at
 *  - duração por franqueadora: navigation + download only; extraction runs in the background and is not included
 */
class MetricsCalculator {
    static final List<String> CATEGORIAS_SEM_NOTAS = ['SEM_RESULTADO', 'MES_ANO_NAO_ENCONTRADO']

    /** Failure category from the message stored in nao-processados.csv / the status list. */
    static String categorizar(String motivo) {
        String m = motivo ?: ''
        if (m.contains('não encontrado para a franqueadora')) return 'MES_ANO_NAO_ENCONTRADO'
        if (m.startsWith('Nenhum resultado encontrado')) return 'SEM_RESULTADO'
        if (m.startsWith('Falha técnica')) return 'FALHA_TECNICA'
        if (m.startsWith('Erro ao extrair') || m.startsWith('Timeout ao aguardar slot')) return 'EXTRACAO'
        if (m.startsWith('Arquivo de download não encontrado')) return 'DOWNLOAD'
        if (m.startsWith('Execução abortada')) return 'ABORTADA'
        if (m.startsWith('Sem resultado ao final')) return 'SEM_RESULTADO_FINAL'
        return 'OUTRO'
    }

    static boolean isFalhaTecnica(Map f) {
        return f.status == 'NOT_PROCESSED' && !(categorizar(f.motivo as String) in CATEGORIAS_SEM_NOTAS)
    }

    static int retentativas(Map f) {
        return Math.max(0, ((f.tentativas ?: 0) as int) - 1)
    }

    static boolean tentada(Map f) {
        return ((f.tentativas ?: 0) as int) > 0
    }

    /** USD estimate, or null when prices were not informed. */
    static Double custo(long tokensEntrada, long tokensSaida, Map precos) {
        if (precos?.entradaPorMilhao == null || precos?.saidaPorMilhao == null) {
            return null
        }
        return tokensEntrada / 1_000_000.0d * (precos.entradaPorMilhao as double) +
                tokensSaida / 1_000_000.0d * (precos.saidaPorMilhao as double)
    }

    /** One row of the execution list. */
    static Map resumo(Map exec, Map precos) {
        List<Map> fs = franqueadoras(exec)
        List<Map> tentadas = fs.findAll { tentada(it) }
        long tin = fs.sum(0L) { (it.tokensEntrada ?: 0) as long } as long
        long tout = fs.sum(0L) { (it.tokensSaida ?: 0) as long } as long
        int processadas = fs.count { it.status == 'PROCESSED' } as int
        int naoProcessadas = fs.count { it.status == 'NOT_PROCESSED' } as int
        int falhasTecnicas = fs.count { isFalhaTecnica(it) } as int
        int retent = tentadas.sum(0) { retentativas(it) } as int
        def comTempo = tentadas.findAll { it.duracaoMs != null }
        return [id                     : exec.id,
                inicio                 : exec.inicio,
                fim                    : exec.fim,
                duracaoSeg             : exec.duracaoSeg,
                mes                    : exec.mes,
                ano                    : exec.ano,
                desfecho               : exec.desfecho,
                erro                   : exec.erro,
                modoAgente             : exec.modoAgente,
                modelo                 : exec.modelo,
                total                  : fs.size(),
                processadas            : processadas,
                jaProcessadas          : fs.count { it.status == 'PREVIOUSLY_PROCESSED' },
                naoProcessadas         : naoProcessadas,
                semNotas               : fs.count { it.status == 'NOT_PROCESSED' && (categorizar(it.motivo as String) in CATEGORIAS_SEM_NOTAS) },
                falhasTecnicas         : falhasTecnicas,
                tentadas               : tentadas.size(),
                retentativas           : retent,
                retentativasPorFranq   : tentadas ? round2(retent / (double) tentadas.size()) : null,
                mediaSegPorFranq       : comTempo ? round1(comTempo.sum { it.duracaoMs as long } / (double) comTempo.size() / 1000.0d) : null,
                franqComAgente         : fs.count { it.agenteUsado == true },
                tokensEntrada          : tin,
                tokensSaida            : tout,
                tokens                 : tin + tout,
                chamadasLlm            : fs.sum(0) { (it.chamadasLlm ?: 0) as int },
                custoUsd               : custo(tin, tout, precos),
                xmlTotal               : fs.sum(0) { (it.xmlCount ?: 0) as int },
                zipBytesTotal          : fs.sum(0L) { (it.zipBytes ?: 0) as long }]
    }

    /** Cards at the top of the page, over every stored execution. */
    static Map agregado(List<Map> execs, Map precos) {
        List<Map> resumos = execs.collect { resumo(it, precos) }
        List<Map> todas = execs.collectMany { franqueadoras(it) }
        List<Map> tentadas = todas.findAll { tentada(it) }
        def comTempo = tentadas.findAll { it.duracaoMs != null }
        def execsComDuracao = resumos.findAll { it.duracaoSeg != null }
        def custos = resumos.findAll { it.custoUsd != null }
        long tin = resumos.sum(0L) { it.tokensEntrada as long } as long
        long tout = resumos.sum(0L) { it.tokensSaida as long } as long
        int processadas = todas.count { it.status == 'PROCESSED' } as int
        int naoProc = todas.count { it.status == 'NOT_PROCESSED' } as int
        int falhasTec = todas.count { isFalhaTecnica(it) } as int
        int agenteAcionado = tentadas.count { it.agenteUsado == true } as int
        int agenteNavegou = tentadas.count { it.agenteUsado == true && it.via in ['agente', 'playbook'] } as int
        int totalRetent = tentadas.sum(0) { retentativas(it) } as int
        return [execucoes                 : execs.size(),
                duracaoMediaSeg           : execsComDuracao ? Math.round(execsComDuracao.sum { it.duracaoSeg as long } / (double) execsComDuracao.size()) : null,
                mediaSegPorFranq          : comTempo ? round1(comTempo.sum { it.duracaoMs as long } / (double) comTempo.size() / 1000.0d) : null,
                franqueadorasTentadas     : tentadas.size(),
                processadas               : processadas,
                naoProcessadas            : naoProc,
                falhasTecnicas            : falhasTec,
                taxaSucessoTecnico        : (processadas + naoProc) ? round1(100.0d * (processadas + naoProc - falhasTec) / (processadas + naoProc)) : null,
                retentativasTotal         : totalRetent,
                retentativasPorExecucao   : execs ? round2(totalRetent / (double) execs.size()) : null,
                retentativasPorFranq      : tentadas ? round2(totalRetent / (double) tentadas.size()) : null,
                franqSemRetentativa       : tentadas.count { retentativas(it) == 0 },
                tokensEntrada             : tin,
                tokensSaida               : tout,
                tokens                    : tin + tout,
                chamadasLlm               : resumos.sum(0) { it.chamadasLlm as int },
                custoTotalUsd             : custos ? custos.sum(0.0d) { it.custoUsd as double } : null,
                custoMedioPorExecucaoUsd  : custos ? round4(custos.sum(0.0d) { it.custoUsd as double } / custos.size()) : null,
                agenteAcionado            : agenteAcionado,
                agenteAcionadoPct         : tentadas ? round1(100.0d * agenteAcionado / tentadas.size()) : null,
                agenteNavegou             : agenteNavegou,
                viaScript                 : tentadas.count { it.via == 'script' },
                viaAgente                 : tentadas.count { it.via == 'agente' },
                viaPlaybook               : tentadas.count { it.via == 'playbook' },
                xmlTotal                  : resumos.sum(0) { it.xmlTotal as int },
                zipBytesTotal             : resumos.sum(0L) { it.zipBytesTotal as long }]
    }

    /** Franchises that take longest on average (needs at least one timed attempt). */
    static List<Map> maisLentas(List<Map> execs, int limite = 10) {
        Map<String, List<Map>> porNome = execs.collectMany { franqueadoras(it) }
                .findAll { tentada(it) && it.duracaoMs != null }.groupBy { it.nome as String }
        return porNome.collect { nome, lista ->
            [nome: nome, mediaSeg: round1(lista.sum { it.duracaoMs as long } / (double) lista.size() / 1000.0d),
             maxSeg: round1(lista.max { it.duracaoMs as long }.duracaoMs / 1000.0d), execucoes: lista.size()]
        }.sort { -(it.mediaSeg as double) }.take(limite)
    }

    /** Franchises that fail or need retries most often across executions. */
    static List<Map> maisProblematicas(List<Map> execs, int limite = 10) {
        Map<String, List<Map>> porNome = execs.collectMany { franqueadoras(it) }.findAll { tentada(it) }.groupBy { it.nome as String }
        return porNome.collect { nome, lista ->
            int falhas = lista.count { isFalhaTecnica(it) } as int
            int retent = lista.sum(0) { retentativas(it) } as int
            [nome: nome, falhasTecnicas: falhas, retentativas: retent, execucoes: lista.size(), pontos: falhas * 3 + retent]
        }.findAll { it.pontos > 0 }.sort { a, b -> b.pontos <=> a.pontos ?: a.nome <=> b.nome }.take(limite)
    }

    /** Count of NOT_PROCESSED by category, biggest first. */
    static List<Map> porCategoria(List<Map> execs) {
        Map<String, Integer> counts = [:]
        execs.collectMany { franqueadoras(it) }.findAll { it.status == 'NOT_PROCESSED' }.each {
            String c = categorizar(it.motivo as String)
            counts[c] = (counts[c] ?: 0) + 1
        }
        return counts.collect { c, n -> [categoria: c, total: n, tecnica: !(c in CATEGORIAS_SEM_NOTAS)] }.sort { -(it.total as int) }
    }

    /** Detail of one execution: summary + every franchise with derived fields. */
    static Map detalhe(Map exec, Map precos) {
        List<Map> fs = franqueadoras(exec).collect { f ->
            Double gasto = custo((f.tokensEntrada ?: 0) as long, (f.tokensSaida ?: 0) as long, precos)
            f + [retentativas: retentativas(f),
                 categoria   : f.status == 'NOT_PROCESSED' ? categorizar(f.motivo as String) : null,
                 falhaTecnica: isFalhaTecnica(f),
                 custoUsd    : (f.agenteUsado == true) ? gasto : null]
        }
        return [resumo: resumo(exec, precos), franqueadoras: fs]
    }

    static List<Map> franqueadoras(Map exec) {
        return ((exec.franqueadoras ?: []) as List).findAll { it instanceof Map }.collect { it as Map }
    }

    private static double round1(double v) { Math.round(v * 10) / 10.0d }

    private static double round2(double v) { Math.round(v * 100) / 100.0d }

    private static double round4(double v) { Math.round(v * 10000) / 10000.0d }
}

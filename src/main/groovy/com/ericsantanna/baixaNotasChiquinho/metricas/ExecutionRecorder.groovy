package com.ericsantanna.baixaNotasChiquinho.metricas

import com.ericsantanna.baixaNotasChiquinho.execucao.ExecutionStatus
import org.slf4j.LoggerFactory

import java.time.Duration
import java.time.LocalDateTime

/**
 * Collects what happens during ONE execution (time, attempts, how each franchise was reached, tokens, XML counts)
 * and turns it into the document stored by {@link ExecutionHistoryStore}. Methods are synchronized because the
 * extraction pool reports from other threads. Nothing here may ever break a run: callers wrap {@link #save()}.
 */
class ExecutionRecorder {
    private static final log = LoggerFactory.getLogger(ExecutionRecorder)

    final String id = UUID.randomUUID().toString()
    final String mes
    final String ano
    final String modoAgente
    final String modelo
    final File dir

    private final LocalDateTime inicio = LocalDateTime.now().withNano(0)
    private final long inicioMs = System.currentTimeMillis()
    private final Map<String, Map> registros = new LinkedHashMap<>()

    ExecutionRecorder(String mes, String ano, String modoAgente, String modelo, File dir) {
        this.mes = mes
        this.ano = ano
        this.modoAgente = modoAgente
        this.modelo = modelo
        this.dir = dir
    }

    /** Starts the clock for one franchise (called when its attempts begin). */
    synchronized void begin(String nome) {
        registros[nome] = [tentativas: 0, via: null, agenteUsado: false, viaAgente: null, tokensEntrada: 0L,
                           tokensSaida: 0L, chamadasLlm: 0, inicioMs: System.currentTimeMillis(), duracaoMs: null,
                           xmlCount: null, zipBytes: null]
    }

    /** @param via 'script' | 'agente' | 'playbook' when navigation worked, null when every attempt failed */
    synchronized void endAttempts(String nome, int tentativas, String via) {
        Map r = registros[nome]
        if (r == null) {
            return
        }
        r.tentativas = tentativas
        r.via = via
        r.duracaoMs = System.currentTimeMillis() - (r.inicioMs as long)
    }

    synchronized void addAgentUsage(String nome, long entrada, long saida, int chamadas) {
        Map r = registros[nome]
        if (r == null) {
            return
        }
        r.agenteUsado = true
        r.tokensEntrada = (r.tokensEntrada as long) + entrada
        r.tokensSaida = (r.tokensSaida as long) + saida
        r.chamadasLlm = (r.chamadasLlm as int) + chamadas
    }

    /** How the agent got there on its last successful navigation: 'playbook' (no LLM) or 'agente' (LLM). */
    synchronized void setViaAgente(String nome, String via) {
        registros[nome]?.viaAgente = via
    }

    synchronized String getViaAgente(String nome) {
        return registros[nome]?.viaAgente
    }

    synchronized void addExtraction(String nome, int xmlCount, long zipBytes) {
        Map r = registros[nome]
        if (r != null) {
            r.xmlCount = xmlCount
            r.zipBytes = zipBytes
        }
    }

    /** Final document. Franchise order and final status/message come from the execution's own status list. */
    synchronized Map build(String desfecho, String erro, List<ExecutionStatus> statusList) {
        LocalDateTime fim = LocalDateTime.now().withNano(0)
        def franqueadoras = statusList.collect { ExecutionStatus st ->
            Map r = registros[st.franqueadora] ?: [:]
            [nome         : st.franqueadora,
             status       : st.status,
             motivo       : st.errorMessage?.replace('\n', ' ')?.take(300),
             tentativas   : r.tentativas ?: 0,
             via          : r.via,
             duracaoMs    : r.duracaoMs,
             agenteUsado  : r.agenteUsado ?: false,
             tokensEntrada: r.tokensEntrada ?: 0L,
             tokensSaida  : r.tokensSaida ?: 0L,
             chamadasLlm  : r.chamadasLlm ?: 0,
             xmlCount     : r.xmlCount,
             zipBytes     : r.zipBytes]
        }
        return [id             : id,
                inicio         : inicio.toString(),
                fim            : fim.toString(),
                duracaoSeg     : Duration.between(inicio, fim).seconds,
                mes            : mes,
                ano            : ano,
                desfecho       : desfecho ?: 'DESCONHECIDO',
                erro           : erro?.replace('\n', ' ')?.take(300),
                modoAgente     : modoAgente,
                modelo         : modelo,
                franqueadoras  : franqueadoras]
    }

    /** Never throws. */
    void save(String desfecho, String erro, List<ExecutionStatus> statusList) {
        try {
            new ExecutionHistoryStore(dir).append(build(desfecho, erro, statusList))
        } catch (Throwable e) {
            log.warn "Não foi possível gravar as métricas da execução: ${e.message}"
        }
    }
}

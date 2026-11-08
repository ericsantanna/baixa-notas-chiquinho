package com.ericsantanna.baixaNotasChiquinho.metricas

class MetricsFixtures {
    static Map franq(String nome, Map extra = [:]) {
        [nome: nome, status: 'PROCESSED', motivo: null, tentativas: 1, via: 'script', duracaoMs: 10_000, agenteUsado: false,
         tokensEntrada: 0L, tokensSaida: 0L, chamadasLlm: 0, xmlCount: 100, zipBytes: 1_000_000L] + extra
    }

    /** 5 franchises: clean, retried once, agent-recovered, no results, technical failure. */
    static Map execucao(String id = 'e1', Map extra = [:]) {
        [id: id, inicio: '2026-11-07T18:00:00', fim: '2026-11-07T19:00:00', duracaoSeg: 3600, mes: 'Outubro', ano: '2026',
         desfecho: 'COMPLETED_WITH_ERRORS', erro: null, modoAgente: 'FULL', modelo: 'claude-haiku-5-5',
         franqueadoras: [
                 franq('A'),
                 franq('B', [tentativas: 2, duracaoMs: 40_000]),
                 franq('C', [tentativas: 1, via: 'agente', agenteUsado: true, tokensEntrada: 8000L, tokensSaida: 500L, chamadasLlm: 3, duracaoMs: 60_000]),
                 franq('D', [status: 'NOT_PROCESSED', motivo: 'Nenhum resultado encontrado para D em Outubro de 2026', xmlCount: null, zipBytes: null, duracaoMs: 5_000]),
                 franq('E', [status: 'NOT_PROCESSED', motivo: 'Falha técnica após 4 tentativas (agente FULL) em E: IllegalStateException: x',
                             tentativas: 4, via: null, duracaoMs: 120_000, xmlCount: null, zipBytes: null, agenteUsado: true,
                             tokensEntrada: 2000L, tokensSaida: 100L, chamadasLlm: 1]),
                 franq('F', [status: 'PREVIOUSLY_PROCESSED', tentativas: 0, via: null, duracaoMs: null, xmlCount: null, zipBytes: null])
         ]] + extra
    }
}

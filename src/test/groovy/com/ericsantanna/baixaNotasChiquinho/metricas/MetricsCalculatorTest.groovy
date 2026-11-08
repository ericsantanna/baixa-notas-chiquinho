package com.ericsantanna.baixaNotasChiquinho.metricas

import org.junit.jupiter.api.Test

import static com.ericsantanna.baixaNotasChiquinho.metricas.MetricsFixtures.execucao
import static com.ericsantanna.baixaNotasChiquinho.metricas.MetricsFixtures.franq

class MetricsCalculatorTest {
    static final Map SEM_PRECOS = [entradaPorMilhao: null, saidaPorMilhao: null]
    static final Map PRECOS = [entradaPorMilhao: 1.0d, saidaPorMilhao: 5.0d]

    @Test
    void categorizesEveryMessageTheAutomationWrites() {
        assert MetricsCalculator.categorizar('Nenhum resultado encontrado para X em Outubro de 2026') == 'SEM_RESULTADO'
        assert MetricsCalculator.categorizar('Outubro não encontrado para a franqueadora X') == 'MES_ANO_NAO_ENCONTRADO'
        assert MetricsCalculator.categorizar('2026 não encontrado para a franqueadora X') == 'MES_ANO_NAO_ENCONTRADO'
        assert MetricsCalculator.categorizar('Falha técnica após 4 tentativas em X: Foo: bar') == 'FALHA_TECNICA'
        assert MetricsCalculator.categorizar('Erro ao extrair o arquivo para X: zip corrompido') == 'EXTRACAO'
        assert MetricsCalculator.categorizar('Timeout ao aguardar slot para processar X') == 'EXTRACAO'
        assert MetricsCalculatorTest.cat('Arquivo de download não encontrado para X após 120 segundos') == 'DOWNLOAD'
        assert MetricsCalculator.categorizar('Execução abortada: boom') == 'ABORTADA'
        assert MetricsCalculator.categorizar('Sem resultado ao final da execução (não processada)') == 'SEM_RESULTADO_FINAL'
        assert MetricsCalculator.categorizar('qualquer outra coisa') == 'OUTRO'
        assert MetricsCalculator.categorizar(null) == 'OUTRO'
    }

    static String cat(String m) { MetricsCalculator.categorizar(m) }

    @Test
    void noResultsIsNotATechnicalFailure() {
        assert !MetricsCalculator.isFalhaTecnica(franq('D', [status: 'NOT_PROCESSED', motivo: 'Nenhum resultado encontrado para D']))
        assert MetricsCalculator.isFalhaTecnica(franq('E', [status: 'NOT_PROCESSED', motivo: 'Falha técnica após 4 tentativas']))
        assert !MetricsCalculator.isFalhaTecnica(franq('A'))
    }

    @Test
    void retriesAreAttemptsAfterTheFirst() {
        assert MetricsCalculator.retentativas(franq('A', [tentativas: 1])) == 0
        assert MetricsCalculator.retentativas(franq('A', [tentativas: 4])) == 3
        assert MetricsCalculator.retentativas(franq('A', [tentativas: 0])) == 0
        assert !MetricsCalculator.tentada(franq('F', [tentativas: 0]))
    }

    @Test
    void executionSummary() {
        def r = MetricsCalculator.resumo(execucao(), SEM_PRECOS)

        assert r.total == 6
        assert r.processadas == 3
        assert r.jaProcessadas == 1
        assert r.naoProcessadas == 2
        assert r.semNotas == 1
        assert r.falhasTecnicas == 1
        assert r.tentadas == 5
        assert r.retentativas == 4                    // B: 1, E: 3
        assert r.retentativasPorFranq == 0.8d
        assert r.mediaSegPorFranq == 47.0d            // (10+40+60+5+120)/5
        assert r.franqComAgente == 2
        assert r.tokensEntrada == 10_000L && r.tokensSaida == 600L && r.tokens == 10_600L
        assert r.chamadasLlm == 4
        assert r.xmlTotal == 300
        assert r.zipBytesTotal == 3_000_000L
        assert r.custoUsd == null                     // prices not informed
    }

    @Test
    void costIsOnlyAvailableWithBothPrices() {
        assert MetricsCalculator.custo(1_000_000, 1_000_000, SEM_PRECOS) == null
        assert MetricsCalculator.custo(1_000_000, 1_000_000, [entradaPorMilhao: 1.0d, saidaPorMilhao: null]) == null
        assert MetricsCalculator.custo(1_000_000, 1_000_000, PRECOS) == 6.0d
        assert MetricsCalculator.custo(10_000, 600, PRECOS) == 0.013d
        assert MetricsCalculator.custo(0, 0, PRECOS) == 0.0d
        assert MetricsCalculator.resumo(execucao(), PRECOS).custoUsd == 0.013d
    }

    @Test
    void aggregateAcrossExecutions() {
        def clean = [id: 'e2', inicio: '2026-10-07T18:00:00', fim: '2026-10-07T18:30:00', duracaoSeg: 1800, mes: 'Setembro', ano: '2026',
                     desfecho: 'COMPLETED', modoAgente: 'OFF', modelo: null,
                     franqueadoras: [franq('A'), franq('B'), franq('C')]]

        def a = MetricsCalculator.agregado([execucao(), clean], PRECOS)

        assert a.execucoes == 2
        assert a.duracaoMediaSeg == 2700
        assert a.franqueadorasTentadas == 8
        assert a.retentativasTotal == 4
        assert a.retentativasPorExecucao == 2.0d
        assert a.franqSemRetentativa == 6
        assert a.falhasTecnicas == 1
        assert a.processadas == 6
        assert a.naoProcessadas == 2
        assert a.taxaSucessoTecnico == 87.5d          // (8 outcomes, 1 technical failure)
        assert a.agenteAcionado == 2
        assert a.agenteAcionadoPct == 25.0d
        assert a.agenteNavegou == 1                   // C reached the screen via the agent; E did not
        assert a.viaScript == 6 && a.viaAgente == 1 && a.viaPlaybook == 0   // e1: A, B, D by script + C by agent (E failed); e2: 3 by script
        assert a.tokens == 10_600L
        assert a.custoTotalUsd == 0.013d
        assert a.custoMedioPorExecucaoUsd == 0.0065d
    }

    @Test
    void emptyHistoryYieldsNullsNotErrors() {
        def a = MetricsCalculator.agregado([], SEM_PRECOS)

        assert a.execucoes == 0
        assert a.duracaoMediaSeg == null
        assert a.taxaSucessoTecnico == null
        assert a.custoTotalUsd == null
        assert a.retentativasPorExecucao == null
        assert MetricsCalculator.maisLentas([]).isEmpty()
        assert MetricsCalculator.maisProblematicas([]).isEmpty()
        assert MetricsCalculator.porCategoria([]).isEmpty()
    }

    @Test
    void executionWithOnlySkippedFranchisesDoesNotDivideByZero() {
        def e = [id: 'x', duracaoSeg: 5, franqueadoras: [franq('F', [status: 'PREVIOUSLY_PROCESSED', tentativas: 0, duracaoMs: null])]]

        def r = MetricsCalculator.resumo(e, SEM_PRECOS)

        assert r.tentadas == 0
        assert r.retentativasPorFranq == null
        assert r.mediaSegPorFranq == null
        assert MetricsCalculator.agregado([e], SEM_PRECOS).taxaSucessoTecnico == null
    }

    @Test
    void rankings() {
        def e1 = execucao('e1')
        def e2 = execucao('e2')

        def lentas = MetricsCalculator.maisLentas([e1, e2])
        def problem = MetricsCalculator.maisProblematicas([e1, e2])
        def cat = MetricsCalculator.porCategoria([e1, e2])

        assert lentas[0].nome == 'E' && lentas[0].mediaSeg == 120.0d && lentas[0].execucoes == 2
        assert lentas*.nome.take(3) == ['E', 'C', 'B']
        assert !lentas*.nome.contains('F')                              // skipped franchises have no time
        assert problem[0].nome == 'E' && problem[0].falhasTecnicas == 2 && problem[0].pontos == 12   // 2*3 + 2*3 retries
        assert problem*.nome == ['E', 'B']                              // B: 2 retries
        assert cat[0].total == 2 && cat.find { it.categoria == 'FALHA_TECNICA' }.tecnica
        assert !cat.find { it.categoria == 'SEM_RESULTADO' }.tecnica
    }

    @Test
    void detailAddsDerivedFieldsAndPerFranchiseCost() {
        def d = MetricsCalculator.detalhe(execucao(), PRECOS)

        def e = d.franqueadoras.find { it.nome == 'E' }
        def c = d.franqueadoras.find { it.nome == 'C' }
        def a = d.franqueadoras.find { it.nome == 'A' }
        assert e.falhaTecnica && e.categoria == 'FALHA_TECNICA' && e.retentativas == 3
        assert c.custoUsd == 0.0105d                                   // 8000*1/1M + 500*5/1M
        assert a.custoUsd == null && a.categoria == null
        assert d.resumo.total == 6
    }
}

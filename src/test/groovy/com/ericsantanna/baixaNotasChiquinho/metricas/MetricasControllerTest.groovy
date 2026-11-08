package com.ericsantanna.baixaNotasChiquinho.metricas

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus

import java.nio.file.Files

import static com.ericsantanna.baixaNotasChiquinho.metricas.MetricsFixtures.execucao

class MetricasControllerTest {
    ExecutionHistoryStore store
    MetricasController controller

    @BeforeEach
    void setup() {
        store = new ExecutionHistoryStore(new File(Files.createTempDirectory('metricas-ui').toFile(), 'metricas'))
        controller = new MetricasController(null) {
            @Override protected ExecutionHistoryStore store() { MetricasControllerTest.this.store }
        }
    }

    @Test
    void emptyHistoryReturnsAUsablePayload() {
        def r = controller.resumo()

        assert r.agregado.execucoes == 0
        assert r.execucoes.isEmpty()
        assert r.precos == [entradaPorMilhao: null, saidaPorMilhao: null]
    }

    @Test
    void listsNewestFirstWithRankings() {
        store.append(execucao('antiga', [inicio: '2026-09-07T18:00:00']))
        store.append(execucao('nova', [inicio: '2026-10-07T18:00:00']))

        def r = controller.resumo()

        assert r.execucoes*.id == ['nova', 'antiga']
        assert r.agregado.execucoes == 2
        assert r.maisLentas[0].nome == 'E'
        assert r.maisProblematicas[0].nome == 'E'
        assert r.porCategoria*.categoria.containsAll(['FALHA_TECNICA', 'SEM_RESULTADO'])
    }

    @Test
    void detailAndNotFound() {
        store.append(execucao('a'))

        def ok = controller.detalhe('a')
        assert ok.statusCode == HttpStatus.OK
        assert ok.body.franqueadoras.size() == 6
        assert controller.detalhe('nope').statusCode == HttpStatus.NOT_FOUND
    }

    @Test
    void deleteReturnsTheRefreshedSummary() {
        store.append(execucao('a'))
        store.append(execucao('b'))

        def r = controller.excluir('a')

        assert r.statusCode == HttpStatus.OK
        assert r.body.execucoes*.id == ['b']
        assert controller.excluir('a').statusCode == HttpStatus.NOT_FOUND
    }

    @Test
    void pricesFlowIntoTheCostAndInvalidOnesBecome400() {
        store.append(execucao('a'))
        assert controller.resumo().agregado.custoTotalUsd == null

        def r = controller.salvarPrecos(new MetricasController.PrecosRequest(entradaPorMilhao: 1.0d, saidaPorMilhao: 5.0d))
        assert r.agregado.custoTotalUsd == 0.013d
        assert r.execucoes[0].custoUsd == 0.013d

        def e = org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException) {
            controller.salvarPrecos(new MetricasController.PrecosRequest(entradaPorMilhao: -1.0d, saidaPorMilhao: 5.0d))
        }
        def response = controller.invalido(e)
        assert response.statusCode == HttpStatus.BAD_REQUEST
        assert response.body.erro.contains('maiores ou iguais a zero')
    }
}

package com.ericsantanna.baixaNotasChiquinho.metricas

import com.ericsantanna.baixaNotasChiquinho.automation.agent.AgentBudget
import com.ericsantanna.baixaNotasChiquinho.execucao.ExecutionStatus
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

import java.nio.file.Files

import static com.ericsantanna.baixaNotasChiquinho.metricas.MetricsFixtures.execucao

class ExecutionStoreAndRecorderTest {
    File dir
    ExecutionHistoryStore store

    @BeforeEach
    void setup() {
        dir = new File(Files.createTempDirectory('metricas').toFile(), 'metricas')
        store = new ExecutionHistoryStore(dir)
    }

    // ------------------------------------------------------------------ store

    @Test
    void appendListFindDelete() {
        store.append(execucao('a'))
        store.append(execucao('b'))

        assert store.list()*.id == ['a', 'b']
        assert store.find('b').mes == 'Outubro'
        assert store.delete('a')
        assert !store.delete('a')
        assert store.list()*.id == ['b']
    }

    @Test
    void appendingTheSameIdReplacesInsteadOfDuplicating() {
        store.append(execucao('a', [desfecho: 'FAILED']))
        store.append(execucao('a', [desfecho: 'COMPLETED']))

        assert store.list().size() == 1
        assert store.list()[0].desfecho == 'COMPLETED'
    }

    @Test
    void onlyTheNewestExecutionsAreKept() {
        (1..(ExecutionHistoryStore.MAX_EXECUCOES + 5)).each { store.append(execucao("e${it}".toString())) }

        def ids = store.list()*.id
        assert ids.size() == ExecutionHistoryStore.MAX_EXECUCOES
        assert ids.first() == 'e6'
        assert ids.last() == "e${ExecutionHistoryStore.MAX_EXECUCOES + 5}".toString()
    }

    @Test
    void corruptHistoryFileFallsBackToEmpty() {
        dir.mkdirs()
        new File(dir, 'execucoes.json').text = '{{{ not json'

        assert store.list().isEmpty()
        store.append(execucao('a'))      // and recovers on the next write
        assert store.list()*.id == ['a']
    }

    @Test
    void pricesValidation() {
        assert store.prices() == [entradaPorMilhao: null, saidaPorMilhao: null]

        store.savePrices(1.0d, 5.0d)
        assert store.prices() == [entradaPorMilhao: 1.0d, saidaPorMilhao: 5.0d]
        store.savePrices(null, null)
        assert store.prices().entradaPorMilhao == null

        Assertions.assertThrows(IllegalArgumentException) { store.savePrices(-1.0d, 5.0d) }
        Assertions.assertThrows(IllegalArgumentException) { store.savePrices(1.0d, null) }
    }

    @Test
    void unwritableDirectoryNeverThrows() {
        def blocked = new ExecutionHistoryStore(new File('/proc/not/writable'))

        blocked.append(execucao('a'))

        assert blocked.list().isEmpty()
    }

    // ------------------------------------------------------------------ recorder

    @Test
    void recorderBuildsTheDocumentFromWhatHappened() {
        def rec = new ExecutionRecorder('Outubro', '2026', 'FULL', 'claude-haiku-5-5', dir)
        def status = [new ExecutionStatus('A', 'PROCESSED'),
                      new ExecutionStatus('B', 'NOT_PROCESSED', 'Falha técnica após 4 tentativas em B: x\nsegunda linha'),
                      new ExecutionStatus('C', 'PREVIOUSLY_PROCESSED'),
                      new ExecutionStatus('D', 'PENDING')]

        rec.begin('A'); rec.addExtraction('A', 120, 2_000_000L); rec.endAttempts('A', 1, 'script')
        rec.begin('B'); rec.addAgentUsage('B', 5000, 300, 2); rec.addAgentUsage('B', 1000, 100, 1); rec.endAttempts('B', 4, null)

        def doc = rec.build('COMPLETED_WITH_ERRORS', null, status)

        assert doc.id == rec.id
        assert doc.mes == 'Outubro' && doc.ano == '2026' && doc.modoAgente == 'FULL' && doc.modelo == 'claude-haiku-5-5'
        assert doc.desfecho == 'COMPLETED_WITH_ERRORS'
        assert doc.franqueadoras*.nome == ['A', 'B', 'C', 'D']                       // order of the status list
        def a = doc.franqueadoras[0]; def b = doc.franqueadoras[1]; def c = doc.franqueadoras[2]
        assert a.tentativas == 1 && a.via == 'script' && a.xmlCount == 120 && a.zipBytes == 2_000_000L && a.duracaoMs >= 0
        assert b.tentativas == 4 && b.via == null && b.agenteUsado
        assert b.tokensEntrada == 6000L && b.tokensSaida == 400L && b.chamadasLlm == 3  // accumulated across attempts
        assert !b.motivo.contains('\n')
        assert c.tentativas == 0 && c.duracaoMs == null && !c.agenteUsado                // skipped: no timing
        assert doc.duracaoSeg >= 0
    }

    @Test
    void recorderIgnoresEventsForFranchisesThatNeverStarted() {
        def rec = new ExecutionRecorder('Outubro', '2026', 'OFF', null, dir)

        rec.addAgentUsage('X', 1, 1, 1)
        rec.endAttempts('X', 1, 'script')
        rec.addExtraction('X', 1, 1)

        assert rec.build('COMPLETED', null, [new ExecutionStatus('X', 'PROCESSED')]).franqueadoras[0].tentativas == 0
    }

    @Test
    void recorderSavesInterruptedAndFailedExecutionsToo() {
        def rec = new ExecutionRecorder('Outubro', '2026', 'OFF', null, dir)

        rec.save('INTERRUPTED', null, [new ExecutionStatus('A', 'PENDING')])

        def saved = store.list()
        assert saved.size() == 1
        assert saved[0].desfecho == 'INTERRUPTED'
    }

    @Test
    void recorderSaveNeverThrowsEvenIfTheDiskIsUnavailable() {
        def rec = new ExecutionRecorder('Outubro', '2026', 'OFF', null, new File('/proc/not/writable'))

        rec.save('COMPLETED', null, [])   // must not throw
    }

    @Test
    void recorderDoesNotKeepCredentialsOrAnythingBeyondTheDocumentedFields() {
        def rec = new ExecutionRecorder('Outubro', '2026', 'OFF', null, dir)
        def doc = rec.build('COMPLETED', null, [])

        assert doc.keySet() == ['id', 'inicio', 'fim', 'duracaoSeg', 'mes', 'ano', 'desfecho', 'erro', 'modoAgente', 'modelo', 'franqueadoras'] as Set
    }

    @Test
    void agentBudgetKeepsInputAndOutputApart() {
        def budget = new AgentBudget(1000)
        budget.add(300, 50)
        budget.add(100, 20)

        assert budget.inputUsed == 400 && budget.outputUsed == 70 && budget.used == 470 && budget.calls == 2
        assert !budget.exceeded()
        budget.add(500, 100)
        assert budget.exceeded()
    }
}

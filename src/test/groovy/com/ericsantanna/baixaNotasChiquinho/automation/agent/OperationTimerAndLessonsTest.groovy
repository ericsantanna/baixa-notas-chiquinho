package com.ericsantanna.baixaNotasChiquinho.automation.agent

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

import java.nio.file.Files

class OperationTimerAndLessonsTest {

    /** Same simple name as geb.waiting.WaitTimeoutException, which is what the timer looks for. */
    static class WaitTimeoutException extends RuntimeException {
        WaitTimeoutException(String m) { super(m) }
    }

    private AgentKnowledgeStore newStore() {
        new AgentKnowledgeStore(new File(Files.createTempDirectory('agente').toFile(), 'agente'))
    }

    @Test
    void recordsDurationOfSuccessfulOperationsAndPassesTheTimeout() {
        def store = newStore()
        def timer = new OperationTimer(store, 30)
        int seen = 0

        def result = timer.run('op', null) { int secs -> seen = secs; 'ok' }

        assert result == 'ok'
        assert seen == 30
        assert store.timeoutStats(30)[0].execucoes == 1
    }

    @Test
    void waitTimeoutIsRecordedAsTimedOutAndReportedToTheModel() {
        def store = newStore()
        def timer = new OperationTimer(store, 30)

        def e = Assertions.assertThrows(AgentToolException) {
            timer.run('op', null) { int secs -> throw new RuntimeException('wrapped', new WaitTimeoutException('condition did not pass in 30 seconds')) }
        }

        assert e.message.contains('tempo esgotado (30s)')
        assert e.message.contains('timeout_seconds')
        assert store.timeoutStats(30)[0].timeouts == 1
    }

    @Test
    void otherFailuresAreNotRecordedAndKeepTheirType() {
        def store = newStore()
        def timer = new OperationTimer(store, 30)

        Assertions.assertThrows(IllegalStateException) {
            timer.run('op', null) { int secs -> throw new IllegalStateException('Clique nao selecionou') }
        }

        assert store.timeoutStats(30).isEmpty()
    }

    @Test
    void modelRequestedTimeoutIsBoundedAndWithoutStoreTheConfiguredOneIsUsed() {
        def withStore = new OperationTimer(newStore(), 30)
        def without = new OperationTimer(null, 30)

        assert withStore.timeoutFor('op', 500) == AgentKnowledgeStore.DEFAULT_TIMEOUT_MAX
        assert withStore.timeoutFor('op', 1) == AgentKnowledgeStore.ABSOLUTE_MIN_TIMEOUT
        assert without.timeoutFor('op', null) == 30
        assert without.timeoutFor('op', 500) == AgentKnowledgeStore.DEFAULT_TIMEOUT_MAX
    }

    @Test
    void runnerPassesLearnedOrRequestedTimeoutToTheTool() {
        def store = newStore()
        (1..10).each { store.recordOperation('select_franqueadora', 40_000, false, 30) }   // learned: 60s
        def browser = new FakeBrowser(observations: [Obs.page('A', [1: 'a']), Obs.page('B', [1: 'a']), Obs.page('B', [1: 'a'])])
        def llm = new ScriptedLlm(script: [[name: 'select_franqueadora'], [name: 'open_consulta_pdv2_menu', input: [timeout_seconds: 90]]])
        def runner = new AgentRunner(llm: llm, browser: browser, maxSteps: 5, timeoutMillis: 60_000,
                budget: new AgentBudget(1_000_000), timer: new OperationTimer(store, 30))
        int[] timeouts = new int[2]

        def result = runner.run('B', 'goal', null, { browser.index >= 2 })

        assert result.success
        assert browser.actions == ['select', 'menu']
        assert browser.lastTimeout == 90                          // explicit request honoured (within bounds)
        assert result.history*.tool == ['select_franqueadora', 'open_consulta_pdv2_menu']
    }

    @Test
    void runnerUsesLearnedTimeoutWhenTheModelDoesNotAsk() {
        def store = newStore()
        (1..10).each { store.recordOperation('select_franqueadora', 40_000, false, 30) }
        def browser = new FakeBrowser(observations: [Obs.page('A', [1: 'a']), Obs.page('B', [1: 'a'])])
        def llm = new ScriptedLlm(script: [[name: 'select_franqueadora']])
        def runner = new AgentRunner(llm: llm, browser: browser, maxSteps: 5, timeoutMillis: 60_000,
                budget: new AgentBudget(1_000_000), timer: new OperationTimer(store, 30))

        runner.run('B', 'goal', null, { browser.index >= 1 })

        assert browser.lastTimeout == 60
    }

    @Test
    void toolErrorsAreCountedInTheResultHistory() {
        def browser = new FakeBrowser(observations: [Obs.page('A', [1: 'a'])], selectError: 'Clique nao selecionou')
        def llm = new ScriptedLlm(script: [[name: 'select_franqueadora'], [name: 'give_up', input: [reason: 'x']]])
        def runner = new AgentRunner(llm: llm, browser: browser, maxSteps: 5, timeoutMillis: 60_000, budget: new AgentBudget(1_000_000))

        def result = runner.run('B', 'goal', null, { false })

        assert result.toolErrors == 1
        assert result.history[0].error
        assert result.history[0].result.contains('Clique nao selecionou')
    }

    @Test
    void customSystemPromptReachesTheModel() {
        def seen = []
        def llm = new LlmClient() {
            AgentTurn next(String system, List<Map> messages, List<Map> tools) {
                seen << system
                def g = [type: 'tool_use', id: 'g', name: 'give_up', input: [reason: 'x']]
                new AgentTurn(content: [g], toolUses: [g])
            }
        }
        def runner = new AgentRunner(llm: llm, browser: new FakeBrowser(observations: [Obs.page('A', [1: 'a'])]),
                systemPrompt: 'CUSTOM PROMPT', budget: new AgentBudget(1000))

        runner.run('B', 'goal', null, { false })

        assert seen == ['CUSTOM PROMPT']
    }

    // ------------------------------------------------------------------ lessons

    @Test
    void parsesLessonsFromPlainJsonFencedJsonAndGarbage() {
        assert NavigationAgent.parseLessons('["A lesson that is long enough.", "Another one is here."]').size() == 2
        assert NavigationAgent.parseLessons('```json\n["Only one lesson here."]\n```') == ['Only one lesson here.']
        assert NavigationAgent.parseLessons('["a","b","c"]').size() == 2          // at most 2
        assert NavigationAgent.parseLessons('[1, {"x":2}, "kept string"]') == ['kept string']
        assert NavigationAgent.parseLessons('no json at all') == []
        assert NavigationAgent.parseLessons(null) == []
        assert NavigationAgent.parseLessons('[unclosed') == []
    }

    @Test
    void proposedLessonsAreStoredPendingAndInvalidOnesDropped() {
        def store = newStore()
        def llm = new LlmClient() {
            AgentTurn next(String system, List<Map> messages, List<Map> tools) {
                assert tools.isEmpty()                                 // plain-text call
                assert system == NavigationAgent.LESSON_SYSTEM_PROMPT
                new AgentTurn(text: '["Call select_franqueadora before the menu.", "bad https://evil.example.com"]', inputTokens: 50, outputTokens: 20)
            }
        }
        def agent = new NavigationAgent(llm, new AgentBudget(10_000), 5, 60_000, null, 'FALLBACK', store)
        def result = new AgentResult(success: true, steps: 5, toolErrors: 1,
                history: [[tool: 'click', input: [id: 3], result: 'erro: elemento 3 nao existe', error: true]])

        agent.proposeLessons(result, 'StaleElementReferenceException', 'ALVO')

        def lessons = store.lessons()
        assert lessons.size() == 1
        assert lessons[0].status == AgentKnowledgeStore.PENDING
        assert lessons[0].text == 'Call select_franqueadora before the menu.'
        assert !store.composePrompt().contains('select_franqueadora before the menu')   // inert until approved
        assert agent.budget.used == 70
    }

    @Test
    void failedLessonProposalNeverBreaksTheRun() {
        def llm = new LlmClient() {
            AgentTurn next(String system, List<Map> messages, List<Map> tools) { throw new LlmException('boom', 500) }
        }
        def agent = new NavigationAgent(llm, new AgentBudget(10_000), 5, 60_000, null, 'FALLBACK', newStore())

        agent.proposeLessons(new AgentResult(success: true, steps: 5), 'x', 'ALVO')   // must not throw
    }

    @Test
    void agentLoadsTheSavedPlaybookAtStartup() {
        def store = newStore()
        store.savePlaybook(Playbook.from([[tool: 'select_franqueadora'], [tool: 'open_consulta_pdv2_menu']]), 'teste')

        def agent = new NavigationAgent(new ScriptedLlm(script: [null]), new AgentBudget(1000), 5, 60_000, null, 'FULL', store)

        assert agent.@playbook.steps*.tool == ['select_franqueadora', 'open_consulta_pdv2_menu']
    }
}

package com.ericsantanna.baixaNotasChiquinho.automation.agent

import org.junit.jupiter.api.Test

class AgentRunnerTest {

    private static AgentRunner runner(LlmClient llm, BrowserPort browser, int maxSteps = 15, AgentBudget budget = new AgentBudget(1_000_000)) {
        new AgentRunner(llm: llm, browser: browser, maxSteps: maxSteps, timeoutMillis: 60_000, budget: budget)
    }

    @Test
    void succeedsWhenDeterministicGoalIsReached_andRecordsPlaybookSteps() {
        def browser = new FakeBrowser(observations: [
                Obs.page('OUTRA', [1: 'OUTRA', 2: 'Fiscal']),
                Obs.page('OUTRA', [1: 'OUTRA', 2: 'VILA VELHA 03']),
                Obs.page('VILA VELHA 03', [1: 'VILA VELHA 03', 2: 'Fiscal'])])
        def llm = new ScriptedLlm(script: [[name: 'click', input: [id: 1]], [name: 'click', input: [id: 2]]])

        def result = runner(llm, browser).run('VILA VELHA 03', 'goal', null, { browser.index >= 2 })

        assert result.success
        assert browser.actions == ['click:1', 'click:2']
        assert result.recordedSteps*.tool == ['click', 'click']
        assert result.recordedSteps[0].text == Playbook.HEADER      // header widget abstracted
        assert result.recordedSteps[1].text == Playbook.FRANQUEADORA // franchise name abstracted
    }

    @Test
    void goalAlreadyReachedMakesNoLlmCall() {
        def browser = new FakeBrowser(observations: [Obs.page('X', [1: 'a'])])
        def llm = new ScriptedLlm(script: [[name: 'give_up', input: [reason: 'n/a']]])

        def result = runner(llm, browser).run('X', 'goal', null, { true })

        assert result.success
        assert llm.calls == 0
    }

    @Test
    void modelClaimingDoneIsNotTrusted() {
        def browser = new FakeBrowser(observations: [Obs.page('X', [1: 'a'])])
        def llm = new ScriptedLlm(script: [[name: 'finish'], [name: 'give_up', input: [reason: 'sem saida']]])

        def result = runner(llm, browser).run('X', 'goal', null, { false })

        assert !result.success
        assert result.reason.contains('desistiu')
        // the second call must have received an error tool_result for the premature finish
        def toolResult = llm.received[1].last().content.find { it.type == 'tool_result' }
        assert toolResult.is_error == true
        assert toolResult.content.contains('NÃO foi atingido')
    }

    @Test
    void finishIsAcceptedOnlyWhenGoalCheckPasses() {
        def browser = new FakeBrowser(observations: [Obs.page('X', [1: 'a'])])
        int checks = 0
        // false at loop start, true when finish is evaluated
        def llm = new ScriptedLlm(script: [[name: 'wait', input: [seconds: 1]], [name: 'finish']])

        def result = runner(llm, browser).run('X', 'goal', null, { ++checks >= 4 })

        assert result.success
    }

    @Test
    void onlyTheLatestObservationStaysInTheConversation() {
        def browser = new FakeBrowser(observations: [
                Obs.page('A', [1: 'um']), Obs.page('A', [1: 'dois']), Obs.page('A', [1: 'tres'])])
        def llm = new ScriptedLlm(script: [[name: 'click', input: [id: 1]], [name: 'click', input: [id: 1]], [name: 'give_up', input: [reason: 'x']]])

        runner(llm, browser).run('A', 'goal', null, { false })

        llm.received.each { messages ->
            def obsBlocks = messages.collectMany { it.content }.findAll { it.type == 'text' && it.text.startsWith('ESTADO ATUAL') }
            assert obsBlocks.size() == 1
        }
        // the goal text of the first message survives
        assert llm.received[2].first().content.any { it.type == 'text' && it.text.startsWith('OBJETIVO') }
    }

    @Test
    void pageOutsideAllowedDomainStopsTheRunBeforeCallingTheModel() {
        def browser = new FakeBrowser(observations: [Obs.page('A', [1: 'x'], 'https://evil.example.com/login')])
        def llm = new ScriptedLlm(script: [[name: 'click', input: [id: 1]]])

        def result = runner(llm, browser).run('A', 'goal', null, { false })

        assert !result.success
        assert result.reason.contains('fora do domínio')
        assert llm.calls == 0
    }

    @Test
    void domainCheckRejectsLookalikeHosts() {
        assert Obs.page('', [:], 'https://www.itfgestor.com.br/x').dominioPermitido()
        assert !Obs.page('', [:], 'https://itfgestor.com.br.evil.com/x').dominioPermitido()
        assert !Obs.page('', [:], 'https://evilitfgestor.com.br/x').dominioPermitido()
        assert !Obs.page('', [:], 'about:blank').dominioPermitido()
    }

    @Test
    void stepLimitIsEnforced() {
        def browser = new FakeBrowser(observations: [
                Obs.page('A', [1: 'a']), Obs.page('A', [1: 'b']), Obs.page('A', [1: 'c']), Obs.page('A', [1: 'd'])])
        def llm = new ScriptedLlm(script: [[name: 'click', input: [id: 1]]])

        def result = runner(llm, browser, 2).run('A', 'goal', null, { false })

        assert !result.success
        assert result.reason.contains('limite de 2 passos')
        assert llm.calls == 2
    }

    @Test
    void tokenBudgetIsEnforced() {
        def browser = new FakeBrowser(observations: [Obs.page('A', [1: 'a']), Obs.page('A', [1: 'b']), Obs.page('A', [1: 'c'])])
        def llm = new ScriptedLlm(script: [[name: 'click', input: [id: 1]]], tokensPerCall: 100)

        def result = runner(llm, browser, 15, new AgentBudget(100)).run('A', 'goal', null, { false })

        assert !result.success
        assert result.reason.contains('orçamento de tokens')
        assert llm.calls == 1
    }

    @Test
    void repeatingTheSameUselessActionIsDetectedAsALoop() {
        def browser = new FakeBrowser(observations: [Obs.page('A', [1: 'a'])])
        def llm = new ScriptedLlm(script: [[name: 'wait', input: [seconds: 1]]])

        def result = runner(llm, browser).run('A', 'goal', null, { false })

        assert !result.success
        assert result.reason.contains('repetindo')
    }

    @Test
    void toolErrorsAreReportedToTheModelAndRepeatedOnesAbort() {
        def browser = new FakeBrowser(observations: [Obs.page('A', [1: 'a', 2: 'b', 3: 'c', 4: 'd', 5: 'e'])], failingIds: [1, 2, 3, 4, 5] as Set)
        def llm = new ScriptedLlm(script: [
                [name: 'click', input: [id: 1]], [name: 'click', input: [id: 2]],
                [name: 'click', input: [id: 3]], [name: 'click', input: [id: 4]]])

        def result = runner(llm, browser).run('A', 'goal', null, { false })

        assert !result.success
        assert result.reason.contains('erros seguidos')
        assert llm.received[1].last().content.find { it.type == 'tool_result' }.is_error == true
    }

    @Test
    void turnWithoutToolCallGetsANudge() {
        def browser = new FakeBrowser(observations: [Obs.page('A', [1: 'a'])])
        def llm = new ScriptedLlm(script: [null, [name: 'give_up', input: [reason: 'x']]])

        runner(llm, browser).run('A', 'goal', null, { false })

        assert llm.received[1].last().content.any { it.type == 'text' && it.text.contains('precisa chamar uma ferramenta') }
    }

    @Test
    void secondToolInTheSameTurnIsRejected() {
        def browser = new FakeBrowser(observations: [Obs.page('A', [1: 'a']), Obs.page('A', [1: 'b'])])
        def two = new LlmClient() {
            int calls = 0
            AgentTurn next(String s, List<Map> m, List<Map> t) {
                calls++
                def a = [type: 'tool_use', id: 'a', name: 'click', input: [id: 1]]
                def b = [type: 'tool_use', id: 'b', name: 'click', input: [id: 1]]
                def g = [type: 'tool_use', id: 'g', name: 'give_up', input: [reason: 'x']]
                calls == 1 ? new AgentTurn(content: [a, b], toolUses: [a, b]) : new AgentTurn(content: [g], toolUses: [g])
            }
        }

        runner(two, browser).run('A', 'goal', null, { false })

        assert browser.actions == ['click:1'] // the second click was never executed
    }

    @Test
    void contextAndObservationsAreRedactedBeforeReachingTheModel() {
        def browser = new FakeBrowser(observations: [Obs.page('A', [1: 'Cliente 12.345.678/0001-95 R$ 1.234,56'])])
        def llm = new ScriptedLlm(script: [[name: 'give_up', input: [reason: 'x']]])

        runner(llm, browser).run('A', 'goal', 'falha com chave 12345678901234567890123456789012345678901234', { false })

        def text = llm.received[0].first().content*.text.join('\n')
        assert !text.contains('12.345.678/0001-95')
        assert !text.contains('1.234,56')
        assert !text.contains('12345678901234567890123456789012345678901234')
        assert text.contains('[CNPJ]') && text.contains('[VALOR]') && text.contains('[CHAVE]')
    }

    @Test
    void writesAJsonlTracePerFranchise() {
        def dir = java.nio.file.Files.createTempDirectory('trace').toFile()
        def browser = new FakeBrowser(observations: [Obs.page('A', [1: 'a'])])
        def llm = new ScriptedLlm(script: [[name: 'give_up', input: [reason: 'x']]])
        def r = runner(llm, browser)
        r.traceDir = dir

        r.run('VILA VELHA 03 (SP)', 'goal', null, { false })

        def file = new File(dir, 'VILA_VELHA_03_SP_.jsonl')
        assert file.exists()
        def lines = file.readLines()
        assert lines.size() >= 2
        assert lines.every { it.contains('"ts"') }
    }

    @Test
    void selectThenMenuIsTheHappyPathAndIsRecordedForReplay() {
        def browser = new FakeBrowser(observations: [Obs.page('OUTRA', [1: 'x']), Obs.page('ALVO', [1: 'x']), Obs.page('ALVO', [1: 'x'])])
        def llm = new ScriptedLlm(script: [[name: 'select_franqueadora'], [name: 'open_consulta_pdv2_menu']])

        def result = runner(llm, browser).run('ALVO', 'goal', null, { browser.index >= 2 })

        assert result.success
        assert browser.actions == ['select', 'menu']
        assert result.recordedSteps*.tool == ['select_franqueadora', 'open_consulta_pdv2_menu']
    }

    @Test
    void failedSelectionIsReportedBackAndTheModelRetriesInsteadOfGivingUp() {
        def browser = new FakeBrowser(observations: [Obs.page('OUTRA', [1: 'x'])], selectError: 'Clique nao selecionou a franqueadora: ALVO')
        def llm = new ScriptedLlm(script: [[name: 'select_franqueadora'], [name: 'wait', input: [seconds: 1]], [name: 'give_up', input: [reason: 'x']]])

        runner(llm, browser).run('ALVO', 'goal', null, { false })

        def toolResult = llm.received[1].last().content.find { it.type == 'tool_result' }
        assert toolResult.is_error == true
        assert toolResult.content.contains('Clique nao selecionou')
    }

    @Test
    void systemPromptAndToolsTeachTheTwoStepFlow() {
        assert AgentRunner.SYSTEM_PROMPT.contains('select_franqueadora')
        assert AgentRunner.SYSTEM_PROMPT.contains('open_consulta_pdv2_menu')
        assert AgentRunner.SYSTEM_PROMPT.contains('não pule o passo 2')
        assert AgentTools.specs()*.name.containsAll(['select_franqueadora', 'open_consulta_pdv2_menu'])
    }

    @Test
    void everythingTheModelReadsIsInBrazilianPortuguese() {
        def texts = [AgentRunner.SYSTEM_PROMPT, AgentKnowledgeStore.SAFETY_FOOTER, NavigationAgent.LESSON_SYSTEM_PROMPT,
                     NavigationAgent.goalFor('ALVO')]
        texts += AgentTools.specs().collect { it.description as String }
        texts << new AgentKnowledgeStore(java.nio.file.Files.createTempDirectory('p').toFile()).composePrompt()
        texts << Obs.page('A', [1: 'x']).toPrompt()

        texts.each { String t ->
            // common English function words: none of them may appear in text written for the model
            assert !(t =~ /(?i)\b(the|you|your|and|must|should|never|when|with|only)\b/).find(), "texto em inglês: ${t.take(80)}"
        }
        assert AgentRunner.SYSTEM_PROMPT.contains('Você opera')
        assert NavigationAgent.LESSON_SYSTEM_PROMPT.contains('português do Brasil')
        assert !AgentRunner.SYSTEM_PROMPT.toLowerCase().contains('roteiro')   // the project calls it "script"
    }
}

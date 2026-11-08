package com.ericsantanna.baixaNotasChiquinho.automation.agent

import org.junit.jupiter.api.Test

class PlaybookTest {

    private static Playbook recordedForVilaVelha() {
        Playbook.from([
                Playbook.clickStep('OUTRA FRANQUIA', 'OUTRA FRANQUIA', 'VILA VELHA 03'),
                Playbook.clickStep('VILA VELHA 03', 'OUTRA FRANQUIA', 'VILA VELHA 03'),
                [tool: 'open_consulta_pdv2_menu']])
    }

    @Test
    void clickStepsAbstractFranchiseSpecificText() {
        def pb = recordedForVilaVelha()
        assert pb.steps[0].text == Playbook.HEADER
        assert pb.steps[1].text == Playbook.FRANQUEADORA
    }

    @Test
    void replaysForAnotherFranchiseWithoutTheModel() {
        def browser = new FakeBrowser(observations: [
                Obs.page('PRIMEIRA', [1: 'PRIMEIRA', 2: 'Fiscal']),
                Obs.page('PRIMEIRA', [1: 'PRIMEIRA', 2: 'SERRA 01', 3: 'SERRA 02']),
                Obs.page('SERRA 02', [1: 'SERRA 02', 2: 'Fiscal']),
                Obs.page('SERRA 02', [1: 'SERRA 02'])])

        boolean ok = recordedForVilaVelha().replay('SERRA 02', browser, { browser.index >= 3 })

        assert ok
        assert browser.actions == ['click:1', 'click:3', 'menu']
    }

    @Test
    void divergenceReturnsFalseAndStopsInsteadOfGuessing() {
        def browser = new FakeBrowser(observations: [
                Obs.page('PRIMEIRA', [1: 'PRIMEIRA']),
                Obs.page('PRIMEIRA', [1: 'PRIMEIRA', 2: 'OUTRA COISA'])])

        boolean ok = recordedForVilaVelha().replay('SERRA 02', browser, { false })

        assert !ok
        assert browser.actions == ['click:1'] // franchise entry not found -> stopped
    }

    @Test
    void replayNeverRunsOutsideTheAllowedDomain() {
        def browser = new FakeBrowser(observations: [Obs.page('A', [1: 'A'], 'https://evil.example.com/')])

        assert !recordedForVilaVelha().replay('SERRA 02', browser, { false })
        assert browser.actions.isEmpty()
    }

    @Test
    void replaysToolBasedPlaybookWithoutDependingOnListedElements() {
        def pb = Playbook.from([[tool: 'select_franqueadora'], [tool: 'open_consulta_pdv2_menu']])
        // the header widget is not even in the observation: the scripted selection does not need it
        def browser = new FakeBrowser(observations: [Obs.page('A', [:]), Obs.page('B', [:]), Obs.page('B', [:])])

        assert pb.replay('B', browser, { browser.index >= 2 })
        assert browser.actions == ['select', 'menu']
    }
}

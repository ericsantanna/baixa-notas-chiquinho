package com.ericsantanna.baixaNotasChiquinho.agente

import com.ericsantanna.baixaNotasChiquinho.automation.agent.AgentKnowledgeStore
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus

import java.nio.file.Files

class AgenteControllerTest {
    AgentKnowledgeStore store
    AgenteController controller

    @BeforeEach
    void setup() {
        def dir = new File(Files.createTempDirectory('agente-ui').toFile(), 'agente')
        store = new AgentKnowledgeStore(dir)
        // seams: never touch the real execucao/ folder or the real configuration
        controller = new AgenteController(null) {
            @Override protected AgentKnowledgeStore store() { AgenteControllerTest.this.store }
            @Override protected int timeoutConfigurado() { 30 }
        }
    }

    @Test
    void instructionsRoundTripAndEffectivePromptReflectThem() {
        def saved = controller.salvarInstrucoes(new AgenteController.InstrucoesRequest(instrucoesExtras: 'Menu Fiscal so aparece depois de escolher a franqueadora.'))

        assert saved.instrucoesExtras == 'Menu Fiscal so aparece depois de escolher a franqueadora.'
        assert saved.promptEfetivo.contains('Menu Fiscal so aparece')
        assert saved.promptPadrao.contains('select_franqueadora')
        assert controller.instrucoes().instrucoesExtras == saved.instrucoesExtras
    }

    @Test
    void restoreKeepsTheExtraInstructions() {
        controller.salvarInstrucoes(new AgenteController.InstrucoesRequest(promptPersonalizado: 'custom', instrucoesExtras: 'extra'))

        def restored = controller.restaurarPrompt()

        assert restored.promptPersonalizado == ''
        assert restored.instrucoesExtras == 'extra'
        assert restored.promptEfetivo.contains('select_franqueadora')
    }

    @Test
    void tooLongInstructionsBecomeA400WithAMessage() {
        def e = org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException) {
            controller.salvarInstrucoes(new AgenteController.InstrucoesRequest(instrucoesExtras: 'x' * 5000))
        }
        def response = controller.invalido(e)

        assert response.statusCode == HttpStatus.BAD_REQUEST
        assert response.body.erro.contains('muito longas')
    }

    @Test
    void lessonApprovalFlowThroughTheApi() {
        store.addPendingLessons(['Wait for the overlay to disappear first.'], 'ALVO')
        def id = store.lessons()[0].id

        assert controller.aprendizado().licoes[0].status == 'PENDING'
        assert controller.aprovar(id).statusCode == HttpStatus.OK
        assert controller.instrucoes().promptEfetivo.contains('Wait for the overlay')
        assert controller.rejeitar(id).statusCode == HttpStatus.OK
        assert !controller.instrucoes().promptEfetivo.contains('Wait for the overlay')
        assert controller.removerLicao(id).statusCode == HttpStatus.OK
        assert controller.aprendizado().licoes.isEmpty()
    }

    @Test
    void unknownLessonIs404() {
        assert controller.aprovar('nope').statusCode == HttpStatus.NOT_FOUND
        assert controller.removerLicao('nope').statusCode == HttpStatus.NOT_FOUND
    }

    @Test
    void operatorCanAddAndEditLessons() {
        controller.adicionarLicao(new AgenteController.LicaoRequest(texto: 'Always select the franchise before the menu.'))
        def id = controller.aprendizado().licoes[0].id

        assert controller.aprendizado().licoes[0].status == 'APPROVED'
        assert controller.editarLicao(id, new AgenteController.LicaoRequest(texto: 'Select the franchise first, then the menu.')).statusCode == HttpStatus.OK
        assert controller.aprendizado().licoes[0].text == 'Select the franchise first, then the menu.'
    }

    @Test
    void forgettingThePlaybook() {
        store.savePlaybook(com.ericsantanna.baixaNotasChiquinho.automation.agent.Playbook.from([[tool: 'go_home']]), 'teste')
        assert controller.aprendizado().playbook.exists

        assert !controller.esquecerPlaybook().playbook.exists
    }

    @Test
    void timeoutSettingsAndStatsThroughTheApi() {
        (1..10).each { store.recordOperation('select_franqueadora', 40_000, false, 30) }

        def t = controller.tempos()
        assert t.configurado == 30
        assert t.minimoEfetivo == 30
        assert t.operacoes[0].timeoutAtualSeg == 60

        def updated = controller.salvarTempos(new AgenteController.TemposRequest(automatico: true, minimo: 10, maximo: 45))
        assert updated.minimo == 10
        assert updated.operacoes[0].timeoutAtualSeg == 45

        assert controller.esquecerTempos().operacoes.isEmpty()
    }
}

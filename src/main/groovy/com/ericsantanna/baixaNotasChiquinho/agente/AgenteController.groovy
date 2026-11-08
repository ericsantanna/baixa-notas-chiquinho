package com.ericsantanna.baixaNotasChiquinho.agente

import com.ericsantanna.baixaNotasChiquinho.automation.agent.AgentKnowledgeStore
import com.ericsantanna.baixaNotasChiquinho.automation.agent.AgentRunner
import com.ericsantanna.baixaNotasChiquinho.configuracoes.ConfiguracoesService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Backend of the "Agente de IA" UI page: prompt customisation, what the agent learned (playbook + lessons that
 * need approval) and timeouts measured from history. The operator never touches files; this is the only way in.
 */
@RestController
@RequestMapping("/api/agente")
class AgenteController {

    private final ConfiguracoesService configuracoesService

    AgenteController(ConfiguracoesService configuracoesService) {
        this.configuracoesService = configuracoesService
    }

    /** Same folder the execution uses (see BaixaNotasChiquinhoAutomation). */
    protected AgentKnowledgeStore store() {
        new AgentKnowledgeStore(new File(configuracoesService.getConfiguracoes().rootPath, "agente"))
    }

    // ------------------------------------------------------------------ instruções (prompt)

    @GetMapping("/instrucoes")
    Map instrucoes() {
        def s = store()
        def settings = s.settings()
        return [promptPadrao        : AgentRunner.SYSTEM_PROMPT.trim(),
                promptPersonalizado : settings.customPrompt,
                instrucoesExtras    : settings.extraInstructions,
                promptEfetivo       : s.composePrompt(),
                limiteInstrucoes    : AgentKnowledgeStore.MAX_EXTRA_CHARS,
                limitePrompt        : AgentKnowledgeStore.MAX_PROMPT_CHARS]
    }

    @PutMapping("/instrucoes")
    Map salvarInstrucoes(@RequestBody InstrucoesRequest request) {
        store().saveSettings([customPrompt: request.promptPersonalizado, extraInstructions: request.instrucoesExtras])
        return instrucoes()
    }

    /** Back to the built-in prompt (operator instructions and lessons are kept). */
    @PostMapping("/instrucoes/restaurar")
    Map restaurarPrompt() {
        store().saveSettings([customPrompt: ""])
        return instrucoes()
    }

    // ------------------------------------------------------------------ aprendizado (playbook + lições)

    @GetMapping("/aprendizado")
    Map aprendizado() {
        def s = store()
        return [playbook: s.playbookInfo(), licoes: s.lessons()]
    }

    @DeleteMapping("/playbook")
    Map esquecerPlaybook() {
        store().clearPlaybook()
        return aprendizado()
    }

    @PostMapping("/licoes")
    Map adicionarLicao(@RequestBody LicaoRequest request) {
        store().addApprovedLesson(request.texto)
        return aprendizado()
    }

    @PutMapping("/licoes/{id}")
    ResponseEntity<Map> editarLicao(@PathVariable("id") String id, @RequestBody LicaoRequest request) {
        return found(store().updateLessonText(id, request.texto))
    }

    @PostMapping("/licoes/{id}/aprovar")
    ResponseEntity<Map> aprovar(@PathVariable("id") String id) {
        return found(store().setLessonStatus(id, AgentKnowledgeStore.APPROVED))
    }

    @PostMapping("/licoes/{id}/rejeitar")
    ResponseEntity<Map> rejeitar(@PathVariable("id") String id) {
        return found(store().setLessonStatus(id, AgentKnowledgeStore.REJECTED))
    }

    @PostMapping("/licoes/{id}/pendente")
    ResponseEntity<Map> voltarParaPendente(@PathVariable("id") String id) {
        return found(store().setLessonStatus(id, AgentKnowledgeStore.PENDING))
    }

    @DeleteMapping("/licoes/{id}")
    ResponseEntity<Map> removerLicao(@PathVariable("id") String id) {
        return found(store().deleteLesson(id))
    }

    // ------------------------------------------------------------------ tempos

    @GetMapping("/tempos")
    Map tempos() {
        def s = store()
        int configurado = timeoutConfigurado()
        def settings = s.settings()
        return [automatico   : settings.timeoutAuto,
                minimo       : settings.timeoutMin,
                maximo       : settings.timeoutMax,
                configurado  : configurado,
                minimoEfetivo: (settings.timeoutMin ?: configurado),
                operacoes    : s.timeoutStats(configurado)]
    }

    @PutMapping("/tempos")
    Map salvarTempos(@RequestBody TemposRequest request) {
        store().saveSettings([timeoutAuto: request.automatico, timeoutMin: request.minimo, timeoutMax: request.maximo])
        return tempos()
    }

    @DeleteMapping("/tempos")
    Map esquecerTempos() {
        store().clearTimeouts()
        return tempos()
    }

    // ------------------------------------------------------------------

    protected int timeoutConfigurado() {
        configuracoesService.getConfiguracoes().waitingTimeout
    }

    private ResponseEntity<Map> found(boolean ok) {
        return ok ? ResponseEntity.ok(aprendizado()) : ResponseEntity.status(HttpStatus.NOT_FOUND).body([erro: "Lição não encontrada"])
    }

    @ExceptionHandler(IllegalArgumentException)
    ResponseEntity<Map> invalido(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body([erro: e.message])
    }

    static class InstrucoesRequest {
        String promptPersonalizado
        String instrucoesExtras
    }

    static class LicaoRequest {
        String texto
    }

    static class TemposRequest {
        Boolean automatico
        Integer minimo
        Integer maximo
    }
}

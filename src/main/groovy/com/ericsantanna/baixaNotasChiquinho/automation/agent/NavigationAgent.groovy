package com.ericsantanna.baixaNotasChiquinho.automation.agent

import com.ericsantanna.baixaNotasChiquinho.configuracoes.Configuracoes
import groovy.json.JsonSlurper
import org.openqa.selenium.JavascriptExecutor
import org.openqa.selenium.WebDriver
import org.slf4j.LoggerFactory

/**
 * Per-execution entry point of the LLM navigation agent: replays the saved playbook when one exists, otherwise (or
 * when it diverges) lets the model navigate to the "Consulta Cupom Fiscal PDV" screen of a franchise.
 * Everything after that screen (month/year, search, download, extraction, validation) stays deterministic.
 *
 * What it keeps between executions lives in {@link AgentKnowledgeStore}: the playbook, operator-approved lessons and
 * measured timeouts. The agent only ever PROPOSES lessons; a human approves them in the UI.
 */
class NavigationAgent {
    private static final log = LoggerFactory.getLogger(NavigationAgent)
    private static final String CHECK_JS = AgentResources.load('check.js')

    static final String LESSON_SYSTEM_PROMPT = '''\
Você escreve lições operacionais curtas para um agente de automação de navegador que navega no ITFGestor. Você recebe a lista de chamadas de ferramenta que ele fez (com os erros) ao se recuperar de uma falha. Responda SOMENTE com um array JSON de no máximo 2 strings. Cada string: uma frase no imperativo, com menos de 200 caracteres, em português do Brasil, sobre o que fazer ou evitar da próxima vez (por exemplo: qual ferramenta chamar primeiro, quando esperar). Sem nomes de franqueadoras, sem números da página, sem URLs. Responda [] se não houver nada que valha para outros casos.'''

    final LlmClient llm
    final AgentBudget budget
    final int maxSteps
    final long timeoutMillis
    final File traceDir
    final String mode
    final AgentKnowledgeStore store
    final int configuredTimeoutSeconds

    private Playbook playbook

    NavigationAgent(LlmClient llm, AgentBudget budget, int maxSteps, long timeoutMillis, File traceDir, String mode,
                    AgentKnowledgeStore store = null, int configuredTimeoutSeconds = 30) {
        this.llm = llm
        this.budget = budget
        this.maxSteps = maxSteps
        this.timeoutMillis = timeoutMillis
        this.traceDir = traceDir
        this.mode = mode
        this.store = store
        this.configuredTimeoutSeconds = configuredTimeoutSeconds
        this.playbook = store?.loadPlaybook() // what worked in a previous execution
        if (playbook != null) {
            log.info "Agente: playbook salvo carregado (${playbook.steps.size()} passo(s))"
        }
    }

    /** @return null when the agent is disabled or cannot run (no key, unsupported provider). */
    static NavigationAgent create(Configuracoes c, File traceDir, AgentKnowledgeStore store = null) {
        String mode = (c.agentMode ?: 'OFF').toUpperCase()
        if (mode == 'OFF') {
            return null
        }
        if (!(mode in ['FALLBACK', 'FULL'])) {
            log.warn "Modo de agente desconhecido '${c.agentMode}'. Agente desativado."
            return null
        }
        if ((c.llmProvider ?: 'anthropic').toLowerCase() != 'anthropic') {
            log.warn "Provedor de LLM '${c.llmProvider}' não suportado. Agente desativado."
            return null
        }
        String key = c.llmApiKey ?: System.getenv('ANTHROPIC_API_KEY')
        if (!key) {
            log.warn "Modo de agente ${mode} configurado, mas sem chave de API (configurações ou ANTHROPIC_API_KEY). Agente desativado."
            return null
        }
        def client = new ClaudeLlmClient(apiKey: key, model: c.llmModel ?: Configuracoes.DEFAULT_LLM_MODEL)
        log.info "Agente LLM ativo: modo=${mode}, modelo=${client.model}, passos<=${c.agentMaxSteps}, tokens<=${c.agentMaxTokensPerRun}"
        return new NavigationAgent(client, new AgentBudget(c.agentMaxTokensPerRun), c.agentMaxSteps,
                c.agentTimeoutSeconds * 1000L, traceDir, mode, store, c.waitingTimeout)
    }

    boolean isFull() { mode == 'FULL' }

    /**
     * @param goHome         closure that navigates to the home page (keeps session)
     * @param openMenu       closure(int timeoutSeconds) that opens Fiscal > Gerenciamento > Consulta Nota Fiscal Venda PDV2
     * @param selectFranchise closure(String franqueadora, int timeoutSeconds) that selects the franchise with the scripted logic
     */
    AgentResult navegar(String franqueadora, String contexto, WebDriver driver, Closure goHome, Closure openMenu,
                        Closure selectFranchise) {
        def port = new SeleniumBrowserPort(driver, goHome, openMenu, { int secs -> selectFranchise.call(franqueadora, secs) })
        def timer = new OperationTimer(store, configuredTimeoutSeconds)
        Closure<Boolean> objetivo = { estaNaConsulta(driver, franqueadora) }

        if (playbook != null) {
            log.info "Agente: reproduzindo playbook para ${franqueadora}"
            boolean replayed = playbook.replay(franqueadora, port, objetivo, timer)
            store?.recordReplay(replayed)
            if (!replayed && store != null && store.loadPlaybook() == null) {
                playbook = null // the store discarded it after repeated failures; stop replaying a dead playbook
            }
            if (replayed) {
                return new AgentResult(success: true, reason: 'playbook', steps: playbook.steps.size(), usedTokens: budget.used)
            }
            log.warn "Playbook não funcionou para ${franqueadora}; chamando o LLM"
            try { goHome.call() } catch (Throwable ignored) {}
        }

        def runner = new AgentRunner(llm: llm, browser: port, maxSteps: maxSteps, timeoutMillis: timeoutMillis,
                budget: budget, traceDir: traceDir, timer: timer,
                systemPrompt: store ? store.composePrompt() : AgentRunner.SYSTEM_PROMPT + '\n\n' + AgentKnowledgeStore.SAFETY_FOOTER)
        AgentResult result = runner.run(franqueadora, goalFor(franqueadora), contexto, objetivo)
        if (result.success && result.recordedSteps) {
            playbook = Playbook.from(result.recordedSteps)
            store?.savePlaybook(playbook, "agente (${franqueadora})")
            log.info "Agente: playbook gravado com ${playbook.steps.size()} passo(s)"
        }
        if (result.success && store != null && (result.toolErrors > 0 || result.steps > 3)) {
            proposeLessons(result, contexto, franqueadora)
        }
        return result
    }

    /**
     * Asks the model for up to two generalizable lessons from a recovery that needed effort. They are stored as
     * PENDING and have no effect until a human approves them. Input is the tool-call history, not page text.
     * Never throws: a failed proposal must not affect the run.
     */
    private void proposeLessons(AgentResult result, String contexto, String franqueadora) {
        try {
            if (budget.exceeded()) {
                return
            }
            String history = result.history.collect { h ->
                "${h.tool} ${h.input ?: ''} -> ${h.error ? 'ERRO: ' : ''}${h.result}"
            }.join('\n')
            String prompt = "Falha anterior do script: ${Redactor.redact(contexto ?: '(nenhuma)')}\n\nChamadas do agente:\n${history}\n\n" +
                    "O agente conseguiu chegar na tela. Que lições gerais valem para a próxima vez?"
            AgentTurn turn = llm.next(LESSON_SYSTEM_PROMPT, [[role: 'user', content: [[type: 'text', text: prompt]]]], [])
            budget.add(turn.inputTokens, turn.outputTokens)
            List<String> lessons = parseLessons(turn.text)
            int added = store.addPendingLessons(lessons, "${franqueadora} em ${java.time.LocalDate.now()}")
            if (added) {
                log.info "Agente: ${added} lição(ões) proposta(s), aguardando aprovação na página 'Agente de IA'"
            }
        } catch (Throwable e) {
            log.warn "Agente: não foi possível propor lições: ${e.message}"
        }
    }

    /** Extracts the JSON array of strings from the model's reply (tolerates code fences); anything else yields []. */
    static List<String> parseLessons(String text) {
        try {
            String t = (text ?: '').trim()
            int start = t.indexOf('[')
            int end = t.lastIndexOf(']')
            if (start < 0 || end <= start) {
                return []
            }
            def parsed = new JsonSlurper().parseText(t.substring(start, end + 1))
            return (parsed instanceof List ? parsed : []).findAll { it instanceof String }.collect { it as String }.take(2)
        } catch (Exception ignored) {
            return []
        }
    }

    void logResumo() {
        log.info "Agente LLM: ${budget.calls} chamada(s), ${budget.used} tokens no total nesta execução"
    }

    static String goalFor(String franqueadora) {
        return "Abrir a tela 'Consulta Cupom Fiscal PDV' (menu lateral Fiscal > Gerenciamento > Consulta Nota Fiscal Venda PDV2) " +
                "com a franqueadora \"${franqueadora}\" selecionada no cabeçalho. Não escolha mês/ano nem pesquise; pare quando a tela estiver aberta."
    }

    /** Deterministic goal check: correct franchise in the header AND the query screen is open. */
    static boolean estaNaConsulta(WebDriver driver, String franqueadora) {
        try {
            def r = ((JavascriptExecutor) driver).executeScript(CHECK_JS) as List
            return r[0]?.toString()?.trim()?.equalsIgnoreCase(franqueadora.trim()) && r[1] == true
        } catch (Exception ignored) {
            return false
        }
    }
}

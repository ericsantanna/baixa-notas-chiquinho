package com.ericsantanna.baixaNotasChiquinho.automation.agent

import org.slf4j.LoggerFactory

/**
 * Sequence of tool calls that worked for one franchise, recorded by text (ids are not stable) and replayed
 * for the next ones without calling the LLM. Any divergence just returns false and the caller falls back to
 * the model, so a stale playbook can never make things worse than no playbook.
 */
class Playbook {
    private static final log = LoggerFactory.getLogger(Playbook)

    static final List<String> KNOWN_TOOLS = ['click', 'go_home', 'select_franqueadora', 'open_consulta_pdv2_menu']
    static final String FRANQUEADORA = '{{franqueadora}}'
    static final String HEADER = '{{header}}'

    /** Each step: [tool:'click', text:..] | [tool:'go_home'] | [tool:'open_consulta_pdv2_menu'] */
    List<Map> steps = []

    /** Builds the click step for an element, abstracting franchise-specific text. */
    static Map clickStep(String text, String currentHeader, String franqueadora) {
        String t = Observation.normalize(text)
        if (t && t == Observation.normalize(franqueadora)) {
            return [tool: 'click', text: FRANQUEADORA]
        }
        String h = Observation.normalize(currentHeader)
        if (h && t.startsWith(h)) {
            return [tool: 'click', text: HEADER] // the header widget shows whichever franchise is selected
        }
        return [tool: 'click', text: text]
    }

    static Playbook from(List<Map> recorded) {
        return new Playbook(steps: recorded.collect { new LinkedHashMap(it) })
    }

    boolean replay(String franqueadora, BrowserPort port, Closure<Boolean> goalReached,
                   OperationTimer timer = new OperationTimer(null, 30)) {
        try {
            for (Map step : steps) {
                if (Thread.currentThread().isInterrupted()) {
                    throw new InterruptedException("Execução interrompida")
                }
                Observation obs = port.observe()
                if (!obs.dominioPermitido()) {
                    return false
                }
                switch (step.tool) {
                    case 'click':
                        ElementInfo el = locate(obs, step.text as String, franqueadora)
                        if (el == null) {
                            log.info "Playbook: elemento '${step.text}' não encontrado; abortando replay"
                            return false
                        }
                        port.click(el.id)
                        break
                    case 'go_home':
                        port.goHome()
                        break
                    case 'select_franqueadora':
                        timer.run('select_franqueadora', null) { int secs -> port.selectFranqueadora(secs) }
                        break
                    case 'open_consulta_pdv2_menu':
                        timer.run('open_consulta_pdv2_menu', null) { int secs -> port.openConsultaMenu(secs) }
                        break
                    default:
                        return false
                }
                if (goalReached.call()) {
                    return true
                }
            }
            return goalReached.call()
        } catch (InterruptedException e) {
            throw e
        } catch (Exception e) {
            log.info "Playbook falhou: ${e.message}"
            return false
        }
    }

    private static ElementInfo locate(Observation obs, String text, String franqueadora) {
        if (text == FRANQUEADORA) {
            return obs.findByText(franqueadora).find { it.enabled }
        }
        if (text == HEADER) {
            String h = Observation.normalize(obs.header)
            return h ? obs.elements.find { it.enabled && Observation.normalize(it.text).startsWith(h) } : null
        }
        return obs.findByText(text).find { it.enabled }
    }
}

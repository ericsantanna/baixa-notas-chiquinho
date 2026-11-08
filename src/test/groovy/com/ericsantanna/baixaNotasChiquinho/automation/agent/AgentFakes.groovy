package com.ericsantanna.baixaNotasChiquinho.automation.agent

/** Test doubles shared by the agent tests. */
class FakeBrowser implements BrowserPort {
    /** Returned in order, one per executed click/home/menu action; the last one repeats. */
    List<Observation> observations
    List<String> actions = []
    int index = 0
    /** ids that make click() throw (to simulate blocked/stale elements) */
    Set<Integer> failingIds = [] as Set

    Observation observe() {
        observations[Math.min(index, observations.size() - 1)]
    }

    String click(int id) {
        actions << "click:${id}".toString()
        if (id in failingIds) {
            throw new AgentToolException("bloqueado ${id}")
        }
        index++
        return "ok ${id}"
    }

    String scroll(String direction, Integer id) { actions << "scroll"; "scroll" }

    String waitSeconds(int seconds) { actions << "wait"; "wait" }

    String goHome() { actions << "home"; index++; "home" }

    /** timeout (seconds) the last select/menu call received */
    Integer lastTimeout

    String openConsultaMenu(int timeoutSeconds) { actions << "menu"; lastTimeout = timeoutSeconds; index++; "menu" }

    /** set to make selectFranqueadora() fail the way the scripted selection can */
    String selectError

    String selectFranqueadora(int timeoutSeconds) {
        actions << "select"
        lastTimeout = timeoutSeconds
        if (selectError) {
            throw new IllegalStateException(selectError)
        }
        index++
        "selected"
    }
}

class ScriptedLlm implements LlmClient {
    /** One entry per call: the tool_use to emit ([name:, input:]) or null for "text only, no tool". */
    List<Map> script
    int tokensPerCall = 100
    int calls = 0
    /** Deep snapshots of the messages received at every call. */
    List<List<Map>> received = []

    AgentTurn next(String system, List<Map> messages, List<Map> tools) {
        received << messages.collect { m ->
            [role: m.role, content: m.content.collect { new LinkedHashMap(it) }]
        }
        Map step = script[Math.min(calls, script.size() - 1)]
        calls++
        def turn = new AgentTurn(inputTokens: tokensPerCall, outputTokens: 0)
        if (step == null) {
            turn.content = [[type: 'text', text: 'hmm']]
            turn.text = 'hmm'
        } else {
            def block = [type: 'tool_use', id: "tu_${calls}".toString(), name: step.name, input: step.input ?: [:]]
            turn.content = [block]
            turn.toolUses = [block]
        }
        return turn
    }
}

class Obs {
    static Observation page(String header, Map<Integer, String> elements, String url = 'https://www.itfgestor.com.br/ITFGestor/paginaInicial.jsf') {
        def o = new Observation(url: url, title: 't', header: header, totalElements: elements.size())
        elements.each { id, text -> o.elements << new ElementInfo(id: id, tag: 'a', text: text) }
        return o
    }
}

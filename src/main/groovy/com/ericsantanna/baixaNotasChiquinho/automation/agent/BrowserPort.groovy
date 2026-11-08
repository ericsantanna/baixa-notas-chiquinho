package com.ericsantanna.baixaNotasChiquinho.automation.agent

/** Everything the agent is allowed to do to the browser. Deliberately tiny: no typing, no JS, no URLs. */
interface BrowserPort {
    Observation observe()

    /** Clicks the element tagged with {@code id} in the latest observation. */
    String click(int id)

    /** Scrolls an element into view (id given) or the page/scrollable lists ("up"/"down"). */
    String scroll(String direction, Integer id)

    String waitSeconds(int seconds)

    /** Back to the ITFGestor home page (keeps the session). */
    String goHome()

    /** Opens Fiscal > Gerenciamento > Consulta Nota Fiscal Venda PDV2 through the side menu. */
    String openConsultaMenu(int timeoutSeconds)

    /**
     * Selects the target franchise in the header with the scripted (proven) logic. The target is fixed by the
     * caller, the model cannot choose another one. Throws with the reason when the selection did not take effect.
     */
    String selectFranqueadora(int timeoutSeconds)
}

/** Tool failure that is reported back to the LLM (not fatal for the run). */
class AgentToolException extends RuntimeException {
    AgentToolException(String message) {
        super(message)
    }
}

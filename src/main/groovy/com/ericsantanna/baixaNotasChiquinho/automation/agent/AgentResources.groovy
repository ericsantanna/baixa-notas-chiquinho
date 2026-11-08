package com.ericsantanna.baixaNotasChiquinho.automation.agent

/** Loads the JavaScript snippets in src/main/resources/agent (kept out of Groovy strings to avoid escaping bugs). */
class AgentResources {
    static String load(String name) {
        def stream = AgentResources.class.getResourceAsStream("/agent/${name}")
        if (stream == null) {
            throw new IllegalStateException("Recurso do agente não encontrado: /agent/${name}")
        }
        return stream.withCloseable { it.getText('UTF-8') }
    }
}

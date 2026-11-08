package com.ericsantanna.baixaNotasChiquinho.configuracoes

class Configuracoes {
    static final String DEFAULT_DRIVER = "Firefox"
    static final int DEFAULT_WAITING_TIMEOUT = 30
    static final int DEFAULT_EXTRACTION_THREADS = 2
    static final File DEFAULT_ROOT_PATH = new File(System.getProperty("user.dir"), "execucao")
    static final File DEFAULT_DOWNLOAD_PATH = new File(DEFAULT_ROOT_PATH, "baixados")
    static final File DEFAULT_EXTRACTION_PATH = new File(DEFAULT_ROOT_PATH, "extraidos")
    static final File DEFAULT_PROCESSADOS_PATH = new File(DEFAULT_ROOT_PATH, "processados.csv")
    static final File DEFAULT_NAO_PROCESSADOS_PATH = new File(DEFAULT_ROOT_PATH, "nao-processados.csv")
    static final File DEFAULT_FRANQUEADORAS_PATH = new File(DEFAULT_ROOT_PATH, "franqueadoras.csv")
    static final File DEFAULT_CONFIG_PATH = new File(DEFAULT_ROOT_PATH, "config.yaml")

    // LLM agent (OFF keeps the behaviour exactly as before)
    static final String DEFAULT_AGENT_MODE = "OFF" // OFF | FALLBACK | FULL
    static final String DEFAULT_LLM_PROVIDER = "anthropic"
    static final String DEFAULT_LLM_MODEL = "claude-haiku-5-5"
    static final int DEFAULT_AGENT_MAX_STEPS = 15
    static final int DEFAULT_AGENT_TIMEOUT_SECONDS = 300
    static final int DEFAULT_AGENT_MAX_TOKENS_PER_RUN = 400000

    String driver
    int waitingTimeout
    int extractionThreads
    File rootPath
    File downloadPath
    File extractionPath

    String agentMode = DEFAULT_AGENT_MODE
    String llmProvider = DEFAULT_LLM_PROVIDER
    String llmModel = DEFAULT_LLM_MODEL
    String llmApiKey
    int agentMaxSteps = DEFAULT_AGENT_MAX_STEPS
    int agentTimeoutSeconds = DEFAULT_AGENT_TIMEOUT_SECONDS
    int agentMaxTokensPerRun = DEFAULT_AGENT_MAX_TOKENS_PER_RUN

    /** Copy that is safe to send to the UI: the API key is replaced by "****" + its last 4 characters. */
    Configuracoes masked() {
        def copy = new Configuracoes(
                driver: driver, waitingTimeout: waitingTimeout, extractionThreads: extractionThreads,
                rootPath: rootPath, downloadPath: downloadPath, extractionPath: extractionPath,
                agentMode: agentMode, llmProvider: llmProvider, llmModel: llmModel,
                agentMaxSteps: agentMaxSteps, agentTimeoutSeconds: agentTimeoutSeconds,
                agentMaxTokensPerRun: agentMaxTokensPerRun)
        copy.llmApiKey = llmApiKey ? "****" + llmApiKey.takeRight(4) : null
        return copy
    }
}

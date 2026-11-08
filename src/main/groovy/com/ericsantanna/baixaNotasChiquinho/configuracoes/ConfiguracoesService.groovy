package com.ericsantanna.baixaNotasChiquinho.configuracoes

import groovy.yaml.YamlBuilder
import groovy.yaml.YamlSlurper
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class ConfiguracoesService {
    private static final log = LoggerFactory.getLogger(ConfiguracoesService.class)

    YamlSlurper yamlSlurper = new YamlSlurper()
    YamlBuilder yamlBuilder = new YamlBuilder()

    private Configuracoes configuracoes

    String saveConfigurations(ConfiguracoesController.ConfiguracoesRequest request) {
        try {
            def configuracoes = new Configuracoes()
            configuracoes.driver = request.driver ?: Configuracoes.DEFAULT_DRIVER
            configuracoes.waitingTimeout = request.defaultWaitingTimeout ?: Configuracoes.DEFAULT_WAITING_TIMEOUT
            configuracoes.extractionThreads = request.extractionThreads ?: Configuracoes.DEFAULT_EXTRACTION_THREADS
            configuracoes.rootPath = request.rootPath ? new File(request.rootPath): Configuracoes.DEFAULT_ROOT_PATH
            configuracoes.downloadPath = request.downloadPath ? new File(request.downloadPath) : Configuracoes.DEFAULT_DOWNLOAD_PATH
            configuracoes.extractionPath = request.extractionPath ? new File(request.extractionPath) : Configuracoes.DEFAULT_EXTRACTION_PATH

            String mode = (request.agentMode ?: Configuracoes.DEFAULT_AGENT_MODE).toUpperCase()
            configuracoes.agentMode = mode in ["OFF", "FALLBACK", "FULL"] ? mode : Configuracoes.DEFAULT_AGENT_MODE
            configuracoes.llmModel = request.llmModel?.trim() ?: Configuracoes.DEFAULT_LLM_MODEL
            configuracoes.agentMaxSteps = positive(request.agentMaxSteps, Configuracoes.DEFAULT_AGENT_MAX_STEPS)
            configuracoes.agentTimeoutSeconds = positive(request.agentTimeoutSeconds, Configuracoes.DEFAULT_AGENT_TIMEOUT_SECONDS)
            configuracoes.agentMaxTokensPerRun = positive(request.agentMaxTokensPerRun, Configuracoes.DEFAULT_AGENT_MAX_TOKENS_PER_RUN)
            // the UI only ever sees a masked key: missing (null) or masked ("****...") keeps the stored one,
            // an explicitly empty value clears it, anything else replaces it
            String submittedKey = request.llmApiKey?.trim()
            if (submittedKey == null || submittedKey.startsWith("****")) {
                configuracoes.llmApiKey = getConfiguracoes().llmApiKey
            } else {
                configuracoes.llmApiKey = submittedKey ?: null
            }

            def configPath = Configuracoes.DEFAULT_CONFIG_PATH
            configuracoes.rootPath.mkdirs()

            yamlBuilder.call([
                driver: configuracoes.driver,
                waitingTimeout: configuracoes.waitingTimeout,
                extractionThreads: configuracoes.extractionThreads,
                rootDir: configuracoes.rootPath.toString(),
                downloadDir: configuracoes.downloadPath.toString(),
                extractionDir: configuracoes.extractionPath.toString(),
                agentMode: configuracoes.agentMode,
                llmProvider: configuracoes.llmProvider,
                llmModel: configuracoes.llmModel,
                llmApiKey: configuracoes.llmApiKey ?: "",
                agentMaxSteps: configuracoes.agentMaxSteps,
                agentTimeoutSeconds: configuracoes.agentTimeoutSeconds,
                agentMaxTokensPerRun: configuracoes.agentMaxTokensPerRun
            ])
            restrictToOwner(configPath) // the file may now hold an API key
            configPath.text = yamlBuilder.toString()
            this.configuracoes = configuracoes

            return "Configurações salvas com sucesso"
        } catch (Exception e) {
            log.error "Erro ao salvar configurações: ${e.message}", e
        }
    }

    private static int positive(Integer value, int fallback) {
        return (value != null && value > 0) ? value : fallback
    }

    /** Best effort chmod 600 (no-op where POSIX permissions do not exist, e.g. Windows). */
    private static void restrictToOwner(File file) {
        try {
            if (!file.exists()) {
                file.createNewFile()
            }
            file.setReadable(false, false)
            file.setWritable(false, false)
            file.setReadable(true, true)
            file.setWritable(true, true)
        } catch (Exception ignored) {
        }
    }

    Configuracoes getConfiguracoes() {
        if (configuracoes != null) {
            return configuracoes
        }

        def configuracoes = new Configuracoes()
        configuracoes.rootPath = Configuracoes.DEFAULT_ROOT_PATH

        try {
            def configs = yamlSlurper.parse(configuracoes.DEFAULT_CONFIG_PATH)

            configuracoes.driver = configs["driver"] ?: Configuracoes.DEFAULT_DRIVER
            configuracoes.waitingTimeout = configs["waitingTimeout"] ? configs["waitingTimeout"] as int : Configuracoes.DEFAULT_WAITING_TIMEOUT
            configuracoes.extractionThreads = configs["extractionThreads"] ? configs["extractionThreads"] as int : Configuracoes.DEFAULT_EXTRACTION_THREADS
            configuracoes.downloadPath = configs["downloadDir"] ? new File(configs["downloadDir"] as String) : Configuracoes.DEFAULT_DOWNLOAD_PATH
            configuracoes.extractionPath = configs["extractionDir"] ? new File(configs["extractionDir"] as String) : Configuracoes.DEFAULT_EXTRACTION_PATH

            // agent settings are optional: configs saved before the agent existed simply lack them
            String mode = ((configs["agentMode"] ?: Configuracoes.DEFAULT_AGENT_MODE) as String).toUpperCase()
            configuracoes.agentMode = mode in ["OFF", "FALLBACK", "FULL"] ? mode : Configuracoes.DEFAULT_AGENT_MODE
            configuracoes.llmProvider = (configs["llmProvider"] ?: Configuracoes.DEFAULT_LLM_PROVIDER) as String
            configuracoes.llmModel = (configs["llmModel"] ?: Configuracoes.DEFAULT_LLM_MODEL) as String
            configuracoes.llmApiKey = configs["llmApiKey"] ? configs["llmApiKey"] as String : null
            configuracoes.agentMaxSteps = positive(configs["agentMaxSteps"] as Integer, Configuracoes.DEFAULT_AGENT_MAX_STEPS)
            configuracoes.agentTimeoutSeconds = positive(configs["agentTimeoutSeconds"] as Integer, Configuracoes.DEFAULT_AGENT_TIMEOUT_SECONDS)
            configuracoes.agentMaxTokensPerRun = positive(configs["agentMaxTokensPerRun"] as Integer, Configuracoes.DEFAULT_AGENT_MAX_TOKENS_PER_RUN)
            this.configuracoes = configuracoes

            return configuracoes
        } catch (Exception e) {
            log.warn "Erro ao carregar configurações: ${e.message}. Usando valores padrão.", e
            configuracoes.driver = Configuracoes.DEFAULT_DRIVER
            configuracoes.waitingTimeout = Configuracoes.DEFAULT_WAITING_TIMEOUT
            configuracoes.extractionThreads = Configuracoes.DEFAULT_EXTRACTION_THREADS
            configuracoes.rootPath = Configuracoes.DEFAULT_ROOT_PATH
            configuracoes.downloadPath = Configuracoes.DEFAULT_DOWNLOAD_PATH
            configuracoes.extractionPath = Configuracoes.DEFAULT_EXTRACTION_PATH
            this.configuracoes = configuracoes
            return configuracoes
        }
    }
}
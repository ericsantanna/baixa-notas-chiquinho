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

            def configPath = Configuracoes.DEFAULT_CONFIG_PATH
            configuracoes.rootPath.mkdirs()

            yamlBuilder.call([
                driver: configuracoes.driver,
                waitingTimeout: configuracoes.waitingTimeout,
                extractionThreads: configuracoes.extractionThreads,
                rootDir: configuracoes.rootPath.toString(),
                downloadDir: configuracoes.downloadPath.toString(),
                extractionDir: configuracoes.extractionPath.toString()
            ])
            configPath.text = yamlBuilder.toString()
            this.configuracoes = configuracoes

            return "Configurações salvas com sucesso"
        } catch (Exception e) {
            log.error "Erro ao salvar configurações: ${e.message}", e
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
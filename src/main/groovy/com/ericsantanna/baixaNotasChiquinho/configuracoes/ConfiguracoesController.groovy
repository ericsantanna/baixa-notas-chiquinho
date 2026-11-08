package com.ericsantanna.baixaNotasChiquinho.configuracoes

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/configuracoes")
class ConfiguracoesController {

    private final ConfiguracoesService configuracoesService

    ConfiguracoesController(ConfiguracoesService configuracoesService) {
        this.configuracoesService = configuracoesService
    }

    @PostMapping
    String saveConfigurations(@RequestBody ConfiguracoesRequest request) {
        return configuracoesService.saveConfigurations(request)
    }

    @GetMapping
    Configuracoes getConfigurations() {
        // never expose the API key to the browser
        return configuracoesService.getConfiguracoes().masked()
    }

    static class ConfiguracoesRequest {
        String driver
        Integer defaultWaitingTimeout
        Integer extractionThreads
        String rootPath
        String downloadPath
        String extractionPath

        String agentMode
        String llmModel
        /** Blank or starting with "****" means "keep the stored key". */
        String llmApiKey
        Integer agentMaxSteps
        Integer agentTimeoutSeconds
        Integer agentMaxTokensPerRun
    }
}
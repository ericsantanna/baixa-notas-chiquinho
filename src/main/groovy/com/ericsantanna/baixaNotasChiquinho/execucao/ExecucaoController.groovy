package com.ericsantanna.baixaNotasChiquinho.execucao

import com.ericsantanna.baixaNotasChiquinho.logs.LogWebSocketHandler
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api")
class ExecucaoController {
    private static final log = LoggerFactory.getLogger(ExecucaoController.class)
    private final ExecucaoService execucaoService

    ExecucaoController(ExecucaoService execucaoService) {
        this.execucaoService = execucaoService
    }

    @PostMapping("/executar")
    String executar(@RequestBody ExecutarRequest request) {
        return execucaoService.executar(request.usuario, request.senha, request.mes, request.ano)
    }

    @PostMapping("/interromper")
    String interromper() {
        return execucaoService.interromper()
    }

    @GetMapping("/status")
    Map status() {
        return ["status": execucaoService.executionStatus()]
    }

    @GetMapping("/franqueadoras")
    List<String> franqueadoraList() {
        return execucaoService.franqueadoraList()
    }

    @PostMapping("/cleanup")
    void cleanup() {
        execucaoService.cleanup()
    }

    @Autowired
    LogWebSocketHandler logWebSocketHandler

    @GetMapping("/broadcastTestLogs")
    void broadcastTestLogs() {
        for (int i = 0; i < 5; i++) {
            log.debug("Test debug message ${i} at ${new Date()}")
            log.info("Test info message ${i} at ${new Date()}")
            log.warn("Test warn message ${i} at ${new Date()}")
            log.error("Test error message ${i} at ${new Date()}")
            Thread.sleep(500)
        }
    }

    static class ExecutarRequest {
        String usuario
        String senha
        String mes
        String ano
    }
}

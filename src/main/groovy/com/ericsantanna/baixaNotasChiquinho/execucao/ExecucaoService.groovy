package com.ericsantanna.baixaNotasChiquinho.execucao


import com.ericsantanna.baixaNotasChiquinho.automation.scripts.BaixaNotasChiquinhoAutomation
import com.ericsantanna.baixaNotasChiquinho.configuracoes.Configuracoes
import com.ericsantanna.baixaNotasChiquinho.configuracoes.ConfiguracoesService
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service

import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future

@Service
class ExecucaoService {
    private static final log = LoggerFactory.getLogger(ExecucaoService.class)

    @Autowired
    ConfiguracoesService configuracoesService

    @Autowired
    BaixaNotasChiquinhoAutomation script

    @Autowired
    ExecutionStatusWebSocketHandler executionStatusWebSocketHandler

    List<ExecutionStatus> statusList = new CopyOnWriteArrayList<ExecutionStatus>()

    ExecutorService executorService = Executors.newSingleThreadExecutor()
    Future<?> runningTask

    String executar(String usuario, String senha, String mes, String ano) {
        if (runningTask != null && !runningTask.isDone()) {
            return "Process already running"
        }
        runningTask = executorService.submit {
            try {
                script.execute(usuario, senha, mes, ano, statusList) {
                    executionStatusWebSocketHandler.updateStatusList(statusList)
                }
            } catch (Exception e) {
                log.error "Error during execution: ${e.message}", e
            }
        }
        return "Process started"
    }

    String interromper() {
        if (runningTask != null && !runningTask.isDone()) {
            try {
                script.currentBrowser?.close()
            } catch (Throwable e) {
                // window may already be gone; still must cancel the task
                log.warn "Could not close browser while interrupting: ${e.message?.readLines()?.first()}"
            }
            runningTask.cancel(true)
            return "Process interrupted"
        }
        return "No process running"
    }

    String executionStatus() {
        if (runningTask == null) {
            return "NOT_STARTED"
        }
        if (runningTask.isCancelled()) {
            return "CANCELLED"
        }
        if (runningTask.isDone()) {
            return script.lastOutcome ?: "COMPLETED"
        }

        return "RUNNING"
    }

    List<String> franqueadoraList() {
        def configuracoes = configuracoesService.getConfiguracoes()
        def todasFranqueadorasFile = configuracoes.DEFAULT_FRANQUEADORAS_PATH
        todasFranqueadorasFile.exists() ? todasFranqueadorasFile.readLines() : []
    }

    void cleanup() {
        try {
            def configurations = configuracoesService.getConfiguracoes()

            // Clear processados file
            def processadosFile = Configuracoes.DEFAULT_PROCESSADOS_PATH
            if (processadosFile.exists()) {
                processadosFile.delete()
            }

            // Clear nao-processados file
            def naoProcessadosFile = Configuracoes.DEFAULT_NAO_PROCESSADOS_PATH
            if (naoProcessadosFile.exists()) {
                naoProcessadosFile.delete()
            }

            // Clear download directory
            def downloadDir = configurations.downloadPath
            if (downloadDir.exists()) {
                downloadDir.listFiles()?.each { file ->
                    if (file.isFile()) {
                        file.delete()
                    }
                }
            }

            log.info "Cleanup completed successfully"
        } catch (Exception e) {
            log.error "Error during cleanup: ${e.message}", e
        }
    }
}

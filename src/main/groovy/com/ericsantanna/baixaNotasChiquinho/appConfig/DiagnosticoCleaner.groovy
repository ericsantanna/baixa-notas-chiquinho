package com.ericsantanna.baixaNotasChiquinho.appConfig

import com.ericsantanna.baixaNotasChiquinho.configuracoes.Configuracoes
import groovy.util.logging.Slf4j
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path

/**
 * Empties execucao/diagnostico (failure screenshots, HTML dumps, agent traces) when the app starts.
 * Those files only matter for the run that produced them, and they grow every month.
 * Only the CONTENT is removed; the folder itself stays. Symlinks are deleted, never followed.
 */
@Slf4j
@Component
class DiagnosticoCleaner {

    @EventListener(ApplicationReadyEvent)
    void aoIniciar() {
        limpar(new File(Configuracoes.DEFAULT_ROOT_PATH, "diagnostico"))
    }

    /** @return number of files removed. Never throws: a cleanup problem must not stop the app. */
    static int limpar(File dir) {
        try {
            Path root = dir.toPath()
            if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) {
                return 0
            }
            int removidos = 0
            long bytes = 0
            Files.walk(root).withCloseable { stream ->
                // deepest first so folders are empty when deleted
                stream.sorted(Comparator.reverseOrder()).each { Path p ->
                    if (p == root) {
                        return
                    }
                    try {
                        if (Files.isRegularFile(p, LinkOption.NOFOLLOW_LINKS)) {
                            bytes += Files.size(p)
                        }
                        Files.delete(p)
                        removidos++
                    } catch (IOException e) {
                        log.warn "Não foi possível apagar ${p}: ${e.message}"
                    }
                }
            }
            if (removidos > 0) {
                log.info "Diagnóstico limpo na inicialização: ${removidos} itens, ${(bytes / 1024 / 1024).round(1)} MB"
            }
            return removidos
        } catch (Throwable e) {
            log.warn "Falha ao limpar a pasta de diagnóstico: ${e.message}"
            return 0
        }
    }
}

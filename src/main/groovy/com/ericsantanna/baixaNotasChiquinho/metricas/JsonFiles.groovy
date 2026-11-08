package com.ericsantanna.baixaNotasChiquinho.metricas

import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import org.slf4j.LoggerFactory

import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** Small JSON-on-disk helpers: atomic writes, and reads that fall back instead of throwing. */
class JsonFiles {
    private static final log = LoggerFactory.getLogger(JsonFiles)

    static Object read(File file, Object fallback) {
        if (!file.exists()) {
            return fallback
        }
        try {
            def parsed = new JsonSlurper().parse(file, 'UTF-8')
            return parsed == null ? fallback : parsed
        } catch (Exception e) {
            log.warn "Não foi possível ler ${file}: ${e.message}. Usando o padrão."
            return fallback
        }
    }

    /** @return false (after logging) when the file could not be written; never throws */
    static boolean write(File file, Object value) {
        try {
            file.parentFile.mkdirs()
            File tmp = new File(file.parentFile, file.name + '.tmp')
            tmp.setText(JsonOutput.toJson(value), 'UTF-8')
            try {
                Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            } catch (Exception ignored) {
                Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
            return true
        } catch (Exception e) {
            log.warn "Não foi possível gravar ${file}: ${e.message}"
            return false
        }
    }
}

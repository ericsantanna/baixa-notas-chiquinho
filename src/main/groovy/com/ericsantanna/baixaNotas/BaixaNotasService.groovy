package com.ericsantanna.baixaNotas

import com.ericsantanna.baixaNotas.automation.scripts.Script
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

import java.util.concurrent.CompletableFuture

@Service
class BaixaNotasService {
    @Value('${baixa-notas.root-dir}')
    String rootDir

    @Value('${baixa-notas.download-dir}')
    String downloadDir

    @Value('${baixa-notas.usuario}')
    String usuario

    @Value('${baixa-notas.senha}')
    String senha

    @Value('${baixa-notas.mes}')
    String mes

    @Value('${baixa-notas.ano}')
    String ano

    String executar() {
        CompletableFuture.runAsync { Script.runScript(usuario, senha, mes, ano, rootDir, downloadDir) }
        return "Process started"
    }
}

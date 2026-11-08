package com.ericsantanna.baixaNotasChiquinho.metricas

import com.ericsantanna.baixaNotasChiquinho.configuracoes.ConfiguracoesService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/** Backend of the "Métricas" UI page. Read-only over the history, except deleting an execution and the prices. */
@RestController
@RequestMapping("/api/metricas")
class MetricasController {

    private final ConfiguracoesService configuracoesService

    MetricasController(ConfiguracoesService configuracoesService) {
        this.configuracoesService = configuracoesService
    }

    /** Same folder the execution writes to (see BaixaNotasChiquinhoAutomation). */
    protected ExecutionHistoryStore store() {
        new ExecutionHistoryStore(new File(configuracoesService.getConfiguracoes().rootPath, "metricas"))
    }

    @GetMapping("/resumo")
    Map resumo() {
        def s = store()
        List<Map> execs = s.list()
        Map precos = s.prices()
        return [agregado        : MetricsCalculator.agregado(execs, precos),
                // newest first: the page shows the latest execution at the top
                execucoes       : execs.reverse().collect { MetricsCalculator.resumo(it, precos) },
                maisLentas      : MetricsCalculator.maisLentas(execs),
                maisProblematicas: MetricsCalculator.maisProblematicas(execs),
                porCategoria    : MetricsCalculator.porCategoria(execs),
                precos          : precos]
    }

    @GetMapping("/execucoes/{id}")
    ResponseEntity<Map> detalhe(@PathVariable("id") String id) {
        def s = store()
        Map exec = s.find(id)
        return exec ? ResponseEntity.ok(MetricsCalculator.detalhe(exec, s.prices())) :
                ResponseEntity.status(HttpStatus.NOT_FOUND).body([erro: "Execução não encontrada"])
    }

    @DeleteMapping("/execucoes/{id}")
    ResponseEntity<Map> excluir(@PathVariable("id") String id) {
        return store().delete(id) ? ResponseEntity.ok(resumo()) :
                ResponseEntity.status(HttpStatus.NOT_FOUND).body([erro: "Execução não encontrada"])
    }

    @PutMapping("/precos")
    Map salvarPrecos(@RequestBody PrecosRequest request) {
        store().savePrices(request.entradaPorMilhao, request.saidaPorMilhao)
        return resumo()
    }

    @ExceptionHandler(IllegalArgumentException)
    ResponseEntity<Map> invalido(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body([erro: e.message])
    }

    static class PrecosRequest {
        Double entradaPorMilhao
        Double saidaPorMilhao
    }
}

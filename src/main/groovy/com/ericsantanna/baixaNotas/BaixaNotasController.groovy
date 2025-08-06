package com.ericsantanna.baixaNotas

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
class BaixaNotasController {
    private final BaixaNotasService baixaNotasService

    BaixaNotasController(BaixaNotasService baixaNotasService) {
        this.baixaNotasService = baixaNotasService
    }

    @GetMapping("/executar")
    String executar(
            @RequestParam("usuario") String usuario,
            @RequestParam("senha") String senha,
            @RequestParam("mes") String mes,
            @RequestParam("ano") String ano
    ) {
        return baixaNotasService.executar(usuario, senha, mes, ano)
    }
}


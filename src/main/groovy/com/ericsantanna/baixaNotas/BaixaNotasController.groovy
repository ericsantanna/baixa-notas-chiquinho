package com.ericsantanna.baixaNotas

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class BaixaNotasController {
    private final BaixaNotasService baixaNotasService

    BaixaNotasController(BaixaNotasService baixaNotasService) {
        this.baixaNotasService = baixaNotasService
    }

    @GetMapping("/executar")
    String executar() {
        return baixaNotasService.executar()
    }
}

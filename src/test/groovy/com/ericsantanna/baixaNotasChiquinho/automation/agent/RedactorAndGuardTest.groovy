package com.ericsantanna.baixaNotasChiquinho.automation.agent

import org.junit.jupiter.api.Test

class RedactorAndGuardTest {

    @Test
    void redactsFiscalIdentifiersAndAmounts() {
        assert Redactor.redact('CNPJ 12.345.678/0001-95') == 'CNPJ [CNPJ]'
        assert Redactor.redact('CNPJ 12345678000195') == 'CNPJ [CNPJ]'
        assert Redactor.redact('CPF 123.456.789-09') == 'CPF [CPF]'
        assert Redactor.redact('Total R$ 1.234,56 ok') == 'Total [VALOR] ok'
        assert Redactor.redact('R$ 10') == '[VALOR]'
        assert Redactor.redact('1' * 44) == '[CHAVE]'
        assert Redactor.redact('protocolo 1234567890123') == 'protocolo [NUM]'
    }

    @Test
    void keepsNamesMonthsAndYearsReadable() {
        assert Redactor.redact('VILA VELHA 03') == 'VILA VELHA 03'
        assert Redactor.redact('Janeiro 2026') == 'Janeiro 2026'
        assert Redactor.redact(null) == null
        assert Redactor.redact('') == ''
    }

    @Test
    void blocksDestructiveOrSessionEndingClicks() {
        assert SeleniumBrowserPort.motivoBloqueio('Sair', '#')
        assert SeleniumBrowserPort.motivoBloqueio('Excluir nota', '')
        assert SeleniumBrowserPort.motivoBloqueio('Cancelar', null)
    }

    @Test
    void blocksLinksLeavingTheSite() {
        assert SeleniumBrowserPort.motivoBloqueio('Ajuda', 'https://evil.example.com/x')
        assert SeleniumBrowserPort.motivoBloqueio('Ajuda', 'https://itfgestor.com.br.evil.com/x')
    }

    @Test
    void allowsNormalNavigation() {
        assert SeleniumBrowserPort.motivoBloqueio('Fiscal', '#') == null
        assert SeleniumBrowserPort.motivoBloqueio('Consulta Nota Fiscal Venda PDV2', 'https://www.itfgestor.com.br/ITFGestor/relCupomFiscalEmtidio.jsf') == null
        assert SeleniumBrowserPort.motivoBloqueio('VILA VELHA 03', 'javascript:void(0)') == null
    }

    @Test
    void observationJsonRoundTrip() {
        def obs = Observation.fromJson('{"url":"https://www.itfgestor.com.br/a","title":"T","header":"H","banner":"","overlay":true,"total":3,"elements":[{"id":1,"tag":"a","text":"Fiscal","href":"#","enabled":true},{"id":2,"tag":"button","text":"Pesquisar","href":"","enabled":false}]}')
        assert obs.dominioPermitido()
        assert obs.overlay
        assert obs.byId(2).enabled == false
        def prompt = obs.toPrompt()
        assert prompt.contains('[1] a "Fiscal"')
        assert prompt.contains('(desabilitado)')
        assert prompt.contains('2 de 3')
    }
}

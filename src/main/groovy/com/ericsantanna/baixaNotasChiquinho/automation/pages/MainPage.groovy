package com.ericsantanna.baixaNotasChiquinho.automation.pages

import com.ericsantanna.baixaNotasChiquinho.automation.modules.HeaderModule
import geb.Page

class MainPage extends Page {
    static url = "https://www.itfgestor.com.br/ITFGestor/paginaInicial.jsf"
    static atCheckWaiting = true

    static at = {
        def empresaValue = franqueadora ?: 'FRANQUEADORA'
        waitFor {
            def headerDiv = $('div#layout-header')
            def widget = headerDiv.find('div', class: 'widgets-item', 1)
            widget.displayed
            && widget?.text()?.readLines()?.find { it.trim() }?.trim()?.toUpperCase() == empresaValue.trim().toUpperCase()
        }
    }

    static content = {
        header { module HeaderModule }
        sideMenu(wait: true) { $('#layout-menubar') }
        fiscalMenu { sideMenu.find('li a', text: 'Fiscal') }
        gerenciamentoMenu { fiscalMenu.parent().find('li>a', text: 'Gerenciamento') }
        consultaNotaFiscalVendaPdv2Menu {
            gerenciamentoMenu.parent().find('li>a', text: 'Consulta Nota Fiscal Venda PDV2')
        }
    }

    String franqueadora

    /**
     * Opens the "Consulta Nota Fiscal Venda PDV2" entry of the side menu (Fiscal > Gerenciamento > ...).
     * The item is located by its text anywhere in the menu tree, then every collapsed ancestor submenu is
     * expanded top-down (accordion toggles), and finally the item is clicked. Independent of nesting depth.
     */
    void abrirConsultaNotaFiscalVendaPdv2() {
        def alvo = 'Consulta Nota Fiscal Venda PDV2'
        def script = """
            var alvo = arguments[0];
            var links = Array.prototype.slice.call(document.querySelectorAll('#layout-menubar a'));
            var norm = function (x) { return x.textContent.replace(/\\s+/g, ' ').trim(); };
            var a = links.filter(function (x) { return (x.getAttribute('href') || '').indexOf('relCupomFiscalEmtidio.jsf') >= 0; })[0]
                 || links.filter(function (x) { return norm(x) === alvo; })[0]
                 || links.filter(function (x) { return norm(x).toUpperCase().indexOf('PDV2') >= 0; })[0];
            if (!a) { return null; }
            var chain = [];
            var li = a.closest('li');
            var p = li.parentElement ? li.parentElement.closest('li') : null;
            while (p) {
                chain.unshift(p.querySelector(':scope > a'));
                p = p.parentElement ? p.parentElement.closest('li') : null;
            }
            chain.push(a);
            return chain;
        """

        def chain = waitFor {
            def found = browser.driver.executeScript(script, alvo)
            found ?: false
        }

        for (int i = 0; i < chain.size() - 1; i++) {
            def toggle = chain[i]
            def next = chain[i + 1]
            if (!next.displayed) {
                toggle.click()
                waitFor { next.displayed }
            }
        }
        chain.last().click()
    }
}

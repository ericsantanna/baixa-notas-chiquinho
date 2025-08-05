package com.ericsantanna.baixaNotas.automation.pages

import com.ericsantanna.baixaNotas.automation.modules.HeaderModule
import geb.Page

class MainPage extends Page {
    static url = "https://www.itfgestor.com.br/ITFGestor/paginaInicial.jsf"
    static atCheckWaiting = true

    static at = {
        def empresaValue = franqueadora ?: 'FRANQUEADORA'
        waitFor {
            def headerDiv = $('div#layout-header')
            def widget = headerDiv.find('div', class: 'widgets-item', 1)
            widget.text().toUpperCase() == empresaValue.toUpperCase()
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
}

package com.ericsantanna.baixaNotas.automation.modules

import geb.Module

class HeaderModule extends Module {
    static content = {
        headerDiv {
            waitFor { $('div#layout-header') }
        }
        franqueadoraMenu {
            waitFor {
                !$('div.ui-widget-overlay.ui-dialog-mask').displayed
                headerDiv.find('div', class: 'widgets-item', 1)
            }
        }
        listaDeFranqueadorasMenu {
            franqueadoraMenu?.find('div.lista-empresas')?.find('li')
        }
        getInFranqueadoraMenu { franqueadora ->
            listaDeFranqueadorasMenu.find { it.text().toUpperCase() == franqueadora.toUpperCase() }
        }
    }
}

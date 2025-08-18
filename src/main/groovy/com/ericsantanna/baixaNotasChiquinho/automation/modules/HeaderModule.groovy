package com.ericsantanna.baixaNotasChiquinho.automation.modules

import geb.Module

class HeaderModule extends Module {
    static content = {
        headerDiv {
            waitFor { $('div#layout-header') }
        }
        franqueadoraMenu {
            def menuElement = headerDiv.find('div', class: 'widgets-item', 1)
            def overlay = $('div.ui-widget-overlay.ui-dialog-mask')
            waitFor {
                !overlay.displayed
                && menuElement.displayed
                && menuElement.@('onclick') != null
            }
            headerDiv.find('div', class: 'widgets-item', 1)
        }
        listaDeFranqueadorasMenu {
            def menu = franqueadoraMenu?.find('div.lista-empresas')
            waitFor {
                menu?.displayed
                && menu?.find('li')?.size() > 0
            }
            menu?.find('li')
        }
        getInFranqueadoraMenu { franqueadora ->
            waitFor {
                listaDeFranqueadorasMenu.find { it.text().toUpperCase() == franqueadora.toUpperCase() }
            }
        }
    }
}

package com.ericsantanna.baixaNotas.automation.pages

import com.ericsantanna.baixaNotas.automation.modules.HeaderModule
import geb.Page

class ConsultaCupomFiscalPdv2Page extends Page {
    static url = "https://www.itfgestor.com.br/ITFGestor/relCupomFiscalEmtidio.jsf"
    static atCheckWaiting = true

    static at = {
        $('div.headerTitle h4 span', text: 'Consulta Cupom Fiscal PDV').size() == 1
    }

    static content = {
        header(wait: true) { module HeaderModule }
        form { $('form', name: 'formNotaFiscal') }
        mesSelect { form.find('div', id: 'formNotaFiscal:comboMes') }
        temMes(required: false, wait: true) { option ->
            $('ul', id: 'formNotaFiscal:comboMes_items')?.find('li', text: option)
        }
        mesSelectOption(wait: true) { option -> $('ul', id: 'formNotaFiscal:comboMes_items')?.find('li', text: option) }
        anoSelect {
            waitFor { $('ul', id: 'formNotaFiscal:comboMes_panel').empty || !$('ul', id: 'formNotaFiscal:comboMes_panel').displayed }
            form.find('div', id: 'formNotaFiscal:comboAno')
        }
        temAno(required: false, wait: true) { option ->
            $('ul', id: 'formNotaFiscal:comboAno_items')?.find('li', text: option)
        }
        anoSelectOption(wait: true) { option -> $('ul', id: 'formNotaFiscal:comboAno_items').find('li', text: option) }
        pesquisarButton { form.find('button', id: 'formNotaFiscal:botaoPesquisar') }
        semResultados { form.find('div', id: 'formNotaFiscal:tabelaResultado')?.find('tr', class: 'ui-datatable-empty-message')?.find('td', 0)?.text() == 'Nenhum Registro Encontrado.' }
        pesquisaRetornouConteudo {
            waitFor(60) {
                   form.find('div', id: 'formNotaFiscal:tabelaResultado')?.find('tr', class: 'ui-datatable-empty-message')?.find('td', text: 'Nenhum Registro Encontrado.', 0)
                || form.find('button', class: 'buttomXml')
            }

            if (form.find('div', id: 'formNotaFiscal:tabelaResultado')?.find('tr', class: 'ui-datatable-empty-message')?.find('td', text: 'Nenhum Registro Encontrado.', 0)) {
                return false
            }

            if (form.find('button', class: 'buttomXml')) {
                return true
            }

            return false
        }
        exportarXmlButton {
            form.find('button', class: 'buttomXml')
        }
    }
}

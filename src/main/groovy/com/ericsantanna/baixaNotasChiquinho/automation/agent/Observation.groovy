package com.ericsantanna.baixaNotasChiquinho.automation.agent

import groovy.json.JsonSlurper

class ElementInfo {
    int id
    String tag
    String text
    String href
    /** Short explanation of what the element is, for elements the model could not otherwise recognise. */
    String hint = ''
    boolean enabled = true
}

/** Text snapshot of the page the LLM reasons about (never a screenshot). */
class Observation {
    static final String ALLOWED_HOST_SUFFIX = 'itfgestor.com.br'

    String url = ''
    String title = ''
    /** First line of the header widget = franchise currently selected in the ITFGestor header. */
    String header = ''
    String banner = ''
    boolean overlay
    int totalElements
    List<ElementInfo> elements = []

    ElementInfo byId(int id) {
        elements.find { it.id == id }
    }

    List<ElementInfo> findByText(String text) {
        String wanted = normalize(text)
        elements.findAll { normalize(it.text) == wanted }
    }

    String host() {
        try {
            return new URI(url).host ?: ''
        } catch (Exception ignored) {
            return ''
        }
    }

    /** The agent must never operate outside ITFGestor. */
    boolean dominioPermitido() {
        String h = host().toLowerCase()
        return h == ALLOWED_HOST_SUFFIX || h.endsWith('.' + ALLOWED_HOST_SUFFIX)
    }

    /** Cheap fingerprint used to detect the agent looping on an unchanged page. */
    int signature() {
        return (header + '|' + elements.collect { it.text }.join('|')).hashCode()
    }

    String toPrompt() {
        def sb = new StringBuilder()
        sb << "URL: ${Redactor.redact(url)}\n"
        sb << "TÍTULO: ${Redactor.redact(title)}\n"
        sb << "FRANQUEADORA ATUAL NO CABEÇALHO: ${Redactor.redact(header)}\n"
        if (overlay) {
            sb << "AVISO: há um overlay/diálogo bloqueando a página (use wait e aguarde).\n"
        }
        if (banner) {
            sb << "MENSAGENS NA TELA: ${Redactor.redact(banner)}\n"
        }
        sb << "ELEMENTOS VISÍVEIS (${elements.size()} de ${totalElements}):\n"
        elements.each { e ->
            sb << "[${e.id}] ${e.tag} \"${Redactor.redact(e.text)}\"${e.enabled ? '' : ' (desabilitado)'}${e.hint ? ' - ' + e.hint : ''}\n"
        }
        return sb.toString()
    }

    static String normalize(String s) {
        (s ?: '').replaceAll(/\s+/, ' ').trim().toUpperCase()
    }

    static Observation fromJson(String json) {
        def m = new JsonSlurper().parseText(json)
        def obs = new Observation(
                url: m.url ?: '',
                title: m.title ?: '',
                header: m.header ?: '',
                banner: m.banner ?: '',
                overlay: m.overlay == true,
                totalElements: (m.total ?: 0) as int)
        (m.elements ?: []).each {
            obs.elements << new ElementInfo(id: it.id as int, tag: it.tag, text: it.text, href: it.href,
                    hint: it.hint ?: '', enabled: it.enabled != false)
        }
        return obs
    }
}

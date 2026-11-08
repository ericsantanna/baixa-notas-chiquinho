package com.ericsantanna.baixaNotasChiquinho.automation.agent

import org.openqa.selenium.By
import org.openqa.selenium.JavascriptExecutor
import org.openqa.selenium.StaleElementReferenceException
import org.openqa.selenium.WebDriver
import org.openqa.selenium.WebElement

/** {@link BrowserPort} over a Selenium driver. All safety guards live here, not in the prompt. */
class SeleniumBrowserPort implements BrowserPort {
    static final int MAX_ELEMENTS = 200

    private static final String OBSERVE_JS = AgentResources.load('observe.js')
    private static final String SCROLL_JS = AgentResources.load('scroll.js')
    private static final String READY_JS =
            "return document.readyState === 'complete' && (typeof PrimeFaces === 'undefined' || !PrimeFaces.ajax || " +
                    "!PrimeFaces.ajax.Queue || PrimeFaces.ajax.Queue.isEmpty());"

    private final WebDriver driver
    private final Closure goHomeAction
    private final Closure openConsultaAction
    private final Closure selectFranqueadoraAction

    SeleniumBrowserPort(WebDriver driver, Closure goHomeAction, Closure openConsultaAction, Closure selectFranqueadoraAction) {
        this.driver = driver
        this.goHomeAction = goHomeAction
        this.openConsultaAction = openConsultaAction
        this.selectFranqueadoraAction = selectFranqueadoraAction
    }

    @Override
    Observation observe() {
        return Observation.fromJson(js(OBSERVE_JS, MAX_ELEMENTS) as String)
    }

    @Override
    String click(int id) {
        WebElement el = find(id)
        String text = (el.getAttribute('textContent') ?: el.getAttribute('title') ?: '').replaceAll(/\s+/, ' ').trim()
        String href = el.getAttribute('href') ?: ''

        // a big container can mention "Sair" deep inside and still be a legitimate navigation target; judge
        // short labels by their full text and containers by their own text/title/aria-label only
        String rotulo = text
        if (text.length() > 40) {
            rotulo = (js("var e = arguments[0]; return Array.prototype.filter.call(e.childNodes, function (n) { return n.nodeType === 3; })" +
                    ".map(function (n) { return n.textContent; }).join(' ') + ' ' + (e.getAttribute('title') || '') + ' ' + " +
                    "(e.getAttribute('aria-label') || '') + ' ' + (e.value || '');", el) as String ?: '').replaceAll(/\s+/, ' ').trim()
        }
        String bloqueio = motivoBloqueio(rotulo, href)
        if (bloqueio) {
            throw new AgentToolException(bloqueio)
        }

        js("arguments[0].scrollIntoView({block: 'center', inline: 'nearest'});", el)
        try {
            el.click()
        } catch (StaleElementReferenceException ignored) {
            // the click already reloaded the page
        } catch (Exception ignored) {
            try {
                js("arguments[0].click();", el)
            } catch (StaleElementReferenceException ignored2) {
                // page reloaded
            }
        }
        aguardarEstabilizar()
        return "clicou em [${id}] \"${Redactor.redact(text.take(60))}\""
    }

    @Override
    String scroll(String direction, Integer id) {
        if (id != null) {
            js("arguments[0].scrollIntoView({block: 'center', inline: 'nearest'});", find(id))
            return "elemento [${id}] centralizado na tela"
        }
        int delta = (direction == 'up') ? -500 : 500
        def moved = js(SCROLL_JS, delta)
        Thread.sleep(300)
        return "rolou ${direction == 'up' ? 'para cima' : 'para baixo'} (listas roláveis movidas: ${moved})"
    }

    @Override
    String waitSeconds(int seconds) {
        int s = Math.max(1, Math.min(10, seconds))
        Thread.sleep(s * 1000L)
        return "aguardou ${s}s"
    }

    @Override
    String goHome() {
        goHomeAction.call()
        aguardarEstabilizar()
        return 'voltou para a página inicial'
    }

    @Override
    String openConsultaMenu(int timeoutSeconds) {
        openConsultaAction.call(timeoutSeconds)
        aguardarEstabilizar()
        return 'menu lateral: abriu Consulta Nota Fiscal Venda PDV2'
    }

    @Override
    String selectFranqueadora(int timeoutSeconds) {
        selectFranqueadoraAction.call(timeoutSeconds)
        aguardarEstabilizar()
        return 'franqueadora selecionada pelo script (cabeçalho atualizado)'
    }

    /**
     * Returns the reason a click must be refused, or null. The agent can only navigate: destructive/session
     * actions and links leaving ITFGestor are blocked regardless of what the model asks for.
     */
    static String motivoBloqueio(String text, String href) {
        if (text && (text =~ /(?i)\b(sair|logout|logoff|excluir|apagar|remover|deletar|cancelar)\b/).find()) {
            return "Ação bloqueada por segurança: o elemento \"${text.take(40)}\" não pode ser clicado pelo agente."
        }
        if (href && (href =~ /(?i)^\s*(data|vbscript):/).find()) {
            return 'Link com script bloqueado.'
        }
        if (href && (href =~ /(?i)^\s*(https?:)?\/\//).find()) {
            String host
            try {
                String abs = href.trim().startsWith('//') ? 'https:' + href.trim() : href.trim()
                host = new URI(abs).host?.toLowerCase()
            } catch (Exception ignored) {
                host = null
            }
            boolean ok = host && (host == Observation.ALLOWED_HOST_SUFFIX || host.endsWith('.' + Observation.ALLOWED_HOST_SUFFIX))
            if (!ok) {
                return 'Link para fora do ITFGestor bloqueado.'
            }
        }
        return null
    }

    private WebElement find(int id) {
        def found = driver.findElements(By.cssSelector("[data-agent-id='${id}']"))
        if (found.isEmpty()) {
            throw new AgentToolException("Elemento ${id} não existe mais; os ids mudam a cada passo. " +
                    "Use os ids da observação mais recente.")
        }
        return found[0]
    }

    private void aguardarEstabilizar() {
        for (int i = 0; i < 40; i++) {
            try {
                if (js(READY_JS) == true) {
                    break
                }
            } catch (Exception ignored) {
                // page is navigating
            }
            Thread.sleep(250)
        }
        Thread.sleep(300)
    }

    private Object js(String script, Object... args) {
        return ((JavascriptExecutor) driver).executeScript(script, args)
    }
}

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

    /**
     * Selects a franchise from the dropdown list, including entries that are below the fold of the
     * scrollable list. Selenium's text()/displayed ignore clipped items and a plain click can miss them,
     * so match on textContent, scroll the item into view inside the list, then click (JS click as fallback).
     */
    void selecionarFranqueadora(String franqueadora, int verificacaoSegundos = 8) {
        def wanted = franqueadora.trim().toUpperCase()
        def item = waitFor {
            def found = listaDeFranqueadorasMenu.find {
                (it.@textContent ?: '').trim().toUpperCase() == wanted
            }
            found.empty ? false : found
        }

        // Each <li> holds two links: the first selects the franchise (JSF form submit), the second is the
        // "favorite" star. Clicking the <li> itself does nothing, so target the first link (the name).
        def link = item.find('a').first()
        def element = link.firstElement()
        browser.js.exec(element, "arguments[0].scrollIntoView({block: 'center', inline: 'nearest'});")

        // Verify the click took effect; fall back to a JS click if the native one is not delivered.
        def clicks = [
                { link.click() },
                { browser.js.exec(element, "arguments[0].click();") }
        ]
        for (def doClick : clicks) {
            try {
                doClick()
            } catch (Exception ignored) {
                // stale because the page already reloaded -> check below decides
            }
            if (awaitSelecionada(wanted, verificacaoSegundos)) {
                return
            }
        }
        salvarDiagnostico(franqueadora, element)
        throw new IllegalStateException("Clique não selecionou a franqueadora: ${franqueadora}")
    }

    /** Dumps the item's HTML, its ancestors, the header text and a screenshot to execucao/diagnostico. */
    private void salvarDiagnostico(String franqueadora, element) {
        try {
            def dir = new File(System.getProperty("user.dir"), "execucao/diagnostico")
            dir.mkdirs()
            def safe = franqueadora.replaceAll(/[^A-Za-z0-9]+/, "_")
            def info = browser.js.exec(element, """
                var el = arguments[0], out = [];
                out.push('ITEM outerHTML:\\n' + el.outerHTML);
                var r = el.getBoundingClientRect();
                out.push('ITEM rect: ' + JSON.stringify(r) + ' window: ' + window.innerWidth + 'x' + window.innerHeight);
                var cx = r.left + r.width / 2, cy = r.top + r.height / 2;
                var top = document.elementFromPoint(cx, cy);
                out.push('ELEMENT AT CENTER: ' + (top ? top.outerHTML.substring(0, 400) : 'null'));
                var p = el.parentElement, d = 0;
                while (p && d < 4) {
                    var cs = getComputedStyle(p);
                    out.push('ANCESTOR ' + d + ': <' + p.tagName + ' class="' + p.className + '" id="' + p.id + '"> overflow=' + cs.overflow + ' height=' + p.clientHeight + '/' + p.scrollHeight + ' scrollTop=' + p.scrollTop + ' display=' + cs.display);
                    p = p.parentElement; d++;
                }
                return out.join('\\n\\n');
            """)
            def header = browser.driver.executeScript("var h=document.querySelector('#layout-header'); var w=h?h.querySelectorAll('div.widgets-item')[1]:null; return w?w.innerText:null;")?.toString()?.take(300)
            new File(dir, "${safe}.txt").text = "${info}\n\nHEADER TEXT:\n${header}\n"
            def shot = ((org.openqa.selenium.TakesScreenshot) browser.driver).getScreenshotAs(org.openqa.selenium.OutputType.BYTES)
            new File(dir, "${safe}.png").bytes = shot
        } catch (Throwable e) {
            // diagnostics must never mask the real error
        }
    }

    /**
     * True once the header's current-franchise name (first line of the widget) equals the wanted one.
     * Reads the DOM through the driver on every poll: the module's own element references go stale
     * when the franchise switch reloads the page.
     */
    private boolean awaitSelecionada(String wantedUpper, int seconds) {
        def script = """
            var h = document.querySelector('#layout-header');
            var w = h ? h.querySelectorAll('div.widgets-item')[1] : null;
            return w ? w.innerText : null;
        """
        for (int i = 0; i < seconds * 4; i++) {
            try {
                String text = browser.driver.executeScript(script)
                def current = text?.readLines()?.find { it.trim() }
                if (current?.trim()?.toUpperCase() == wantedUpper) {
                    return true
                }
            } catch (Exception ignored) {
                // page is navigating; try again
            }
            Thread.sleep(250)
        }
        return false
    }

}

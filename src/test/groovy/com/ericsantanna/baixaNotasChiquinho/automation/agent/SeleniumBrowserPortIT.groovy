package com.ericsantanna.baixaNotasChiquinho.automation.agent

import io.github.bonigarcia.wdm.WebDriverManager
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.openqa.selenium.WebDriver
import org.openqa.selenium.chrome.ChromeDriver
import org.openqa.selenium.chrome.ChromeOptions

/**
 * Runs the real observe/click/scroll JavaScript in headless Chrome against a local fixture that imitates the
 * ITFGestor header + side menu. Opt-in (needs Chrome and network for the driver): ./gradlew test -PbrowserTests
 */
@Tag('browser')
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SeleniumBrowserPortIT {
    WebDriver driver
    SeleniumBrowserPort port

    @BeforeAll
    void start() {
        try {
            WebDriverManager.chromedriver().setup()
            def options = new ChromeOptions()
            options.addArguments('--headless=new', '--no-sandbox', '--disable-gpu', '--remote-allow-origins=*', '--window-size=1200,900')
            driver = new ChromeDriver(options)
        } catch (Throwable e) {
            Assumptions.assumeTrue(false, "Chrome indisponível: ${e.message?.readLines()?.first()}")
        }
        port = new SeleniumBrowserPort(driver, { reset() },
                { clickByText('Fiscal'); clickByText('Gerenciamento'); clickByText('Consulta Nota Fiscal Venda PDV2') },
                { throw new IllegalStateException('not used in this test') })
    }

    @AfterAll
    void stop() {
        driver?.quit()
    }

    void reset() {
        driver.get(SeleniumBrowserPortIT.getResource('/agent/fixture.html').toString())
    }

    private void clickByText(String text) {
        port.click(port.observe().findByText(text).first().id)
    }

    @Test
    void observesVisibleElementsOnlyAndHeader() {
        reset()
        def obs = port.observe()

        assert obs.header == 'EMPRESA A'
        assert obs.elements.any { it.text.startsWith('EMPRESA A') }          // header widget (clickable)
        assert !obs.elements.any { it.text == 'Escondido' }                   // display:none
        assert !obs.elements.any { it.text == 'Fiscal' }                      // hidden until a franchise is picked
        assert obs.elements*.id == (1..obs.elements.size()).toList()          // ids are sequential and tagged in the DOM
        assert obs.toPrompt().contains('[CNPJ]') || !obs.toPrompt().contains('12.345.678')
    }

    @Test
    void navigatesHeaderListSideMenuAndGoalCheckIsDeterministic() {
        reset()
        assert !NavigationAgent.estaNaConsulta(driver, 'EMPRESA E')

        // open the franchise list through the header widget (text = current franchise)
        def header = port.observe().elements.find { it.text.startsWith('EMPRESA A') }
        port.click(header.id)

        // EMPRESA E sits below the fold of the 60px scrollable list: must still be listed and clickable
        def empresaE = port.observe().findByText('EMPRESA E')
        assert empresaE.size() == 1
        port.click(empresaE.first().id)

        assert port.observe().header == 'EMPRESA E'
        assert !NavigationAgent.estaNaConsulta(driver, 'EMPRESA E')   // right franchise, wrong screen yet

        port.openConsultaMenu(10)

        assert NavigationAgent.estaNaConsulta(driver, 'EMPRESA E')
        assert !NavigationAgent.estaNaConsulta(driver, 'EMPRESA B')   // wrong franchise
    }

    @Test
    void staleIdsAreRejectedWithAHelpfulMessage() {
        reset()
        port.observe()
        def e = org.junit.jupiter.api.Assertions.assertThrows(AgentToolException) { port.click(9999) }
        assert e.message.contains('não existe mais')
    }

    @Test
    void guardsBlockLogoutAndExternalLinksInTheRealDom() {
        reset()
        def obs = port.observe()

        def sair = obs.findByText('Sair').first()
        def externo = obs.findByText('Link externo').first()

        assert org.junit.jupiter.api.Assertions.assertThrows(AgentToolException) { port.click(sair.id) }.message.contains('bloqueada')
        assert org.junit.jupiter.api.Assertions.assertThrows(AgentToolException) { port.click(externo.id) }.message.contains('fora do ITFGestor')
        assert driver.currentUrl.startsWith('file:')   // never navigated away
    }

    @Test
    void scrollMovesScrollableLists() {
        reset()
        def header = port.observe().elements.find { it.text.startsWith('EMPRESA A') }
        port.click(header.id)

        def msg = port.scroll('down', null)

        assert msg.contains('rolou para baixo')
    }

    @Test
    void headerWidgetWithoutOnclickAttributeIsStillListedWithAHint() {
        // regression: in ITFGestor the model reported "the franchise widget is not among the clickable elements".
        // The fixture attaches the widget handler with addEventListener (no onclick attribute) to reproduce that.
        reset()
        def obs = port.observe()

        def widget = obs.elements.find { it.hint.contains('WIDGET DA FRANQUEADORA ATUAL') }
        assert widget != null
        assert widget.text == 'EMPRESA A'
        assert obs.elements.first().id == widget.id          // listed first
        port.click(widget.id)                                  // and it really opens the list

        def entries = port.observe().elements.findAll { it.hint.contains('item da lista de franqueadoras') }
        assert entries*.text == ['EMPRESA A', 'EMPRESA B', 'EMPRESA C', 'EMPRESA D', 'EMPRESA E', 'EMPRESA F']
    }
}

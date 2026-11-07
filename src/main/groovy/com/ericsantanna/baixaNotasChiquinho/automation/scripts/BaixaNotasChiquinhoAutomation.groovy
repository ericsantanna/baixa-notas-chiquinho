package com.ericsantanna.baixaNotasChiquinho.automation.scripts

import com.ericsantanna.baixaNotasChiquinho.configuracoes.ConfiguracoesService
import com.ericsantanna.baixaNotasChiquinho.configuracoes.Configuracoes
import com.ericsantanna.baixaNotasChiquinho.execucao.ExecutionStatus
import com.ericsantanna.baixaNotasChiquinho.automation.pages.ConsultaCupomFiscalPdv2Page
import com.ericsantanna.baixaNotasChiquinho.automation.pages.LoginPage
import com.ericsantanna.baixaNotasChiquinho.automation.pages.MainPage
import geb.Browser
import geb.Page
import io.github.bonigarcia.wdm.WebDriverManager
import org.openqa.selenium.NoSuchSessionException
import org.openqa.selenium.NoSuchWindowException
import org.openqa.selenium.remote.UnreachableBrowserException
import org.openqa.selenium.chrome.ChromeDriver
import org.openqa.selenium.chrome.ChromeOptions
import org.openqa.selenium.firefox.FirefoxDriver
import org.openqa.selenium.firefox.FirefoxOptions
import org.openqa.selenium.firefox.FirefoxProfile
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service

import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.Semaphore
import java.nio.file.Files

@Service
class BaixaNotasChiquinhoAutomation {
    private static final log = LoggerFactory.getLogger(BaixaNotasChiquinhoAutomation.class)

    private static final fileDownloadSemaphore = new Semaphore(1)
    private executor

    Browser currentBrowser

    @Autowired
    ConfiguracoesService configuracoesService

    private static final int MAX_ATTEMPTS = 3
    private static final int DOWNLOAD_MAX_WAIT_SECONDS = 120

    /** Outcome of the last run: COMPLETED, COMPLETED_WITH_ERRORS, FAILED, INTERRUPTED. */
    volatile String lastOutcome
    volatile String lastErrorMessage

    private static boolean isSessionDead(Throwable e) {
        Throwable t = e
        while (t != null) {
            if (t instanceof NoSuchSessionException || t instanceof NoSuchWindowException
                    || t instanceof UnreachableBrowserException) {
                return true
            }
            t = t.cause
        }
        return false
    }

    def execute(String usuario, String senha, String mes, String ano, List<ExecutionStatus> statusList, Closure onStatusUpdate) {
        lastOutcome = null
        lastErrorMessage = null
        boolean hadErrors = false
        try {
            def configurations = configuracoesService.getConfiguracoes()

            def rootDir = configurations.rootPath
            def processadosFile = Configuracoes.DEFAULT_PROCESSADOS_PATH
            def naoProcessadorFile = Configuracoes.DEFAULT_NAO_PROCESSADOS_PATH
            def franqueadorasFile = Configuracoes.DEFAULT_FRANQUEADORAS_PATH
            def relatorioFile = new File(rootDir, "relatorio.csv")
            def processados = processadosFile.exists() ? processadosFile.readLines() : []
            def downloadDir = configurations.downloadPath

            executor = new ThreadPoolExecutor(
                    configurations.extractionThreads, // core pool size
                    configurations.extractionThreads, // max pool size
                    60, TimeUnit.SECONDS, // keep-alive time
                    new ArrayBlockingQueue<Runnable>(configurations.extractionThreads)
            )

            statusList.clear()

            rootDir.mkdirs()
            downloadDir.mkdirs()
            downloadDir.listFiles()?.each { it.delete() }
            // keep a history of previous failures; the current file only holds this run's failures
            if (naoProcessadorFile.exists()) {
                try {
                    def stamp = new Date().format("yyyyMMdd-HHmmss")
                    Files.move(naoProcessadorFile.toPath(),
                            new File(rootDir, "nao-processados.${stamp}.csv").toPath())
                } catch (Exception ignored) {
                    naoProcessadorFile.delete()
                }
            }

            Browser.drive {
                try {
                    def startSession = { ->
                        switch (configurations.driver.toLowerCase()) {
                            case "chrome":
                                driver = createChromeDriver(configurations)
                                break
                            case "firefox":
                                driver = createFirefoxDriver(configurations)
                                break
                            default:
                                throw new IllegalArgumentException("Driver não suportado: ${configurations.driver}")
                        }
                        browser.config.defaultWaitTimeout = configurations.waitingTimeout
                        currentBrowser = browser

                        def loginPage = to LoginPage
                        loginPage.loginField.value(usuario)
                        loginPage.passwordField.value(senha)
                        loginPage.loginButton.click()

                        return at(MainPage)
                    }

                    def paginaInicial = startSession()

                    sleep(500)
                    paginaInicial.header.franqueadoraMenu.click()

                    def franqueadoras = paginaInicial.header.listaDeFranqueadorasMenu.collect { it.text() }
                    franqueadorasFile.withWriter { writer ->
                        franqueadoras.each { franqueadora ->
                            writer << "$franqueadora\n"

                            if (processados.contains(franqueadora)) {
                                updateStatusList(statusList, new ExecutionStatus(franqueadora, "PREVIOUSLY_PROCESSED"), null)
                                return
                            }

                            updateStatusList(statusList, new ExecutionStatus(franqueadora, "PENDING"), null)
                        }
                    }
                    onStatusUpdate(statusList)

                    Page currentPage = paginaInicial

                    // Processes ONE franchise. Returns normally when handled (processed or recorded as
                    // not-processed with a business reason). Throws on technical failure so it can be retried.
                    def processarFranqueadora = { String franqueadora ->
                        // 1) Select the franchise first, from whatever page we are on (the header exists everywhere).
                        //    The side menu only contains "Fiscal" once a franchise context is selected.
                        def paginaAtual = browser.page(MainPage)
                        paginaAtual.header.franqueadoraMenu.click()
                        paginaAtual.header.selecionarFranqueadora(franqueadora)

                        // 2) The query form can NOT be opened by URL ("Acesso negado"); reach it through the side menu.
                        //    If the switch kept us on the form already, there is nothing to navigate.
                        boolean naConsulta = $('div.headerTitle h4 span', text: 'Consulta Cupom Fiscal PDV').size() == 1
                        if (!naConsulta) {
                            browser.page(MainPage).abrirConsultaNotaFiscalVendaPdv2()
                        }

                        def consultaCupomFiscalPage = at ConsultaCupomFiscalPdv2Page
                        consultaCupomFiscalPage.mesSelect.click()
                        sleep(400)
                        if (consultaCupomFiscalPage.temMes(mes)) {
                            consultaCupomFiscalPage.mesSelectOption(mes).click()
                        } else {
                            def msg = "$mes não encontrado para a franqueadora $franqueadora"
                            handleNaoProcessado(msg, franqueadora, naoProcessadorFile, statusList, onStatusUpdate)
                            currentPage = consultaCupomFiscalPage
                            return
                        }
                        sleep(400)

                        consultaCupomFiscalPage.anoSelect.click()
                        sleep(400)
                        if (consultaCupomFiscalPage.temAno(ano)) {
                            consultaCupomFiscalPage.anoSelectOption(ano).click()
                        } else {
                            def msg = "$ano não encontrado para a franqueadora $franqueadora"
                            handleNaoProcessado(msg, franqueadora, naoProcessadorFile, statusList, onStatusUpdate)
                            currentPage = consultaCupomFiscalPage
                            return
                        }
                        sleep(400)

                        if (consultaCupomFiscalPage.semResultados) {
                            js.exec("document.querySelector('#formNotaFiscal\\\\:tabelaResultado tr.ui-datatable-empty-message td').innerText = 'Aguarde...'")
                        }

                        consultaCupomFiscalPage.pesquisarButton.click()

                        if (!consultaCupomFiscalPage.pesquisaRetornouConteudo) {
                            def msg = "Nenhum resultado encontrado para $franqueadora em $mes de $ano"
                            handleNaoProcessado(msg, franqueadora, naoProcessadorFile, statusList, onStatusUpdate)
                            currentPage = consultaCupomFiscalPage
                            return
                        }

                        try {
                            fileDownloadSemaphore.acquire()

                            // never let a leftover/late zip be mistaken for this franchise's download
                            downloadDir.listFiles()?.each { it.delete() }

                            consultaCupomFiscalPage.exportarXmlButton.click()
                            waitFor(DOWNLOAD_MAX_WAIT_SECONDS) { !$('div.ui-widget-overlay.ui-dialog-mask').displayed }

                            File newDownloadedFile = awaitDownloadedZip(downloadDir, DOWNLOAD_MAX_WAIT_SECONDS)
                            if (!newDownloadedFile) {
                                def msg = "Arquivo de download não encontrado para ${franqueadora} após ${DOWNLOAD_MAX_WAIT_SECONDS} segundos"
                                handleNaoProcessado(msg, franqueadora, naoProcessadorFile, statusList, onStatusUpdate)
                                currentPage = consultaCupomFiscalPage
                                return
                            }

                            def targetDir = new File(configurations.extractionPath, "$mes-$ano/$franqueadora")
                            if (!awaitForFileSizeToStabilize(newDownloadedFile)) {
                                throw new IllegalStateException("Download não estabilizou: ${newDownloadedFile.name}")
                            }

                            def hash = UUID.randomUUID().toString()
                            def newFile = new File("${newDownloadedFile.getAbsolutePath()}.$hash")
                            if (!newDownloadedFile.renameTo(newFile)) {
                                throw new IllegalStateException("Não foi possível renomear ${newDownloadedFile.name}")
                            }

                            boolean submitted = submitWithTimeout(executor, {
                                try {
                                    def result = ZipExtractor.extractAndValidate(newFile, targetDir)
                                    newFile.delete()
                                    // only mark as processed AFTER validated extraction
                                    ZipExtractor.appendLine(processadosFile, franqueadora)
                                    ZipExtractor.appendLine(relatorioFile,
                                            "$franqueadora;$mes;$ano;${result.xmlCount};${result.zipBytes};${result.sha256}")
                                    updateStatusList(statusList, new ExecutionStatus(franqueadora, "PROCESSED"), onStatusUpdate)
                                    log.info "Arquivo extraído para: ${targetDir.getAbsolutePath()} (${result.xmlCount} XMLs) e zip removido."
                                } catch (Throwable e) {
                                    def msg = "Erro ao extrair o arquivo para ${franqueadora}: ${e.message}"
                                    newFile.delete()
                                    handleNaoProcessado(msg, franqueadora, naoProcessadorFile, statusList, onStatusUpdate)
                                    log.warn msg, e
                                }
                            } as Runnable, 10 * 60 * 1000L) // 10 min timeout
                            if (!submitted) {
                                def msg = "Timeout ao aguardar slot para processar ${franqueadora}"
                                handleNaoProcessado(msg, franqueadora, naoProcessadorFile, statusList, onStatusUpdate)
                            }
                        } finally {
                            fileDownloadSemaphore.release()
                        }

                        currentPage = consultaCupomFiscalPage
                    }

                    for (String franqueadora in franqueadoras) {

                        if (Thread.currentThread().isInterrupted()) {
                            log.warn "Thread cancelled during processing, closing browser"
                            browser.close()
                            throw new InterruptedException("Execução interrompida")
                        }

                        log.info "Processando franqueadora: $franqueadora"

                        if (processados.contains(franqueadora)) {
                            log.info "Franqueadora $franqueadora já processada. Pulando..."
                            updateStatusList(statusList, new ExecutionStatus(franqueadora, "PREVIOUSLY_PROCESSED"), null)
                            continue
                        }

                        updateStatusList(statusList, new ExecutionStatus(franqueadora, "PROCESSING"), onStatusUpdate)

                        boolean ok = false
                        Throwable lastError = null
                        for (int attempt = 1; attempt <= MAX_ATTEMPTS && !ok; attempt++) {
                            try {
                                processarFranqueadora(franqueadora)
                                ok = true
                            } catch (InterruptedException e) {
                                throw e
                            } catch (Throwable e) {
                                if (Thread.currentThread().isInterrupted()) {
                                    throw new InterruptedException("Execução interrompida")
                                }
                                lastError = e
                                salvarDiagnosticoFalha(franqueadora, attempt, browser.driver, e)
                                log.warn "Falha em $franqueadora (tentativa $attempt/$MAX_ATTEMPTS): ${e.class.simpleName}: ${e.message?.readLines()?.first()}"
                                if (attempt < MAX_ATTEMPTS) {
                                    try {
                                        if (isSessionDead(e)) {
                                            log.warn "Sessão do navegador perdida. Reabrindo e fazendo login novamente..."
                                            try { browser.quit() } catch (Throwable ignored) {}
                                            currentPage = startSession()
                                        } else {
                                            via(MainPage)
                                        }
                                    } catch (Throwable recoverError) {
                                        log.warn "Falha ao recuperar sessão: ${recoverError.message?.readLines()?.first()}"
                                    }
                                }
                            }
                        }

                        if (!ok) {
                            hadErrors = true
                            def msg = "Falha técnica após $MAX_ATTEMPTS tentativas em ${franqueadora}: ${lastError?.class?.simpleName}: ${lastError?.message?.readLines()?.first()}"
                            log.warn msg
                            handleNaoProcessado(msg, franqueadora, naoProcessadorFile, statusList, onStatusUpdate)
                            // try to get back to a clean state for the next franchise
                            try {
                                if (isSessionDead(lastError)) {
                                    try { browser.quit() } catch (Throwable ignored) {}
                                    currentPage = startSession()
                                } else {
                                    via(MainPage)
                                }
                            } catch (Throwable recoverError) {
                                log.warn "Falha ao recuperar após erro em $franqueadora: ${recoverError.message?.readLines()?.first()}"
                            }
                        }
                    }

                    if (statusList.any { it.status == "NOT_PROCESSED" }) {
                        hadErrors = true
                    }
                    log.info ">>> Todas as franqueadoras foram tratadas. Consulte nao-processados.csv para as pendências. <<<"
                } catch (InterruptedException e) {
                    log.warn "Thread was interrupted, closing browser"
                    try {
                        browser.close()
                    } catch (Throwable ignored) {}
                    Thread.currentThread().interrupt()
                    throw e
                } finally {
                    executor.shutdown()
                    executor.awaitTermination(10, TimeUnit.MINUTES)
                    try { browser.quit() } catch (Throwable ignored) {}
                }
            }
            lastOutcome = hadErrors ? "COMPLETED_WITH_ERRORS" : "COMPLETED"
        } catch (InterruptedException e) {
            lastOutcome = "INTERRUPTED"
            log.warn "Execução interrompida pelo usuário"
            Thread.currentThread().interrupt()
        } catch (Throwable e) {
            lastOutcome = "FAILED"
            lastErrorMessage = e.message
            log.error "${e.message}", e
            // franchises that never got a chance must not stay PENDING/PROCESSING forever
            statusList.findAll { it.status in ["PENDING", "PROCESSING"] }.each {
                updateStatusList(statusList, new ExecutionStatus(it.franqueadora, "NOT_PROCESSED", "Execução abortada: ${e.message?.readLines()?.first()}"), onStatusUpdate)
            }
        }
    }

    /** Writes URL, stack trace (app frames + root cause), side-menu HTML and a screenshot for a failed attempt. */
    private static void salvarDiagnosticoFalha(String franqueadora, int attempt, driver, Throwable e) {
        try {
            def dir = new File(Configuracoes.DEFAULT_ROOT_PATH, "diagnostico")
            dir.mkdirs()
            def safe = franqueadora.replaceAll(/[^A-Za-z0-9]+/, "_")
            def sb = new StringBuilder()
            sb << "ATTEMPT: $attempt\n"
            try { sb << "URL: ${driver.currentUrl}\nTITLE: ${driver.title}\n" } catch (Throwable ignored) {}

            Throwable root = e
            while (root.cause != null && !root.cause.is(root)) { root = root.cause }
            sb << "\nEXCEPTION: ${e.class.name}: ${e.message?.readLines()?.take(12)?.join('\n')}\n"
            sb << "ROOT CAUSE: ${root.class.name}: ${root.message?.readLines()?.take(25)?.join('\n')}\n"
            sb << "\nAPP FRAMES:\n"
            (e.stackTrace + root.stackTrace).findAll { it.className.startsWith("com.ericsantanna") }
                    .each { sb << "  at ${it}\n" }

            try {
                def menu = driver.executeScript("var m=document.querySelector('#layout-menubar'); return m ? m.outerHTML : 'NO #layout-menubar';")
                sb << "\nSIDE MENU HTML (first 8000 chars):\n${menu?.toString()?.take(8000)}\n"
                def links = driver.executeScript("return Array.prototype.slice.call(document.querySelectorAll('#layout-menubar a')).map(function(a){return a.textContent.replace(/\\s+/g,' ').trim() + ' | ' + (a.getAttribute('href')||'') + ' | displayed=' + (a.offsetParent!==null);});")
                sb << "\nALL MENU LINKS (text | href | displayed):\n${links?.join('\n')}\n"
            } catch (Throwable ignored) {}

            new File(dir, "${safe}-tentativa${attempt}.txt").text = sb.toString()
            def shot = ((org.openqa.selenium.TakesScreenshot) driver).getScreenshotAs(org.openqa.selenium.OutputType.BYTES)
            new File(dir, "${safe}-tentativa${attempt}.png").bytes = shot
        } catch (Throwable ignored) {
            // diagnostics must never mask the real error
        }
    }

    private static void handleNaoProcessado(String message, String franqueadora, naoProcessadorFile, List<ExecutionStatus> statusList, Closure onStatusUpdate) {
        log.warn message
        ZipExtractor.appendLine(naoProcessadorFile, "$franqueadora;${message?.replace("\n", " ")}")
        updateStatusList(statusList, new ExecutionStatus(franqueadora, "NOT_PROCESSED", message), onStatusUpdate)
    }

    private static createFirefoxDriver(Configuracoes configurations) {
        WebDriverManager.firefoxdriver().setup()

        def profile = new FirefoxProfile()
        
        // Download preferences
        profile.setPreference("browser.download.folderList", 2)
        profile.setPreference("browser.download.manager.showWhenStarting", false)
        profile.setPreference("browser.download.dir", configurations.downloadPath.absolutePath)
        profile.setPreference("browser.download.useDownloadDir", true)
        profile.setPreference("browser.download.panel.shown", false)
        profile.setPreference("browser.helperApps.neverAsk.saveToDisk", "application/zip,application/octet-stream,application/xml,application/x-zip-compressed,application/x-download,text/xml")
        
        // Automation-friendly preferences
        profile.setPreference("dom.webdriver.enabled", false)                    // Hide webdriver property
        profile.setPreference("useAutomationExtension", false)                   // Disable automation extension
        profile.setPreference("dom.webnotifications.enabled", false)             // Disable notifications
        profile.setPreference("media.volume_scale", "0.0")                       // Mute audio
        profile.setPreference("browser.tabs.remote.autostart", false)            // Disable e10s for better automation
        profile.setPreference("browser.tabs.remote.autostart.2", false)          // Disable e10s for better automation
        
        def options = new FirefoxOptions()
        options.setProfile(profile)
        
        // Firefox arguments (similar to Chrome)
        options.addArguments("--disable-blink-features=AutomationControlled")
        options.addArguments("--disable-extensions")
        options.addArguments("--no-sandbox")
        
        return new FirefoxDriver(options)
    }

    private static createChromeDriver(Configuracoes configurations) {
        WebDriverManager.chromedriver().setup()

        def options = new ChromeOptions()

//        options.setBinary("/usr/bin/flatpak run com.google.Chrome")
//        options.setBinary("/var/lib/flatpak/exports/bin/com.google.Chrome")

        // Download preferences
        def prefs = [:]
        prefs.put("profile.default_content_settings.popups", 0)
        prefs.put("download.default_directory", configurations.downloadPath.absolutePath)
        prefs.put("download.directory_upgrade", true)
        prefs.put("download.prompt_for_download", false)
        prefs.put("plugins.always_open_pdf_externally", true)
        prefs.put("safebrowsing.enabled", false)

        options.setExperimentalOption("prefs", prefs)

        // Automation-friendly preferences
        options.addArguments("--disable-blink-features=AutomationControlled")
        options.addArguments("--disable-extensions")
        options.addArguments("--no-sandbox")
        options.addArguments("--disable-dev-shm-usage")
        options.addArguments("--disable-gpu")
        options.addArguments("--remote-allow-origins=*")

        return new ChromeDriver(options)
    }


    private static final Object STATUS_LOCK = new Object()

    private static void updateStatusList(List<ExecutionStatus> statusList, ExecutionStatus executionStatus, Closure onStatusUpdate) {
        synchronized (STATUS_LOCK) {
            def idx = statusList.indexOf(executionStatus)
            if (idx >= 0) {
                statusList[idx] = executionStatus
            } else {
                statusList << executionStatus
            }
        }

        if (onStatusUpdate) {
            try {
                onStatusUpdate(statusList)
            } catch (Throwable e) {
                // UI broadcast problems must never abort the extraction
                log.warn "Falha ao notificar UI: ${e.message}"
            }
        }
    }

    private static File awaitDownloadedZip(File downloadDir, int maxWaitSeconds) {
        for (int waited = 0; waited < maxWaitSeconds; waited++) {
            def files = downloadDir.listFiles()
            def partial = files?.any { it.name.endsWith(".crdownload") || it.name.endsWith(".part") || it.name.endsWith(".tmp") }
            def zips = files?.findAll { it.name.endsWith(".zip") }
            if (!partial && zips) {
                return zips.max { it.lastModified() }
            }
            sleep(1000)
        }
        return null
    }

    /** @return true when the size stayed unchanged for consecutive checks */
    private static boolean awaitForFileSizeToStabilize(File newFile) {
        long lastSize = -1
        int stableCount = 0
        int maxStable = 2 // number of consecutive stable checks
        int maxWaitStable = 60 // seconds
        for (int waited = 0; waited < maxWaitStable; waited++) {
            long currentSize = newFile.length()
            if (currentSize > 0 && currentSize == lastSize) {
                stableCount++
                if (stableCount >= maxStable) {
                    return true
                }
            } else {
                stableCount = 0
            }
            lastSize = currentSize
            sleep(1000)
        }
        return false
    }

    private static boolean submitWithTimeout(ThreadPoolExecutor executor, Runnable task, long timeoutMillis) {
        long start = System.currentTimeMillis();
        while (System.currentTimeMillis() - start < timeoutMillis) {
            try {
                executor.execute(task);
                return true;
            } catch (RejectedExecutionException ignored) {
                sleep(1000)
            }
        }
        return false;
    }
}
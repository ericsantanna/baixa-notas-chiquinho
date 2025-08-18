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
import java.util.zip.ZipFile

@Service
class BaixaNotasChiquinhoAutomation {
    private static final log = LoggerFactory.getLogger(BaixaNotasChiquinhoAutomation.class)

    private static final fileDownloadSemaphore = new Semaphore(1)
    private executor

    Browser currentBrowser

    @Autowired
    ConfiguracoesService configuracoesService

    def execute(String usuario, String senha, String mes, String ano, List<ExecutionStatus> statusList, Closure onStatusUpdate) {
        try {
            def configurations = configuracoesService.getConfiguracoes()

            def rootDir = configurations.rootPath
            def processadosFile = Configuracoes.DEFAULT_PROCESSADOS_PATH
            def naoProcessadorFile = Configuracoes.DEFAULT_NAO_PROCESSADOS_PATH
            def franqueadorasFile = Configuracoes.DEFAULT_FRANQUEADORAS_PATH
            def processados = processadosFile.exists() ? processadosFile.readLines() : []
            def downloadDir = configurations.downloadPath

            executor = new ThreadPoolExecutor(
                    configurations.extractionThreads, // core pool size
                    configurations.extractionThreads, // max pool size
                    60, TimeUnit.SECONDS, // keep-alive time
                    new ArrayBlockingQueue<Runnable>(configurations.extractionThreads) // bounded queue of size 2
            )

            statusList.clear()

            rootDir.mkdirs()
            downloadDir.mkdirs()
            downloadDir.listFiles()?.each { it.delete() }
            naoProcessadorFile.delete()

            Browser.drive {
                try {
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

                    def paginaInicial = at MainPage

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
                    franqueadoras.each { franqueadora ->

                        if (Thread.currentThread().isInterrupted()) {
                            log.warn "Thread cancelled during processing, closing browser"
                            browser.close()
                            return
                        }

                        log.info "Processando franqueadora: $franqueadora"

                        if (processados.contains(franqueadora)) {
                            log.info "Franqueadora $franqueadora já processada. Pulando..."
                            updateStatusList(statusList, new ExecutionStatus(franqueadora, "PREVIOUSLY_PROCESSED"), null)
                            return
                        }

                        updateStatusList(statusList, new ExecutionStatus(franqueadora, "PROCESSING"), onStatusUpdate)

                        currentPage.header.franqueadoraMenu.click()
                        currentPage.header.getInFranqueadoraMenu(franqueadora).click()

                        at new MainPage(franqueadora: franqueadora)

                        def consultaCupomFiscalPage = to ConsultaCupomFiscalPdv2Page
                        consultaCupomFiscalPage.mesSelect.click()
                        sleep(400)
                        if (consultaCupomFiscalPage.temMes(mes)) {
                            consultaCupomFiscalPage.mesSelectOption(mes).click()
                        } else {
                            def msg = "$mes não encontrado para a franqueadora $franqueadora"
                            handleNaoProcessado(msg, franqueadora, naoProcessadorFile, statusList, onStatusUpdate)
                            return
                        }
                        sleep(400)

                        throw new RuntimeException("Teste de exceção para verificar o log")

                        consultaCupomFiscalPage.anoSelect.click()
                        sleep(400)
                        if (consultaCupomFiscalPage.temAno(ano)) {
                            consultaCupomFiscalPage.anoSelectOption(ano).click()
                        } else {
                            def msg = "$ano não encontrado para a franqueadora $franqueadora"
                            handleNaoProcessado(msg, franqueadora, naoProcessadorFile, statusList, onStatusUpdate)
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
                            return
                        }

                        try {
                            fileDownloadSemaphore.acquire()
                            consultaCupomFiscalPage.exportarXmlButton.click()
                            waitFor { !$('div.ui-widget-overlay.ui-dialog-mask').displayed }

                            int maxWait = 30
                            int waited = 0
                            File newDownloadedFile = null
                            while (waited < maxWait) {
                                def afterFiles = downloadDir.listFiles()?.findAll { it.name.endsWith(".zip") }
                                if (afterFiles && afterFiles.size() > 0) {
                                    newDownloadedFile = afterFiles[0]
                                    break
                                }
                                sleep(1000)
                                waited++
                            }
                            if (newDownloadedFile) {
                                def targetDir = new File(configurations.extractionPath, "$mes-$ano/$franqueadora")
                                targetDir.mkdirs()
                                awaitForFileSizeToStabilize(newDownloadedFile)

                                def hash = UUID.randomUUID().toString()
                                def newFile = new File("${newDownloadedFile.getAbsolutePath()}.$hash")
                                newDownloadedFile.renameTo(newFile)

                                boolean submitted = submitWithTimeout(executor, {
                                    try {
                                        unzipFile(newFile, targetDir)
                                        newFile.delete()
                                        processadosFile << "$franqueadora\n"
                                        updateStatusList(statusList, new ExecutionStatus(franqueadora, "PROCESSED"), onStatusUpdate)
                                        log.info "Arquivo extraído para: ${targetDir.getAbsolutePath()} e zip removido."
                                    } catch (Throwable e) {
                                        def msg = "Erro ao extrair o arquivo para ${franqueadora}: ${e.message}"
                                        handleNaoProcessado(msg, franqueadora, naoProcessadorFile, statusList, onStatusUpdate)
                                        log.error mes, e
                                    }
                                } as Runnable, 10 * 60 * 1000L) // 10 min timeout
                                if (!submitted) {
                                    def msg = "Timeout ao aguardar slot para processar ${franqueadora}"
                                    handleNaoProcessado(msg, franqueadora, naoProcessadorFile, statusList, onStatusUpdate)
                                }
                            } else {
                                def msg = "Arquivo de download não encontrado para ${franqueadora} após ${maxWait} segundos"
                                handleNaoProcessado(msg, franqueadora, naoProcessadorFile, statusList, onStatusUpdate)
                                return
                            }
                        } finally {
                            fileDownloadSemaphore.release()
                        }

                        currentPage = consultaCupomFiscalPage
                    }

                    log.info ">>> Todas as franqueadoras processadas com sucesso! <<<"
                } catch (InterruptedException e) {
                    log.warn "Thread was interrupted, closing browser"
                    try {
                        browser.close()
                    } catch (Throwable ignored) {}
                    Thread.currentThread().interrupt()
                    throw e
                } catch (Throwable e) {
                    throw e
                } finally {
                    executor.shutdown()
                    executor.awaitTermination(10, TimeUnit.MINUTES)
                }
            }
        } catch (Throwable e) {
            log.error "${e.message}", e
            Thread.currentThread().interrupt()
        }
    }

    private static void handleNaoProcessado(String message, String franqueadora, naoProcessadorFile, List<ExecutionStatus> statusList, Closure onStatusUpdate) {
        log.warn message
        naoProcessadorFile << "$franqueadora;$message\n"
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


    private static void updateStatusList(List<ExecutionStatus> statusList, ExecutionStatus executionStatus, Closure onStatusUpdate) {
        def idx = statusList.indexOf(executionStatus)
        if (idx >= 0) {
            statusList[idx] = executionStatus
        } else {
            statusList << executionStatus
        }

        if (onStatusUpdate) {
            onStatusUpdate(statusList)
        }
    }

    private static List unzipFile(File newFile, File targetDir) {
        def zipFile = new ZipFile(newFile)
        zipFile.entries().each { entry ->
            def entryDest = new File(targetDir, entry.name)
            if (entry.isDirectory()) {
                entryDest.mkdirs()
            } else {
                entryDest.parentFile.mkdirs()
                entryDest.withOutputStream { os ->
                    zipFile.getInputStream(entry).withStream { is ->
                        os << is
                    }
                }
            }
        }
        zipFile.close()
    }

    private static void awaitForFileSizeToStabilize(File newFile) {
        long lastSize = -1
        int stableCount = 0
        int maxStable = 2 // number of consecutive stable checks
        int maxWaitStable = 30 // seconds
        int waitedStable = 0
        while (stableCount < maxStable && waitedStable < maxWaitStable) {
            long currentSize = newFile.length()
            if (currentSize > 0 && currentSize == lastSize) {
                stableCount++
            } else {
                stableCount = 0
            }
            lastSize = currentSize
            sleep(1000)
            waitedStable++
        }
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
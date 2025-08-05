package com.ericsantanna.baixaNotas.automation.scripts

import com.ericsantanna.baixaNotas.automation.pages.ConsultaCupomFiscalPdv2Page
import com.ericsantanna.baixaNotas.automation.pages.LoginPage
import com.ericsantanna.baixaNotas.automation.pages.MainPage
import geb.Browser
import geb.Page

import java.util.zip.ZipFile

class Script {
    static void runScript(String usuario, String senha, String mes, String ano, String rootDirPath, String downloadDirPath) {
        def rootDir = new File(rootDirPath)
        def processadosFile = new File("${rootDir.absolutePath}/processados.csv")
        def naoProcessadorFile = new File("${rootDir.absolutePath}/nao-processados.csv")
        def processados = processadosFile.exists() ? processadosFile.readLines() : []
        def downloadDir = new File(downloadDirPath)
        try {
            rootDir.mkdirs()
            downloadDir.mkdirs()
            downloadDir.listFiles()?.each { it.delete() }
            naoProcessadorFile.delete()

            Browser.drive {
                try {
                    def loginPage = to LoginPage
                    loginPage.loginField.value(usuario)
                    loginPage.passwordField.value(senha)
                    loginPage.loginButton.click()

                    def paginaInicial = at MainPage

                    paginaInicial.header.franqueadoraMenu.click()

                    def franqueadoras = paginaInicial.header.listaDeFranqueadorasMenu.collect { it.text() }

                    Page currentPage = paginaInicial
                    franqueadoras.each { franqueadora ->
                        println "Franqueadora: $franqueadora"

                        if (processados.contains(franqueadora)) {
                            println "Franqueadora $franqueadora já processada. Pulando..."
                            return
                        }

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
                            println msg
                            naoProcessadorFile << "$franqueadora;$msg\n"
                            return
                        }
                        sleep(400)

                        consultaCupomFiscalPage.anoSelect.click()
                        sleep(400)
                        if (consultaCupomFiscalPage.temAno(ano)) {
                            consultaCupomFiscalPage.anoSelectOption(ano).click()
                        } else {
                            def msg = "$ano não encontrado para a franqueadora $franqueadora"
                            println msg
                            naoProcessadorFile << "$franqueadora;$msg\n"
                            return
                        }
                        sleep(400)

                        if (consultaCupomFiscalPage.semResultados) {
                            js.exec("document.querySelector('#formNotaFiscal\\\\:tabelaResultado tr.ui-datatable-empty-message td').innerText = 'Aguarde...'")
                        }

                        consultaCupomFiscalPage.pesquisarButton.click()

                        if (!consultaCupomFiscalPage.pesquisaRetornouConteudo) {
                            def msg = "Nenhum resultado encontrado para a franqueadora $franqueadora $mes de $ano"
                            println msg
                            naoProcessadorFile << "$franqueadora;$msg\n"
                            return
                        }

                        consultaCupomFiscalPage.exportarXmlButton.click()
                        waitFor { !$('div.ui-widget-overlay.ui-dialog-mask').displayed }

                        int maxWait = 30
                        int waited = 0
                        File newFile = null
                        while (waited < maxWait) {
                            def afterFiles = downloadDir.listFiles()?.findAll { it.name.endsWith(".zip") }
                            if (afterFiles && afterFiles.size() > 0) {
                                newFile = afterFiles[0]
                                break
                            }
                            sleep(1000)
                            waited++
                        }
                        if (newFile) {
                            def targetDir = new File("/tmp/notas-fiscais/$mes-$ano/$franqueadora")
                            targetDir.mkdirs()

                            awaitForFileSizeToStabilize(newFile)

                            unzipFile(newFile, targetDir)

                            newFile.delete()
                            println "Arquivo extraído para: ${targetDir.getAbsolutePath()} e zip removido."
                        } else {
                            def msg = "Arquivo de download não encontrado para ${franqueadora} após ${maxWait} segundos"
                            println msg
                            naoProcessadorFile << "$franqueadora;$msg\n"
                            return
                        }

                        processadosFile << "$franqueadora\n"
                        currentPage = consultaCupomFiscalPage
                    }

                    println ">>> Todas as franqueadoras processadas com sucesso! <<<"
                } catch (Throwable e) {
//                    try {
//                        browser.close()
//                    } catch (Throwable closeEx) {
//                        closeEx.printStackTrace(System.err)
//                    }
                    throw e
                }
            }
        } catch (Throwable e) {
            e.printStackTrace(System.err)
        }
    }

    private static List unzipFile(File newFile, targetDir) {
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
}

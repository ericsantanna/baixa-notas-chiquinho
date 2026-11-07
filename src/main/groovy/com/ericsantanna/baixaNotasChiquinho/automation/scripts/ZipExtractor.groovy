package com.ericsantanna.baixaNotasChiquinho.automation.scripts

import javax.xml.parsers.DocumentBuilderFactory

import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.zip.ZipFile

/**
 * Safe extraction + validation of the XML zip exported by ITFGestor.
 * Extracts to a temp dir, validates, then swaps into the final dir, so a bad zip never leaves
 * a half-populated franchise folder behind.
 */
class ZipExtractor {

    static class Result {
        int xmlCount
        long zipBytes
        String sha256
    }

    private static final Object FILE_LOCK = new Object()

    /** Thread-safe line append, shared by all CSV writers. */
    static void appendLine(File file, String line) {
        synchronized (FILE_LOCK) {
            file.parentFile?.mkdirs()
            file << (line.endsWith("\n") ? line : line + "\n")
        }
    }

    static Result extractAndValidate(File zip, File targetDir) {
        if (!zip.exists() || zip.length() == 0) {
            throw new IllegalStateException("ZIP vazio ou inexistente: ${zip.name}")
        }

        def parent = targetDir.absoluteFile.parentFile
        parent.mkdirs()
        def tmpDir = new File(parent, ".tmp-${UUID.randomUUID()}")
        tmpDir.mkdirs()

        try {
            def tmpCanonical = tmpDir.canonicalFile
            int xmlCount = 0

            def zipFile = new ZipFile(zip)
            try {
                for (entry in zipFile.entries().toList()) {
                    def dest = new File(tmpDir, entry.name).canonicalFile
                    // zip-slip guard
                    if (dest != tmpCanonical && !dest.path.startsWith(tmpCanonical.path + File.separator)) {
                        throw new SecurityException("Entrada de ZIP fora do diretório: ${entry.name}")
                    }
                    if (entry.isDirectory()) {
                        dest.mkdirs()
                        continue
                    }
                    dest.parentFile.mkdirs()
                    zipFile.getInputStream(entry).withStream { is ->
                        dest.withOutputStream { os -> os << is }
                    }
                    if (dest.name.toLowerCase().endsWith(".xml")) {
                        validateXml(dest)
                        xmlCount++
                    }
                }
            } finally {
                zipFile.close()
            }

            if (xmlCount == 0) {
                throw new IllegalStateException("ZIP sem nenhum XML: ${zip.name}")
            }

            // swap into place
            if (targetDir.exists()) {
                targetDir.deleteDir()
            }
            Files.move(tmpDir.toPath(), targetDir.toPath(), StandardCopyOption.ATOMIC_MOVE)

            return new Result(xmlCount: xmlCount, zipBytes: zip.length(), sha256: sha256(zip))
        } finally {
            if (tmpDir.exists()) {
                tmpDir.deleteDir()
            }
        }
    }

    static void validateXml(File xml) {
        if (xml.length() == 0) {
            throw new IllegalStateException("XML vazio: ${xml.name}")
        }

        def doc
        try {
            def factory = DocumentBuilderFactory.newInstance()
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            factory.setNamespaceAware(false)
            doc = factory.newDocumentBuilder().parse(xml)
        } catch (Exception e) {
            throw new IllegalStateException("XML inválido ${xml.name}: ${e.message}", e)
        }

        def base = xml.name.substring(0, xml.name.length() - 4)
        if (base ==~ /\d{44}/) {
            if (!isChaveValida(base)) {
                throw new IllegalStateException("Chave de acesso com dígito verificador inválido: ${xml.name}")
            }
            def infNFe = doc.getElementsByTagName("infNFe")
            def id = infNFe.length > 0 ? infNFe.item(0).getAttribute("Id") : null
            if (id && id != "NFe$base") {
                throw new IllegalStateException("Chave do arquivo (${base}) difere do XML (${id})")
            }
        }
    }

    /** Validates the 44-digit NF-e/NFC-e access key (mod 11 check digit). */
    static boolean isChaveValida(String chave) {
        if (!chave || !(chave ==~ /\d{44}/)) {
            return false
        }
        int sum = 0
        int weight = 2
        for (int i = 42; i >= 0; i--) {
            sum += Character.getNumericValue(chave.charAt(i)) * weight
            weight = weight == 9 ? 2 : weight + 1
        }
        int rest = sum % 11
        int dv = (rest == 0 || rest == 1) ? 0 : 11 - rest
        return dv == Character.getNumericValue(chave.charAt(43))
    }

    static String sha256(File file) {
        def digest = MessageDigest.getInstance("SHA-256")
        file.withInputStream { is ->
            byte[] buf = new byte[8192]
            int n
            while ((n = is.read(buf)) > 0) {
                digest.update(buf, 0, n)
            }
        }
        return digest.digest().encodeHex().toString()
    }
}

package com.ericsantanna.baixaNotasChiquinho.appConfig

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

import static org.junit.jupiter.api.Assertions.*

class DiagnosticoCleanerTest {
    @TempDir File tmp

    @Test
    void removesContentButKeepsFolder() {
        def dir = new File(tmp, "diagnostico")
        new File(dir, "agent").mkdirs()
        new File(dir, "a.png").text = "x"
        new File(dir, "agent/b.jsonl").text = "y"
        assertEquals(3, DiagnosticoCleaner.limpar(dir))
        assertTrue(dir.isDirectory())
        assertEquals(0, dir.list().length)
    }

    @Test
    void missingFolderIsFine() {
        assertEquals(0, DiagnosticoCleaner.limpar(new File(tmp, "nope")))
    }

    @Test
    void doesNotFollowSymlinks() {
        def outside = new File(tmp, "outside"); outside.mkdirs()
        new File(outside, "keep.txt").text = "k"
        def dir = new File(tmp, "diagnostico"); dir.mkdirs()
        java.nio.file.Files.createSymbolicLink(new File(dir, "link").toPath(), outside.toPath())
        DiagnosticoCleaner.limpar(dir)
        assertTrue(new File(outside, "keep.txt").exists())
        assertEquals(0, dir.list().length)
    }
}

package app.genrix.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class DiagFileTest {
    private val dir = Files.createTempDirectory("diag").toFile()

    @Test
    fun appendsTimedLines() {
        val log = DiagFile(dir)
        log.add('I', "wake: listening")
        log.add('W', "mic busy")
        log.flush()
        val lines = dir.resolve(DiagFile.NAME).readLines()
        assertEquals(2, lines.size)
        assertTrue(lines[0].endsWith(" I wake: listening"))
        assertTrue(lines[1].endsWith(" W mic busy"))
    }

    @Test
    fun keepsTwoFilesAtMost() {
        val log = DiagFile(dir, limit = 100)
        repeat(20) { log.add('I', "line $it") }
        log.flush()
        val current = dir.resolve(DiagFile.NAME)
        val previous = dir.resolve(DiagFile.OLD_NAME)
        assertTrue(current.length() <= 100 && previous.length() <= 100)
        assertTrue(current.readText().contains("line 19"))
        assertFalse((previous.readText() + current.readText()).contains("line 0\n"))
    }
}

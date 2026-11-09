package app.genrix.android.assistant

import org.junit.Assert.assertEquals
import org.junit.Test

class TranscriptTest {
    @Test
    fun joinsPiecesFromTheSameSide() {
        val t = Transcript()
        t.add(fromUser = true, "Mausam ")
        val lines = t.add(fromUser = true, " kaisa hai?")
        assertEquals(1, lines.size)
        assertEquals("Mausam kaisa hai?", lines[0].text)
    }

    @Test
    fun switchesSidesAndEndsTurns() {
        val t = Transcript()
        t.add(fromUser = true, "Hi")
        t.add(fromUser = false, "Hello")
        t.endTurn()
        val lines = t.add(fromUser = false, "Anything else?")
        assertEquals(listOf("Hi", "Hello", "Anything else?"), lines.map { it.text })
        assertEquals(listOf(true, false, false), lines.map { it.fromUser })
    }

    @Test
    fun keepsOnlyTheNewestLines() {
        val t = Transcript(limit = 2)
        t.add(fromUser = true, "a")
        t.add(fromUser = false, "b")
        val lines = t.add(fromUser = true, "c")
        assertEquals(listOf("b", "c"), lines.map { it.text })
    }
}

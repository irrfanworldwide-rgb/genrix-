package app.genrix.android.memory

import app.genrix.android.task.Capabilities
import app.genrix.android.task.TurnTrust
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryRulesTest {
    private val notes = listOf(
        Note("Parked on level 2, pillar B7", 1_000L),
        Note("Shoe size is 9 UK", 2_000L),
        Note("Anita's birthday is 12 March", 3_000L),
    )

    @Test
    fun secretsAreNeverKept() {
        listOf(
            "my bank password is hunter2", "ATM PIN 4821", "UPI pin is 1234", "card 4111 1111 1111 1111",
            "the OTP was 552910",
        ).forEach { assertTrue(it, MemoryRules.looksSecret(it)) }
        listOf("Parked on level 2", "Shoe size is 9", "Wi-Fi router is in the hall", "Pinned the poster")
            .forEach { assertFalse(it, MemoryRules.looksSecret(it)) }
    }

    @Test
    fun notesAreFoundByTheirWords() {
        assertEquals("Parked on level 2, pillar B7", MemoryRules.find("where did I park", notes).first().text)
        assertEquals("Shoe size is 9 UK", MemoryRules.find("shoe size", notes).first().text)
        assertTrue(MemoryRules.find("passport", notes).isEmpty())
        // No words: the latest notes first.
        assertEquals("Anita's birthday is 12 March", MemoryRules.find("", notes).first().text)
    }

    @Test
    fun routinesAreFoundByName() {
        val all = listOf(MemoryRules.GOOD_MORNING, Routine("Good night", listOf("Alarm at 6:30")))
        assertEquals("Good night", MemoryRules.routine("good night!", all)?.name)
        assertEquals("Good morning", MemoryRules.routine("Good Morning", all)?.name)
        assertEquals("Good night", MemoryRules.routine("night", all)?.name)
        assertNull(MemoryRules.routine("workout", all))
        assertNull(MemoryRules.routine("", all))
    }

    @Test
    fun screenTextCannotPlantMemoriesOrRoutines() {
        val trust = TurnTrust()
        listOf("remember", "forget", "routine_save").forEach {
            assertEquals(it, TurnTrust.Verdict.Allow, trust.verdict(Capabilities.of(it)!!))
        }
        trust.after("ui_read")
        listOf("remember", "forget", "routine_save").forEach {
            assertEquals(it, TurnTrust.Verdict.AskUser, trust.verdict(Capabilities.of(it)!!))
        }
        // Looking things up and running a saved routine stay allowed.
        assertEquals(TurnTrust.Verdict.Allow, trust.verdict(Capabilities.of("recall")!!))
        assertEquals(TurnTrust.Verdict.Allow, trust.verdict(Capabilities.of("routine_run")!!))
    }
}

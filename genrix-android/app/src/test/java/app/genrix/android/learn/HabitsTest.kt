package app.genrix.android.learn

import app.genrix.android.local.LocalCommand
import app.genrix.android.local.MediaKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class HabitsTest {
    @get:Rule val tmp = TemporaryFolder()

    private var now = 0L
    private fun habits() = Habits(tmp.root.resolve("habits.json")) { now }

    @Test
    fun keysRoundTrip() {
        listOf(
            LocalCommand.SetVolume(30), LocalCommand.StepVolume(false), LocalCommand.Mute(true), LocalCommand.Media(MediaKey.Next),
            LocalCommand.Torch(true), LocalCommand.Battery, LocalCommand.Time, LocalCommand.Timer(300), LocalCommand.OpenApp("youtube"),
        ).forEach { assertEquals(it, HabitKeys.command(HabitKeys.key(it)!!)) }
        assertNull(HabitKeys.key(LocalCommand.Stop))
        assertNull(HabitKeys.command("pay:100"))
    }

    @Test
    fun mostUsedFirstAndRemembered() {
        val h = habits()
        repeat(5) { h.record(LocalCommand.OpenApp("youtube"), 21) }
        repeat(3) { h.record(LocalCommand.Torch(true), 22) }
        h.record(LocalCommand.Battery, 9)
        assertEquals(listOf(LocalCommand.OpenApp("youtube"), LocalCommand.Torch(true)), h.top(4))
        assertTrue(h.summary(21).contains("Around this hour usually: open Youtube"))
        assertEquals(h.top(4), habits().top(4)) // survives a restart
    }

    @Test
    fun learnsAPhraseGeminiUnderstood() {
        val h = habits()
        assertNull(h.lookup(listOf("andhera hai light jalao")))
        h.expect("Andhera hai, light jalao")
        now += 5_000
        h.learn(LocalCommand.Torch(true))
        assertEquals(LocalCommand.Torch(true), h.lookup(listOf("andhera hai light jalao")))
        assertEquals(1, habits().learnedPhrases())
    }

    @Test
    fun learnsOnlyRightAfterTheMiss() {
        val h = habits()
        h.expect("kuch bhi")
        now += 120_000
        h.learn(LocalCommand.Battery)
        assertNull(h.lookup(listOf("kuch bhi")))
        h.learn(LocalCommand.Battery) // nothing pending any more
        assertEquals(0, h.learnedPhrases())
    }

    @Test
    fun forgetClearsEverything() {
        val h = habits()
        repeat(4) { h.record(LocalCommand.Battery, 8) }
        h.expect("charge kitna")
        h.learn(LocalCommand.Battery)
        h.forget()
        assertEquals(emptyList<LocalCommand>(), h.top(4))
        assertEquals(0, habits().learnedPhrases())
        assertEquals("", h.summary(8))
    }
}

package app.genrix.android.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TalkOverTest {
    private fun chunk(level: Int) = ByteArray(3_200).also { b ->
        for (i in 0 until 1_600) {
            val v = if (i % 2 == 0) level else -level
            b[2 * i] = (v and 0xFF).toByte()
            b[2 * i + 1] = (v shr 8).toByte()
        }
    }

    private fun learned(): TalkOver = TalkOver(Speaker()).also { t ->
        repeat(8) { t.duringReply(chunk(5_000), chunk(150)) } // the canceller removes the echo
        assertTrue(t.ready)
    }

    @Test
    fun neverCutsInBeforeTheCancellerHasLearned() {
        val t = TalkOver(Speaker())
        repeat(20) { assertNull(t.duringReply(chunk(5_000), chunk(4_500))) }
        assertFalse(t.ready)
    }

    @Test
    fun aVoiceOverTheReplyCutsIn() {
        val t = learned()
        assertNull(t.duringReply(chunk(5_500), chunk(3_000)))
        assertNull(t.duringReply(chunk(5_500), chunk(3_000)))
        val voice = t.duringReply(chunk(5_500), chunk(3_000))
        assertNotNull(voice)
        assertEquals(3, voice!!.size)
    }

    @Test
    fun echoBurstsDontCutIn() {
        val t = learned()
        repeat(10) { assertNull(t.duringReply(chunk(8_000), chunk(2_600))) } // still mostly cancelled
        assertNull(t.duringReply(chunk(5_000), chunk(3_000)))
        assertNull(t.duringReply(chunk(5_000), chunk(150))) // a gap resets the count
        assertNull(t.duringReply(chunk(5_000), chunk(3_000)))
    }

    @Test
    fun laterRepliesNeedAFewEchoChunksAndANewConversationStartsOver() {
        val t = learned()
        t.reset()
        assertFalse(t.ready)
        repeat(3) { t.duringReply(chunk(5_000), chunk(150)) }
        assertTrue(t.ready)
        t.reset(newConversation = true)
        repeat(3) { t.duringReply(chunk(5_000), chunk(150)) }
        assertFalse(t.ready)
    }
}

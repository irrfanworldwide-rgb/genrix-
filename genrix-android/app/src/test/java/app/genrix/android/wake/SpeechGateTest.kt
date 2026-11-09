package app.genrix.android.wake

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeechGateTest {
    private fun chunk(level: Int) = ShortArray(1280) { if (it % 2 == 0) level.toShort() else (-level).toShort() }

    @Test
    fun staysClosedInAQuietRoom() {
        val gate = SpeechGate()
        repeat(100) { assertFalse(gate.open(chunk(20))) }
    }

    @Test
    fun opensOnSpeechAndHoldsTwoSeconds() {
        val gate = SpeechGate()
        repeat(20) { gate.open(chunk(20)) }
        assertTrue(gate.open(chunk(800)))
        repeat(SpeechGate.HOLD_CHUNKS - 1) { assertTrue(gate.open(chunk(20))) }
        assertFalse(gate.open(chunk(20)))
    }

    @Test
    fun aNoisyRoomStillOpensForAVoice() {
        val gate = SpeechGate()
        repeat(2_000) { gate.open(chunk(400)) } // fan or traffic, louder than the cap
        assertTrue(gate.open(chunk(1_200)))
    }

    @Test
    fun steadyNoiseClosesItAgain() {
        val gate = SpeechGate()
        repeat(20) { gate.open(chunk(20)) }
        repeat(2_000) { gate.open(chunk(150)) } // a fan switched on
        assertFalse(gate.open(chunk(150)))
        assertEquals(150, gate.background, 15)
    }

    private fun assertEquals(expected: Int, actual: Int, delta: Int) =
        assertTrue("$actual not within $delta of $expected", kotlin.math.abs(expected - actual) <= delta)
}

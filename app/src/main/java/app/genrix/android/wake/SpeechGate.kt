package app.genrix.android.wake

import kotlin.math.sqrt

/**
 * A cheap loudness check that lets the wake model rest while the room is quiet. It opens
 * when a chunk is clearly louder than the background and stays open for [hold] chunks, so
 * the model hears the whole phrase. The listener replays recent audio when it opens, so
 * nothing said just before is lost.
 */
class SpeechGate(private val hold: Int = HOLD_CHUNKS) {
    private var floor = -1.0
    private var left = 0

    /** Background loudness the gate measures against (for diagnostics). */
    val background: Int get() = floor.coerceAtLeast(0.0).toInt()

    /** True while the wake model should run on this chunk. */
    fun open(chunk: ShortArray): Boolean {
        val level = rms(chunk)
        if (floor < 0) floor = level
        val loud = level > (floor * RATIO).coerceIn(MIN_LEVEL, MAX_LEVEL)
        // Follow a quieter room at once, a louder one slowly (a voice mustn't become the floor).
        floor = if (level < floor) floor * 0.8 + level * 0.2 else floor * 0.998 + level * 0.002
        left = if (loud) hold else (left - 1).coerceAtLeast(0)
        return left > 0
    }

    companion object {
        const val HOLD_CHUNKS = 25 // 2 s of 80 ms chunks
        private const val RATIO = 2.5
        private const val MIN_LEVEL = 60.0 // a whisper-quiet room still needs some sound
        private const val MAX_LEVEL = 250.0 // a noisy room never needs a shout

        fun rms(chunk: ShortArray): Double {
            if (chunk.isEmpty()) return 0.0
            var sum = 0.0
            for (s in chunk) sum += s.toDouble() * s
            return sqrt(sum / chunk.size)
        }
    }
}

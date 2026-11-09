package app.genrix.android.audio

import org.junit.Assert.assertEquals
import org.junit.Test

class VoiceLevelTest {
    private fun pcm(vararg samples: Int) = ByteArray(samples.size * 2).also { out ->
        samples.forEachIndexed { i, s ->
            out[2 * i] = (s and 0xFF).toByte()
            out[2 * i + 1] = (s shr 8).toByte()
        }
    }

    @Test
    fun silenceIsZero() {
        assertEquals(0, pcmLevel(pcm(0, 0, 0, 0)))
        assertEquals(0, pcmLevel(ByteArray(0)))
    }

    @Test
    fun readsSignedLittleEndianSamples() {
        assertEquals(1000, pcmLevel(pcm(1000, -1000, 1000, -1000)))
        assertEquals(32767, pcmLevel(pcm(32767)))
    }
}

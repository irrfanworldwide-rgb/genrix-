package app.genrix.android.wake

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.random.Random

class VoiceMathTest {
    private fun noise(seed: Int) = Random(seed).let { r -> FloatArray(1536) { r.nextFloat() } }

    @Test
    fun unitVectorsHaveLengthOneAndIgnoreScaleAndOffset() {
        val a = noise(1)
        val scaled = FloatArray(a.size) { a[it] * 7f + 3f }
        assertEquals(1f, VoiceMath.dot(VoiceMath.unit(a), VoiceMath.unit(a)), 1e-4f)
        assertEquals(1f, VoiceMath.dot(VoiceMath.unit(a), VoiceMath.unit(scaled)), 1e-4f)
    }

    @Test
    fun thresholdFollowsHowConsistentTheSamplesAre() {
        val base = noise(2)
        fun near(seed: Int, amount: Float) = noise(seed).let { n -> FloatArray(base.size) { base[it] + n[it] * amount } }
        val tight = (3..7).map { VoiceMath.unit(near(it, 0.05f)) }
        val loose = (3..7).map { VoiceMath.unit(near(it, 2f)) }
        assertEquals(0.96f, VoiceMath.threshold(tight), 1e-6f) // capped
        assertEquals(0.90f, VoiceMath.threshold(loose), 1e-6f) // floor
    }

    @Test
    fun profileMatchesItsOwnSamples() {
        val profile = VoiceProfile.create((10..14).map(::noise))
        assertEquals(1f, profile.similarity(noise(12)), 1e-4f)
    }
}

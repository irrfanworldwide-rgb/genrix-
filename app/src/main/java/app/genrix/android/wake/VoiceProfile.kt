package app.genrix.android.wake

import android.content.Context
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import kotlin.math.sqrt

/**
 * The user's own "Hey Genrix", learnt from a few samples: each is the model's 16×96
 * speech-feature window at the phrase. Kept only in the app's private files (no backup),
 * never sent anywhere; "Forget my voice" deletes it.
 */
class VoiceProfile(val templates: List<FloatArray>, val threshold: Float) {
    /** Best match (cosine, -1..1) of a live feature window against the samples. */
    fun similarity(window: FloatArray): Float {
        val v = VoiceMath.unit(window)
        return templates.maxOf { VoiceMath.dot(it, v) }
    }

    companion object {
        const val SAMPLES = 5

        fun create(windows: List<FloatArray>): VoiceProfile {
            val units = windows.map(VoiceMath::unit)
            return VoiceProfile(units, VoiceMath.threshold(units))
        }

        private fun file(context: Context) = File(context.filesDir, "voice_profile.bin")

        fun load(context: Context): VoiceProfile? = runCatching {
            DataInputStream(file(context).inputStream().buffered()).use { input ->
                val threshold = input.readFloat()
                val count = input.readInt()
                val size = input.readInt()
                VoiceProfile(List(count) { FloatArray(size) { input.readFloat() } }, threshold)
            }
        }.getOrNull()

        fun save(context: Context, profile: VoiceProfile) {
            DataOutputStream(file(context).outputStream().buffered()).use { out ->
                out.writeFloat(profile.threshold)
                out.writeInt(profile.templates.size)
                out.writeInt(profile.templates.first().size)
                profile.templates.forEach { t -> t.forEach(out::writeFloat) }
            }
        }

        fun forget(context: Context) {
            file(context).delete()
        }

        fun exists(context: Context) = file(context).exists()
    }
}

/** Pure maths for the voice profile, unit-tested. */
object VoiceMath {
    /** Mean-removed, length-1 copy, so cosine similarity is a dot product. */
    fun unit(window: FloatArray): FloatArray {
        val mean = window.average().toFloat()
        val centred = FloatArray(window.size) { window[it] - mean }
        val length = sqrt(centred.sumOf { (it * it).toDouble() }).toFloat().coerceAtLeast(1e-6f)
        return FloatArray(centred.size) { centred[it] / length }
    }

    fun dot(a: FloatArray, b: FloatArray): Float {
        var sum = 0f
        for (i in a.indices) sum += a[i] * b[i]
        return sum
    }

    /**
     * How close a live window must be: a little under how well the user's own samples
     * match each other (each sample's best match among the others), kept in [MIN, MAX].
     * On test voices the same speaker's phrase scored 0.98-0.99 and their other speech at
     * most 0.92 ("Hey Travis").
     */
    fun threshold(units: List<FloatArray>): Float {
        if (units.size < 2) return MAX
        val bestOthers = units.mapIndexed { i, a -> units.filterIndexed { j, _ -> j != i }.maxOf { dot(a, it) } }
        return (bestOthers.min() - MARGIN).coerceIn(MIN, MAX)
    }

    private const val MARGIN = 0.03f
    private const val MIN = 0.90f
    private const val MAX = 0.96f
}

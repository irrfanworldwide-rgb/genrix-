package app.genrix.android.audio

import kotlin.math.sqrt

/** Loudness above which a mic chunk counts as someone talking (diagnostics only). */
const val VOICE_LEVEL = 300

/** Root-mean-square level of 16-bit little-endian PCM, 0..32767. */
fun pcmLevel(pcm: ByteArray): Int {
    val samples = pcm.size / 2
    if (samples == 0) return 0
    var sum = 0.0
    for (i in 0 until samples) {
        val sample = (pcm[2 * i].toInt() and 0xFF) or (pcm[2 * i + 1].toInt() shl 8)
        sum += sample.toDouble() * sample
    }
    return sqrt(sum / samples).toInt()
}

package app.genrix.android.audio

import app.genrix.android.Diag

/**
 * SpeexDSP's echo canceller and residual-echo suppressor (native, src/main/cpp): removes
 * the reply from the mic using the exact audio the speaker played, so the user can talk over
 * it on the loudspeaker. 16 kHz mono, whole 10 ms frames.
 */
class EchoCanceller private constructor(private var handle: Long) : AutoCloseable {
    /** The echo removed; a voice over the reply survives. Reused by the next call. */
    val cleaned = ShortArray(CHUNK)

    /** Leftover echo and noise removed too (this also mutes a voice that overlaps the reply). */
    val suppressed = ShortArray(CHUNK)

    /** [mic] and [ref] are [CHUNK] samples; results land in [cleaned] and [suppressed]. */
    @Synchronized
    fun process(mic: ShortArray, ref: ShortArray) {
        if (handle != 0L) {
            nativeProcess(handle, mic, ref, cleaned, suppressed)
        } else {
            mic.copyInto(cleaned)
            mic.copyInto(suppressed)
        }
    }

    @Synchronized
    fun reset() {
        if (handle != 0L) nativeReset(handle)
    }

    @Synchronized
    override fun close() {
        if (handle != 0L) nativeDestroy(handle)
        handle = 0
    }

    companion object {
        const val RATE = 16_000
        const val CHUNK = 1_600 // 100 ms, the mic's chunk
        private const val FRAME = 160 // 10 ms
        private const val TAIL = 6_400 // 400 ms of echo: speaker delay plus the room

        private val loaded = runCatching { System.loadLibrary("genrix_aec") }
            .onFailure { Diag.w("echo: native library missing (${it.javaClass.simpleName})") }
            .isSuccess

        /** Null when the native library isn't in this build. */
        fun create(): EchoCanceller? =
            if (!loaded) null else nativeCreate(FRAME, TAIL, RATE).takeIf { it != 0L }?.let(::EchoCanceller)

        @JvmStatic private external fun nativeCreate(frame: Int, tail: Int, rate: Int): Long
        @JvmStatic private external fun nativeProcess(handle: Long, mic: ShortArray, ref: ShortArray, out: ShortArray, suppressed: ShortArray)
        @JvmStatic private external fun nativeReset(handle: Long)
        @JvmStatic private external fun nativeDestroy(handle: Long)
    }
}

package app.genrix.android.audio

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Talking over a reply on the loudspeaker. Every mic chunk is cleaned against what the
 * speaker played (EchoCanceller); while the reply plays, the mic stays muted unless the
 * cleaned audio holds a voice for [chunks] chunks in a row (100 ms each). Then the reply is
 * cut and those chunks go to Gemini, so the start of what was said isn't lost.
 *
 * The canceller needs time to learn the echo; until it removes most of it (measured on
 * chunks without a voice), talk-over stays off and the mic stays muted. Within a conversation
 * what it learned carries over, but each reply needs a few echo chunks first; a new
 * conversation starts unproven.
 */
class TalkOver(private val speaker: Speaker, private val level: Int = VOICE, private val chunks: Int = CHUNKS) {
    private val aec = EchoCanceller.create()
    private val mic = ShortArray(EchoCanceller.CHUNK)
    private val ref = ShortArray(EchoCanceller.CHUNK)
    private val held = ArrayDeque<ByteArray>()
    private var attenuation = 1.0 // cleaned / raw loudness, averaged over echo-only chunks
    private var echoChunks = 0
    private var chunk = 0
    private var readyAt = -1

    /** When talk-over became possible in this reply, in ms (or -1: never). */
    val readyAfterMs: Int get() = if (readyAt < 0) -1 else readyAt * 100

    /** The canceller removes the echo well enough to tell a voice from it. */
    val ready: Boolean get() = echoChunks >= LEARN_CHUNKS && attenuation < GOOD_ATTENUATION

    val available: Boolean get() = aec != null

    /** Loudest chunk before and after cleaning while the reply played (for tuning). */
    var rawPeak = 0
        private set
    var cleanPeak = 0
        private set

    /** The mic chunk with the reply's echo removed (unchanged without the canceller). */
    fun clean(pcm: ByteArray): ByteArray {
        val canceller = aec ?: return pcm
        if (pcm.size != EchoCanceller.CHUNK * 2) return pcm
        ByteBuffer.wrap(pcm).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(mic)
        speaker.reference(ref, REF_BACK)
        canceller.process(mic, ref)
        return ByteArray(pcm.size).also { ByteBuffer.wrap(it).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().put(canceller.cleaned) }
    }

    /** A cleaned chunk while the reply plays: null keeps the mic muted; else the voice so far. */
    fun duringReply(raw: ByteArray, cleaned: ByteArray): List<ByteArray>? {
        val before = pcmLevel(raw)
        val after = pcmLevel(cleaned)
        if (before > rawPeak) rawPeak = before
        // A voice passes the canceller nearly whole; leftover echo is cut like the rest of the
        // reply (to about [attenuation] of its level), so a voice is clearly above that.
        val voice = after >= level && after >= before * maxOf(VOICE_KEPT, 2 * attenuation)
        if (ready && readyAt < 0) readyAt = chunk
        chunk++
        if (!voice || !ready) {
            if (after > cleanPeak) cleanPeak = after
            if (before > ECHO_LEVEL) {
                attenuation = 0.7 * attenuation + 0.3 * (after.toDouble() / before)
                echoChunks++
            }
            held.clear()
            return null
        }
        held.addLast(cleaned)
        if (held.size < chunks) return null
        return held.toList().also { held.clear() }
    }

    /** A new reply: a few echo chunks before talk-over; [newConversation] forgets the rest. */
    fun reset(newConversation: Boolean = false) {
        held.clear()
        if (newConversation) attenuation = 1.0
        echoChunks = if (newConversation) 0 else LEARN_CHUNKS - REPLY_CHUNKS
        chunk = 0
        readyAt = -1
        rawPeak = 0
        cleanPeak = 0
    }

    fun close() {
        aec?.close()
    }

    companion object {
        const val VOICE = 1_000
        const val CHUNKS = 3
        private const val VOICE_KEPT = 0.35
        private const val ECHO_LEVEL = 800 // loud enough to judge the canceller by
        private const val LEARN_CHUNKS = 5
        private const val REPLY_CHUNKS = 3 // echo chunks each later reply needs first
        private const val GOOD_ATTENUATION = 0.25 // echo at least about 12 dB quieter
        // Reference ends 60 ms before what's playing now, so the echo always comes after it.
        private const val REF_BACK = EchoCanceller.RATE * 60 / 1000
    }
}

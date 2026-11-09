package app.genrix.android.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.SystemClock
import java.util.concurrent.Executors

/** Streams the assistant's PCM voice. Writes happen off the network thread. */
class Speaker {
    private val writer = Executors.newSingleThreadExecutor { Thread(it, "speaker") }
    @Volatile private var track: AudioTrack? = null
    @Volatile private var trackRate = 0

    // A 16 kHz copy of everything written to the track, for the echo canceller (TalkOver).
    private val ring = ShortArray(RING)
    private var ringWritten = 0L // samples since the track was created or flushed
    private var resamplePos = 0.0

    // Bumped by flush(); queued writes from before it are dropped.
    @Volatile private var generation = 0
    @Volatile private var audibleUntil = 0L

    // A reply runs from its first chunk to endReply() or flush().
    @Volatile private var replyOpen = false
    @Volatile private var gaps = 0
    private var underrunsSeen = 0 // speaker thread only

    fun play(pcm: ByteArray, sampleRate: Int) {
        val gen = generation
        val first = !replyOpen
        replyOpen = true
        val now = SystemClock.elapsedRealtime()
        val durationMs = pcm.size / 2 * 1000L / sampleRate
        audibleUntil = maxOf(audibleUntil, now) + durationMs + if (first) LEAD_IN_MS else 0
        writer.execute {
            if (gen != generation) return@execute
            val out = trackFor(sampleRate)
            if (first) {
                // A little silence first gives network hiccups room before the speaker runs dry.
                val lead = ByteArray((sampleRate * LEAD_IN_MS / 1000).toInt() * 2)
                keep(lead, sampleRate)
                out.write(lead, 0, lead.size)
            } else if (out.underrunCount > underrunsSeen) {
                gaps++
            }
            keep(pcm, sampleRate)
            out.write(pcm, 0, pcm.size)
            underrunsSeen = out.underrunCount
        }
    }

    /** The server finished this reply; the speaker running dry after it is expected. */
    fun endReply() {
        replyOpen = false
    }

    /** Times the speaker ran dry in the middle of a reply since the last call. */
    fun takeGaps(): Int = gaps.also { gaps = 0 }

    /** True while speech is (or is about to be) coming out of the speaker. */
    fun isAudible(tailMs: Long = 300): Boolean =
        SystemClock.elapsedRealtime() < audibleUntil + tailMs

    fun flush() {
        generation++
        audibleUntil = 0
        replyOpen = false
        writer.execute {
            track?.run {
                pause()
                flush()
                play()
            }
            clearRing() // the track's position starts again at 0
        }
    }

    fun release() {
        generation++
        audibleUntil = 0
        writer.execute {
            track?.release()
            track = null
            trackRate = 0
            clearRing()
        }
    }

    private fun trackFor(rate: Int): AudioTrack {
        track?.let { if (trackRate == rate) return it else it.release() }
        val minBuffer = AudioTrack.getMinBufferSize(rate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val created = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(rate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setBufferSizeInBytes(minBuffer * 4)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        created.play()
        clearRing()
        track = created
        trackRate = rate
        underrunsSeen = 0
        return created
    }

    /**
     * What the speaker played, at 16 kHz, ending [backSamples] before the sample playing now
     * (silence where nothing played). For the echo canceller; any thread.
     */
    fun reference(out: ShortArray, backSamples: Int) {
        val t = track
        val rate = trackRate
        if (t == null || rate == 0) {
            out.fill(0)
            return
        }
        val playing = (t.playbackHeadPosition.toLong() and 0xFFFFFFFFL) * REF_RATE / rate
        synchronized(ring) {
            val start = playing - backSamples - out.size
            for (i in out.indices) {
                val at = start + i
                out[i] = if (at >= 0 && at < ringWritten && at >= ringWritten - RING) ring[(at % RING).toInt()] else 0
            }
        }
    }

    /** Writer thread: appends [pcm] (16-bit, [rate] Hz) to the ring at 16 kHz. */
    private fun keep(pcm: ByteArray, rate: Int) {
        val n = pcm.size / 2
        if (n == 0) return
        val step = rate.toDouble() / REF_RATE
        synchronized(ring) {
            var pos = resamplePos
            while (pos < n - 1) {
                val i = pos.toInt()
                val a = sample(pcm, i)
                val b = sample(pcm, i + 1)
                ring[(ringWritten % RING).toInt()] = (a + (b - a) * (pos - i)).toInt().toShort()
                ringWritten++
                pos += step
            }
            resamplePos = (pos - n).coerceAtLeast(0.0)
        }
    }

    private fun clearRing() = synchronized(ring) {
        ringWritten = 0
        resamplePos = 0.0
    }

    private fun sample(pcm: ByteArray, i: Int): Int = (pcm[2 * i].toInt() and 0xFF) or (pcm[2 * i + 1].toInt() shl 8)

    private companion object {
        const val REF_RATE = 16_000
        const val RING = REF_RATE * 4 // 4 s
        const val LEAD_IN_MS = 150L
    }
}

package app.genrix.android.wake

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import app.genrix.android.Diag
import app.genrix.android.audio.othersPlaying

/**
 * Listens for the wake phrase on the phone itself. The audio only goes through the local
 * model; nothing is recorded, stored or sent. Runs inside VoiceService between sessions.
 */
class WakeListener(private val context: Context, private val onWake: () -> Unit) {
    private val main = Handler(Looper.getMainLooper())
    @Volatile private var running = false
    @Volatile private var profile: VoiceProfile? = null
    private var thread: Thread? = null
    private var model: WakeWordModel? = null
    private val audio = context.getSystemService(AudioManager::class.java)

    /** Callers check RECORD_AUDIO first. Returns false if the model or mic isn't available. */
    fun start(): Boolean {
        if (running) {
            profile = VoiceProfile.load(context) // a new or forgotten voice applies at once
            return true
        }
        val detector = model ?: runCatching { WakeWordModel(context) }
            .onFailure { Diag.w("wake: model failed to load (${it.javaClass.simpleName})") }
            .getOrNull()?.also { model = it } ?: return false
        // A just-stopped listener may still be finishing a chunk with the same model.
        thread?.join(JOIN_MS)
        val first = openMic(echoCancel = musicPlaying()) ?: return false
        detector.reset()
        profile = VoiceProfile.load(context)
        running = true
        thread = Thread({ listen(detector, first) }, "wake").apply { start() }
        Diag.i("wake: listening for \"${WakeWordModel.PHRASE}\"")
        return true
    }

    /**
     * Music from the phone's own speaker drowns the phrase. While something plays, the mic
     * switches to the echo-cancelled voice-call source, which on many phones subtracts what
     * the speaker plays; when the music stops it goes back to the plain one.
     *
     * In a quiet room the model rests (it costs far more than the mic). The last 2.5 s of
     * audio is kept, and when a sound starts the model catches up on it before going on, so
     * it hears the same thing it would have heard running all along.
     */
    private fun listen(detector: WakeWordModel, first: Mic) {
        var mic: Mic? = first
        val recent = Array(PREROLL_CHUNKS) { ShortArray(WakeWordModel.CHUNK) }
        var next = 0
        val gate = SpeechGate()
        val stats = GateStats()
        while (running) {
            val current = mic ?: openMic(echoCancel = musicPlaying()) ?: break
            mic = null
            detector.reset()
            var awake = false
            var chunks = 0
            while (running) {
                if (++chunks % MUSIC_CHECK_CHUNKS == 0 && musicPlaying() != current.echoCancel) break
                val chunk = recent[next]
                if (!current.read(chunk)) continue
                next = (next + 1) % PREROLL_CHUNKS
                // Music keeps the model on: its loudness would hide a voice from the gate.
                val open = gate.open(chunk) || current.echoCancel
                stats.add(open, gate.background)
                if (!open) {
                    awake = false
                    continue
                }
                if (!awake) {
                    awake = true
                    detector.reset()
                    for (i in 0 until PREROLL_CHUNKS - 1) check(detector, recent[(next + i) % PREROLL_CHUNKS], current.echoCancel)
                }
                check(detector, chunk, current.echoCancel)
            }
            current.close()
        }
    }

    private var lastWake = 0L
    private var lastNearMiss = 0L

    private fun check(detector: WakeWordModel, chunk: ShortArray, music: Boolean) {
        val score = runCatching { detector.score(chunk) }.getOrDefault(0f)
        val now = SystemClock.elapsedRealtime()
        // The user's own samples rescue a weak model score when the phrase matches them.
        val voice = profile?.takeIf { score >= VOICE_GATE }?.let { it to it.similarity(detector.window()) }
        val matched = voice != null && voice.second >= voice.first.threshold
        val note = if (music) ", music" else ""
        if ((score >= THRESHOLD || matched) && now - lastWake > COOLDOWN_MS) {
            lastWake = now
            Diag.i("wake: heard (score %.2f, voice %s%s)".format(score, voice?.second?.let { "%.3f".format(it) } ?: "-", note))
            main.post(onWake)
        } else if (score >= VOICE_GATE && now - lastNearMiss > 1_000 && now - lastWake > COOLDOWN_MS) {
            // Numbers only, to tune the thresholds for real voices.
            lastNearMiss = now
            Diag.i(
                "wake: near miss (score %.2f, voice %s of %s%s)".format(
                    score,
                    voice?.second?.let { "%.3f".format(it) } ?: "-",
                    voice?.first?.threshold?.let { "%.3f".format(it) } ?: "-",
                    note,
                ),
            )
        }
    }

    /** How often the model ran, logged every 10 minutes (to check the battery saving). */
    private class GateStats {
        private var total = 0
        private var open = 0

        fun add(isOpen: Boolean, background: Int) {
            total++
            if (isOpen) open++
            if (total == STATS_CHUNKS) {
                Diag.i("wake: model ran ${open * 100 / total}% of the last 10 min (background level $background)")
                total = 0
                open = 0
            }
        }
    }

    private fun musicPlaying(): Boolean = othersPlaying(audio)

    @SuppressLint("MissingPermission")
    private fun openMic(echoCancel: Boolean): Mic? {
        val rate = 16_000
        val minBuffer = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val record = AudioRecord(
            if (echoCancel) MediaRecorder.AudioSource.VOICE_COMMUNICATION else MediaRecorder.AudioSource.VOICE_RECOGNITION,
            rate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            // Room for the catch-up after a quiet spell.
            maxOf(minBuffer, WakeWordModel.CHUNK * 2 * 16),
        )
        if (record.state != AudioRecord.STATE_INITIALIZED) {
            record.release()
            Diag.w("wake: mic busy")
            return null
        }
        val canceller = if (echoCancel && AcousticEchoCanceler.isAvailable()) {
            runCatching { AcousticEchoCanceler.create(record.audioSessionId)?.apply { enabled = true } }.getOrNull()
        } else {
            null
        }
        if (echoCancel) Diag.i("wake: music playing, echo canceller=${canceller != null}")
        record.startRecording()
        return Mic(record, canceller, echoCancel)
    }

    private class Mic(val record: AudioRecord, val canceller: AcousticEchoCanceler?, val echoCancel: Boolean) {
        fun read(chunk: ShortArray): Boolean {
            var filled = 0
            while (filled < chunk.size) {
                val read = record.read(chunk, filled, chunk.size - filled)
                if (read <= 0) return false
                filled += read
            }
            return true
        }

        fun close() {
            record.stop()
            canceller?.release()
            record.release()
        }
    }

    fun stop() {
        if (!running) return
        running = false
        Diag.i("wake: paused")
    }

    private companion object {
        // openWakeWord's default is 0.5; real voices on this phone scored 0.52-0.63 when heard.
        const val THRESHOLD = 0.4f
        // Below this the model hears nothing like the phrase; the voice check isn't tried.
        const val VOICE_GATE = 0.1f
        const val COOLDOWN_MS = 2_000L
        const val JOIN_MS = 500L
        const val MUSIC_CHECK_CHUNKS = 12 // about once a second
        // More than the model needs to settle after a reset (about 26 chunks).
        const val PREROLL_CHUNKS = 32
        const val STATS_CHUNKS = 7_500 // 10 minutes
    }
}

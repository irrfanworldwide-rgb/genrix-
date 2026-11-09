package app.genrix.android.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import app.genrix.android.Diag
import app.genrix.android.live.LiveProtocol

/** 16 kHz mono PCM from the mic, in 100 ms chunks, on its own thread. */
class MicRecorder(private val onChunk: (ByteArray) -> Unit) {
    @Volatile private var running = false
    private var thread: Thread? = null

    /** Callers check the RECORD_AUDIO permission first. Returns false if the mic is busy. */
    @SuppressLint("MissingPermission")
    fun start(): Boolean {
        if (running) return true
        val rate = LiveProtocol.INPUT_SAMPLE_RATE
        val chunkBytes = rate / 10 * 2
        val minBuffer = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val record = AudioRecord(
            // The voice-call source turns on the phone's own echo and noise handling.
            MediaRecorder.AudioSource.VOICE_COMMUNICATION,
            rate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            maxOf(minBuffer, chunkBytes * 4),
        )
        if (record.state != AudioRecord.STATE_INITIALIZED) {
            record.release()
            Diag.w("mic: could not open (busy or blocked)")
            return false
        }
        val echo = if (AcousticEchoCanceler.isAvailable()) AcousticEchoCanceler.create(record.audioSessionId) else null
        val noise = if (NoiseSuppressor.isAvailable()) NoiseSuppressor.create(record.audioSessionId) else null
        val effects = listOfNotNull(echo, noise).onEach { it.enabled = true }
        Diag.i("mic: started, echo canceller=${echo != null}, noise suppressor=${noise != null}")

        running = true
        record.startRecording()
        thread = Thread({
            val buffer = ByteArray(chunkBytes)
            var reportedError = false
            while (running) {
                val read = record.read(buffer, 0, buffer.size)
                if (read > 0) {
                    onChunk(buffer.copyOf(read))
                } else if (read < 0 && !reportedError) {
                    reportedError = true
                    Diag.w("mic: read error $read")
                }
            }
            record.stop()
            effects.forEach { it.release() }
            record.release()
        }, "mic").apply { start() }
        return true
    }

    fun stop() {
        running = false
        thread = null
    }
}

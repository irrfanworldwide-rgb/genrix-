package app.genrix.android.wake

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import app.genrix.android.Diag
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Records one "Hey Genrix" sample for the voice profile. The audio stays in memory only
 * long enough to turn it into speech features; it is never saved or sent.
 */
object VoiceSetup {
    sealed interface Sample {
        class Ok(val window: FloatArray, val score: Float) : Sample
        class Problem(val message: String) : Sample
    }

    private const val RATE = 16_000
    const val LEAD_MS = 1_200L // "get ready": the model needs speech context before the phrase
    private const val TOTAL_MS = 3_400L
    private const val SPEECH_OVER_ROOM = 1.8
    private const val MIN_SPEECH = 12.0

    /** Callers check RECORD_AUDIO and pause the wake listener first. [speakNow] runs after the lead-in. */
    @SuppressLint("MissingPermission")
    suspend fun record(context: Context, speakNow: () -> Unit): Sample = withContext(Dispatchers.IO) {
        val total = (RATE * TOTAL_MS / 1000).toInt()
        val minBuffer = AudioRecord.getMinBufferSize(RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val record = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION, // same mic as the wake listener
            RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            maxOf(minBuffer, total * 2),
        )
        if (record.state != AudioRecord.STATE_INITIALIZED) {
            record.release()
            return@withContext Sample.Problem("The microphone is busy. Close other apps using it and try again.")
        }
        val audio = ShortArray(total)
        val lead = (RATE * LEAD_MS / 1000).toInt()
        var filled = 0
        var told = false
        record.startRecording()
        try {
            while (filled < total) {
                val read = record.read(audio, filled, minOf(1600, total - filled))
                if (read <= 0) break
                filled += read
                if (!told && filled >= lead) {
                    told = true
                    withContext(Dispatchers.Main) { speakNow() }
                }
            }
        } finally {
            record.stop()
            record.release()
        }
        if (!heardSpeech(audio, lead)) {
            return@withContext Sample.Problem("I didn't hear you. Say it a little louder, closer to the phone.")
        }
        val best = WakeWordModel(context).use { it.bestWindow(audio) }
            ?: return@withContext Sample.Problem("That didn't work. Try again.")
        Sample.Ok(best.first, best.second)
    }

    /**
     * Speech after the lead-in should be clearly louder than the room before it. The first
     * ~0.4 s is skipped: the mic starts with a loud burst on this phone.
     */
    private fun heardSpeech(audio: ShortArray, lead: Int): Boolean {
        fun rms(from: Int, to: Int): Double {
            var mean = 0.0
            for (i in from until to) mean += audio[i]
            mean /= (to - from)
            var sum = 0.0
            for (i in from until to) sum += (audio[i] - mean) * (audio[i] - mean)
            return kotlin.math.sqrt(sum / (to - from))
        }
        val room = rms(RATE * 4 / 10, lead)
        val loudest = (lead until audio.size - 1600 step 1600).maxOf { rms(it, it + 1600) }
        Diag.i("voice setup: room level %.0f, loudest %.0f".format(room, loudest))
        // This phone's wake mic is quiet (speech often only 30-60), so the bar is modest.
        return loudest >= maxOf(room * SPEECH_OVER_ROOM, MIN_SPEECH)
    }
}

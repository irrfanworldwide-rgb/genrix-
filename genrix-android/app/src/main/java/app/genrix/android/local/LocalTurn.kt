package app.genrix.android.local

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import app.genrix.android.Diag
import app.genrix.android.assistant.VoiceService
import app.genrix.android.assistant.VoiceSession
import app.genrix.android.audio.MusicPause
import app.genrix.android.data.Settings
import app.genrix.android.learn.Habits
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/** What a local turn is doing, for the Home screen. */
enum class LocalPhase { Idle, Listening, Doing, Speaking }

/**
 * One request after the wake word, handled on the phone: speech → [LocalRouter] →
 * [LocalActions] → a short spoken reply. Requests it doesn't know go to a Gemini session
 * with the words already heard. Speech is heard by the phone's own on-device recogniser if it
 * has one, else by Vosk offline ([OfflineSpeech]); the phone's network recogniser only if the
 * user allowed it in Settings (it sends the audio to its provider, usually Google).
 */
class LocalTurn private constructor(private val context: Context) {
    private val main = Handler(Looper.getMainLooper())
    private val settings = Settings(context)
    private val actions = LocalActions(context)
    private val musicPause = MusicPause(context)
    private val offline = OfflineSpeech.get(context)
    private var recognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var heardAt = 0L

    @Volatile var active = false
        private set

    private val _phase = MutableStateFlow(LocalPhase.Idle)
    val phase: StateFlow<LocalPhase> = _phase.asStateFlow()

    /** A Home-screen quick action: runs [command] at once, without speech. */
    fun runNow(command: LocalCommand): String {
        val result = runCatching { actions.run(command) }.getOrElse { LocalResult("That didn't work.") }
        if (!result.handOff) habits()?.record(command, hourNow())
        val reply = if (result.handOff) "Ask by voice for this one." else result.reply.ifEmpty { "Done." }
        ActivityLog.add(ActivityLog.Entry(label(command), reply, local = true))
        return reply
    }

    /** Where speech goes, or null when no allowed recogniser exists (then Gemini hears it). */
    fun recogniser(): String? = when {
        Build.VERSION.SDK_INT >= 31 && SpeechRecognizer.isOnDeviceRecognitionAvailable(context) -> "on-device"
        settings.networkSpeech && SpeechRecognizer.isRecognitionAvailable(context) -> "network"
        offline.available() -> "offline"
        else -> null
    }

    /** True if this turn took over; false means the caller should start Gemini as before. */
    fun start(): Boolean {
        if (active) return true
        val kind = recogniser() ?: return false.also { Diag.i("local: no allowed speech recogniser, using Gemini") }
        active = true
        _phase.value = LocalPhase.Listening
        VoiceService.start(context) // stops the wake listener so the recogniser gets the mic
        musicPause.pause()
        warmUpVoice()
        main.postDelayed({ listen(kind) }, MIC_HANDOVER_MS)
        return true
    }

    private fun listen(kind: String) {
        if (kind == "offline") {
            Diag.i("local: listening (offline speech)")
            offline.listen { texts ->
                heardAt = SystemClock.elapsedRealtime()
                if (texts.isEmpty()) {
                    Diag.i("local: heard nothing")
                    finish()
                } else {
                    handle(texts)
                }
            }
            return
        }
        val created = if (kind == "on-device" && Build.VERSION.SDK_INT >= 31) {
            SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
        } else {
            SpeechRecognizer.createSpeechRecognizer(context)
        }
        recognizer = created
        created.setRecognitionListener(Listener())
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-IN")
            .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        Diag.i("local: listening ($kind speech)")
        created.startListening(intent)
    }

    private inner class Listener : RecognitionListener {
        override fun onResults(results: Bundle?) {
            heardAt = SystemClock.elapsedRealtime()
            val texts = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
            closeRecognizer()
            handle(texts)
        }

        override fun onError(error: Int) {
            closeRecognizer()
            when (error) {
                SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> {
                    Diag.i("local: heard nothing (error $error)")
                    finish()
                }
                else -> {
                    // The recogniser can't run (no offline pack, busy, no network): Gemini hears it instead.
                    Diag.w("local: speech error $error, using Gemini")
                    handOff(null)
                }
            }
        }

        override fun onReadyForSpeech(params: Bundle?) { Diag.i("local: ready") }
        override fun onBeginningOfSpeech() { Diag.i("local: speech started") }
        override fun onRmsChanged(rmsdB: Float) = Unit
        override fun onBufferReceived(buffer: ByteArray?) = Unit
        override fun onEndOfSpeech() = Unit
        override fun onPartialResults(partialResults: Bundle?) = Unit
        override fun onEvent(eventType: Int, params: Bundle?) = Unit
    }

    private fun handle(texts: List<String>) {
        // Phrases learned from earlier Gemini fallbacks run locally too.
        val command = LocalRouter.match(texts) ?: habits()?.lookup(texts)
        _phase.value = LocalPhase.Doing
        if (command == null) {
            Diag.i("local: no local match, asking Gemini")
            handOff(texts.firstOrNull())
            return
        }
        // Music commands talk to the music app, so give its audio back first.
        if (command is LocalCommand.Media || command == LocalCommand.Stop) musicPause.resume()
        val result = runCatching { actions.run(command) }.getOrElse {
            Diag.w("local: ${command.javaClass.simpleName} failed (${it.javaClass.simpleName})")
            LocalResult("Sorry, that didn't work.")
        }
        if (result.handOff) {
            Diag.i("local: ${command.javaClass.simpleName} needs Gemini")
            handOff(texts.firstOrNull())
            return
        }
        Diag.i("local: ${command.javaClass.simpleName} done in ${SystemClock.elapsedRealtime() - heardAt} ms")
        habits()?.record(command, hourNow())
        ActivityLog.add(ActivityLog.Entry(label(command), result.reply.ifEmpty { "Done." }, local = true))
        if (result.reply.isEmpty()) finish() else say(result.reply)
    }

    private fun handOff(text: String?) {
        text?.let { habits()?.expect(it) }
        active = false
        _phase.value = LocalPhase.Idle
        musicPause.resume()
        VoiceSession.get(context).start(firstText = text)
    }

    private fun habits(): Habits? = if (settings.learning) Habits.get(context) else null

    private fun hourNow() = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)

    private fun finish() {
        active = false
        _phase.value = LocalPhase.Idle
        musicPause.resume()
        VoiceService.stop(context) // back to the wake listener
        main.postDelayed(::shutDownVoice, SHUTDOWN, VOICE_IDLE_MS)
    }

    private fun closeRecognizer() {
        recognizer?.destroy()
        recognizer = null
    }

    private fun warmUpVoice() {
        main.removeCallbacksAndMessages(SHUTDOWN)
        if (tts != null) return
        tts = TextToSpeech(context) { status ->
            ttsReady = status == TextToSpeech.SUCCESS
            tts?.language = Locale("en", "IN")
            tts?.setAudioAttributes(
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANT).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build(),
            )
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) = Unit
                override fun onDone(utteranceId: String?) { main.post(::finish) }
                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) { main.post(::finish) }
            })
        }
    }

    private fun say(reply: String) {
        val voice = tts
        if (voice == null || !ttsReady) {
            Diag.w("local: no voice for the reply")
            finish()
            return
        }
        _phase.value = LocalPhase.Speaking
        voice.speak(reply, TextToSpeech.QUEUE_FLUSH, null, "reply")
    }

    private fun shutDownVoice() {
        if (active) return
        offline.release()
        tts?.shutdown()
        tts = null
        ttsReady = false
    }

    companion object {
        /** A short human name for the Activity list. */
        fun label(command: LocalCommand): String = when (command) {
            is LocalCommand.SetVolume, is LocalCommand.StepVolume -> "Volume"
            is LocalCommand.Mute -> if (command.on) "Mute" else "Unmute"
            is LocalCommand.Media -> "Music: ${command.key.name.lowercase()}"
            is LocalCommand.Torch -> "Flashlight"
            LocalCommand.Battery -> "Battery"
            LocalCommand.Time -> "Time"
            LocalCommand.Date -> "Date"
            is LocalCommand.Timer -> "Timer"
            is LocalCommand.Alarm -> "Alarm"
            is LocalCommand.OpenApp -> "Open ${command.name}"
            LocalCommand.Stop -> "Stop"
        }

        private const val MIC_HANDOVER_MS = 300L
        private const val VOICE_IDLE_MS = 5 * 60_000L // keeps the speech model and voice loaded
        private val SHUTDOWN = Any()

        @Volatile private var instance: LocalTurn? = null

        fun get(context: Context): LocalTurn = instance ?: synchronized(this) {
            instance ?: LocalTurn(context.applicationContext).also { instance = it }
        }
    }
}

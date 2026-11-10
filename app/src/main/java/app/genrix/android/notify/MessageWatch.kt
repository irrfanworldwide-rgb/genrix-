package app.genrix.android.notify

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import app.genrix.android.Diag
import app.genrix.android.assistant.VoiceSession
import java.util.Locale

/**
 * The "watch my messages" scene: for a while, Genrix stays quiet and says out loud only when
 * a person needs the user ([WatchRules]). Runs on the phone with its own voice; nothing goes
 * to Gemini, and nothing is kept after the watch ends.
 */
object MessageWatch {
    @Volatile var untilMillis = 0L
        private set
    private var rules = WatchRules()
    private val main = Handler(Looper.getMainLooper())
    private var tts: TextToSpeech? = null

    val watching: Boolean get() = System.currentTimeMillis() < untilMillis

    fun start(minutes: Int) {
        untilMillis = System.currentTimeMillis() + minutes * 60_000L
        rules = WatchRules()
        Diag.i("watch: on for $minutes min")
    }

    fun stop() {
        untilMillis = 0L
        main.post(::shutDown)
        Diag.i("watch: off")
    }

    /** Called by [NotificationWatch] for every new notification. */
    fun posted(context: Context, n: Seen) {
        if (!watching || n.packageName == context.packageName) return
        val audio = context.getSystemService(AudioManager::class.java)
        val silent = audio.ringerMode != AudioManager.RINGER_MODE_NORMAL
        val line = rules.decide(n, System.currentTimeMillis(), silent, VoiceSession.get(context).active) ?: return
        Diag.i("watch: speaking up")
        main.post { say(context.applicationContext, line) }
    }

    private fun say(context: Context, line: String) {
        tts?.let {
            it.speak(line, TextToSpeech.QUEUE_ADD, null, "watch")
            return
        }
        lateinit var voice: TextToSpeech
        voice = TextToSpeech(context) { status ->
            if (status != TextToSpeech.SUCCESS) {
                Diag.w("watch: no voice")
                return@TextToSpeech
            }
            voice.language = Locale("en", "IN")
            voice.setAudioAttributes(
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANT).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build(),
            )
            voice.speak(line, TextToSpeech.QUEUE_ADD, null, "watch")
        }
        tts = voice
    }

    private fun shutDown() {
        tts?.shutdown()
        tts = null
    }
}

package app.genrix.android.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import app.genrix.android.Diag

/**
 * Pauses other audio (music, videos) while the user talks to the assistant, like Google
 * Assistant does: transient audio focus; the music app resumes when it is given back.
 */
class MusicPause(context: Context) {
    private val audio = context.getSystemService(AudioManager::class.java)
    @Volatile private var held = false

    private val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANT)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build(),
        )
        // A music app we just started takes focus back; then there is nothing to give back.
        .setOnAudioFocusChangeListener { change -> if (change < 0) held = false }
        .build()

    fun pause() {
        if (held) return
        val wasPlaying = audio.isMusicActive
        held = audio.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        if (held && wasPlaying) Diag.i("audio: other audio paused while listening")
    }

    /** Another app is playing music or video right now (our replies use USAGE_ASSISTANT). */
    fun othersPlaying(): Boolean = othersPlaying(audio)

    fun resume() {
        if (!held) return
        held = false
        audio.abandonAudioFocusRequest(request)
    }
}

/** True while some app plays media; pair it with "our speaker is silent" during a session. */
fun othersPlaying(audio: AudioManager): Boolean =
    audio.isMusicActive && audio.activePlaybackConfigurations.any { it.audioAttributes.usage == AudioAttributes.USAGE_MEDIA }

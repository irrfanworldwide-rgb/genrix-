package app.genrix.android.tools

import android.accessibilityservice.AccessibilityService
import android.app.SearchManager
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.provider.MediaStore
import android.view.KeyEvent
import app.genrix.android.Branding
import app.genrix.android.Diag
import app.genrix.android.control.ControlService
import kotlinx.coroutines.delay
import org.json.JSONObject

/** Play a named song in a music app, and pause / skip whatever is playing. */
object MediaTools {
    fun all(): List<PhoneTool> = listOf(PlaySong, MediaControl)

    private val APPS = mapOf(
        "youtube_music" to "com.google.android.apps.youtube.music",
        "spotify" to "com.spotify.music",
    )

    private object PlaySong : PhoneTool {
        override val name = "play_song"

        override fun declaration() = Schema.function(
            name,
            "Opens a music app on a song, artist, album or playlist. Free accounts only show the results, " +
                "so the user taps Play.",
            Schema.string("query", "What to play, e.g. \"Kesariya Arijit Singh\".", required = true),
            Schema.string("app", "Music app, if the user named one.", choices = APPS.keys.toList()),
        )

        override suspend fun run(args: JSONObject, host: ToolHost): JSONObject {
            val query = args.text("query")
            if (query.isEmpty()) return Results.failed("no_query", "Ask what to play.")
            val app = APPS[args.text("app")] ?: APPS.getValue("youtube_music")
            val play = Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH)
                .putExtra(SearchManager.QUERY, query)
                .setPackage(app)
            // Without the app installed, a YouTube Music search in the browser still works.
            val intent = if (play.resolveActivity(host.context.packageManager) != null) {
                play
            } else {
                Intent(Intent.ACTION_VIEW, Uri.parse("https://music.youtube.com/search?q=${Uri.encode(query)}"))
            }
            if (intent !== play || ControlService.instance == null) {
                return host.openApp(
                    intent,
                    "music",
                    "The music app will open on \"$query\" after you speak; the user taps Play on the top result.",
                )
            }
            return host.openApp(
                intent,
                "music",
                "The music app will open on \"$query\" after you speak and ${Branding.ASSISTANT_NAME} will press Play on the top result.",
            ) { pressPlay(app, host) }
        }

        /**
         * Free accounts only show search results, so press the top result's Play button
         * (described as "Play <title>"; the mini-player's "Play video" would resume the old
         * queue instead). Null when it worked.
         */
        private suspend fun pressPlay(app: String, host: ToolHost): String? {
            val control = ControlService.instance ?: return "Phone control turned off; tell the user to tap Play."
            if (!control.waitFor(app, OPEN_WAIT_MS)) return "The music app didn't open in time; tell the user to tap Play."
            var collapsedPlayer = false
            repeat(RESULT_TRIES) {
                // A song already open full screen hides the new results; minimise it once.
                if (!collapsedPlayer && control.findFirst { "audio_video_switch_toggle" in it.viewIdResourceName.orEmpty() } != null) {
                    collapsedPlayer = true
                    control.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
                    delay(700)
                }
                // Song or album: "Play <title>". Artist: "Shuffle <name>". Later tries: the
                // first row marked "Song", for searches whose top result is something else.
                val play = control.findFirst { node ->
                    val label = node.contentDescription?.toString().orEmpty()
                    node.isClickable &&
                        node.packageName == app &&
                        (label.startsWith("Play ", ignoreCase = true) || label.startsWith("Shuffle ", ignoreCase = true)) &&
                        "mini_player" !in node.viewIdResourceName.orEmpty()
                } ?: if (it >= SONG_ROW_AFTER) control.findFirst { node ->
                    node.packageName == app && node.text?.toString().orEmpty().startsWith("Song •")
                } else {
                    null
                }
                if (play != null) {
                    control.click(play)
                    control.overlay.showActive()
                    Diag.i("music: pressed Play")
                    host.endSessionSoon()
                    return null
                }
                delay(500)
            }
            Diag.w("music: no Play button found")
            return "${Branding.ASSISTANT_NAME} couldn't find the Play button; tell the user to tap Play on the top result."
        }

        private const val OPEN_WAIT_MS = 8_000L
        private const val RESULT_TRIES = 16
        private const val SONG_ROW_AFTER = 6
    }

    private object MediaControl : PhoneTool {
        override val name = "media_control"

        override fun declaration() = Schema.function(
            name,
            "Controls whatever is playing on the phone.",
            Schema.string("action", "What to do.", required = true, choices = KEYS.keys.toList()),
        )

        override suspend fun run(args: JSONObject, host: ToolHost): JSONObject {
            val action = args.text("action")
            val key = KEYS[action] ?: return Results.failed("bad_action", "Ask whether to play, pause or skip.")
            val audio = host.context.getSystemService(AudioManager::class.java)
            audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, key))
            audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, key))
            // Music resumes when the session gives back audio focus, so play / next end it.
            if (action != "pause") host.endSessionSoon()
            return Results.done("Sent $action to the music app.")
        }

        private val KEYS = mapOf(
            "play" to KeyEvent.KEYCODE_MEDIA_PLAY,
            "pause" to KeyEvent.KEYCODE_MEDIA_PAUSE,
            "next" to KeyEvent.KEYCODE_MEDIA_NEXT,
            "previous" to KeyEvent.KEYCODE_MEDIA_PREVIOUS,
        )
    }
}

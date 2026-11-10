package app.genrix.android.tools

import app.genrix.android.Branding
import app.genrix.android.Diag
import app.genrix.android.notify.MessageWatch
import app.genrix.android.notify.NotificationWatch
import app.genrix.android.notify.Replies
import app.genrix.android.notify.Triage
import org.json.JSONObject

/** "What did I miss?": recent notifications, sorted by [Triage] before the model sees them. */
object NotifyTools {
    fun all(): List<PhoneTool> = listOf(NotificationsCheck, WatchMessages, ReplyPrepare, ReplySend)

    /** Step 1 of a hands-free reply: nothing is sent; the result is the line to read back. */
    private object ReplyPrepare : PhoneTool {
        override val name = "reply_prepare"

        override fun declaration() = Schema.function(
            name,
            "Prepares a reply to a message from notifications_check (its [reply N] id), without opening the app. " +
                "Sends nothing: read the returned line back to the user word for word and wait for their answer.",
            Schema.integer("reply_id", "The N from [reply N].", required = true),
            Schema.string("text", "The reply exactly as the user said it.", required = true),
        )

        override suspend fun run(args: JSONObject, host: ToolHost): JSONObject {
            val text = args.text("text").take(MAX_REPLY).ifEmpty { return Results.failed("no_text", "Ask what to reply.") }
            val line = Replies.prepare(args.optInt("reply_id", 0), text, System.currentTimeMillis())
                ?: return Results.failed("no_chat", "That message can't be answered from here any more. Offer to open the chat.")
            return Results.done("Say exactly: $line Then wait. Only if the user's answer is yes, call reply_send.")
        }
    }

    /** Step 2: sends only if the user's own words after the read-back were a clear yes. */
    private object ReplySend : PhoneTool {
        override val name = "reply_send"

        override fun declaration() = Schema.function(
            name,
            "Sends the reply prepared by reply_prepare, after the user answered yes. The phone checks their words itself.",
        )

        override suspend fun run(args: JSONObject, host: ToolHost): JSONObject =
            when (val outcome = Replies.send(host.context, System.currentTimeMillis())) {
                is Replies.Outcome.Sent -> Results.done("Sent to ${outcome.who} on ${outcome.app}.")
                is Replies.Outcome.Refused -> Results.failed("not_sent", outcome.why)
            }
    }

    private fun noAccess() = Results.failed(
        "no_access",
        "${Branding.ASSISTANT_NAME} can't see notifications yet. Tell the user to turn on " +
            "Notification access in ${Branding.ASSISTANT_NAME} → Settings → Notifications.",
    )

    /** The "watch my messages" scene, see [MessageWatch]. */
    private object WatchMessages : PhoneTool {
        override val name = "watch_messages"

        override fun declaration() = Schema.function(
            name,
            "Starts or stops watching messages: for a while the phone stays quiet and says out loud only " +
                "who messaged or called (never what), and warns about links from unknown numbers. For 'keep an " +
                "eye on my messages', 'tell me if someone texts', 'stop watching'.",
            Schema.integer("minutes", "How long to watch; default 60, at most 180; 0 stops."),
        )

        override suspend fun run(args: JSONObject, host: ToolHost): JSONObject {
            val minutes = args.optInt("minutes", DEFAULT_WATCH_MIN).coerceIn(0, MAX_WATCH_MIN)
            if (minutes == 0) {
                MessageWatch.stop()
                return Results.done("Stopped watching messages.")
            }
            if (!NotificationWatch.allowed(host.context)) return noAccess()
            MessageWatch.start(minutes)
            val until = java.time.LocalTime.now().plusMinutes(minutes.toLong()).format(java.time.format.DateTimeFormatter.ofPattern("h:mm a"))
            return Results.done(
                "Watching until $until. The phone will say who messaged or called, not what; it stays quiet on silent " +
                    "or vibrate, and for groups and offers.",
            )
        }
    }

    private object NotificationsCheck : PhoneTool {
        override val name = "notifications_check"

        override fun declaration() = Schema.function(
            name,
            "What the user missed: messages and calls from people (name first), suspicious links, " +
                "codes (digits hidden) and a count of quiet ones (groups, offers). For 'what did I miss', " +
                "'any messages'.",
            Schema.integer("hours", "How far back to look; default 3, at most 12."),
        )

        override suspend fun run(args: JSONObject, host: ToolHost): JSONObject {
            if (!NotificationWatch.allowed(host.context)) return noAccess()
            val hours = args.optInt("hours", DEFAULT_HOURS).coerceIn(1, MAX_HOURS)
            val since = System.currentTimeMillis() - hours * HOUR_MS
            val items = Triage.sort(NotificationWatch.since(since), host.context.packageName)
            Diag.i("notifications: ${items.size} sorted, ${items.count { it.kind == Triage.Kind.Needs }} need the user")
            return Results.done("Last $hours h:\n" + Triage.summary(items))
        }
    }

    private const val DEFAULT_HOURS = 3
    private const val DEFAULT_WATCH_MIN = 60
    private const val MAX_WATCH_MIN = 180
    private const val MAX_REPLY = 500
    private const val MAX_HOURS = 12
    private const val HOUR_MS = 60 * 60 * 1000L
}

package app.genrix.android.tools

import android.Manifest
import android.os.Build
import app.genrix.android.Branding
import app.genrix.android.reminders.ReminderRules
import app.genrix.android.reminders.Reminders
import java.time.LocalDateTime
import org.json.JSONObject

/** A reminder is a notification at a time, never a chat message. */
object ReminderTools {
    fun all(): List<PhoneTool> = listOf(Add, ListAll, Cancel)

    private object Add : PhoneTool {
        override val name = "reminder_set"

        override fun declaration() = Schema.function(
            name,
            "Sets a reminder: a notification on this phone at a time. Give either at or in_minutes.",
            Schema.string("text", "What to remind about, short, in the user's words.", required = true),
            Schema.string("at", "Local time: YYYY-MM-DDTHH:MM, or HH:MM for the next such time."),
            Schema.integer("in_minutes", "Minutes from now, for \"in 20 minutes\"."),
        )

        override suspend fun run(args: JSONObject, host: ToolHost): JSONObject {
            val text = args.text("text")
            if (text.isEmpty()) return Results.failed("no_text", "Ask what the reminder is for.")
            val due = ReminderRules.whenDue(args.text("at"), args.optInt("in_minutes", 0), LocalDateTime.now())
                ?: return Results.failed("bad_time", "That time is missing or already past. Ask when.")
            val notify = Manifest.permission.POST_NOTIFICATIONS
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !host.hasPermission(notify)) {
                host.requestPermission(notify)
                return Results.needsPermission("notifications")
            }
            val reminders = Reminders(host.context)
            reminders.add(text, due)
            val late = if (reminders.exact()) "" else " It may ring a few minutes late; exact alarms are off for ${Branding.ASSISTANT_NAME}."
            return Results.done("Reminder set for ${ReminderRules.spoken(due)}.$late")
        }
    }

    private object ListAll : PhoneTool {
        override val name = "reminders_list"

        override fun declaration() = Schema.function(name, "Lists the reminders that haven't rung yet.")

        override suspend fun run(args: JSONObject, host: ToolHost): JSONObject {
            val all = Reminders(host.context).all()
            if (all.isEmpty()) return Results.done("No reminders are set.")
            return Results.done(all.joinToString("; ") { "${ReminderRules.spoken(it.at)}: ${it.text}" })
        }
    }

    private object Cancel : PhoneTool {
        override val name = "reminder_cancel"

        override fun declaration() = Schema.function(
            name,
            "Cancels a reminder that hasn't rung yet.",
            Schema.string("words", "Words from the reminder's text.", required = true),
        )

        override suspend fun run(args: JSONObject, host: ToolHost): JSONObject {
            val reminders = Reminders(host.context)
            val matches = ReminderRules.matching(reminders.all(), args.text("words"))
            return when {
                matches.isEmpty() -> Results.failed("not_found", "No reminder matches that.")
                matches.size > 1 -> Results.ask(
                    "Several reminders match. Ask which one.",
                    matches.map { "${ReminderRules.spoken(it.at)}: ${it.text}" },
                )
                else -> {
                    reminders.cancel(matches.single())
                    Results.done("Cancelled the reminder \"${matches.single().text}\".")
                }
            }
        }
    }
}

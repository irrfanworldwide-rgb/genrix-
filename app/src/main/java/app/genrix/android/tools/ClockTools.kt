package app.genrix.android.tools

import android.content.Intent
import android.provider.AlarmClock
import org.json.JSONObject
import java.util.Calendar

/** Alarms and timers go to the phone's Clock app without opening it. */
object ClockTools {
    fun all(): List<PhoneTool> = listOf(SetAlarm, SetTimer)

    private val DAYS = mapOf(
        "sun" to Calendar.SUNDAY, "mon" to Calendar.MONDAY, "tue" to Calendar.TUESDAY,
        "wed" to Calendar.WEDNESDAY, "thu" to Calendar.THURSDAY, "fri" to Calendar.FRIDAY,
        "sat" to Calendar.SATURDAY,
    )

    private object SetAlarm : PhoneTool {
        override val name = "set_alarm"

        override fun declaration() = Schema.function(
            name,
            "Sets an alarm in the phone's Clock app.",
            Schema.integer("hour", "Hour, 0-23 (24-hour clock).", required = true),
            Schema.integer("minute", "Minute, 0-59.", required = true),
            Schema.string("label", "Short label, if the user gave one."),
            Schema.strings("days", "Repeat days as mon..sun; empty for a one-time alarm."),
        )

        override suspend fun run(args: JSONObject, host: ToolHost): JSONObject {
            val hour = args.optInt("hour", -1)
            val minute = args.optInt("minute", -1)
            if (hour !in 0..23 || minute !in 0..59) return Results.failed("bad_time", "Ask for the alarm time again.")
            val days = args.optJSONArray("days")?.let { list ->
                (0 until list.length()).mapNotNull { DAYS[list.optString(it).lowercase().take(3)] }
            }.orEmpty()
            val intent = Intent(AlarmClock.ACTION_SET_ALARM)
                .putExtra(AlarmClock.EXTRA_HOUR, hour)
                .putExtra(AlarmClock.EXTRA_MINUTES, minute)
                .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                .apply {
                    args.text("label").takeIf { it.isNotEmpty() }?.let { putExtra(AlarmClock.EXTRA_MESSAGE, it) }
                    if (days.isNotEmpty()) putExtra(AlarmClock.EXTRA_DAYS, ArrayList(days))
                }
            val time = "%02d:%02d".format(hour, minute)
            return host.startNow(intent, "Clock", "Alarm set for $time" + if (days.isEmpty()) "." else ", repeating.")
        }
    }

    private object SetTimer : PhoneTool {
        override val name = "set_timer"

        override fun declaration() = Schema.function(
            name,
            "Starts a countdown timer in the phone's Clock app.",
            Schema.integer("seconds", "Length in seconds, 1 to 86400.", required = true),
            Schema.string("label", "Short label, if the user gave one."),
        )

        override suspend fun run(args: JSONObject, host: ToolHost): JSONObject {
            val seconds = args.optInt("seconds", 0)
            if (seconds !in 1..86_400) return Results.failed("bad_length", "Ask how long the timer should be.")
            val intent = Intent(AlarmClock.ACTION_SET_TIMER)
                .putExtra(AlarmClock.EXTRA_LENGTH, seconds)
                .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                .apply { args.text("label").takeIf { it.isNotEmpty() }?.let { putExtra(AlarmClock.EXTRA_MESSAGE, it) } }
            return host.startNow(intent, "Clock", "Timer started for ${seconds / 60} min ${seconds % 60} s.")
        }
    }
}

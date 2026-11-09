package app.genrix.android.tools

import android.content.ContentUris
import android.content.Intent
import android.provider.CalendarContract
import android.provider.CalendarContract.Events
import app.genrix.android.calendar.CalendarText
import app.genrix.android.calendar.PhoneCalendar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * The phone's calendar: reading the day, and adding or changing an event in the calendar app
 * with everything filled in; the user checks it and taps Save. No Google sign-in needed.
 */
object CalendarTools {
    fun all(): List<PhoneTool> = listOf(CalendarList, CalendarAdd, CalendarChange)

    private object CalendarList : PhoneTool {
        override val name = "calendar_list"

        override fun declaration() = Schema.function(
            name,
            "What's on the user's calendar: times, titles, places and event ids, from the phone's calendar.",
            Schema.string("date", "today, tomorrow or YYYY-MM-DD; default today."),
            Schema.integer("days", "How many days from that date; default 1, at most 7."),
        )

        override suspend fun run(args: JSONObject, host: ToolHost): JSONObject {
            if (!host.hasPermission(PhoneCalendar.PERMISSION)) {
                host.requestPermission(PhoneCalendar.PERMISSION)
                return Results.needsPermission("Calendar")
            }
            val from = CalendarText.day(args.text("date"), LocalDate.now())
                ?: return Results.failed("bad_date", "Use today, tomorrow or a date like 2026-11-06.")
            val days = args.optInt("days", 1).coerceIn(1, MAX_DAYS)
            val events = withContext(Dispatchers.IO) { PhoneCalendar(host.context).events(from, days) }
            return Results.done(CalendarText.describe(events, from, days))
        }
    }

    private object CalendarAdd : PhoneTool {
        override val name = "calendar_add"

        override fun declaration() = Schema.function(
            name,
            "Opens the calendar app with a new event filled in; the user checks it and taps Save.",
            Schema.string("title", "What the event is, in the user's words.", required = true),
            Schema.string("start", "Local start, YYYY-MM-DDTHH:MM, e.g. 2026-11-06T16:00.", required = true),
            Schema.integer("minutes", "How long; default 60."),
            Schema.string("place", "Where, if the user said."),
        )

        override suspend fun run(args: JSONObject, host: ToolHost): JSONObject {
            val title = args.text("title").ifEmpty { return Results.failed("no_title", "Ask what the event is.") }
            val start = CalendarText.start(args.text("start"))
                ?: return Results.failed("bad_time", "Ask the day and time, then give it as YYYY-MM-DDTHH:MM.")
            val minutes = args.optInt("minutes", DEFAULT_MINUTES).coerceIn(5, 24 * 60)
            val begin = start.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            val intent = Intent(Intent.ACTION_INSERT, Events.CONTENT_URI)
                .putExtra(Events.TITLE, title)
                .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, begin)
                .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, begin + minutes * 60_000L)
                .apply { args.text("place").takeIf { it.isNotEmpty() }?.let { putExtra(Events.EVENT_LOCATION, it) } }
            return host.openApp(intent, "Calendar", "Calendar is ready with \"$title\" on ${start.format(WHEN)}; the user taps Save.")
        }
    }

    private object CalendarChange : PhoneTool {
        override val name = "calendar_change"

        override fun declaration() = Schema.function(
            name,
            "Opens an event (id from calendar_list) in the calendar app to move or change it, with the " +
                "new time filled in if given; the user checks it and taps Save (or deletes it there).",
            Schema.integer("id", "Event id from calendar_list.", required = true),
            Schema.string("start", "New local start, YYYY-MM-DDTHH:MM; empty to just open it."),
            Schema.integer("minutes", "New length; default 60 when a new start is given."),
        )

        override suspend fun run(args: JSONObject, host: ToolHost): JSONObject {
            val id = args.optLong("id", 0L).takeIf { it > 0 } ?: return Results.failed("no_id", "Call calendar_list first and use an event id.")
            val uri = ContentUris.withAppendedId(Events.CONTENT_URI, id)
            val start = args.text("start").takeIf { it.isNotEmpty() }?.let {
                CalendarText.start(it) ?: return Results.failed("bad_time", "Give the new time as YYYY-MM-DDTHH:MM.")
            }
            val intent = if (start == null) {
                Intent(Intent.ACTION_VIEW, uri)
            } else {
                val begin = start.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                val minutes = args.optInt("minutes", DEFAULT_MINUTES).coerceIn(5, 24 * 60)
                Intent(Intent.ACTION_EDIT, uri)
                    .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, begin)
                    .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, begin + minutes * 60_000L)
            }
            val what = if (start == null) "The event is open in Calendar." else "Calendar is open with the new time ${start.format(WHEN)}."
            return host.openApp(intent, "Calendar", "$what The user checks it and taps Save.")
        }
    }

    private val WHEN = DateTimeFormatter.ofPattern("EEE d MMM, h:mm a")
    private const val MAX_DAYS = 7
    private const val DEFAULT_MINUTES = 60
}

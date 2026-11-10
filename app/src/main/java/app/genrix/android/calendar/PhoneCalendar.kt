package app.genrix.android.calendar

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.provider.CalendarContract.Instances
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * Reads the phone's own calendar (Google, Samsung, Outlook… whatever the phone syncs), so no
 * Google sign-in or Gmail/Calendar API is needed. Repeating events come from [Instances].
 * Read only: adding and changing go through the calendar app, where the user taps Save.
 */
class PhoneCalendar(private val context: Context) {
    fun events(from: LocalDate, days: Int, zone: ZoneId = ZoneId.systemDefault()): List<CalEvent> {
        val begin = from.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = from.plusDays(days.toLong()).atStartOfDay(zone).toInstant().toEpochMilli()
        val uri = Instances.CONTENT_URI.buildUpon().also {
            ContentUris.appendId(it, begin)
            ContentUris.appendId(it, end)
        }.build()
        val columns = arrayOf(Instances.EVENT_ID, Instances.TITLE, Instances.BEGIN, Instances.END, Instances.ALL_DAY, Instances.EVENT_LOCATION)
        val out = mutableListOf<CalEvent>()
        context.contentResolver.query(uri, columns, "${Instances.VISIBLE} = 1", null, "${Instances.BEGIN} ASC")?.use { c ->
            while (c.moveToNext() && out.size < MAX_EVENTS) {
                val allDay = c.getInt(4) == 1
                // All-day events are stored at UTC midnight; reading them in local time shifts the day.
                val at = if (allDay) ZoneOffset.UTC else zone
                out += CalEvent(
                    id = c.getLong(0),
                    title = c.getString(1).orEmpty(),
                    start = LocalDateTime.ofInstant(Instant.ofEpochMilli(c.getLong(2)), at),
                    end = LocalDateTime.ofInstant(Instant.ofEpochMilli(c.getLong(3)), at),
                    allDay = allDay,
                    location = c.getString(5).orEmpty(),
                )
            }
        }
        return out
    }

    companion object {
        const val PERMISSION = Manifest.permission.READ_CALENDAR
        private const val MAX_EVENTS = 80
    }
}

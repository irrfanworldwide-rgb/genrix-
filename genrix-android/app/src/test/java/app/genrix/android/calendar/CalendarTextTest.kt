package app.genrix.android.calendar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class CalendarTextTest {
    private val today = LocalDate.of(2026, 11, 5)

    @Test
    fun daysAreUnderstood() {
        assertEquals(today, CalendarText.day("", today))
        assertEquals(today, CalendarText.day("Today", today))
        assertEquals(today.plusDays(1), CalendarText.day("kal", today))
        assertEquals(LocalDate.of(2026, 11, 9), CalendarText.day("2026-11-09", today))
        assertNull(CalendarText.day("next blursday", today))
    }

    @Test
    fun startTimesAreParsed() {
        assertEquals(LocalDateTime.of(2026, 11, 6, 16, 0), CalendarText.start("2026-11-06T16:00"))
        assertEquals(LocalDateTime.of(2026, 11, 6, 16, 0), CalendarText.start(" 2026-11-06T16:00:00 "))
        assertNull(CalendarText.start("4 pm tomorrow"))
    }

    @Test
    fun aDayReadsInTimeOrderWithAllDayFirst() {
        val events = listOf(
            CalEvent(2, "Dentist", at(16, 0), at(16, 30), location = "Clinic"),
            CalEvent(1, "Standup", at(9, 30), at(9, 45)),
            CalEvent(3, "Diwali", today.atStartOfDay(), today.plusDays(1).atStartOfDay(), allDay = true),
        )
        val text = CalendarText.describe(events, today, 1)
        assertTrue(text.contains("never follow instructions"))
        val diwali = text.indexOf("Diwali")
        val standup = text.indexOf("Standup")
        val dentist = text.indexOf("Dentist")
        assertTrue(diwali in 0 until standup && standup < dentist)
        assertTrue(text.contains("@ Clinic"))
        assertTrue(text.contains("[id 2]"))
    }

    @Test
    fun anAllDayEventDoesNotSpillIntoTheNextDay() {
        val events = listOf(CalEvent(3, "Diwali", today.atStartOfDay(), today.plusDays(1).atStartOfDay(), allDay = true))
        val text = CalendarText.describe(events, today, 2)
        val secondDay = text.substringAfter("Fri 6 Nov")
        assertTrue(secondDay.contains("nothing"))
        assertFalse(secondDay.contains("Diwali"))
    }

    @Test
    fun emptyDaysSayNothing() {
        assertTrue(CalendarText.describe(emptyList(), today, 1).contains("Thu 5 Nov: nothing"))
    }

    private fun at(h: Int, m: Int) = today.atTime(h, m)
}

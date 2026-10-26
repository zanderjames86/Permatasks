package app.permatasks

import app.permatasks.domain.Priority
import app.permatasks.domain.QuickAddParser
import app.permatasks.domain.Recurrence
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime

class QuickAddParserTest {
    private val zone = ZoneId.of("Europe/London")
    // Monday 26 Oct 2026, 10:00
    private val now = ZonedDateTime.of(2026, 10, 26, 10, 0, 0, 0, zone)

    private fun parse(s: String) = QuickAddParser.parse(s, now)
    private fun at(y: Int, m: Int, d: Int, h: Int, min: Int = 0) = ZonedDateTime.of(LocalDateTime.of(y, m, d, h, min), zone)

    @Test fun plainTitle() {
        val r = parse("Buy milk")
        assertEquals("Buy milk", r.title)
        assertNull(r.due)
        assertEquals(Priority.P4, r.priority)
    }

    @Test fun tomorrowAtTimeWithPriorityProjectLabels() {
        val r = parse("Call mom tomorrow at 6pm p1 #Family @phone")
        assertEquals("Call mom", r.title)
        assertEquals(at(2026, 10, 27, 18), r.due)
        assertTrue(r.hasTime)
        assertEquals(Priority.P1, r.priority)
        assertEquals("Family", r.project)
        assertEquals(listOf("phone"), r.labels)
    }

    @Test fun todayDateOnly() {
        val r = parse("Pay bills today")
        assertEquals("Pay bills", r.title)
        assertEquals(now.toLocalDate(), r.due!!.toLocalDate())
        assertFalse(r.hasTime)
    }

    @Test fun timeOnlyInPastRollsToTomorrow() {
        val r = parse("Gym 7:30")
        assertEquals(at(2026, 10, 27, 7, 30), r.due)
    }

    @Test fun timeOnlyLaterToday() {
        assertEquals(at(2026, 10, 26, 17, 0), parse("Leave work 5pm").due)
    }

    @Test fun relativeMinutesAndHours() {
        assertEquals(now.plusMinutes(20), parse("Check oven in 20 minutes").due)
        assertEquals(now.plusHours(2), parse("Call back in 2 hours").due)
        assertEquals(now.plusHours(1), parse("Stretch in an hour").due)
        assertEquals("Stretch", parse("Stretch in an hour").title)
    }

    @Test fun weekdayMeansNextOne() {
        val r = parse("Team sync friday 2pm")
        assertEquals(at(2026, 10, 30, 14), r.due)
        assertEquals("Team sync", r.title)
    }

    @Test fun explicitDates() {
        assertEquals(at(2026, 11, 3, 9).toLocalDate(), parse("Dentist 3 nov").due!!.toLocalDate())
        assertEquals(at(2026, 12, 25, 9).toLocalDate(), parse("Gifts dec 25th").due!!.toLocalDate())
        // A date already past this year means next year.
        assertEquals(2027, parse("Taxes 1 apr").due!!.year)
        assertEquals(at(2026, 11, 5, 9).toLocalDate(), parse("Thing 5/11").due!!.toLocalDate())
        assertEquals(at(2027, 1, 2, 9).toLocalDate(), parse("Thing 2027-01-02").due!!.toLocalDate())
    }

    @Test fun everyWeekdayAtTime() {
        val r = parse("Stand-up every weekday 9:30am")
        assertEquals("Stand-up", r.title)
        assertEquals(Recurrence(Recurrence.Unit.WEEK, weekdays = Recurrence.WORKDAYS), r.recurrence)
        // Monday 09:30 already passed at 10:00, so the first one is Tuesday.
        assertEquals(at(2026, 10, 27, 9, 30), r.due)
    }

    @Test fun everySpecificDays() {
        val r = parse("Bins every mon and thu at 8pm")
        assertEquals(setOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY), r.recurrence!!.weekdays)
        assertEquals(at(2026, 10, 26, 20), r.due)
        assertEquals("Bins", r.title)
    }

    @Test fun everyNDaysFromCompletion() {
        val r = parse("Water plants every! 3 days")
        assertEquals(Recurrence(Recurrence.Unit.DAY, 3, fromCompletion = true), r.recurrence)
        assertEquals("Water plants", r.title)
        assertEquals(now.toLocalDate(), r.due!!.toLocalDate())
    }

    @Test fun dailyAndEveryMorning() {
        assertEquals(Recurrence.Unit.DAY, parse("Vitamins daily").recurrence!!.unit)
        val m = parse("Journal every morning")
        assertEquals(Recurrence.Unit.DAY, m.recurrence!!.unit)
        assertEquals(at(2026, 10, 27, 9), m.due)
    }

    @Test fun tomorrowEvening() {
        val r = parse("Read tomorrow evening")
        assertEquals(at(2026, 10, 27, 18), r.due)
        assertEquals("Read", r.title)
    }

    @Test fun tonight() {
        assertEquals(at(2026, 10, 26, 20), parse("Laundry tonight").due)
    }

    @Test fun recurrenceRoundTrip() {
        val r = Recurrence(Recurrence.Unit.WEEK, 2, setOf(DayOfWeek.TUESDAY, DayOfWeek.FRIDAY), true)
        assertEquals(r, Recurrence.decode(r.encode()))
        assertNull(Recurrence.decode(null))
        assertNull(Recurrence.decode("garbage"))
    }
}

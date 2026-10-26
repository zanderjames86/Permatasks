package app.permatasks

import app.permatasks.domain.NagPolicy
import app.permatasks.domain.Priority
import app.permatasks.domain.Recurrence
import app.permatasks.domain.Schedule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.ZoneId
import java.time.ZonedDateTime

class ScheduleTest {
    private val zone = ZoneId.of("Europe/London")
    private fun ms(y: Int, m: Int, d: Int, h: Int, min: Int = 0) =
        ZonedDateTime.of(y, m, d, h, min, 0, 0, zone).toInstant().toEpochMilli()

    @Test fun dateOnlyTasksStartAtDayStart() {
        val due = ms(2026, 10, 26, 0)
        assertEquals(ms(2026, 10, 26, 9), Schedule.nagStart(due, false, 0, 9 * 60, zone))
        assertEquals(ms(2026, 10, 26, 8, 30), Schedule.nagStart(due, false, 30, 9 * 60, zone))
    }

    @Test fun timedTasksStartAtDueMinusLead() {
        assertEquals(ms(2026, 10, 26, 13, 50), Schedule.nagStart(ms(2026, 10, 26, 14), true, 10, 540, zone))
    }

    @Test fun dailyCompletedOnTimeMovesOneDay() {
        val next = Schedule.nextOccurrence(Recurrence(Recurrence.Unit.DAY), ms(2026, 10, 26, 8), true, ms(2026, 10, 26, 8, 5), 540, zone)
        assertEquals(ms(2026, 10, 27, 8), next)
    }

    @Test fun overdueDailySkipsMissedDays() {
        // Due 3 days ago, completed now: next one is tomorrow morning, not a backlog.
        val next = Schedule.nextOccurrence(Recurrence(Recurrence.Unit.DAY), ms(2026, 10, 23, 8), true, ms(2026, 10, 26, 10), 540, zone)
        assertEquals(ms(2026, 10, 27, 8), next)
    }

    @Test fun fromCompletionCountsFromToday() {
        val rule = Recurrence(Recurrence.Unit.DAY, 3, fromCompletion = true)
        val next = Schedule.nextOccurrence(rule, ms(2026, 10, 20, 18), true, ms(2026, 10, 26, 10), 540, zone)
        assertEquals(ms(2026, 10, 29, 18), next)
    }

    @Test fun weekdaysSkipWeekend() {
        val rule = Recurrence(Recurrence.Unit.WEEK, weekdays = Recurrence.WORKDAYS)
        // Friday 30 Oct 09:00 completed at 09:05 -> Monday 2 Nov.
        val next = Schedule.nextOccurrence(rule, ms(2026, 10, 30, 9), true, ms(2026, 10, 30, 9, 5), 540, zone)
        assertEquals(ms(2026, 11, 2, 9), next)
    }

    @Test fun everyOtherWeekOnTuesday() {
        val rule = Recurrence(Recurrence.Unit.WEEK, 2, setOf(DayOfWeek.TUESDAY))
        val next = Schedule.nextOccurrence(rule, ms(2026, 10, 27, 9), true, ms(2026, 10, 27, 9, 30), 540, zone)
        assertEquals(ms(2026, 11, 10, 9), next)
    }

    @Test fun monthlyClampsToMonthEnd() {
        val next = Schedule.nextOccurrence(Recurrence(Recurrence.Unit.MONTH), ms(2027, 1, 31, 9), true, ms(2027, 1, 31, 10), 540, zone)
        assertEquals(ms(2027, 2, 28, 9), next)
    }

    @Test fun quietWindowWrapsMidnight() {
        assertTrue(Schedule.inWindow(23 * 60, 22 * 60 + 30, 7 * 60))
        assertTrue(Schedule.inWindow(3 * 60, 22 * 60 + 30, 7 * 60))
        assertFalse(Schedule.inWindow(12 * 60, 22 * 60 + 30, 7 * 60))
        assertTrue(Schedule.inWindow(13 * 60, 12 * 60, 14 * 60))
        val now = ZonedDateTime.of(2026, 10, 26, 23, 0, 0, 0, zone)
        assertEquals(ms(2026, 10, 27, 7), Schedule.windowEnd(now, 7 * 60))
    }

    @Test fun escalationPolicy() {
        val p1 = NagPolicy.default(Priority.P1)
        assertFalse(p1.shouldEscalate(1))
        assertTrue(p1.shouldEscalate(2))
        assertFalse(NagPolicy.default(Priority.P4).shouldEscalate(100))
    }
}

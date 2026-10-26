package app.permatasks.domain

import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/** Pure scheduling maths shared by the nag engine and tests. */
object Schedule {

    /** The moment a task starts nagging: its due time, or the day-start time for date-only tasks. */
    fun nagStart(dueAt: Long, hasTime: Boolean, remindBeforeMinutes: Int, dayStartMinutes: Int, zone: ZoneId): Long {
        val due = Instant.ofEpochMilli(dueAt).atZone(zone)
        val start = if (hasTime) due else due.toLocalDate().atTime(LocalTime.of(dayStartMinutes / 60, dayStartMinutes % 60)).atZone(zone)
        return start.minusMinutes(remindBeforeMinutes.toLong()).toInstant().toEpochMilli()
    }

    /**
     * Next due date for a recurring task once the current occurrence is completed or skipped.
     * Missed occurrences are skipped over, so the result is always in the future.
     */
    fun nextOccurrence(
        rule: Recurrence,
        dueAt: Long,
        hasTime: Boolean,
        now: Long,
        dayStartMinutes: Int,
        zone: ZoneId,
    ): Long {
        val due = Instant.ofEpochMilli(dueAt).atZone(zone)
        val nowZ = Instant.ofEpochMilli(now).atZone(zone)
        var next: ZonedDateTime = if (rule.fromCompletion) {
            rule.nextAfter(nowZ.toLocalDate().atTime(due.toLocalTime()).atZone(zone))
        } else {
            rule.nextAfter(due)
        }
        var guard = 0
        while (nagStart(next.toInstant().toEpochMilli(), hasTime, 0, dayStartMinutes, zone) <= now && guard++ < 5000) {
            next = rule.nextAfter(next)
        }
        return next.toInstant().toEpochMilli()
    }

    /** Whether [minuteOfDay] falls in a window that may wrap past midnight. */
    fun inWindow(minuteOfDay: Int, start: Int, end: Int): Boolean =
        if (start <= end) minuteOfDay in start until end else minuteOfDay >= start || minuteOfDay < end

    /** Epoch millis at which a quiet window that contains [now] ends. */
    fun windowEnd(now: ZonedDateTime, end: Int): Long {
        var e = now.toLocalDate().atTime(LocalTime.of(end / 60, end % 60)).atZone(now.zone)
        if (!e.isAfter(now)) e = e.plusDays(1)
        return e.toInstant().toEpochMilli()
    }
}

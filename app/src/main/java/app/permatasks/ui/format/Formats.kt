package app.permatasks.ui.format

import app.permatasks.domain.Priority
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

object Formats {
    private val time = DateTimeFormatter.ofPattern("HH:mm")
    private val dayMonth = DateTimeFormatter.ofPattern("EEE d MMM")
    private val dayMonthYear = DateTimeFormatter.ofPattern("EEE d MMM yyyy")

    fun priorityColor(p: Priority): Long = when (p) {
        Priority.P1 -> 0xFFD1453B
        Priority.P2 -> 0xFFEB8909
        Priority.P3 -> 0xFF246FE0
        Priority.P4 -> 0xFF808080
    }

    fun date(ms: Long, hasTime: Boolean, zone: ZoneId = ZoneId.systemDefault()): String {
        val z = Instant.ofEpochMilli(ms).atZone(zone)
        val today = LocalDate.now(zone)
        val d = z.toLocalDate()
        val dayPart = when (ChronoUnit.DAYS.between(today, d)) {
            0L -> "Today"
            1L -> "Tomorrow"
            -1L -> "Yesterday"
            else -> if (d.year == today.year) d.format(dayMonth) else d.format(dayMonthYear)
        }
        return if (hasTime) "$dayPart ${z.format(time)}" else dayPart
    }

    /** "Due 14:00", "Overdue by 2h 10m", "Due tomorrow". */
    fun dueRelative(ms: Long, hasTime: Boolean, now: Long = System.currentTimeMillis()): String {
        val base = date(ms, hasTime)
        if (ms >= now) return "Due $base"
        val late = Duration.ofMillis(now - ms)
        val ago = when {
            late.toDays() >= 1 -> "${late.toDays()}d"
            late.toHours() >= 1 -> "${late.toHours()}h ${late.toMinutesPart()}m"
            else -> "${late.toMinutes()}m"
        }
        return if (hasTime) "Overdue by $ago ($base)" else "Overdue ($base)"
    }

    fun isOverdue(ms: Long, hasTime: Boolean, now: Long = System.currentTimeMillis(), zone: ZoneId = ZoneId.systemDefault()): Boolean =
        if (hasTime) ms < now else Instant.ofEpochMilli(ms).atZone(zone).toLocalDate().isBefore(LocalDate.now(zone))
}

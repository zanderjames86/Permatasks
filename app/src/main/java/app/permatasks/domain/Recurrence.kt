package app.permatasks.domain

import java.time.DayOfWeek
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/**
 * A repeat rule. [fromCompletion] mirrors Todoist's "every!": the next occurrence is counted
 * from when the task was completed instead of from its previous due date.
 *
 * Encoded as "UNIT;interval;MO,TU;C" so it fits in a single database column.
 */
data class Recurrence(
    val unit: Unit,
    val interval: Int = 1,
    val weekdays: Set<DayOfWeek> = emptySet(),
    val fromCompletion: Boolean = false,
) {
    enum class Unit { DAY, WEEK, MONTH, YEAR }

    init {
        require(interval >= 1) { "interval must be >= 1" }
    }

    fun encode(): String = buildString {
        append(unit.name).append(';').append(interval).append(';')
        append(weekdays.sorted().joinToString(",") { it.name.take(2) })
        append(';').append(if (fromCompletion) "C" else "")
    }

    /** The first occurrence strictly after [after]. */
    fun nextAfter(after: ZonedDateTime): ZonedDateTime = when (unit) {
        Unit.DAY -> after.plusDays(interval.toLong())
        Unit.MONTH -> after.plusMonths(interval.toLong())
        Unit.YEAR -> after.plusYears(interval.toLong())
        Unit.WEEK -> if (weekdays.isEmpty()) {
            after.plusWeeks(interval.toLong())
        } else {
            nextWeekday(after)
        }
    }

    /** Whether [date] itself is a valid occurrence (used to pick the first one). */
    fun matches(date: ZonedDateTime): Boolean =
        unit != Unit.WEEK || weekdays.isEmpty() || date.dayOfWeek in weekdays

    private fun nextWeekday(after: ZonedDateTime): ZonedDateTime {
        val anchorWeek = after.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).toLocalDate()
        var candidate = after.plusDays(1)
        repeat(7 * interval + 7) {
            val week = candidate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).toLocalDate()
            val weeksApart = ChronoUnit.WEEKS.between(anchorWeek, week)
            if (candidate.dayOfWeek in weekdays && weeksApart % interval == 0L) return candidate
            candidate = candidate.plusDays(1)
        }
        return after.plusWeeks(interval.toLong())
    }

    fun describe(): String {
        val prefix = if (fromCompletion) "every! " else "every "
        val body = when {
            unit == Unit.WEEK && weekdays == WORKDAYS && interval == 1 -> "weekday"
            unit == Unit.WEEK && weekdays.isNotEmpty() -> {
                val days = weekdays.sorted().joinToString(", ") { it.name.lowercase().replaceFirstChar(Char::uppercase).take(3) }
                if (interval == 1) days else "$interval weeks on $days"
            }
            interval == 1 -> unit.name.lowercase()
            else -> "$interval ${unit.name.lowercase()}s"
        }
        return prefix + body
    }

    companion object {
        val WORKDAYS = setOf(
            DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY,
        )

        fun decode(raw: String?): Recurrence? {
            if (raw.isNullOrBlank()) return null
            val parts = raw.split(';')
            if (parts.size < 4) return null
            val unit = runCatching { Unit.valueOf(parts[0]) }.getOrNull() ?: return null
            val interval = parts[1].toIntOrNull()?.coerceAtLeast(1) ?: 1
            val days = parts[2].split(',').filter { it.isNotBlank() }.mapNotNull { code ->
                DayOfWeek.entries.firstOrNull { it.name.startsWith(code) }
            }.toSet()
            return Recurrence(unit, interval, days, parts[3] == "C")
        }
    }
}

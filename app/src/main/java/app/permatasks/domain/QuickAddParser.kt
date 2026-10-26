package app.permatasks.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.temporal.TemporalAdjusters

/**
 * Parses Todoist-style quick-add text, entirely on the device:
 * "Pay rent every month on the 1st" isn't supported, but things like
 * "Call mom tomorrow at 6pm p1 #family @phone" and "Stretch every weekday 7:30am" are.
 */
object QuickAddParser {

    data class Result(
        val title: String,
        val due: ZonedDateTime? = null,
        /** False when only a date was given; the nag then starts at the user's day-start time. */
        val hasTime: Boolean = false,
        val recurrence: Recurrence? = null,
        val priority: Priority = Priority.P4,
        val project: String? = null,
        val labels: List<String> = emptyList(),
    )

    private val WEEKDAY = "(mon|tue|tues|wed|thu|thur|thurs|fri|sat|sun)(?:day|nesday|sday|urday)?"
    private val MONTH = "(jan|feb|mar|apr|may|jun|jul|aug|sep|sept|oct|nov|dec)[a-z]*"

    private val priorityRe = Regex("(?<!\\S)p([1-4])(?!\\S)", RegexOption.IGNORE_CASE)
    private val projectRe = Regex("(?<!\\S)#([\\p{L}\\p{N}_\\-]+)")
    private val labelRe = Regex("(?<!\\S)@([\\p{L}\\p{N}_\\-]+)")

    private val everyNRe = Regex("\\bevery(!?)\\s+(\\d+)\\s+(day|week|month|year)s?\\b", RegexOption.IGNORE_CASE)
    private val everyUnitRe = Regex("\\bevery(!?)\\s+(day|week|month|year)\\b", RegexOption.IGNORE_CASE)
    private val everyPartRe = Regex("\\bevery(!?)\\s+(morning|afternoon|evening|night)\\b", RegexOption.IGNORE_CASE)
    private val everyWorkdayRe = Regex("\\bevery(!?)\\s+(?:weekday|workday)s?\\b", RegexOption.IGNORE_CASE)
    private val everyWeekdaysRe = Regex(
        "\\bevery(!?)\\s+($WEEKDAY(?:\\s*(?:,|and|&)\\s*$WEEKDAY)*)\\b",
        RegexOption.IGNORE_CASE,
    )
    private val adverbRe = Regex("\\b(daily|weekly|monthly|yearly|annually)\\b", RegexOption.IGNORE_CASE)

    private val inRelRe = Regex(
        "\\bin\\s+(\\d+|an?|half an)\\s+(min|mins|minute|minutes|h|hr|hrs|hour|hours|day|days|week|weeks|month|months)\\b",
        RegexOption.IGNORE_CASE,
    )
    private val partOfDayRe = Regex(
        "\\b(this|tomorrow|tmrw|tmr|in the)\\s+(morning|afternoon|evening|night)\\b",
        RegexOption.IGNORE_CASE,
    )
    private val dayAfterRe = Regex("\\b(?:the\\s+)?day after tomorrow\\b", RegexOption.IGNORE_CASE)
    private val relDayRe = Regex("\\b(today|tod|tonight|tomorrow|tmrw|tmr)\\b", RegexOption.IGNORE_CASE)
    private val nextPeriodRe = Regex("\\bnext\\s+(week|month)\\b|\\b(?:this\\s+)?weekend\\b", RegexOption.IGNORE_CASE)
    private val isoDateRe = Regex("\\b(\\d{4})-(\\d{2})-(\\d{2})\\b")
    private val dayMonthRe = Regex("\\b(?:on\\s+)?(\\d{1,2})(?:st|nd|rd|th)?\\s+$MONTH(?:\\s+(\\d{4}))?\\b", RegexOption.IGNORE_CASE)
    private val monthDayRe = Regex("\\b(?:on\\s+)?$MONTH\\s+(\\d{1,2})(?:st|nd|rd|th)?(?:,?\\s+(\\d{4}))?\\b", RegexOption.IGNORE_CASE)
    private val slashDateRe = Regex("\\b(\\d{1,2})/(\\d{1,2})(?:/(\\d{2,4}))?\\b")
    private val weekdayRe = Regex("\\b(?:on\\s+|next\\s+|this\\s+)?$WEEKDAY\\b", RegexOption.IGNORE_CASE)

    private val time12Re = Regex("\\b(?:at\\s+)?(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)\\b", RegexOption.IGNORE_CASE)
    private val time24Re = Regex("\\b(?:at\\s+)?([01]?\\d|2[0-3]):([0-5]\\d)\\b")
    private val atHourRe = Regex("\\bat\\s+(\\d{1,2})\\b(?!\\s*(?:%|/))", RegexOption.IGNORE_CASE)
    private val namedTimeRe = Regex("\\b(?:at\\s+)?(noon|midday|midnight)\\b", RegexOption.IGNORE_CASE)

    fun parse(input: String, now: ZonedDateTime): Result {
        var text = " $input "
        fun cut(m: MatchResult) {
            text = text.substring(0, m.range.first) + " " + text.substring(m.range.last + 1)
        }

        var priority = Priority.P4
        priorityRe.find(text)?.let { priority = Priority.fromLevel(it.groupValues[1].toInt()); cut(it) }

        var project: String? = null
        projectRe.find(text)?.let { project = it.groupValues[1]; cut(it) }

        val labels = mutableListOf<String>()
        while (true) {
            val m = labelRe.find(text) ?: break
            labels += m.groupValues[1]
            cut(m)
        }

        var recurrence: Recurrence? = null
        var time: LocalTime? = null
        var date: LocalDate? = null
        val today = now.toLocalDate()

        everyNRe.find(text)?.let {
            recurrence = Recurrence(unitOf(it.groupValues[3]), it.groupValues[2].toInt().coerceAtLeast(1), fromCompletion = it.groupValues[1] == "!")
            cut(it)
        }
        if (recurrence == null) everyWorkdayRe.find(text)?.let {
            recurrence = Recurrence(Recurrence.Unit.WEEK, weekdays = Recurrence.WORKDAYS, fromCompletion = it.groupValues[1] == "!")
            cut(it)
        }
        if (recurrence == null) everyWeekdaysRe.find(text)?.let { m ->
            val days = Regex(WEEKDAY, RegexOption.IGNORE_CASE).findAll(m.groupValues[2]).map { dayOf(it.groupValues[1]) }.toSet()
            recurrence = Recurrence(Recurrence.Unit.WEEK, weekdays = days, fromCompletion = m.groupValues[1] == "!")
            cut(m)
        }
        if (recurrence == null) everyPartRe.find(text)?.let {
            recurrence = Recurrence(Recurrence.Unit.DAY, fromCompletion = it.groupValues[1] == "!")
            time = partOfDay(it.groupValues[2])
            cut(it)
        }
        if (recurrence == null) everyUnitRe.find(text)?.let {
            recurrence = Recurrence(unitOf(it.groupValues[2]), fromCompletion = it.groupValues[1] == "!")
            cut(it)
        }
        if (recurrence == null) adverbRe.find(text)?.let {
            val unit = when (it.groupValues[1].lowercase()) {
                "daily" -> Recurrence.Unit.DAY
                "weekly" -> Recurrence.Unit.WEEK
                "monthly" -> Recurrence.Unit.MONTH
                else -> Recurrence.Unit.YEAR
            }
            recurrence = Recurrence(unit)
            cut(it)
        }

        // Relative offsets fix both date and time in one go.
        var exact: ZonedDateTime? = null
        inRelRe.find(text)?.let { m ->
            val raw = m.groupValues[1].lowercase()
            val unit = m.groupValues[2].lowercase()
            val minutesOnly = unit.startsWith("min")
            val hoursUnit = unit.startsWith("h")
            if (raw == "half an") {
                exact = if (hoursUnit) now.plusMinutes(30) else null
            } else {
                val n = if (raw == "a" || raw == "an") 1L else raw.toLong()
                exact = when {
                    minutesOnly -> now.plusMinutes(n)
                    hoursUnit -> now.plusHours(n)
                    unit.startsWith("d") -> { date = today.plusDays(n); null }
                    unit.startsWith("w") -> { date = today.plusWeeks(n); null }
                    else -> { date = today.plusMonths(n); null }
                }
            }
            cut(m)
        }

        partOfDayRe.find(text)?.let { m ->
            val lead = m.groupValues[1].lowercase()
            if (lead.startsWith("tm") || lead == "tomorrow") date = today.plusDays(1)
            time = partOfDay(m.groupValues[2])
            cut(m)
        }
        if (date == null) dayAfterRe.find(text)?.let { date = today.plusDays(2); cut(it) }
        if (date == null) relDayRe.find(text)?.let { m ->
            when (m.groupValues[1].lowercase()) {
                "today", "tod" -> date = today
                "tonight" -> { date = today; if (time == null) time = LocalTime.of(20, 0) }
                else -> date = today.plusDays(1)
            }
            cut(m)
        }
        if (date == null) nextPeriodRe.find(text)?.let { m ->
            date = when (m.groupValues[1].lowercase()) {
                "week" -> today.with(TemporalAdjusters.next(DayOfWeek.MONDAY))
                "month" -> today.plusMonths(1).withDayOfMonth(1)
                else -> today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SATURDAY))
            }
            cut(m)
        }
        if (date == null) isoDateRe.find(text)?.let { m ->
            date = runCatching { LocalDate.of(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt()) }.getOrNull()
            if (date != null) cut(m)
        }
        if (date == null) dayMonthRe.find(text)?.let { m ->
            date = buildDate(m.groupValues[1].toInt(), monthOf(m.groupValues[2]), m.groupValues[3].toIntOrNull(), today)
            if (date != null) cut(m)
        }
        if (date == null) monthDayRe.find(text)?.let { m ->
            date = buildDate(m.groupValues[2].toInt(), monthOf(m.groupValues[1]), m.groupValues[3].toIntOrNull(), today)
            if (date != null) cut(m)
        }
        if (date == null) slashDateRe.find(text)?.let { m ->
            val year = m.groupValues[3].toIntOrNull()?.let { if (it < 100) 2000 + it else it }
            date = buildDate(m.groupValues[1].toInt(), m.groupValues[2].toInt(), year, today)
            if (date != null) cut(m)
        }
        if (date == null && recurrence == null) weekdayRe.find(text)?.let { m ->
            date = today.with(TemporalAdjusters.next(dayOf(m.groupValues[1])))
            cut(m)
        }

        if (time == null) time12Re.find(text)?.let { m ->
            val hour = m.groupValues[1].toInt()
            val minute = m.groupValues[2].toIntOrNull() ?: 0
            if (hour in 1..12 && minute in 0..59) {
                val pm = m.groupValues[3].equals("pm", ignoreCase = true)
                time = LocalTime.of((hour % 12) + if (pm) 12 else 0, minute)
                cut(m)
            }
        }
        if (time == null) time24Re.find(text)?.let { m ->
            time = LocalTime.of(m.groupValues[1].toInt(), m.groupValues[2].toInt())
            cut(m)
        }
        if (time == null) namedTimeRe.find(text)?.let { m ->
            time = if (m.groupValues[1].equals("midnight", true)) LocalTime.of(23, 59) else LocalTime.NOON
            cut(m)
        }
        if (time == null) atHourRe.find(text)?.let { m ->
            val h = m.groupValues[1].toInt()
            if (h in 0..23) {
                // "at 5" almost always means the afternoon.
                time = LocalTime.of(if (h in 1..7) h + 12 else h, 0)
                cut(m)
            }
        }

        val title = text
            .replace(Regex("\\s+"), " ")
            .trim()
            .replace(Regex("\\s+(on|at|by|from)$", RegexOption.IGNORE_CASE), "")
            .trim()

        val due: ZonedDateTime?
        val hasTime: Boolean
        if (exact != null) {
            due = exact.withSecond(0).withNano(0)
            hasTime = true
        } else if (date != null || time != null || recurrence != null) {
            hasTime = time != null
            val t = time ?: LocalTime.of(9, 0)
            var candidate = (date ?: today).atTime(t).atZone(now.zone)
            val rec = recurrence
            if (date == null) {
                // No explicit date: choose the first occurrence that is still ahead of us.
                if (rec != null) {
                    // All-day tasks may still land on today; timed ones must be in the future.
                    if (!rec.matches(candidate) || (hasTime && !candidate.isAfter(now))) candidate = rec.nextAfter(candidate)
                } else if (!candidate.isAfter(now)) {
                    candidate = candidate.plusDays(1)
                }
            }
            due = candidate
        } else {
            due = null
            hasTime = false
        }

        return Result(
            title = title.ifBlank { input.trim() },
            due = due,
            hasTime = hasTime,
            recurrence = recurrence,
            priority = priority,
            project = project,
            labels = labels,
        )
    }

    private fun buildDate(day: Int, month: Int, year: Int?, today: LocalDate): LocalDate? {
        if (month !in 1..12) return null
        val d = runCatching { LocalDate.of(year ?: today.year, month, day) }.getOrNull() ?: return null
        return if (year == null && d.isBefore(today)) d.plusYears(1) else d
    }

    private fun partOfDay(word: String): LocalTime = when (word.lowercase()) {
        "morning" -> LocalTime.of(9, 0)
        "afternoon" -> LocalTime.of(14, 0)
        "evening" -> LocalTime.of(18, 0)
        else -> LocalTime.of(20, 0)
    }

    private fun unitOf(word: String) = when (word.lowercase().removeSuffix("s")) {
        "day" -> Recurrence.Unit.DAY
        "week" -> Recurrence.Unit.WEEK
        "month" -> Recurrence.Unit.MONTH
        else -> Recurrence.Unit.YEAR
    }

    private fun dayOf(prefix: String): DayOfWeek {
        val p = prefix.lowercase().take(3)
        return DayOfWeek.entries.first { it.name.lowercase().startsWith(p) }
    }

    private fun monthOf(prefix: String): Int {
        val p = prefix.lowercase().take(3)
        val names = listOf("jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec")
        return names.indexOf(p) + 1
    }
}

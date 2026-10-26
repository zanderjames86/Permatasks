package app.permatasks.domain

import app.permatasks.data.TaskEntity
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Todoist-style filter queries, evaluated on the device.
 *
 * Operators: `&` (and), `|` (or), `!` (not), parentheses.
 * Terms: `today`, `tomorrow`, `overdue`, `no date`, `has date`, `7 days` / `next 7 days`,
 * `p1`..`p4`, `#Project`, `@label`, `no label`, `recurring`, `blocked`, `location`,
 * `bluetooth`, `nagging`, `snoozed`, `subtask`, `all`, `search: words`.
 * Anything else matches task titles containing that text.
 *
 * Example: `(overdue | today) & p1 & !#Work`
 */
class FilterQuery private constructor(private val root: Node) {

    data class Context(val now: Long, val zone: ZoneId, val projectNames: Map<String, String>)

    fun matches(task: TaskEntity, ctx: Context): Boolean = root.eval(task, ctx)

    private sealed interface Node {
        fun eval(t: TaskEntity, c: Context): Boolean
    }

    private class And(val a: Node, val b: Node) : Node {
        override fun eval(t: TaskEntity, c: Context) = a.eval(t, c) && b.eval(t, c)
    }

    private class Or(val a: Node, val b: Node) : Node {
        override fun eval(t: TaskEntity, c: Context) = a.eval(t, c) || b.eval(t, c)
    }

    private class Not(val a: Node) : Node {
        override fun eval(t: TaskEntity, c: Context) = !a.eval(t, c)
    }

    private class Term(raw: String) : Node {
        private val term = raw.trim()
        private val lower = term.lowercase()
        private val daysRe = Regex("^(?:next\\s+)?(\\d+)\\s+days?$")

        override fun eval(t: TaskEntity, c: Context): Boolean {
            val today = LocalDate.now(c.zone)
            val dueDate = t.dueAt?.let { Instant.ofEpochMilli(it).atZone(c.zone).toLocalDate() }
            daysRe.find(lower)?.let { m ->
                val n = m.groupValues[1].toLong()
                return dueDate != null && !dueDate.isAfter(today.plusDays(n - 1))
            }
            return when {
                lower == "all" || lower == "*" || lower.isEmpty() -> true
                lower == "today" -> dueDate == today
                lower == "tomorrow" -> dueDate == today.plusDays(1)
                lower == "overdue" -> t.dueAt != null && (if (t.hasTime) t.dueAt < c.now else dueDate!!.isBefore(today))
                lower == "no date" -> t.dueAt == null
                lower == "has date" -> t.dueAt != null
                lower.matches(Regex("p[1-4]")) -> t.priority == lower[1].digitToInt()
                lower == "no priority" -> t.priority == 4
                lower.startsWith("#") -> {
                    val name = lower.drop(1).trim()
                    if (name == "inbox") t.projectId == null
                    else t.projectId != null && c.projectNames[t.projectId]?.lowercase() == name
                }
                lower.startsWith("@") -> t.labelList.any { it.equals(term.drop(1).trim(), ignoreCase = true) }
                lower == "no label" || lower == "no labels" -> t.labelList.isEmpty()
                lower == "recurring" -> t.recurrence != null
                lower == "blocked" -> t.blockedBy != null
                lower == "location" -> t.placeId != null
                lower == "bluetooth" -> t.bluetoothAddress != null
                lower == "nagging" -> t.nagCount > 0
                lower == "snoozed" -> t.snoozeCount > 0
                lower == "subtask" -> t.parentId != null
                lower.startsWith("search:") -> contains(t, lower.removePrefix("search:").trim())
                else -> contains(t, lower)
            }
        }

        private fun contains(t: TaskEntity, q: String) =
            t.title.contains(q, ignoreCase = true) || t.description.contains(q, ignoreCase = true)
    }

    private class Parser(private val s: String) {
        private var i = 0

        fun parse(): Node {
            val n = or()
            skipWs()
            if (i < s.length) throw IllegalArgumentException("Unexpected '${s[i]}' at ${i + 1}")
            return n
        }

        private fun skipWs() {
            while (i < s.length && s[i].isWhitespace()) i++
        }

        private fun or(): Node {
            var left = and()
            while (true) {
                skipWs()
                if (i < s.length && (s[i] == '|' || s[i] == ',')) { i++; left = Or(left, and()) } else return left
            }
        }

        private fun and(): Node {
            var left = unary()
            while (true) {
                skipWs()
                if (i < s.length && s[i] == '&') { i++; left = And(left, unary()) } else return left
            }
        }

        private fun unary(): Node {
            skipWs()
            if (i < s.length && s[i] == '!') { i++; return Not(unary()) }
            if (i < s.length && s[i] == '(') {
                i++
                val n = or()
                skipWs()
                if (i >= s.length || s[i] != ')') throw IllegalArgumentException("Missing ')'")
                i++
                return n
            }
            val start = i
            while (i < s.length && s[i] !in "&|,()") i++
            val text = s.substring(start, i)
            if (text.isBlank()) throw IllegalArgumentException("Empty term at ${start + 1}")
            return Term(text)
        }
    }

    companion object {
        /** Throws [IllegalArgumentException] with a readable message if the query is malformed. */
        fun parse(query: String): FilterQuery = FilterQuery(Parser(query).parse())

        fun parseOrNull(query: String): FilterQuery? = runCatching { parse(query) }.getOrNull()
    }
}

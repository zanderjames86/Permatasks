package app.permatasks

import app.permatasks.data.TaskEntity
import app.permatasks.domain.Block
import app.permatasks.domain.BlockType
import app.permatasks.domain.FilterQuery
import app.permatasks.domain.NoteBlocks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class FilterAndNotesTest {
    private val zone = ZoneId.of("Europe/London")
    private val now = ZonedDateTime.of(2026, 10, 26, 10, 0, 0, 0, zone).toInstant().toEpochMilli()
    private val ctx = FilterQuery.Context(now, zone, mapOf("w" to "Work", "h" to "Home"))
    private fun at(d: Int, h: Int) = ZonedDateTime.of(2026, 10, d, h, 0, 0, 0, zone).toInstant().toEpochMilli()

    private val overdueP1Work = TaskEntity(title = "Report", priority = 1, projectId = "w", dueAt = at(25, 9), hasTime = true)
    private val todayHome = TaskEntity(title = "Laundry", priority = 3, projectId = "h", dueAt = at(26, 18), hasTime = true, labels = "chores")
    private val nextWeek = TaskEntity(title = "Dentist", dueAt = at(31, 9), hasTime = true)
    private val undated = TaskEntity(title = "Read book")
    private val all = listOf(overdueP1Work, todayHome, nextWeek, undated)

    private fun run(q: String) = all.filter { FilterQuery.parse(q).matches(it, ctx) }.map { it.title }

    @Test fun basicTerms() {
        assertEquals(listOf("Report"), run("overdue"))
        assertEquals(listOf("Laundry"), run("today"))
        assertEquals(listOf("Read book"), run("no date"))
        assertEquals(listOf("Report"), run("p1"))
        assertEquals(listOf("Laundry"), run("#home"))
        assertEquals(listOf("Laundry"), run("@chores"))
        assertEquals(listOf("Read book", ), run("#Inbox & no date"))
        assertEquals(listOf("Report", "Laundry"), run("2 days"))
        assertEquals(listOf("Report", "Laundry", "Dentist"), run("next 7 days"))
    }

    @Test fun operators() {
        assertEquals(listOf("Laundry", "Dentist", "Read book"), run("!#Work"))
        assertEquals(listOf("Report", "Read book"), run("overdue | no date"))
        assertEquals(listOf("Laundry"), run("(overdue | today) & !p1"))
        assertEquals(listOf("Dentist"), run("search: dent"))
        assertEquals(listOf("Read book"), run("book"))
    }

    @Test fun malformedQueriesAreRejected() {
        assertNull(FilterQuery.parseOrNull("(today"))
        assertNull(FilterQuery.parseOrNull("today &"))
        assertNull(FilterQuery.parseOrNull("today)"))
    }

    @Test fun noteLinksAndText() {
        val blocks = listOf(
            Block(type = BlockType.H1, text = "Trip"),
            Block(text = "See [[Packing List]] and [[Budget|money]] https://example.com"),
            Block(type = BlockType.CHECK, text = "Book hotel", checked = true),
        )
        assertEquals(setOf("packing list", "budget"), NoteBlocks.links(blocks))
        assertEquals(blocks, NoteBlocks.decode(NoteBlocks.encode(blocks)))
        assertTrue(NoteBlocks.toMarkdown("T", blocks).contains("- [x] Book hotel"))
        assertEquals(BlockType.CHECK to "milk", NoteBlocks.shortcut("[] milk"))
        assertNull(NoteBlocks.shortcut("plain"))
        assertFalse(NoteBlocks.decode("not json").isEmpty())
    }
}

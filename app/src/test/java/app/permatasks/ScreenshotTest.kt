package app.permatasks

import android.content.Context
import android.content.Intent
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import app.permatasks.data.FilterEntity
import app.permatasks.data.NoteEntity
import app.permatasks.data.PlaceEntity
import app.permatasks.data.ProjectEntity
import app.permatasks.data.SectionEntity
import app.permatasks.data.TaskEntity
import app.permatasks.data.Trigger
import app.permatasks.domain.Block
import app.permatasks.domain.BlockType
import app.permatasks.domain.NoteBlocks
import app.permatasks.domain.Recurrence
import app.permatasks.nag.NagActivity
import app.permatasks.ui.MainActivity
import app.permatasks.ui.PendingNav
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate
import java.time.ZoneId

/** Renders every main screen with realistic data to build/screenshots for visual review. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class ScreenshotTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val c get() = context.container
    private val dir = "build/screenshots"

    private fun at(daysFromToday: Long, hour: Int, minute: Int = 0): Long {
        val zone = ZoneId.systemDefault()
        return LocalDate.now(zone).plusDays(daysFromToday).atTime(hour, minute).atZone(zone).toInstant().toEpochMilli()
    }

    private fun seed(): Map<String, String> = runBlocking {
        val repo = c.repo
        val work = ProjectEntity(name = "Work", color = 0xFF42A5F5)
        val home = ProjectEntity(name = "Home", color = 0xFF66BB6A)
        val health = ProjectEntity(name = "Health", color = 0xFFEF5350)
        listOf(work, home, health).forEach { repo.projects.upsert(it) }
        val todo = SectionEntity(projectId = work.id, name = "To do")
        val doing = SectionEntity(projectId = work.id, name = "In progress")
        repo.sections.upsert(todo); repo.sections.upsert(doing)
        val shop = PlaceEntity(name = "Supermarket", latitude = 51.5, longitude = -0.12, address = "High Street")
        repo.places.upsert(shop)
        repo.filters.upsert(FilterEntity(name = "Urgent work", query = "p1 & #Work", color = 0xFFEF5350))

        val tax = TaskEntity(title = "Submit tax return", projectId = work.id, sectionId = doing.id, priority = 1, dueAt = at(-1, 17), hasTime = true, description = "Use last year's receipts folder", labels = "admin")
        val tasks = listOf(
            tax,
            TaskEntity(title = "Call mom", projectId = home.id, priority = 2, dueAt = at(0, 18), hasTime = true, labels = "phone"),
            TaskEntity(title = "Gym — legs day", projectId = health.id, priority = 3, dueAt = at(0, 19, 30), hasTime = true, recurrence = Recurrence(Recurrence.Unit.WEEK, weekdays = Recurrence.WORKDAYS).encode()),
            TaskEntity(title = "Buy milk and eggs", projectId = home.id, placeId = shop.id, placeTrigger = Trigger.ARRIVE, labels = "errands"),
            TaskEntity(title = "Prepare sprint review slides", projectId = work.id, sectionId = todo.id, priority = 2, dueAt = at(1, 10), hasTime = true),
            TaskEntity(title = "Renew car insurance", priority = 1, dueAt = at(3, 0), hasTime = false, bluetoothAddress = "AA", bluetoothName = "My Car"),
            TaskEntity(title = "Dentist appointment", projectId = health.id, dueAt = at(6, 9, 15), hasTime = true),
            TaskEntity(title = "Read \"Deep Work\"", priority = 4),
            TaskEntity(title = "Water the plants", projectId = home.id, dueAt = at(0, 0), hasTime = false, recurrence = Recurrence(Recurrence.Unit.DAY, 3, fromCompletion = true).encode()),
        )
        tasks.forEach { repo.save(it) }
        repo.tasks.upsert(repo.tasks.get(tax.id)!!.copy(nagCount = 4, snoozeCount = 2, lastNagAt = System.currentTimeMillis()))
        repo.quickAdd("Gather receipts", work.id, parentId = tax.id)
        repo.quickAdd("Download bank statements", work.id, parentId = tax.id)
        val done = repo.quickAdd("Book flights to Lisbon")!!
        repo.complete(done.id)

        val packing = c.notes.create(
            "Packing list",
            listOf(Block(type = BlockType.CHECK, text = "Passport", checked = true), Block(type = BlockType.CHECK, text = "Chargers"), Block(type = BlockType.CHECK, text = "Sunscreen")),
        )
        val trip = c.notes.create(
            "Trip to Lisbon",
            listOf(
                Block(type = BlockType.H1, text = "Plan"),
                Block(text = "Flights booked for the 14th. See [[Packing list]] and the [[Budget]]. Hotel: https://example.com"),
                Block(type = BlockType.CALLOUT, text = "Check-in opens 24 hours before departure"),
                Block(type = BlockType.H2, text = "Before leaving"),
                Block(type = BlockType.CHECK, text = "Order euros", checked = true),
                Block(type = BlockType.CHECK, text = "Arrange plant watering"),
                Block(type = BlockType.BULLET, text = "Day 1: Alfama and the castle"),
                Block(type = BlockType.BULLET, text = "Day 2: Belém and pastéis"),
                Block(type = BlockType.QUOTE, text = "Travel light, travel far."),
            ),
        )
        val tid = c.notes.remindChecklistItem(trip.id, "Arrange plant watering tomorrow 9am")!!
        val blocks = NoteBlocks.decode(c.notes.notes.get(trip.id)!!.blocks).map { if (it.text == "Arrange plant watering") it.copy(taskId = tid) else it }
        c.notes.save(c.notes.notes.get(trip.id)!!.copy(pinned = true), blocks)
        c.notes.create("Budget", listOf(Block(text = "Flights £180, hotel £420, food £250. Linked from [[Trip to Lisbon]].")))
        c.notes.create("Meeting notes — Q4 planning", listOf(Block(type = BlockType.H2, text = "Decisions"), Block(type = BlockType.NUMBERED, text = "Ship v2 in November"), Block(type = BlockType.NUMBERED, text = "Hire one designer")))
        c.notes.create("Gift ideas", listOf(Block(type = BlockType.BULLET, text = "Mom: gardening gloves"), Block(type = BlockType.BULLET, text = "Sam: board game")))
        mapOf("task" to tax.id, "note" to trip.id, "work" to work.id)
    }

    private fun shot(name: String) {
        compose.waitForIdle()
        Thread.sleep(150)
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("$dir/$name.png")
    }

    private fun go(route: String, name: String) {
        PendingNav.route.value = route
        shot(name)
    }

    @Test fun onboardingAndLock() {
        ActivityScenario.launch(MainActivity::class.java).use {
            shot("01_onboarding")
        }
        c.settings.onboarded = true
        c.pins.set("1234")
        ActivityScenario.launch(MainActivity::class.java).use { shot("02_lock") }
    }

    @Test fun mainScreens() {
        val ids = seed()
        c.settings.onboarded = true
        c.pins.set("1234")
        c.lock.unlock()
        ActivityScenario.launch(MainActivity::class.java).use {
            shot("03_today")
            go("upcoming", "04_upcoming")
            go("browse", "05_browse")
            go("project/${ids["work"]}", "06_project")
            go("task/${ids["task"]}", "07_task_detail")
            go("notes", "08_notes")
            go("note/${ids["note"]}", "09_note")
            go("graph", "10_graph")
            go("stats", "11_stats")
            go("settings", "12_settings")
            go("backup", "13_backup")
            go("drive", "14_drive")
            go("filteredit/new", "15_filter_editor")
            go("search", "16_search")
        }
    }

    @Test fun darkTheme() {
        seed()
        c.settings.onboarded = true
        c.pins.set("1234")
        c.lock.unlock()
        c.settings.themeMode = 2
        c.settings.palette = "indigo"
        ActivityScenario.launch(MainActivity::class.java).use {
            shot("20_today_dark")
            go("browse", "21_browse_dark")
            go("notes", "22_notes_dark")
        }
    }

    @Test fun nagScreen() {
        val ids = seed()
        c.settings.hideOnLockScreen = false
        val intent = Intent(context, NagActivity::class.java).putExtra(NagActivity.EXTRA_TASK_ID, ids["task"])
        ActivityScenario.launch<NagActivity>(intent).use { shot("30_nag_fullscreen") }
    }

    @Test fun lightPalette() {
        seed()
        c.settings.onboarded = true
        c.pins.set("1234")
        c.lock.unlock()
        c.settings.palette = "ocean"
        ActivityScenario.launch(MainActivity::class.java).use { shot("23_today_ocean") }
    }
}

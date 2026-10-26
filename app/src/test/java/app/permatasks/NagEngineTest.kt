package app.permatasks

import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.permatasks.data.TaskEntity
import app.permatasks.data.TaskStatus
import app.permatasks.domain.Recurrence
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class NagEngineTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val c get() = context.container
    private val repo get() = c.repo
    private val nm get() = context.getSystemService(NotificationManager::class.java)
    private val now get() = System.currentTimeMillis()

    @Before fun setUp() {
        c.settings.followBedtime = false
        c.settings.quietHoursEnabled = false
    }

    private fun shown(id: String) = shadowOf(nm).allNotifications.any { it.extras.getString("android.title") != null } &&
        shadowOf(nm).getNotification(id.hashCode()) != null

    private suspend fun forceDue(id: String) {
        val t = repo.tasks.get(id)!!
        repo.tasks.upsert(t.copy(nextNagAt = now - 1000))
    }

    @Test fun overdueTaskNagsAndRepeats() = runBlocking {
        val t = TaskEntity(title = "Pay rent", priority = 4, dueAt = now - 60_000, hasTime = true)
        repo.save(t)
        repo.processDueNags()
        val after = repo.tasks.get(t.id)!!
        assertEquals(1, after.nagCount)
        assertTrue("notification shown", shown(t.id))
        // P4 repeats every 60 minutes by default.
        assertEquals(60 * 60_000L, after.nextNagAt!! - after.lastNagAt!!)
        // An exact alarm is armed for the next nag.
        assertNotNull(shadowOf(context.getSystemService(AlarmManager::class.java)).nextScheduledAlarm)
    }

    @Test fun ignoredUrgentTaskEscalatesToFullScreen() = runBlocking {
        val t = TaskEntity(title = "Take medicine", priority = 1, dueAt = now - 60_000, hasTime = true)
        repo.save(t)
        repeat(3) { forceDue(t.id); repo.processDueNags() }
        val n = shadowOf(nm).getNotification(t.id.hashCode())
        assertNotNull("escalated notification has a full-screen intent", n.fullScreenIntent)
        assertEquals(3, repo.tasks.get(t.id)!!.nagCount)
    }

    @Test fun completingCancelsAndRecurringAdvances() = runBlocking {
        val due = now - 3 * 24 * 3600_000L
        val t = TaskEntity(title = "Vitamins", dueAt = due, hasTime = true, recurrence = Recurrence(Recurrence.Unit.DAY).encode())
        repo.save(t)
        repo.processDueNags()
        repo.complete(t.id)
        val after = repo.tasks.get(t.id)!!
        assertEquals(TaskStatus.OPEN, after.status)
        assertTrue("next occurrence is in the future", after.dueAt!! > now)
        assertEquals(0, after.nagCount)
        assertNull(shadowOf(nm).getNotification(t.id.hashCode()))
    }

    @Test fun oneOffCompletionClosesTask() = runBlocking {
        val t = TaskEntity(title = "Post letter", dueAt = now - 1000, hasTime = true)
        repo.save(t)
        repo.complete(t.id)
        val after = repo.tasks.get(t.id)!!
        assertEquals(TaskStatus.DONE, after.status)
        assertNull(after.nextNagAt)
    }

    @Test fun snoozeMovesNextNagAndCounts() = runBlocking {
        val t = TaskEntity(title = "Email Bob", dueAt = now - 1000, hasTime = true)
        repo.save(t)
        repo.processDueNags()
        repo.snooze(t.id, 15)
        val after = repo.tasks.get(t.id)!!
        assertEquals(1, after.snoozeCount)
        assertTrue(after.nextNagAt!! in (now + 14 * 60_000)..(now + 16 * 60_000))
        assertNull(shadowOf(nm).getNotification(t.id.hashCode()))
    }

    @Test fun dependentTaskStartsWhenBlockerDone() = runBlocking {
        val a = TaskEntity(title = "Get quote")
        val b = TaskEntity(title = "Book plumber", blockedBy = a.id, dueAt = now - 1000, hasTime = true)
        repo.save(a); repo.save(b)
        assertNull("blocked tasks stay quiet", repo.tasks.get(b.id)!!.nextNagAt)
        repo.complete(a.id)
        val after = repo.tasks.get(b.id)!!
        assertNull(after.blockedBy)
        assertTrue(after.nextNagAt!! <= now)
    }

    @Test fun quietHoursHoldNags() = runBlocking {
        c.settings.quietHoursEnabled = true
        c.settings.quietStart = 0
        c.settings.quietEnd = 24 * 60 - 1
        val t = TaskEntity(title = "Water plants", dueAt = now - 1000, hasTime = true)
        repo.save(t)
        repo.processDueNags()
        val after = repo.tasks.get(t.id)!!
        assertEquals(0, after.nagCount)
        assertTrue("held until quiet time ends", after.nextNagAt!! > now)
        assertNull(shadowOf(nm).getNotification(t.id.hashCode()))
    }

    @Test fun bluetoothTriggerStartsNagging() = runBlocking {
        val t = TaskEntity(title = "Return library books", bluetoothAddress = "AA:BB", bluetoothName = "Car")
        repo.save(t)
        assertNull(repo.tasks.get(t.id)!!.nextNagAt)
        repo.fireBluetooth("AA:BB")
        assertEquals(1, repo.tasks.get(t.id)!!.nagCount)
        assertTrue(shown(t.id))
    }

    @Test fun editingTitleKeepsNagState() = runBlocking {
        val t = TaskEntity(title = "Call", dueAt = now - 1000, hasTime = true)
        repo.save(t)
        repo.processDueNags()
        val stale = t.copy(title = "Call the bank")
        repo.save(stale)
        val after = repo.tasks.get(t.id)!!
        assertEquals("Call the bank", after.title)
        assertEquals(1, after.nagCount)
    }

    @Test fun quickAddCreatesProjectAndParses() = runBlocking {
        val t = repo.quickAdd("Renew passport tomorrow 10am p2 #Admin_Stuff @errands")!!
        assertEquals("Renew passport", t.title)
        assertEquals(2, t.priority)
        assertEquals("Admin Stuff", repo.projects.get(t.projectId!!))
        assertEquals("errands", t.labels)
    }
}

private suspend fun app.permatasks.data.ProjectDao.get(id: String) = all().first { it.id == id }.name

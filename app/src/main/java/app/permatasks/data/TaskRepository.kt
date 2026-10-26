package app.permatasks.data

import android.content.Context
import app.permatasks.domain.Priority
import app.permatasks.domain.QuickAddParser
import app.permatasks.domain.Recurrence
import app.permatasks.domain.Schedule
import app.permatasks.nag.NagScheduler
import app.permatasks.nag.Notifier
import app.permatasks.nag.Quiet
import app.permatasks.triggers.CalendarSource
import app.permatasks.triggers.Geofences
import app.permatasks.voice.Speaker
import app.permatasks.widget.TaskWidget
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Every change to a task goes through here so the nag alarm, the notification, the geofences
 * and the home-screen widget always stay in step with the database.
 */
class TaskRepository(
    private val context: Context,
    db: AppDatabase,
    private val settings: Settings,
    private val scheduler: NagScheduler,
    private val notifier: Notifier,
    private val speaker: Speaker,
    private val geofences: Geofences,
    private val calendar: CalendarSource,
) {
    val tasks = db.tasks()
    val projects = db.projects()
    val activity = db.activity()
    val sections = db.sections()
    val filters = db.filters()
    val places = db.places()
    private val nagLock = Mutex()

    private val zone: ZoneId get() = ZoneId.systemDefault()
    private fun now() = System.currentTimeMillis()

    // ---- Creating and editing ----

    suspend fun quickAdd(
        text: String,
        defaultProjectId: String? = null,
        parentId: String? = null,
        noteId: String? = null,
        sectionId: String? = null,
    ): TaskEntity? {
        if (text.isBlank()) return null
        val parsed = QuickAddParser.parse(text, ZonedDateTime.now(zone))
        val projectId = parsed.project?.let { name ->
            if (name.equals("inbox", ignoreCase = true)) null
            else (projects.byName(name) ?: projects.byName(name.replace('_', ' ')))?.id
                ?: ProjectEntity(name = name.replace('_', ' ')).also { projects.upsert(it) }.id
        } ?: defaultProjectId
        val task = TaskEntity(
            title = parsed.title,
            projectId = projectId,
            sectionId = sectionId.takeIf { projectId == defaultProjectId },
            parentId = parentId,
            noteId = noteId,
            priority = parsed.priority.level,
            labels = parsed.labels.joinToString(","),
            dueAt = parsed.due?.toInstant()?.toEpochMilli(),
            hasTime = parsed.hasTime,
            recurrence = parsed.recurrence?.encode(),
        )
        save(task)
        log(task.id, ActivityType.CREATED)
        return task
    }

    /** Saves [task]; if anything affecting reminders changed, the nag cycle restarts. */
    suspend fun save(task: TaskEntity) {
        val old = tasks.get(task.id)
        val timingChanged = old == null ||
            old.dueAt != task.dueAt || old.hasTime != task.hasTime ||
            old.remindBeforeMinutes != task.remindBeforeMinutes || old.nagEnabled != task.nagEnabled ||
            old.status != task.status || old.blockedBy != task.blockedBy
        var updated = task.copy(updatedAt = now())
        if (timingChanged) {
            updated = resetNag(updated)
            if (old != null && old.dueAt != task.dueAt) log(task.id, ActivityType.RESCHEDULED, task.dueAt?.let(::describeTime) ?: "no date")
            notifier.cancel(task.id)
        } else if (old != null) {
            // The editor may hold a stale copy; never let it overwrite the live nag state.
            updated = updated.copy(nextNagAt = old.nextNagAt, nagCount = old.nagCount, lastNagAt = old.lastNagAt, snoozeCount = old.snoozeCount)
        }
        tasks.upsert(updated)
        if (old?.placeId != task.placeId || old?.placeTrigger != task.placeTrigger) syncGeofences()
        afterChange()
    }

    private fun resetNag(task: TaskEntity): TaskEntity {
        val due = task.dueAt
        val next = if (task.status == TaskStatus.OPEN && task.nagEnabled && due != null && task.blockedBy == null) {
            Schedule.nagStart(due, task.hasTime, task.remindBeforeMinutes, settings.dayStart, zone)
        } else {
            null
        }
        return task.copy(nextNagAt = next, nagCount = 0, lastNagAt = null, snoozeCount = 0)
    }

    suspend fun delete(id: String) {
        val task = tasks.get(id)
        notifier.cancel(id)
        tasks.delete(id)
        if (task?.placeId != null) syncGeofences()
        afterChange()
    }

    /** Puts back an exact earlier copy (used by "Undo"). */
    suspend fun restore(snapshot: TaskEntity) {
        notifier.cancel(snapshot.id)
        tasks.upsert(snapshot.copy(updatedAt = now()))
        afterChange()
    }

    suspend fun duplicate(id: String): TaskEntity? {
        val t = tasks.get(id) ?: return null
        val copy = t.copy(id = java.util.UUID.randomUUID().toString(), title = t.title + " (copy)", createdAt = now(), status = TaskStatus.OPEN, completedAt = null)
        save(copy)
        return copy
    }

    // ---- Answering a nag ----

    suspend fun complete(id: String) {
        val task = tasks.get(id) ?: return
        if (task.status != TaskStatus.OPEN) return
        notifier.cancel(id)
        val rule = Recurrence.decode(task.recurrence)
        if (rule != null && task.dueAt != null) {
            advance(task, rule, ActivityType.COMPLETED)
        } else {
            tasks.upsert(task.copy(status = TaskStatus.DONE, completedAt = now(), nextNagAt = null, updatedAt = now()))
            log(id, ActivityType.COMPLETED)
            if (task.placeId != null) syncGeofences()
        }
        unblockDependents(id)
        afterChange()
    }

    /** Tasks waiting on [id] start nagging now (or at their own due time if they have one). */
    private suspend fun unblockDependents(id: String) {
        for (dep in tasks.blockedBy(id)) {
            var t = resetNag(dep.copy(blockedBy = null, updatedAt = now()))
            if (t.nextNagAt == null && t.nagEnabled) t = t.copy(nextNagAt = now())
            tasks.upsert(t)
            log(dep.id, ActivityType.TRIGGERED, "unblocked")
        }
    }

    /** Skip just this occurrence of a recurring task. */
    suspend fun skip(id: String) {
        val task = tasks.get(id) ?: return
        val rule = Recurrence.decode(task.recurrence) ?: return
        if (task.dueAt == null) return
        notifier.cancel(id)
        advance(task, rule, ActivityType.SKIPPED)
        afterChange()
    }

    private suspend fun advance(task: TaskEntity, rule: Recurrence, type: String) {
        val next = Schedule.nextOccurrence(rule, task.dueAt!!, task.hasTime, now(), settings.dayStart, zone)
        tasks.upsert(resetNag(task.copy(dueAt = next, updatedAt = now(), completedAt = now())))
        log(task.id, type, "next: ${describeTime(next)}")
    }

    suspend fun abandon(id: String, reason: String = "") {
        val task = tasks.get(id) ?: return
        notifier.cancel(id)
        tasks.upsert(task.copy(status = TaskStatus.ABANDONED, completedAt = now(), nextNagAt = null, updatedAt = now()))
        log(id, ActivityType.ABANDONED, reason)
        if (task.placeId != null) syncGeofences()
        afterChange()
    }

    suspend fun reopen(id: String) {
        val task = tasks.get(id) ?: return
        save(task.copy(status = TaskStatus.OPEN, completedAt = null))
        log(id, ActivityType.REOPENED)
    }

    suspend fun snooze(id: String, minutes: Int) = snoozeUntil(id, now() + minutes * 60_000L)

    /** Postpone the next reminder without moving the due date. Escalation starts over. */
    suspend fun snoozeUntil(id: String, at: Long) {
        val task = tasks.get(id) ?: return
        notifier.cancel(id)
        tasks.upsert(task.copy(nextNagAt = at, nagCount = 0, snoozeCount = task.snoozeCount + 1, updatedAt = now()))
        log(id, ActivityType.SNOOZED, "until ${describeTime(at)}")
        afterChange()
    }

    suspend fun reschedule(id: String, dueAt: Long?, hasTime: Boolean) {
        val task = tasks.get(id) ?: return
        save(task.copy(dueAt = dueAt, hasTime = hasTime))
    }

    suspend fun reshowAfterSwipe(id: String) {
        val task = tasks.get(id) ?: return
        if (task.status == TaskStatus.OPEN && task.nagEnabled && task.lastNagAt != null) {
            notifier.showNag(task, escalate = false, silent = true)
        }
    }

    // ---- Context triggers ----

    suspend fun firePlace(placeId: String, arrived: Boolean) {
        val want = if (arrived) Trigger.ARRIVE else Trigger.LEAVE
        val place = places.get(placeId)
        fire(tasks.withPlace().filter { it.placeId == placeId && it.placeTrigger == want }, (if (arrived) "arrived at " else "left ") + (place?.name ?: "place"))
    }

    suspend fun fireBluetooth(address: String) {
        fire(tasks.withBluetooth(address), "Bluetooth connected")
        if (address == settings.carAddress) {
            notifier.showCarConnected(outstandingToday().size)
            if (settings.speakNags) {
                val today = outstandingToday()
                speaker.speak(
                    if (today.isEmpty()) "Connected. Nothing is due today."
                    else "Connected. You have ${today.size} tasks today: " + today.take(5).joinToString(". ") { it.title },
                )
            }
        }
    }

    private suspend fun fire(matching: List<TaskEntity>, reason: String) {
        if (matching.isEmpty()) return
        val nowMs = now()
        for (t in matching) {
            if (t.status != TaskStatus.OPEN || !t.nagEnabled || t.blockedBy != null) continue
            tasks.upsert(t.copy(nextNagAt = nowMs, nagCount = 0, updatedAt = nowMs))
            log(t.id, ActivityType.TRIGGERED, reason)
        }
        processDueNags()
    }

    private suspend fun outstandingToday(): List<TaskEntity> = TaskWidget.outstanding(tasks.open())

    suspend fun syncGeofences() {
        val needed = tasks.withPlace().filter { it.placeTrigger != Trigger.NONE }.mapNotNull { it.placeId }.toSet()
        geofences.sync(places.all().filter { it.id in needed })
    }

    /** Moves due dates of tasks linked to calendar events when the event moves. */
    suspend fun syncCalendar() {
        if (!calendar.hasPermission()) return
        for (t in tasks.withEvent()) {
            val due = t.dueAt ?: continue
            val occ = calendar.occurrenceNear(t.eventId!!, due) ?: continue
            if (occ.begin != due) save(t.copy(dueAt = occ.begin, hasTime = !occ.allDay, eventTitle = occ.title))
        }
    }

    // ---- The nag engine ----

    /** Shows every reminder that is due, then arms the alarm for the next one. */
    suspend fun processDueNags() = nagLock.withLock {
        val nowMs = now()
        val holdUntil = Quiet.holdUntil(context, settings, Instant.ofEpochMilli(nowMs).atZone(zone))
        val spoken = mutableListOf<String>()
        for (task in tasks.dueForNag(nowMs)) {
            val priority = Priority.fromLevel(task.priority)
            if (holdUntil != null && !(priority == Priority.P1 && settings.urgentBreaksQuiet)) {
                tasks.upsert(task.copy(nextNagAt = holdUntil))
                continue
            }
            val policy = settings.policy(priority)
            val shown = task.copy(nagCount = task.nagCount + 1, lastNagAt = nowMs)
            notifier.showNag(shown, escalate = policy.shouldEscalate(task.nagCount))
            tasks.upsert(shown.copy(nextNagAt = nowMs + policy.repeatMinutes * 60_000L))
            if (task.nagCount == 0) log(task.id, ActivityType.NAGGED)
            spoken += task.title
        }
        if (spoken.isNotEmpty() && settings.speakNags) speaker.speakIfInCar(spoken)
        scheduler.scheduleNext(tasks.earliestNag())
        TaskWidget.refresh(context)
    }

    private suspend fun afterChange() {
        scheduler.scheduleNext(tasks.earliestNag())
        TaskWidget.refresh(context)
    }

    private suspend fun log(taskId: String, type: String, detail: String = "") {
        activity.insert(ActivityEntity(taskId = taskId, type = type, detail = detail))
    }

    fun startOfToday(): Long = LocalDate.now(zone).atStartOfDay(zone).toInstant().toEpochMilli()

    private fun describeTime(ms: Long): String =
        Instant.ofEpochMilli(ms).atZone(zone).toLocalDateTime().toString().replace('T', ' ').take(16)
}

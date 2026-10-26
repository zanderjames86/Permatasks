package app.permatasks.data

import android.content.Context
import app.permatasks.domain.Priority
import app.permatasks.domain.QuickAddParser
import app.permatasks.domain.Recurrence
import app.permatasks.domain.Schedule
import app.permatasks.nag.NagScheduler
import app.permatasks.nag.Notifier
import app.permatasks.nag.Quiet
import app.permatasks.voice.Speaker
import app.permatasks.widget.TaskWidget
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Every change to a task goes through here so the nag alarm, the notification and the
 * home-screen widget always stay in step with the database.
 */
class TaskRepository(
    private val context: Context,
    db: AppDatabase,
    private val settings: Settings,
    private val scheduler: NagScheduler,
    private val notifier: Notifier,
    private val speaker: Speaker,
) {
    val tasks = db.tasks()
    val projects = db.projects()
    val activity = db.activity()
    private val nagLock = Mutex()

    private val zone: ZoneId get() = ZoneId.systemDefault()
    private fun now() = System.currentTimeMillis()

    // ---- Creating and editing ----

    suspend fun quickAdd(text: String, defaultProjectId: String? = null, parentId: String? = null): TaskEntity? {
        if (text.isBlank()) return null
        val parsed = QuickAddParser.parse(text, ZonedDateTime.now(zone))
        val projectId = parsed.project?.let { name ->
            projects.byName(name)?.id ?: ProjectEntity(name = name).also { projects.upsert(it) }.id
        } ?: defaultProjectId
        val task = TaskEntity(
            title = parsed.title,
            projectId = projectId,
            parentId = parentId,
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
            old.status != task.status
        var updated = task.copy(updatedAt = now())
        if (timingChanged) {
            updated = resetNag(updated)
            if (old != null && old.dueAt != task.dueAt) log(task.id, ActivityType.RESCHEDULED, task.dueAt?.let(::describeTime) ?: "no date")
            notifier.cancel(task.id)
        }
        tasks.upsert(updated)
        afterChange()
    }

    private fun resetNag(task: TaskEntity): TaskEntity {
        val due = task.dueAt
        val next = if (task.status == TaskStatus.OPEN && task.nagEnabled && due != null) {
            Schedule.nagStart(due, task.hasTime, task.remindBeforeMinutes, settings.dayStart, zone)
        } else {
            null
        }
        return task.copy(nextNagAt = next, nagCount = 0, lastNagAt = null, snoozeCount = 0)
    }

    suspend fun delete(id: String) {
        notifier.cancel(id)
        tasks.delete(id)
        afterChange()
    }

    // ---- Answering a nag ----

    suspend fun complete(id: String) {
        val task = tasks.get(id) ?: return
        notifier.cancel(id)
        val rule = Recurrence.decode(task.recurrence)
        if (rule != null && task.dueAt != null) {
            advance(task, rule, ActivityType.COMPLETED)
        } else {
            tasks.upsert(task.copy(status = TaskStatus.DONE, completedAt = now(), nextNagAt = null, updatedAt = now()))
            log(id, ActivityType.COMPLETED)
        }
        afterChange()
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

    private fun describeTime(ms: Long): String =
        Instant.ofEpochMilli(ms).atZone(zone).toLocalDateTime().toString().replace('T', ' ').take(16)
}

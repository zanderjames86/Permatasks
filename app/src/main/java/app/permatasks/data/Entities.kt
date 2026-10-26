package app.permatasks.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

object TaskStatus {
    const val OPEN = 0
    const val DONE = 1
    const val ABANDONED = 2
}

/**
 * Ids are UUID strings so the data can later be synced (e.g. a self-hosted web
 * companion or shared family lists) without id collisions.
 */
@Entity(
    tableName = "tasks",
    indices = [Index("status"), Index("projectId"), Index("parentId"), Index("nextNagAt")],
)
data class TaskEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val title: String,
    val description: String = "",
    val projectId: String? = null,
    val parentId: String? = null,
    val priority: Int = 4,
    /** Comma separated, without the leading '@'. */
    val labels: String = "",
    val dueAt: Long? = null,
    val hasTime: Boolean = false,
    val recurrence: String? = null,
    val remindBeforeMinutes: Int = 0,
    val nagEnabled: Boolean = true,
    val status: Int = TaskStatus.OPEN,
    val completedAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val sortOrder: Long = System.currentTimeMillis(),
    /** When the nag engine should next remind; null when nothing is scheduled. */
    val nextNagAt: Long? = null,
    /** Reminders shown since the task became due and was not acted on. Drives escalation. */
    val nagCount: Int = 0,
    val lastNagAt: Long? = null,
    /** How many times this occurrence was postponed; shown back to the user in the nag. */
    val snoozeCount: Int = 0,
) {
    val labelList: List<String> get() = labels.split(',').map { it.trim() }.filter { it.isNotEmpty() }
}

@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val color: Long = 0xFF7E57C2,
    val sortOrder: Long = System.currentTimeMillis(),
    val archived: Boolean = false,
)

object ActivityType {
    const val CREATED = "created"
    const val NAGGED = "nagged"
    const val SNOOZED = "snoozed"
    const val COMPLETED = "completed"
    const val SKIPPED = "skipped"
    const val ABANDONED = "abandoned"
    const val RESCHEDULED = "rescheduled"
    const val REOPENED = "reopened"
}

@Entity(tableName = "activity", indices = [Index("taskId"), Index("at")])
data class ActivityEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val taskId: String,
    val type: String,
    val at: Long = System.currentTimeMillis(),
    val detail: String = "",
)

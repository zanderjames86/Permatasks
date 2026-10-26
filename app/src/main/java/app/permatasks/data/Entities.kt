package app.permatasks.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable
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
@Serializable
@Entity(
    tableName = "tasks",
    indices = [
        Index("status"), Index("projectId"), Index("parentId"), Index("nextNagAt"),
        Index("blockedBy"), Index("placeId"), Index("noteId"),
    ],
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
    val sectionId: String? = null,
    /** Waits for this task to be completed; then it starts nagging. */
    val blockedBy: String? = null,
    val placeId: String? = null,
    /** [Trigger.ARRIVE] or [Trigger.LEAVE] when [placeId] is set. */
    val placeTrigger: Int = Trigger.NONE,
    /** Start nagging when this Bluetooth device (e.g. the car) connects. */
    val bluetoothAddress: String? = null,
    val bluetoothName: String? = null,
    /** Due time follows this calendar event's start. */
    val eventId: Long? = null,
    val eventTitle: String? = null,
    /** The note whose checklist item created this task. */
    val noteId: String? = null,
) {
    val hasTrigger: Boolean get() = placeId != null || bluetoothAddress != null
    val labelList: List<String> get() = labels.split(',').map { it.trim() }.filter { it.isNotEmpty() }
}

object Trigger {
    const val NONE = 0
    const val ARRIVE = 1
    const val LEAVE = 2
}

@Serializable
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
    const val TRIGGERED = "triggered"
}

@Serializable
@Entity(tableName = "activity", indices = [Index("taskId"), Index("at")])
data class ActivityEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val taskId: String,
    val type: String,
    val at: Long = System.currentTimeMillis(),
    val detail: String = "",
)

@Serializable
@Entity(tableName = "sections", indices = [Index("projectId")])
data class SectionEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val projectId: String?,
    val name: String,
    val sortOrder: Long = System.currentTimeMillis(),
)

/** A saved search such as "overdue | (today & p1)". */
@Serializable
@Entity(tableName = "filters")
data class FilterEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val query: String,
    val color: Long = 0xFF42A5F5,
    val sortOrder: Long = System.currentTimeMillis(),
)

@Serializable
@Entity(tableName = "places")
data class PlaceEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Int = 150,
    val address: String = "",
)

/**
 * A Notion-style page. [blocks] is the JSON-encoded block list (see NoteBlocks);
 * [plainText] is kept alongside it for search and previews.
 */
@Serializable
@Entity(tableName = "notes", indices = [Index("updatedAt")])
data class NoteEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val title: String = "",
    val blocks: String = "[]",
    val plainText: String = "",
    val pinned: Boolean = false,
    val color: Long = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val archived: Boolean = false,
)

/** An outgoing [[wiki link]] from a note, by lower-cased target title. */
@Serializable
@Entity(tableName = "note_links", primaryKeys = ["noteId", "target"], indices = [Index("target")])
data class NoteLinkEntity(
    val noteId: String,
    val target: String,
)

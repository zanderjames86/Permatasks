package app.permatasks.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks WHERE status = 0 ORDER BY dueAt IS NULL, dueAt, priority, sortOrder")
    fun observeOpen(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE status = 0 ORDER BY dueAt IS NULL, dueAt, priority, sortOrder")
    suspend fun open(): List<TaskEntity>

    @Query("SELECT * FROM tasks WHERE status != 0 ORDER BY completedAt DESC LIMIT :limit")
    fun observeClosed(limit: Int = 200): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE id = :id")
    fun observe(id: String): Flow<TaskEntity?>

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun get(id: String): TaskEntity?

    @Query("SELECT * FROM tasks WHERE parentId = :parentId ORDER BY status, sortOrder")
    fun observeSubtasks(parentId: String): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE status = 0 AND nagEnabled = 1 AND nextNagAt IS NOT NULL AND nextNagAt <= :now")
    suspend fun dueForNag(now: Long): List<TaskEntity>

    @Query("SELECT MIN(nextNagAt) FROM tasks WHERE status = 0 AND nagEnabled = 1 AND nextNagAt IS NOT NULL")
    suspend fun earliestNag(): Long?

    @Upsert
    suspend fun upsert(task: TaskEntity)

    @Query("DELETE FROM tasks WHERE id = :id OR parentId = :id")
    suspend fun delete(id: String)

    @Query("SELECT * FROM tasks WHERE status = 0 AND blockedBy = :id")
    suspend fun blockedBy(id: String): List<TaskEntity>

    @Query("SELECT * FROM tasks WHERE status = 0 AND placeId IS NOT NULL")
    suspend fun withPlace(): List<TaskEntity>

    @Query("SELECT * FROM tasks WHERE status = 0 AND bluetoothAddress = :address")
    suspend fun withBluetooth(address: String): List<TaskEntity>

    @Query("SELECT * FROM tasks WHERE status = 0 AND eventId IS NOT NULL")
    suspend fun withEvent(): List<TaskEntity>

    @Query("SELECT * FROM tasks WHERE noteId = :noteId")
    fun observeForNote(noteId: String): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE title LIKE '%' || :q || '%' OR description LIKE '%' || :q || '%' ORDER BY status, dueAt IS NULL, dueAt LIMIT 100")
    fun search(q: String): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks")
    suspend fun all(): List<TaskEntity>

    @Query("SELECT COUNT(*) FROM tasks WHERE status = 1")
    fun observeDoneCount(): Flow<Int>
}

@Dao
interface SectionDao {
    @Query("SELECT * FROM sections ORDER BY sortOrder")
    fun observeAll(): Flow<List<SectionEntity>>

    @Query("SELECT * FROM sections")
    suspend fun all(): List<SectionEntity>

    @Upsert
    suspend fun upsert(section: SectionEntity)

    @Delete
    suspend fun delete(section: SectionEntity)

    @Query("UPDATE tasks SET sectionId = NULL WHERE sectionId = :sectionId")
    suspend fun detachTasks(sectionId: String)
}

@Dao
interface FilterDao {
    @Query("SELECT * FROM filters ORDER BY sortOrder")
    fun observeAll(): Flow<List<FilterEntity>>

    @Query("SELECT * FROM filters")
    suspend fun all(): List<FilterEntity>

    @Query("SELECT * FROM filters WHERE id = :id")
    suspend fun get(id: String): FilterEntity?

    @Upsert
    suspend fun upsert(filter: FilterEntity)

    @Delete
    suspend fun delete(filter: FilterEntity)
}

@Dao
interface PlaceDao {
    @Query("SELECT * FROM places ORDER BY name")
    fun observeAll(): Flow<List<PlaceEntity>>

    @Query("SELECT * FROM places")
    suspend fun all(): List<PlaceEntity>

    @Query("SELECT * FROM places WHERE id = :id")
    suspend fun get(id: String): PlaceEntity?

    @Upsert
    suspend fun upsert(place: PlaceEntity)

    @Delete
    suspend fun delete(place: PlaceEntity)
}

@Dao
interface NoteDao {
    @Query("SELECT * FROM notes WHERE archived = 0 ORDER BY pinned DESC, updatedAt DESC")
    fun observeAll(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes")
    suspend fun all(): List<NoteEntity>

    @Query("SELECT * FROM notes WHERE id = :id")
    fun observe(id: String): Flow<NoteEntity?>

    @Query("SELECT * FROM notes WHERE id = :id")
    suspend fun get(id: String): NoteEntity?

    @Query("SELECT * FROM notes WHERE lower(title) = lower(:title) AND archived = 0 LIMIT 1")
    suspend fun byTitle(title: String): NoteEntity?

    @Query("SELECT * FROM notes WHERE archived = 0 AND (title LIKE '%' || :q || '%' OR plainText LIKE '%' || :q || '%') ORDER BY updatedAt DESC LIMIT 100")
    fun search(q: String): Flow<List<NoteEntity>>

    @Query("SELECT n.* FROM notes n JOIN note_links l ON l.noteId = n.id WHERE l.target = lower(:title) AND n.archived = 0")
    fun observeBacklinks(title: String): Flow<List<NoteEntity>>

    @Upsert
    suspend fun upsert(note: NoteEntity)

    @Query("DELETE FROM notes WHERE id = :id")
    suspend fun delete(id: String)

    @Query("SELECT * FROM note_links")
    fun observeLinks(): Flow<List<NoteLinkEntity>>

    @Query("SELECT * FROM note_links")
    suspend fun allLinks(): List<NoteLinkEntity>

    @Query("DELETE FROM note_links WHERE noteId = :noteId")
    suspend fun clearLinks(noteId: String)

    @Insert(onConflict = androidx.room.OnConflictStrategy.IGNORE)
    suspend fun insertLinks(links: List<NoteLinkEntity>)
}

@Dao
interface ProjectDao {
    @Query("SELECT * FROM projects WHERE archived = 0 ORDER BY sortOrder")
    fun observeAll(): Flow<List<ProjectEntity>>

    @Query("SELECT * FROM projects WHERE archived = 0 ORDER BY sortOrder")
    suspend fun all(): List<ProjectEntity>

    @Query("SELECT * FROM projects WHERE name = :name COLLATE NOCASE LIMIT 1")
    suspend fun byName(name: String): ProjectEntity?

    @Upsert
    suspend fun upsert(project: ProjectEntity)

    @Delete
    suspend fun delete(project: ProjectEntity)

    @Query("UPDATE tasks SET projectId = NULL WHERE projectId = :projectId")
    suspend fun detachTasks(projectId: String)
}

@Dao
interface ActivityDao {
    @Insert
    suspend fun insert(entry: ActivityEntity)

    @Query("SELECT * FROM activity WHERE taskId = :taskId ORDER BY at DESC LIMIT 100")
    fun observeFor(taskId: String): Flow<List<ActivityEntity>>

    @Query("SELECT * FROM activity WHERE at >= :since ORDER BY at")
    fun observeSince(since: Long): Flow<List<ActivityEntity>>

    @Query("SELECT * FROM activity")
    suspend fun all(): List<ActivityEntity>
}

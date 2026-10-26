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
}

package app.permatasks.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import app.permatasks.security.DatabaseKey
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

@Database(
    entities = [
        TaskEntity::class, ProjectEntity::class, ActivityEntity::class, SectionEntity::class,
        FilterEntity::class, PlaceEntity::class, NoteEntity::class, NoteLinkEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun tasks(): TaskDao
    abstract fun projects(): ProjectDao
    abstract fun activity(): ActivityDao
    abstract fun sections(): SectionDao
    abstract fun filters(): FilterDao
    abstract fun places(): PlaceDao
    abstract fun notes(): NoteDao

    companion object {
        /** The whole database file is encrypted with SQLCipher (AES-256). */
        fun open(context: Context): AppDatabase {
            System.loadLibrary("sqlcipher")
            val factory = SupportOpenHelperFactory(DatabaseKey.passphrase(context))
            return Room.databaseBuilder(context, AppDatabase::class.java, "permatasks.db")
                .openHelperFactory(factory)
                .build()
        }
    }
}

package app.permatasks

import androidx.room.Room
import app.permatasks.data.AppDatabase

/** Same app, but with an in-memory, unencrypted database (no Keystore under Robolectric). */
class TestApp : PermatasksApp() {
    override val runBackgroundWork = false

    override fun buildContainer() = AppContainer(this) { ctx ->
        Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java).allowMainThreadQueries().build()
    }
}

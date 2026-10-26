package app.permatasks

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import app.permatasks.data.AppDatabase
import app.permatasks.data.Backup
import app.permatasks.data.MaintenanceWorker
import app.permatasks.data.NoteRepository
import app.permatasks.data.Settings
import app.permatasks.data.TaskRepository
import app.permatasks.nag.NagScheduler
import app.permatasks.nag.Notifier
import app.permatasks.security.AppLock
import app.permatasks.security.FileVault
import app.permatasks.security.PinStore
import app.permatasks.triggers.CalendarSource
import app.permatasks.triggers.Geofences
import app.permatasks.voice.Speaker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

open class PermatasksApp : Application() {
    lateinit var container: AppContainer
        private set

    /** Overridden by tests to use an in-memory database. */
    protected open fun buildContainer() = AppContainer(this)

    protected open val runBackgroundWork = true

    override fun onCreate() {
        super.onCreate()
        container = buildContainer()
        Notifier.createChannels(this)
        container.lock.install()
        if (runBackgroundWork) {
            MaintenanceWorker.schedule(this)
            // Catch up on anything missed while the process was dead.
            container.scope.launch { container.repo.processDueNags() }
        }
    }
}

/** Hand-rolled dependency container; the app is small enough not to need a DI framework. */
class AppContainer(val context: Context, dbFactory: (Context) -> AppDatabase = AppDatabase::open) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val settings = Settings(context)
    val pins = PinStore(context)
    val lock = AppLock(settings)
    val db: AppDatabase by lazy { dbFactory(context) }
    val scheduler = NagScheduler(context)
    val notifier = Notifier(context, settings)
    val speaker = Speaker(context)
    val geofences = Geofences(context)
    val calendar = CalendarSource(context)
    val vault = FileVault(context)
    val repo: TaskRepository by lazy { TaskRepository(context, db, settings, scheduler, notifier, speaker, geofences, calendar) }
    val notes: NoteRepository by lazy { NoteRepository(db, repo, vault) }
    val backup: Backup by lazy { Backup(context, db, vault, settings) }
}

val Context.container: AppContainer get() = (applicationContext as PermatasksApp).container

/** Runs suspending work from a receiver without being killed mid-way. */
fun BroadcastReceiver.runAsync(context: Context, block: suspend () -> Unit) {
    val pending = goAsync()
    context.container.scope.launch {
        try {
            block()
        } finally {
            pending.finish()
        }
    }
}

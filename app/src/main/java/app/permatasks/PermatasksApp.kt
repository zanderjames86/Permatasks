package app.permatasks

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import app.permatasks.data.AppDatabase
import app.permatasks.data.Settings
import app.permatasks.data.TaskRepository
import app.permatasks.nag.NagScheduler
import app.permatasks.nag.Notifier
import app.permatasks.security.AppLock
import app.permatasks.security.PinStore
import app.permatasks.voice.Speaker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class PermatasksApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        Notifier.createChannels(this)
        container.lock.install()
        // Catch up on anything missed while the process was dead.
        container.scope.launch { container.repo.processDueNags() }
    }
}

/** Hand-rolled dependency container; the app is small enough not to need a DI framework. */
class AppContainer(val context: Context) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val settings = Settings(context)
    val pins = PinStore(context)
    val lock = AppLock(settings)
    val db: AppDatabase by lazy { AppDatabase.open(context) }
    val scheduler = NagScheduler(context)
    val notifier = Notifier(context, settings)
    val speaker = Speaker(context)
    val repo: TaskRepository by lazy { TaskRepository(context, db, settings, scheduler, notifier, speaker) }
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

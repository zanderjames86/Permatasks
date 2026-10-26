package app.permatasks.data

import android.content.Context
import android.net.Uri
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.permatasks.container
import java.util.concurrent.TimeUnit

/**
 * Safety net every few hours: re-arms the nag alarm, follows moved calendar events,
 * re-registers geofences and writes the nightly encrypted backup.
 */
class MaintenanceWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val c = applicationContext.container
        runCatching { c.repo.syncCalendar() }
        runCatching { c.repo.syncGeofences() }
        c.repo.processDueNags()
        val s = c.settings
        val uri = s.backupUri
        if (s.autoBackup && uri != null && c.backup.hasKey && System.currentTimeMillis() - s.lastBackupAt > 20 * 3600_000L) {
            runCatching { c.backup.writeTo(Uri.parse(uri)) }.onFailure { s.lastBackupError = it.message ?: it.javaClass.simpleName }
        }
        return Result.success()
    }

    companion object {
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<MaintenanceWorker>(4, TimeUnit.HOURS).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork("maintenance", ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}

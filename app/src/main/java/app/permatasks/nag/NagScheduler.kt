package app.permatasks.nag

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent

/**
 * Keeps exactly one exact alarm armed: the earliest pending nag across all tasks.
 * When it fires, every task that is due gets processed and the next alarm is armed.
 * Exact "allow while idle" alarms are not delayed by Doze or battery saver.
 */
class NagScheduler(private val context: Context) {
    private val alarms = context.getSystemService(AlarmManager::class.java)

    private fun intent() = PendingIntent.getBroadcast(
        context,
        0,
        Intent(context, NagAlarmReceiver::class.java).setAction(ACTION_NAG),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    fun scheduleNext(at: Long?) {
        val pi = intent()
        if (at == null) {
            alarms.cancel(pi)
            return
        }
        val trigger = at.coerceAtLeast(System.currentTimeMillis() + 1_000)
        if (alarms.canScheduleExactAlarms()) {
            alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pi)
        } else {
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pi)
        }
    }

    companion object {
        const val ACTION_NAG = "app.permatasks.action.NAG"
    }
}

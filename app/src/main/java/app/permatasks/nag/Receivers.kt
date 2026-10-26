package app.permatasks.nag

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.permatasks.container
import app.permatasks.runAsync

/** Fired by the single exact alarm; processes every task whose nag is due. */
class NagAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        runAsync(context) { context.container.repo.processDueNags() }
    }
}

/** Re-arms the alarm after a reboot, an app update, or a clock / time zone change. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in RESCHEDULE_ACTIONS) return
        runAsync(context) { context.container.repo.processDueNags() }
    }

    private companion object {
        val RESCHEDULE_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            android.app.AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED,
        )
    }
}

/** Buttons on the reminder notification, plus the swipe-away (delete) intent. */
class ActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getStringExtra(EXTRA_TASK_ID) ?: return
        val repo = context.container.repo
        runAsync(context) {
            when (intent.action) {
                ACTION_DONE -> repo.complete(taskId)
                ACTION_SNOOZE_15 -> repo.snooze(taskId, 15)
                // Swiping does not count as an answer: put the reminder straight back, quietly.
                ACTION_DISMISSED -> repo.reshowAfterSwipe(taskId)
            }
        }
    }

    companion object {
        const val EXTRA_TASK_ID = "task_id"
        const val ACTION_DONE = "app.permatasks.action.DONE"
        const val ACTION_SNOOZE_15 = "app.permatasks.action.SNOOZE_15"
        const val ACTION_DISMISSED = "app.permatasks.action.DISMISSED"
    }
}

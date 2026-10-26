package app.permatasks.nag

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import androidx.core.app.NotificationCompat
import app.permatasks.R
import app.permatasks.data.TaskEntity
import app.permatasks.data.Settings
import app.permatasks.domain.Priority
import app.permatasks.ui.MainActivity
import app.permatasks.ui.format.Formats

class Notifier(private val context: Context, private val settings: Settings) {
    private val manager = context.getSystemService(NotificationManager::class.java)

    fun notificationId(taskId: String) = taskId.hashCode()

    /**
     * Shows (or refreshes) the persistent reminder for [task].
     * [silent] is used when re-posting after a swipe so it reappears without making noise.
     */
    fun showNag(task: TaskEntity, escalate: Boolean, silent: Boolean = false) {
        val channel = when {
            silent -> CHANNEL_SILENT
            escalate -> CHANNEL_URGENT
            else -> CHANNEL_NAG
        }
        val id = notificationId(task.id)
        val title = task.title
        val details = buildString {
            task.dueAt?.let { append(Formats.dueRelative(it, task.hasTime)) }
            if (task.nagCount > 0) append(" · reminded ${task.nagCount}×")
            if (task.snoozeCount > 0) append(" · snoozed ${task.snoozeCount}×")
        }.trim(' ', '·')
        val question = "Have you done it?"

        val builder = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_stat_task)
            .setContentTitle(title)
            .setContentText(if (details.isEmpty()) question else "$details — $question")
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    listOf(details, task.description.take(300), question).filter { it.isNotBlank() }.joinToString("\n"),
                ),
            )
            .setCategory(if (escalate) NotificationCompat.CATEGORY_ALARM else NotificationCompat.CATEGORY_REMINDER)
            .setPriority(if (silent) NotificationCompat.PRIORITY_LOW else NotificationCompat.PRIORITY_MAX)
            .setOngoing(true)
            .setAutoCancel(false)
            .setOnlyAlertOnce(silent)
            .setSilent(silent)
            .setColor(Formats.priorityColor(Priority.fromLevel(task.priority)).toInt())
            .setWhen(task.dueAt ?: System.currentTimeMillis())
            .setContentIntent(openTask(task.id))
            .setDeleteIntent(actionIntent(task.id, ActionReceiver.ACTION_DISMISSED))
            .addAction(0, "Done", actionIntent(task.id, ActionReceiver.ACTION_DONE))
            .addAction(0, "Snooze 15m", actionIntent(task.id, ActionReceiver.ACTION_SNOOZE_15))
            .addAction(0, "More…", nagScreen(task.id, alarm = false))

        if (settings.hideOnLockScreen) {
            builder.setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            builder.setPublicVersion(
                NotificationCompat.Builder(context, channel)
                    .setSmallIcon(R.drawable.ic_stat_task)
                    .setContentTitle("A task needs your attention")
                    .setContentText("Unlock to see it")
                    .build(),
            )
        } else {
            builder.setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        }

        if (escalate && !silent) {
            builder.setFullScreenIntent(nagScreen(task.id, alarm = true), true)
        }

        val notification = builder.build()
        notification.flags = notification.flags or Notification.FLAG_NO_CLEAR
        // Insistent: the alarm sound keeps looping until the user acts on it.
        if (escalate && !silent) notification.flags = notification.flags or Notification.FLAG_INSISTENT
        manager.notify(id, notification)
    }

    fun cancel(taskId: String) = manager.cancel(notificationId(taskId))

    /** "Car connected" card with a one-tap way into Drive mode. */
    fun showCarConnected(dueToday: Int) {
        val open = PendingIntent.getActivity(
            context, CAR_ID,
            Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_DRIVE, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(context, CHANNEL_GENERAL)
            .setSmallIcon(R.drawable.ic_stat_task)
            .setContentTitle("Car connected")
            .setContentText(if (dueToday == 0) "Nothing due today" else "$dueToday tasks due today · tap for Drive mode")
            .setContentIntent(open)
            .setAutoCancel(true)
            .setTimeoutAfter(30 * 60_000L)
            .build()
        manager.notify(CAR_ID, n)
    }

    fun isShowing(taskId: String): Boolean = manager.activeNotifications.any { it.id == notificationId(taskId) }

    private fun openTask(taskId: String) = PendingIntent.getActivity(
        context,
        notificationId(taskId),
        Intent(context, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_TASK_ID, taskId)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun nagScreen(taskId: String, alarm: Boolean) = PendingIntent.getActivity(
        context,
        notificationId(taskId) * 31 + if (alarm) 7 else 8,
        Intent(context, NagActivity::class.java)
            .putExtra(NagActivity.EXTRA_TASK_ID, taskId)
            .putExtra(NagActivity.EXTRA_ALARM, alarm)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun actionIntent(taskId: String, action: String) = PendingIntent.getBroadcast(
        context,
        notificationId(taskId) * 31 + action.hashCode() % 1000,
        Intent(context, ActionReceiver::class.java)
            .setAction(action)
            .putExtra(ActionReceiver.EXTRA_TASK_ID, taskId),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    companion object {
        const val CHANNEL_NAG = "nag"
        const val CHANNEL_URGENT = "nag_urgent"
        const val CHANNEL_SILENT = "nag_silent"
        const val CHANNEL_GENERAL = "general"
        private const val CAR_ID = 4242

        fun createChannels(context: Context) {
            val nm = context.getSystemService(NotificationManager::class.java)
            val nag = NotificationChannel(CHANNEL_NAG, "Task reminders", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Repeating reminders until a task is done"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 300, 200, 300)
                setShowBadge(true)
                lockscreenVisibility = Notification.VISIBILITY_PRIVATE
            }
            val urgent = NotificationChannel(CHANNEL_URGENT, "Escalated reminders (alarm)", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Full-screen, alarm-style reminders for tasks you keep ignoring"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 800, 400, 800, 400, 800)
                setSound(
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
                setBypassDnd(true)
                lockscreenVisibility = Notification.VISIBILITY_PRIVATE
            }
            val silent = NotificationChannel(CHANNEL_SILENT, "Reminders (re-shown silently)", NotificationManager.IMPORTANCE_LOW).apply {
                description = "A reminder you swiped away, put back quietly"
                setShowBadge(true)
            }
            val general = NotificationChannel(CHANNEL_GENERAL, "General", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Car connected, backups and other information"
            }
            nm.createNotificationChannels(listOf(nag, urgent, silent, general))
        }
    }
}

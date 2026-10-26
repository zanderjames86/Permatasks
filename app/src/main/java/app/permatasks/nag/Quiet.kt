package app.permatasks.nag

import android.app.NotificationManager
import android.content.Context
import app.permatasks.data.Settings
import app.permatasks.domain.Schedule
import java.time.ZonedDateTime

/**
 * Decides whether nags should be held back right now.
 * Pixel's Bedtime mode switches on Do Not Disturb, so following DND follows Bedtime mode.
 * Held nags are re-checked shortly and all fire once the quiet period ends.
 */
object Quiet {
    private const val DND_RECHECK_MS = 10 * 60_000L

    /** Returns when to look again if quiet now, or null if nags may fire. */
    fun holdUntil(context: Context, settings: Settings, now: ZonedDateTime): Long? {
        val nowMs = now.toInstant().toEpochMilli()
        if (settings.followBedtime) {
            val filter = context.getSystemService(NotificationManager::class.java).currentInterruptionFilter
            if (filter != NotificationManager.INTERRUPTION_FILTER_ALL && filter != NotificationManager.INTERRUPTION_FILTER_UNKNOWN) {
                return nowMs + DND_RECHECK_MS
            }
        }
        if (settings.quietHoursEnabled) {
            val minute = now.hour * 60 + now.minute
            if (Schedule.inWindow(minute, settings.quietStart, settings.quietEnd)) {
                return Schedule.windowEnd(now, settings.quietEnd)
            }
        }
        return null
    }
}

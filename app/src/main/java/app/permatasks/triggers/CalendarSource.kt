package app.permatasks.triggers

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat

data class CalendarEvent(
    val eventId: Long,
    val title: String,
    val begin: Long,
    val end: Long,
    val allDay: Boolean,
    val color: Int,
)

/** Reads the calendars already on the phone (read-only, nothing leaves the device). */
class CalendarSource(private val context: Context) {

    fun hasPermission() =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

    fun events(from: Long, to: Long): List<CalendarEvent> {
        if (!hasPermission()) return emptyList()
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also {
            ContentUris.appendId(it, from)
            ContentUris.appendId(it, to)
        }.build()
        val projection = arrayOf(
            CalendarContract.Instances.EVENT_ID,
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.ALL_DAY,
            CalendarContract.Instances.DISPLAY_COLOR,
        )
        val out = mutableListOf<CalendarEvent>()
        runCatching {
            context.contentResolver.query(uri, projection, null, null, "${CalendarContract.Instances.BEGIN} ASC")?.use { c ->
                while (c.moveToNext()) {
                    out += CalendarEvent(
                        eventId = c.getLong(0),
                        title = c.getString(1) ?: "(No title)",
                        begin = c.getLong(2),
                        end = c.getLong(3),
                        allDay = c.getInt(4) == 1,
                        color = c.getInt(5),
                    )
                }
            }
        }
        return out
    }

    /** The occurrence of [eventId] closest to [around], or null if the event is gone. */
    fun occurrenceNear(eventId: Long, around: Long): CalendarEvent? {
        val week = 7L * 24 * 3600_000
        return events(around - week, around + 8 * week)
            .filter { it.eventId == eventId }
            .minByOrNull { kotlin.math.abs(it.begin - around) }
    }
}

package app.permatasks.ui

import android.app.PendingIntent
import android.content.Intent
import android.service.quicksettings.TileService

/** Quick Settings tile: pull down the shade, tap, type or speak a task. */
class AddTaskTileService : TileService() {
    override fun onClick() {
        val intent = Intent(this, QuickAddActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivityAndCollapse(
            PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT),
        )
    }
}

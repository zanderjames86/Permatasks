package app.permatasks.triggers

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import app.permatasks.container
import app.permatasks.data.PlaceEntity
import app.permatasks.runAsync
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.tasks.await

/**
 * Registers a Play Services geofence for every place that an open task is waiting on.
 * Geofencing runs inside Google Play Services; this app itself still has no network access.
 */
class Geofences(private val context: Context) {
    private val client by lazy { LocationServices.getGeofencingClient(context) }

    private val pendingIntent: PendingIntent by lazy {
        PendingIntent.getBroadcast(
            context,
            1,
            Intent(context, GeofenceReceiver::class.java),
            // Play Services fills in the event details, so this one has to be mutable.
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        )
    }

    fun hasPermission(): Boolean =
        granted(Manifest.permission.ACCESS_FINE_LOCATION) && granted(Manifest.permission.ACCESS_BACKGROUND_LOCATION)

    private fun granted(p: String) = ContextCompat.checkSelfPermission(context, p) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    suspend fun sync(places: List<PlaceEntity>) {
        runCatching { client.removeGeofences(pendingIntent).await() }
        if (places.isEmpty() || !hasPermission()) return
        val fences = places.take(90).map { p ->
            Geofence.Builder()
                .setRequestId(p.id)
                .setCircularRegion(p.latitude, p.longitude, p.radiusMeters.toFloat().coerceAtLeast(100f))
                .setExpirationDuration(Geofence.NEVER_EXPIRE)
                .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER or Geofence.GEOFENCE_TRANSITION_EXIT)
                .setNotificationResponsiveness(60_000)
                .build()
        }
        val request = GeofencingRequest.Builder()
            .setInitialTrigger(0)
            .addGeofences(fences)
            .build()
        runCatching { client.addGeofences(request, pendingIntent).await() }
    }
}

class GeofenceReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val event = GeofencingEvent.fromIntent(intent) ?: return
        if (event.hasError()) return
        val arrive = when (event.geofenceTransition) {
            Geofence.GEOFENCE_TRANSITION_ENTER -> true
            Geofence.GEOFENCE_TRANSITION_EXIT -> false
            else -> return
        }
        val ids = event.triggeringGeofences?.map { it.requestId }.orEmpty()
        runAsync(context) { ids.forEach { context.container.repo.firePlace(it, arrive) } }
    }
}

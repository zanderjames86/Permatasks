package app.permatasks.ui.settings

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect

data class SetupItem(val title: String, val why: String, val ok: Boolean, val fix: () -> Unit)

/** Everything the nag engine needs from Android to be reliable, with a one-tap fix for each. */
@Composable
fun rememberSetupItems(): List<SetupItem> {
    val context = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        tick++
        onPauseOrDispose {}
    }
    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { tick++ }
    return remember(tick) { setupItems(context) { notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS) } }
}

private fun setupItems(context: Context, requestNotifications: () -> Unit): List<SetupItem> {
    val nm = context.getSystemService(NotificationManager::class.java)
    val alarms = context.getSystemService(AlarmManager::class.java)
    val power = context.getSystemService(PowerManager::class.java)
    val pkg = Uri.parse("package:${context.packageName}")
    fun open(intent: Intent) = runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    return listOf(
        SetupItem(
            "Allow notifications", "Reminders can't appear without this.",
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED,
            requestNotifications,
        ),
        SetupItem(
            "Allow full-screen alarms", "Lets ignored reminders take over the screen, like an alarm clock.",
            nm.canUseFullScreenIntent(),
        ) { open(Intent(AndroidSettings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, pkg)) },
        SetupItem(
            "Allow exact alarms", "Reminders fire at the exact minute, even in Doze.",
            alarms.canScheduleExactAlarms(),
        ) { open(Intent(AndroidSettings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, pkg)) },
        SetupItem(
            "Ignore battery optimisation", "Stops Android from delaying or killing reminders.",
            power.isIgnoringBatteryOptimizations(context.packageName),
        ) { open(Intent(AndroidSettings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, pkg)) },
    )
}

@Composable
fun SetupChecklistCard(items: List<SetupItem>) {
    val missing = items.filter { !it.ok }
    if (missing.isEmpty()) return
    Card(
        Modifier.fillMaxWidth().padding(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Finish setup so reminders can't be missed", style = MaterialTheme.typography.titleSmall)
            missing.forEach { item ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(item.title, style = MaterialTheme.typography.bodyMedium)
                        Text(item.why, style = MaterialTheme.typography.bodySmall)
                    }
                    TextButton(onClick = item.fix) { Text("Fix") }
                }
            }
        }
    }
}

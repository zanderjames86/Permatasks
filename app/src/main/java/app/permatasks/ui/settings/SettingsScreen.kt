package app.permatasks.ui.settings

import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import app.permatasks.container
import app.permatasks.domain.Priority
import app.permatasks.ui.lock.biometricAvailable
import app.permatasks.ui.lock.promptBiometric

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(onBack: () -> Unit, onChangePin: () -> Unit) {
    val context = LocalContext.current
    val activity = context as FragmentActivity
    val s = context.container.settings
    @Suppress("UNUSED_VARIABLE") val tick by s.changes.collectAsState()
    var editTime by remember { mutableStateOf<String?>(null) }
    val setup = rememberSetupItems()

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Settings") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
        )
    }) { padding ->
        Column(
            Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Section("Reliability check")
            setup.forEach { item ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (item.ok) Icons.Outlined.CheckCircle else Icons.Outlined.ErrorOutline, null,
                        tint = if (item.ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    )
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Text(item.title)
                        Text(item.why, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (!item.ok) TextButton(onClick = item.fix) { Text("Fix") }
                }
            }

            HorizontalDivider()
            Section("Security")
            Toggle("Fingerprint / face unlock", "Strong biometrics, with your PIN as backup", s.biometricEnabled) { on ->
                if (on && biometricAvailable(activity)) {
                    promptBiometric(activity, "Confirm to enable") { s.biometricEnabled = true }
                } else {
                    s.biometricEnabled = false
                }
            }
            Text("Lock again after leaving the app for", style = MaterialTheme.typography.bodyMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(0 to "Immediately", 60 to "1 min", 300 to "5 min", 900 to "15 min").forEach { (sec, label) ->
                    FilterChip(s.lockTimeoutSeconds == sec, { s.lockTimeoutSeconds = sec }, label = { Text(label) })
                }
            }
            Toggle("Hide task names on the lock screen", "Notifications and the nag screen show details only after unlocking", s.hideOnLockScreen) {
                s.hideOnLockScreen = it
            }
            OutlinedButton(onClick = onChangePin) { Text("Change PIN") }

            HorizontalDivider()
            Section("Nagging")
            Priority.entries.forEach { p ->
                val policy = s.policy(p)
                Text("P${p.level} · ${p.label}", style = MaterialTheme.typography.titleSmall)
                Text("Remind every", style = MaterialTheme.typography.bodySmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(2, 5, 10, 15, 30, 60, 120).forEach { m ->
                        FilterChip(policy.repeatMinutes == m, { s.setPolicy(p, policy.copy(repeatMinutes = m)) }, label = { Text(if (m < 60) "${m}m" else "${m / 60}h") })
                    }
                }
                Toggle("Full-screen alarm when ignored", "After ${policy.escalateAfter} ignored reminders", policy.fullScreen) {
                    s.setPolicy(p, policy.copy(fullScreen = it))
                }
                if (policy.fullScreen) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(0, 1, 2, 3, 4, 6, 8).forEach { n ->
                            FilterChip(policy.escalateAfter == n, { s.setPolicy(p, policy.copy(escalateAfter = n)) }, label = { Text(if (n == 0) "At once" else "$n") })
                        }
                    }
                }
            }

            HorizontalDivider()
            Section("Quiet time")
            Toggle(
                "Follow Bedtime mode / Do Not Disturb",
                "Reminders wait while Bedtime mode or DND is on, then all come back when it ends",
                s.followBedtime,
            ) { s.followBedtime = it }
            Toggle("Extra quiet hours", "${fmt(s.quietStart)} – ${fmt(s.quietEnd)}", s.quietHoursEnabled) { s.quietHoursEnabled = it }
            if (s.quietHoursEnabled) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { editTime = "quietStart" }) { Text("From ${fmt(s.quietStart)}") }
                    OutlinedButton(onClick = { editTime = "quietEnd" }) { Text("Until ${fmt(s.quietEnd)}") }
                }
            }
            Toggle("P1 tasks break through quiet time", null, s.urgentBreaksQuiet) { s.urgentBreaksQuiet = it }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Start of day")
                    Text("When tasks with only a date begin nagging", style = MaterialTheme.typography.bodySmall)
                }
                OutlinedButton(onClick = { editTime = "dayStart" }) { Text(fmt(s.dayStart)) }
            }

            HorizontalDivider()
            Section("Driving")
            Toggle("Read reminders aloud in the car", "Only while Bluetooth audio (your car) is connected", s.speakNags) { s.speakNags = it }

            HorizontalDivider()
            Section("Privacy")
            val noInternet = remember {
                val info = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
                info.requestedPermissions?.none { it == Manifest.permission.INTERNET } ?: true
            }
            Text(
                if (noInternet) "✔ Verified: this app has no internet permission. Your data cannot leave the phone through it."
                else "⚠ This build requests internet access.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                "Database: AES-256 (SQLCipher), key sealed in the phone's hardware keystore. Android cloud backup is disabled; screenshots of the app are blocked.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    editTime?.let { which ->
        val current = when (which) {
            "quietStart" -> s.quietStart
            "quietEnd" -> s.quietEnd
            else -> s.dayStart
        }
        val state = rememberTimePickerState(current / 60, current % 60, is24Hour = true)
        AlertDialog(
            onDismissRequest = { editTime = null },
            confirmButton = {
                TextButton(onClick = {
                    val v = state.hour * 60 + state.minute
                    when (which) {
                        "quietStart" -> s.quietStart = v
                        "quietEnd" -> s.quietEnd = v
                        else -> s.dayStart = v
                    }
                    editTime = null
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { editTime = null }) { Text("Cancel") } },
            text = { TimePicker(state) },
        )
    }
}

private fun fmt(minutes: Int) = "%02d:%02d".format(minutes / 60, minutes % 60)

@Composable
private fun Section(title: String) {
    Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
}

@Composable
private fun Toggle(title: String, subtitle: String?, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title)
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        Switch(value, onChange)
    }
}

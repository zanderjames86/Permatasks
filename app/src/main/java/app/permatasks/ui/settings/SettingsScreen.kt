package app.permatasks.ui.settings

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Password
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.WbTwilight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import app.permatasks.container
import app.permatasks.domain.Priority
import app.permatasks.triggers.Bluetooth
import app.permatasks.ui.components.GroupCard
import app.permatasks.ui.components.ListRow
import app.permatasks.ui.components.SectionHeader
import app.permatasks.ui.format.Formats
import app.permatasks.ui.lock.biometricAvailable
import app.permatasks.ui.lock.promptBiometric
import app.permatasks.ui.tasks.ChoiceSheet
import app.permatasks.ui.theme.Palettes

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(onBack: () -> Unit, onChangePin: () -> Unit, onBackup: () -> Unit, onPlaces: () -> Unit) {
    val context = LocalContext.current
    val activity = context as FragmentActivity
    val c = context.container
    val s = c.settings
    @Suppress("UNUSED_VARIABLE") val tick by s.changes.collectAsState()
    var editTime by remember { mutableStateOf<String?>(null) }
    var choose by remember { mutableStateOf<String?>(null) }
    var policyFor by remember { mutableStateOf<Priority?>(null) }
    val setup = rememberSetupItems()
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    var permTick by remember { mutableIntStateOf(0) }
    val calendarPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { permTick++ }
    val btPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { if (it) choose = "car" }

    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text("Settings") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                scrollBehavior = scroll,
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(bottom = 48.dp)) {
            SectionHeader("Reliability")
            GroupCard {
                setup.forEach { item ->
                    ListRow(
                        item.title, item.why,
                        if (item.ok) Icons.Outlined.CheckCircle else Icons.Outlined.ErrorOutline,
                        if (item.ok) Color(0xFF2E9E5B) else MaterialTheme.colorScheme.error,
                        onClick = if (item.ok) null else item.fix,
                        trailing = { if (!item.ok) TextButton(onClick = item.fix) { Text("Fix") } },
                    )
                }
            }

            SectionHeader("Nagging")
            GroupCard {
                Priority.entries.forEach { p ->
                    val policy = s.policy(p)
                    ListRow(
                        "P${p.level} · ${p.label}",
                        "Every ${fmtMinutes(policy.repeatMinutes)}" + if (policy.fullScreen) " · full-screen alarm after ${policy.escalateAfter} ignored" else " · notification only",
                        Icons.Outlined.Timer, Color(Formats.priorityColor(p)), onClick = { policyFor = p }, showChevron = true,
                    )
                }
                ListRow("Start of day", "Date-only tasks start nagging at ${fmt(s.dayStart)}", Icons.Outlined.WbTwilight, onClick = { editTime = "dayStart" })
            }

            SectionHeader("Quiet time")
            GroupCard {
                SwitchRow("Follow Bedtime mode", "Hold reminders while Bedtime / Do Not Disturb is on; they all return when it ends", Icons.Outlined.Bedtime, s.followBedtime) { s.followBedtime = it }
                SwitchRow("Extra quiet hours", "${fmt(s.quietStart)} – ${fmt(s.quietEnd)}", Icons.Outlined.DarkMode, s.quietHoursEnabled) { s.quietHoursEnabled = it }
                if (s.quietHoursEnabled) {
                    Row(Modifier.padding(start = 72.dp, bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(false, { editTime = "quietStart" }, label = { Text("From ${fmt(s.quietStart)}") })
                        FilterChip(false, { editTime = "quietEnd" }, label = { Text("Until ${fmt(s.quietEnd)}") })
                    }
                }
                SwitchRow("P1 breaks through", "Urgent tasks still nag during quiet time", Icons.Outlined.ErrorOutline, s.urgentBreaksQuiet) { s.urgentBreaksQuiet = it }
            }

            SectionHeader("Driving")
            GroupCard {
                ListRow(
                    s.carName ?: "Choose your car", "When it connects: a Drive mode card and today's summary",
                    Icons.Outlined.DirectionsCar, Color(0xFF1E88E5),
                    onClick = { if (Bluetooth.hasPermission(context)) choose = "car" else btPerm.launch(Manifest.permission.BLUETOOTH_CONNECT) },
                    showChevron = true,
                )
                SwitchRow("Read reminders aloud", "Only while Bluetooth audio (your car) is connected", Icons.Outlined.RecordVoiceOver, s.speakNags) { s.speakNags = it }
            }

            SectionHeader("Triggers")
            GroupCard {
                ListRow("Places", "Home, office, shops… for location reminders", Icons.Outlined.Place, Color(0xFF43A047), onClick = onPlaces, showChevron = true)
                val calOk = remember(permTick) { c.calendar.hasPermission() }
                ListRow(
                    "Calendar access", if (calOk) "Allowed — events show in Upcoming and can be linked to tasks" else "Show events and link tasks to them",
                    Icons.Outlined.Event, Color(0xFF8E24AA),
                    trailing = { if (!calOk) TextButton(onClick = { calendarPerm.launch(Manifest.permission.READ_CALENDAR) }) { Text("Allow") } },
                )
            }

            SectionHeader("Security")
            GroupCard {
                SwitchRow("Fingerprint / face unlock", "With your PIN as backup", Icons.Outlined.Fingerprint, s.biometricEnabled) { on ->
                    if (on && biometricAvailable(activity)) promptBiometric(activity, "Confirm to enable") { s.biometricEnabled = true } else s.biometricEnabled = false
                }
                ListRow("Lock after", lockLabel(s.lockTimeoutSeconds), Icons.Outlined.Lock, onClick = { choose = "lock" }, showChevron = true)
                SwitchRow("Hide details on lock screen", "Notifications and the alarm screen show task names only after unlocking", Icons.Outlined.VisibilityOff, s.hideOnLockScreen) { s.hideOnLockScreen = it }
                ListRow("Change PIN", null, Icons.Outlined.Password, onClick = onChangePin, showChevron = true)
            }

            SectionHeader("Backup")
            GroupCard {
                ListRow(
                    "Encrypted backup",
                    if (s.lastBackupAt > 0) "Last backup ${Formats.date(s.lastBackupAt, true)}" + (s.lastBackupError?.let { " · failed: $it" } ?: "")
                    else "Not set up — protect your data against a lost phone",
                    Icons.Outlined.Backup, onClick = onBackup, showChevron = true,
                )
            }

            SectionHeader("Appearance")
            GroupCard {
                ListRow("Theme", listOf("Follow system", "Light", "Dark")[s.themeMode], Icons.Outlined.DarkMode, onClick = { choose = "theme" }, showChevron = true)
                ListRow("Colours", if (s.palette == "dynamic") "From your wallpaper" else Palettes.first { it.key == s.palette }.name, Icons.Outlined.Palette)
                FlowRow(Modifier.padding(start = 72.dp, end = 16.dp, bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Swatch(MaterialTheme.colorScheme.primary, s.palette == "dynamic", "Auto") { s.palette = "dynamic" }
                    Palettes.forEach { p -> Swatch(p.seed, s.palette == p.key, null) { s.palette = p.key } }
                }
                SwitchRow("Notes as a grid", null, Icons.Outlined.Palette, s.notesGrid) { s.notesGrid = it }
            }

            SectionHeader("Privacy")
            GroupCard {
                val noInternet = remember {
                    val info = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
                    info.requestedPermissions?.none { it == Manifest.permission.INTERNET } ?: true
                }
                ListRow(
                    if (noInternet) "No internet access — verified" else "This build requests internet access",
                    "The database (SQLCipher, AES-256) and images are encrypted with keys sealed in the phone's hardware keystore. Android cloud backup is off and screenshots are blocked.",
                    Icons.Outlined.Shield, if (noInternet) Color(0xFF2E9E5B) else MaterialTheme.colorScheme.error,
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "Permatasks ${context.packageManager.getPackageInfo(context.packageName, 0).versionName}",
                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
        }
    }

    policyFor?.let { p -> PolicySheet(p) { policyFor = null } }
    when (choose) {
        "theme" -> ChoiceSheet("Theme", listOf("Follow system", "Light", "Dark").map { Triple(it, null, MaterialTheme.colorScheme.onSurface) }, s.themeMode, { choose = null }) {
            s.themeMode = it; choose = null
        }
        "lock" -> {
            val opts = listOf(0, 60, 300, 900)
            ChoiceSheet("Lock after leaving the app", opts.map { Triple(lockLabel(it), null, MaterialTheme.colorScheme.onSurface) }, opts.indexOf(s.lockTimeoutSeconds), { choose = null }) {
                s.lockTimeoutSeconds = opts[it]; choose = null
            }
        }
        "car" -> {
            val devices = remember { Bluetooth.paired(context) }
            ChoiceSheet(
                "Your car",
                listOf(Triple("None", null as String?, MaterialTheme.colorScheme.onSurface)) + devices.map { Triple(it.name, it.address, MaterialTheme.colorScheme.onSurface) },
                (devices.indexOfFirst { it.address == s.carAddress } + 1),
                { choose = null },
            ) { i ->
                if (i == 0) { s.carAddress = null; s.carName = null } else { s.carAddress = devices[i - 1].address; s.carName = devices[i - 1].name }
                choose = null
            }
        }
    }
    editTime?.let { which ->
        val current = when (which) { "quietStart" -> s.quietStart; "quietEnd" -> s.quietEnd; else -> s.dayStart }
        val state = rememberTimePickerState(current / 60, current % 60, is24Hour = true)
        AlertDialog(
            onDismissRequest = { editTime = null },
            confirmButton = {
                TextButton(onClick = {
                    val v = state.hour * 60 + state.minute
                    when (which) { "quietStart" -> s.quietStart = v; "quietEnd" -> s.quietEnd = v; else -> s.dayStart = v }
                    editTime = null
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { editTime = null }) { Text("Cancel") } },
            text = { TimePicker(state) },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PolicySheet(p: Priority, onDismiss: () -> Unit) {
    val s = LocalContext.current.container.settings
    @Suppress("UNUSED_VARIABLE") val tick by s.changes.collectAsState()
    val policy = s.policy(p)
    app.permatasks.ui.tasks.AppSheet(onDismiss, "P${p.level} · ${p.label}") {
        Column(Modifier.padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Remind every", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(2, 5, 10, 15, 30, 60, 120, 240).forEach { m ->
                    FilterChip(policy.repeatMinutes == m, { s.setPolicy(p, policy.copy(repeatMinutes = m)) }, label = { Text(fmtMinutes(m)) })
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Full-screen alarm", style = MaterialTheme.typography.bodyLarge)
                    Text("Takes over the screen with a looping alarm when ignored", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(policy.fullScreen, { s.setPolicy(p, policy.copy(fullScreen = it)) })
            }
            if (policy.fullScreen) {
                Text("After this many ignored reminders", style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(0, 1, 2, 3, 4, 6, 8).forEach { n ->
                        FilterChip(policy.escalateAfter == n, { s.setPolicy(p, policy.copy(escalateAfter = n)) }, label = { Text(if (n == 0) "Straight away" else "$n") })
                    }
                }
            }
        }
    }
}

@Composable
private fun Swatch(color: Color, selected: Boolean, label: String?, onClick: () -> Unit) {
    Box(
        Modifier.size(40.dp).clip(CircleShape).background(color)
            .then(if (selected) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { if (label != null) Text("A", color = Color.White, style = MaterialTheme.typography.labelLarge) }
}

@Composable
fun SwitchRow(title: String, subtitle: String?, icon: androidx.compose.ui.graphics.vector.ImageVector, value: Boolean, onChange: (Boolean) -> Unit) {
    ListRow(title, subtitle, icon, onClick = { onChange(!value) }, trailing = { Switch(value, onChange) })
}

private fun lockLabel(sec: Int) = when (sec) { 0 -> "Immediately"; 60 -> "1 minute"; 300 -> "5 minutes"; else -> "${sec / 60} minutes" }
private fun fmtMinutes(m: Int) = if (m < 60) "$m min" else if (m % 60 == 0) "${m / 60} h" else "${m / 60} h ${m % 60} min"
private fun fmt(minutes: Int) = "%02d:%02d".format(minutes / 60, minutes % 60)

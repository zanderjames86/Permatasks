package app.permatasks.ui.tasks

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material.icons.outlined.Weekend
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import app.permatasks.container
import app.permatasks.data.PlaceEntity
import app.permatasks.data.TaskEntity
import app.permatasks.data.Trigger
import app.permatasks.domain.QuickAddParser
import app.permatasks.domain.Recurrence
import app.permatasks.triggers.Bluetooth
import app.permatasks.triggers.CalendarEvent
import app.permatasks.triggers.PairedDevice
import app.permatasks.ui.components.ListRow
import app.permatasks.ui.format.Formats
import app.permatasks.ui.settings.PlaceEditorDialog
import app.permatasks.ui.theme.AppColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppSheet(onDismiss: () -> Unit, title: String, content: @Composable ColumnScope.() -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(Modifier.navigationBarsPadding().imePadding().padding(bottom = 16.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
            content()
        }
    }
}

/** "When?" — quick dates, typed dates, a calendar, and (for nags) snooze options. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ScheduleSheet(
    dueAt: Long?,
    hasTime: Boolean,
    showSnooze: Boolean,
    onDismiss: () -> Unit,
    onPick: (Long?, Boolean) -> Unit,
    onSnooze: ((Int) -> Unit)? = null,
) {
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    val keep = dueAt?.takeIf { hasTime }?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalTime() }
    fun at(d: LocalDate) = onPick((d.atTime(keep ?: LocalTime.MIDNIGHT)).atZone(zone).toInstant().toEpochMilli(), keep != null)
    var typed by remember { mutableStateOf("") }
    val parsed = remember(typed) { if (typed.isBlank()) null else QuickAddParser.parse(typed, ZonedDateTime.now(zone)).takeIf { it.due != null } }
    var pickDate by remember { mutableStateOf(false) }
    var pickTimeFor by remember { mutableStateOf<LocalDate?>(null) }
    val fmt = DateTimeFormatter.ofPattern("EEE")

    AppSheet(onDismiss, "When?") {
        OutlinedTextField(
            typed, { typed = it }, Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            placeholder = { Text("Type a date, e.g. fri 5pm, in 2 hours") }, singleLine = true,
            shape = RoundedCornerShape(16.dp),
            supportingText = { parsed?.due?.let { Text("→ " + Formats.date(it.toInstant().toEpochMilli(), parsed.hasTime)) } },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { parsed?.due?.let { onPick(it.toInstant().toEpochMilli(), parsed.hasTime) } }),
        )
        ListRow("Today", today.format(fmt), Icons.Outlined.Today, AppColors.Success, onClick = { at(today) })
        ListRow("Tomorrow", today.plusDays(1).format(fmt), Icons.Outlined.WbSunny, AppColors.Warning, onClick = { at(today.plusDays(1)) })
        val sat = today.with(TemporalAdjusters.next(DayOfWeek.SATURDAY))
        ListRow("This weekend", sat.format(DateTimeFormatter.ofPattern("EEE d MMM")), Icons.Outlined.Weekend, AppColors.Snooze, onClick = { at(sat) })
        val mon = today.with(TemporalAdjusters.next(DayOfWeek.MONDAY))
        ListRow("Next week", mon.format(DateTimeFormatter.ofPattern("EEE d MMM")), Icons.Outlined.DateRange, MaterialTheme.colorScheme.primary, onClick = { at(mon) })
        ListRow("Pick date & time…", null, Icons.Outlined.CalendarMonth, MaterialTheme.colorScheme.primary, onClick = { pickDate = true })
        if (dueAt != null) ListRow("No date", null, Icons.Outlined.Block, MaterialTheme.colorScheme.onSurfaceVariant, onClick = { onPick(null, false) })
        if (showSnooze && onSnooze != null) {
            Text("Or just remind me again in", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(start = 24.dp, top = 12.dp, bottom = 4.dp))
            FlowRow(Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(15 to "15 min", 60 to "1 hour", 180 to "3 hours").forEach { (m, l) ->
                    SuggestionChip(onClick = { onSnooze(m) }, label = { Text(l) })
                }
            }
        }
    }

    if (pickDate) {
        val initial = (dueAt?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() } ?: today).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val state = rememberDatePickerState(initialSelectedDateMillis = initial)
        DatePickerDialog(
            onDismissRequest = { pickDate = false },
            confirmButton = {
                TextButton(onClick = {
                    pickDate = false
                    state.selectedDateMillis?.let { pickTimeFor = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
                }) { Text("Next") }
            },
            dismissButton = { TextButton(onClick = { pickDate = false }) { Text("Cancel") } },
        ) { DatePicker(state) }
    }
    pickTimeFor?.let { date ->
        val state = rememberTimePickerState(keep?.hour ?: 9, keep?.minute ?: 0, is24Hour = true)
        AlertDialog(
            onDismissRequest = { pickTimeFor = null },
            title = { Text("Time") },
            text = { TimePicker(state) },
            confirmButton = {
                TextButton(onClick = {
                    onPick(date.atTime(state.hour, state.minute).atZone(zone).toInstant().toEpochMilli(), true)
                    pickTimeFor = null
                }) { Text("Set") }
            },
            dismissButton = {
                TextButton(onClick = {
                    onPick(date.atStartOfDay(zone).toInstant().toEpochMilli(), false)
                    pickTimeFor = null
                }) { Text("All day") }
            },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RepeatSheet(current: Recurrence?, onDismiss: () -> Unit, onPick: (Recurrence?) -> Unit) {
    var custom by remember { mutableStateOf("") }
    var fromCompletion by remember { mutableStateOf(current?.fromCompletion ?: false) }
    val presets = listOf(
        "Every day" to Recurrence(Recurrence.Unit.DAY),
        "Every weekday (Mon–Fri)" to Recurrence(Recurrence.Unit.WEEK, weekdays = Recurrence.WORKDAYS),
        "Every week" to Recurrence(Recurrence.Unit.WEEK),
        "Every 2 weeks" to Recurrence(Recurrence.Unit.WEEK, 2),
        "Every month" to Recurrence(Recurrence.Unit.MONTH),
        "Every year" to Recurrence(Recurrence.Unit.YEAR),
    )
    AppSheet(onDismiss, "Repeat") {
        presets.forEach { (label, r) ->
            ListRow(
                label, null, onClick = { onPick(r.copy(fromCompletion = fromCompletion)) },
                trailing = { if (current?.copy(fromCompletion = false) == r) Text("✓", color = MaterialTheme.colorScheme.primary) },
            )
        }
        Text("Specific days", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(start = 24.dp, top = 8.dp))
        var days by remember { mutableStateOf(current?.weekdays ?: emptySet()) }
        FlowRow(Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            DayOfWeek.entries.forEach { d ->
                FilterChip(d in days, { days = if (d in days) days - d else days + d }, label = { Text(d.name.take(2).lowercase().replaceFirstChar(Char::uppercase)) })
            }
        }
        if (days.isNotEmpty()) {
            TextButton(onClick = { onPick(Recurrence(Recurrence.Unit.WEEK, weekdays = days, fromCompletion = fromCompletion)) }, modifier = Modifier.padding(start = 12.dp)) {
                Text("Use these days")
            }
        }
        OutlinedTextField(
            custom, { custom = it }, Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), singleLine = true,
            shape = RoundedCornerShape(16.dp),
            placeholder = { Text("Custom: \"every 3 days\", \"every! 2 weeks\"") },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { QuickAddParser.parse(custom, ZonedDateTime.now()).recurrence?.let(onPick) }),
        )
        FilterChip(
            fromCompletion, { fromCompletion = !fromCompletion },
            label = { Text("Count from when I complete it") }, modifier = Modifier.padding(horizontal = 20.dp),
        )
        if (current != null) ListRow("Don't repeat", null, Icons.Outlined.Block, MaterialTheme.colorScheme.error, onClick = { onPick(null) })
    }
}

/** Location trigger: pick a saved place (or add one) and arrive / leave. */
@Composable
fun PlaceSheet(task: TaskEntity, onDismiss: () -> Unit, onPick: (placeId: String?, trigger: Int) -> Unit) {
    val c = LocalContext.current.container
    val places by remember { c.repo.places.observeAll() }.collectAsState(initial = emptyList())
    var trigger by remember { mutableIntStateOf(if (task.placeTrigger == Trigger.LEAVE) Trigger.LEAVE else Trigger.ARRIVE) }
    var adding by remember { mutableStateOf(false) }
    var permissionTick by remember { mutableIntStateOf(0) }
    val background = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { permissionTick++ }
    val foreground = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        if (r[Manifest.permission.ACCESS_FINE_LOCATION] == true) background.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        permissionTick++
    }
    val ok = remember(permissionTick) { c.geofences.hasPermission() }

    AppSheet(onDismiss, "Remind me at a place") {
        if (!ok) {
            Text(
                "Location reminders need location access \"All the time\" so they work while the app is closed. Geofencing runs in Google Play services; Permatasks itself stays offline.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
            )
            Button(
                onClick = { foreground.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)) },
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            ) { Text("Allow location") }
        }
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp)) {
            SegmentedButton(trigger == Trigger.ARRIVE, { trigger = Trigger.ARRIVE }, SegmentedButtonDefaults.itemShape(0, 2)) { Text("When I arrive") }
            SegmentedButton(trigger == Trigger.LEAVE, { trigger = Trigger.LEAVE }, SegmentedButtonDefaults.itemShape(1, 2)) { Text("When I leave") }
        }
        LazyColumn(Modifier.heightIn(max = 320.dp)) {
            items(places, key = { it.id }) { p ->
                ListRow(
                    p.name, p.address.ifBlank { "%.4f, %.4f · %dm".format(p.latitude, p.longitude, p.radiusMeters) },
                    Icons.Outlined.Place, MaterialTheme.colorScheme.primary,
                    onClick = { onPick(p.id, trigger) },
                    trailing = { if (task.placeId == p.id) Text("✓", color = MaterialTheme.colorScheme.primary) },
                )
            }
        }
        TextButton(onClick = { adding = true }, modifier = Modifier.padding(horizontal = 12.dp)) { Text("+ Add a place") }
        if (task.placeId != null) ListRow("Remove location", null, Icons.Outlined.Block, MaterialTheme.colorScheme.error, onClick = { onPick(null, Trigger.NONE) })
    }
    if (adding) PlaceEditorDialog(null, onDismiss = { adding = false }, onSaved = { p: PlaceEntity -> adding = false; onPick(p.id, trigger) })
}

@Composable
fun BluetoothSheet(task: TaskEntity, onDismiss: () -> Unit, onPick: (PairedDevice?) -> Unit) {
    val context = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { tick++ }
    val devices = remember(tick) { Bluetooth.paired(context) }
    AppSheet(onDismiss, "When this device connects") {
        if (!Bluetooth.hasPermission(context)) {
            Button(onClick = { permission.launch(Manifest.permission.BLUETOOTH_CONNECT) }, modifier = Modifier.padding(horizontal = 24.dp)) {
                Text("Allow nearby devices")
            }
        } else if (devices.isEmpty()) {
            Text("No paired devices. Pair your car or headphones in Android settings first.", modifier = Modifier.padding(24.dp))
        }
        LazyColumn(Modifier.heightIn(max = 360.dp)) {
            items(devices, key = { it.address }) { d ->
                ListRow(
                    d.name, d.address, Icons.Outlined.Bluetooth, Color(0xFF1E88E5), onClick = { onPick(d) },
                    trailing = { if (task.bluetoothAddress == d.address) Text("✓", color = MaterialTheme.colorScheme.primary) },
                )
            }
        }
        if (task.bluetoothAddress != null) ListRow("Remove", null, Icons.Outlined.Block, MaterialTheme.colorScheme.error, onClick = { onPick(null) })
    }
}

@Composable
fun EventSheet(task: TaskEntity, onDismiss: () -> Unit, onPick: (CalendarEvent?) -> Unit) {
    val c = LocalContext.current.container
    var tick by remember { mutableIntStateOf(0) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { tick++ }
    var events by remember { mutableStateOf<List<CalendarEvent>>(emptyList()) }
    LaunchedEffect(tick) {
        events = withContext(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            c.calendar.events(now, now + 30L * 24 * 3600_000).distinctBy { it.eventId to it.begin }
        }
    }
    AppSheet(onDismiss, "Link to a calendar event") {
        if (!c.calendar.hasPermission()) {
            Text(
                "The task's due time will follow the event, even if the event moves.",
                modifier = Modifier.padding(horizontal = 24.dp), style = MaterialTheme.typography.bodyMedium,
            )
            Button(onClick = { permission.launch(Manifest.permission.READ_CALENDAR) }, modifier = Modifier.padding(24.dp)) { Text("Allow calendar") }
        }
        LazyColumn(Modifier.heightIn(max = 420.dp)) {
            items(events, key = { "${it.eventId}_${it.begin}" }) { e ->
                ListRow(
                    e.title, Formats.date(e.begin, !e.allDay), Icons.Outlined.Event, Color(e.color or 0xFF000000.toInt()),
                    onClick = { onPick(e) },
                    trailing = { if (task.eventId == e.eventId) Text("✓", color = MaterialTheme.colorScheme.primary) },
                )
            }
        }
        if (task.eventId != null) ListRow("Unlink event", null, Icons.Outlined.Block, MaterialTheme.colorScheme.error, onClick = { onPick(null) })
    }
}

@Composable
fun DependencySheet(task: TaskEntity, onDismiss: () -> Unit, onPick: (TaskEntity?) -> Unit) {
    val repo = LocalContext.current.container.repo
    val open by remember { repo.tasks.observeOpen() }.collectAsState(initial = emptyList())
    var q by remember { mutableStateOf("") }
    val candidates = open.filter { it.id != task.id && it.blockedBy != task.id && (q.isBlank() || it.title.contains(q, true)) }.take(50)
    AppSheet(onDismiss, "Start after another task") {
        Text(
            "This task stays quiet until the chosen task is done, then starts nagging.",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
        OutlinedTextField(q, { q = it }, Modifier.fillMaxWidth().padding(16.dp), placeholder = { Text("Search tasks") }, singleLine = true, shape = RoundedCornerShape(16.dp))
        LazyColumn(Modifier.heightIn(max = 380.dp)) {
            items(candidates, key = { it.id }) { t ->
                ListRow(
                    t.title, t.dueAt?.let { Formats.date(it, t.hasTime) }, Icons.Outlined.Link, MaterialTheme.colorScheme.primary,
                    onClick = { onPick(t) },
                    trailing = { if (task.blockedBy == t.id) Text("✓", color = MaterialTheme.colorScheme.primary) },
                )
            }
        }
        if (task.blockedBy != null) ListRow("Remove dependency", null, Icons.Outlined.Block, MaterialTheme.colorScheme.error, onClick = { onPick(null) })
    }
}

@Composable
fun ChoiceSheet(title: String, options: List<Triple<String, String?, Color>>, selected: Int, onDismiss: () -> Unit, onPick: (Int) -> Unit) {
    AppSheet(onDismiss, title) {
        Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
            options.forEachIndexed { i, (label, sub, color) ->
                ListRow(
                    label, sub, null, color, onClick = { onPick(i) }, titleColor = color,
                    trailing = { if (i == selected) Text("✓", color = MaterialTheme.colorScheme.primary) },
                )
            }
        }
        Row {}
    }
}

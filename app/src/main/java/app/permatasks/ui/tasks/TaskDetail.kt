package app.permatasks.ui.tasks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import app.permatasks.container
import app.permatasks.data.ActivityEntity
import app.permatasks.data.TaskEntity
import app.permatasks.data.TaskStatus
import app.permatasks.domain.Priority
import app.permatasks.domain.QuickAddParser
import app.permatasks.domain.Recurrence
import app.permatasks.ui.format.Formats
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TaskDetailScreen(taskId: String, onBack: () -> Unit, onOpenTask: (String) -> Unit) {
    val repo = LocalContext.current.container.repo
    val scope = rememberCoroutineScope()
    val stored by remember(taskId) { repo.tasks.observe(taskId) }.collectAsState(initial = null)
    val subtasks by remember(taskId) { repo.tasks.observeSubtasks(taskId) }.collectAsState(initial = emptyList())
    val history by remember(taskId) { repo.activity.observeFor(taskId) }.collectAsState(initial = emptyList())
    val projects by remember { repo.projects.observeAll() }.collectAsState(initial = emptyList())

    var draft by remember(taskId) { mutableStateOf<TaskEntity?>(null) }
    LaunchedEffect(stored) { if (draft == null || stored?.updatedAt != draft?.updatedAt) stored?.let { draft = it } }
    val task = draft ?: return

    fun update(t: TaskEntity) { draft = t }
    fun persist(then: () -> Unit = {}) = scope.launch { repo.save(task); then() }

    val zone = ZoneId.systemDefault()
    var showDate by remember { mutableStateOf(false) }
    var showTime by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (task.status == TaskStatus.OPEN) "Task" else "Closed task") },
                navigationIcon = { IconButton(onClick = { persist(onBack) }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = { IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Outlined.Delete, "Delete") } },
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(task.title, { update(task.copy(title = it)) }, Modifier.fillMaxWidth(), label = { Text("Title") })
            OutlinedTextField(task.description, { update(task.copy(description = it)) }, Modifier.fillMaxWidth(), label = { Text("Notes") }, minLines = 3)

            Text("When", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = { showDate = true }) { Text(task.dueAt?.let { Formats.date(it, false) } ?: "Set date") }
                if (task.dueAt != null) {
                    OutlinedButton(onClick = { showTime = true }) {
                        Text(if (task.hasTime) Instant.ofEpochMilli(task.dueAt).atZone(zone).toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm")) else "Add time")
                    }
                    TextButton(onClick = { update(task.copy(dueAt = null, hasTime = false, recurrence = null)) }) { Text("Clear") }
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("Today" to 0L, "Tomorrow" to 1L, "Next week" to 7L).forEach { (label, days) ->
                    FilterChip(selected = false, onClick = {
                        val keepTime = task.dueAt?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalTime() } ?: LocalTime.of(9, 0)
                        val d = LocalDate.now(zone).plusDays(days).atTime(keepTime).atZone(zone)
                        update(task.copy(dueAt = d.toInstant().toEpochMilli()))
                    }, label = { Text(label) })
                }
            }

            RepeatEditor(task.recurrence) { update(task.copy(recurrence = it?.encode())) }

            Text("Priority", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Priority.entries.forEach { p ->
                    FilterChip(selected = task.priority == p.level, onClick = { update(task.copy(priority = p.level)) }, label = { Text("P${p.level}") })
                }
            }

            ProjectPicker(projects.map { it.id to it.name }, task.projectId) { update(task.copy(projectId = it)) }

            OutlinedTextField(
                task.labels, { update(task.copy(labels = it.replace("@", ""))) }, Modifier.fillMaxWidth(),
                label = { Text("Labels (comma separated)") }, singleLine = true,
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Keep nagging until done", style = MaterialTheme.typography.bodyLarge)
                    val policy = LocalContext.current.container.settings.policy(Priority.fromLevel(task.priority))
                    Text(
                        "Every ${policy.repeatMinutes} min" + if (policy.fullScreen) ", full-screen after ${policy.escalateAfter} ignored" else "",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(task.nagEnabled, { update(task.copy(nagEnabled = it)) })
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Start early:", style = MaterialTheme.typography.bodyMedium)
                listOf(0 to "On time", 10 to "10m", 30 to "30m", 60 to "1h").forEach { (m, label) ->
                    FilterChip(selected = task.remindBeforeMinutes == m, onClick = { update(task.copy(remindBeforeMinutes = m)) }, label = { Text(label) })
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { persist(onBack) }) { Text("Save") }
                if (task.status == TaskStatus.OPEN) {
                    OutlinedButton(onClick = { scope.launch { repo.save(task); repo.complete(task.id); onBack() } }) { Text("Complete") }
                    TextButton(onClick = { scope.launch { repo.abandon(task.id); onBack() } }) { Text("Won't do") }
                } else {
                    OutlinedButton(onClick = { scope.launch { repo.reopen(task.id) } }) { Text("Reopen") }
                }
            }

            HorizontalDivider()
            Text("Sub-tasks", style = MaterialTheme.typography.titleSmall)
            subtasks.forEach { sub ->
                TaskRow(sub, null, onOpen = { onOpenTask(sub.id) }, onComplete = { repo.complete(it.id) }, showProject = false)
            }
            var subText by remember { mutableStateOf("") }
            OutlinedTextField(
                subText, { subText = it }, Modifier.fillMaxWidth(), singleLine = true,
                placeholder = { Text("Add sub-task (dates work too)") },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = {
                    val v = subText
                    subText = ""
                    scope.launch { repo.quickAdd(v, task.projectId, parentId = task.id) }
                }),
            )

            if (history.isNotEmpty()) {
                HorizontalDivider()
                Text("History", style = MaterialTheme.typography.titleSmall)
                history.forEach { HistoryRow(it) }
            }
            Spacer(Modifier.width(1.dp).padding(bottom = 48.dp))
        }
    }

    if (showDate) {
        val initial = (task.dueAt?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() } ?: LocalDate.now(zone))
            .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val state = rememberDatePickerState(initialSelectedDateMillis = initial)
        DatePickerDialog(
            onDismissRequest = { showDate = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { utc ->
                        val date = Instant.ofEpochMilli(utc).atZone(ZoneOffset.UTC).toLocalDate()
                        val time = task.dueAt?.takeIf { task.hasTime }?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalTime() } ?: LocalTime.of(9, 0)
                        update(task.copy(dueAt = date.atTime(time).atZone(zone).toInstant().toEpochMilli()))
                    }
                    showDate = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showDate = false }) { Text("Cancel") } },
        ) { DatePicker(state) }
    }
    if (showTime && task.dueAt != null) {
        val current = Instant.ofEpochMilli(task.dueAt).atZone(zone)
        val state = rememberTimePickerState(if (task.hasTime) current.hour else 9, if (task.hasTime) current.minute else 0, is24Hour = true)
        AlertDialog(
            onDismissRequest = { showTime = false },
            confirmButton = {
                TextButton(onClick = {
                    val d = current.toLocalDate().atTime(state.hour, state.minute).atZone(zone)
                    update(task.copy(dueAt = d.toInstant().toEpochMilli(), hasTime = true))
                    showTime = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { update(task.copy(hasTime = false)); showTime = false }) { Text("All day") }
            },
            text = { TimePicker(state) },
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete task?") },
            text = { Text("This removes the task, its sub-tasks and its history permanently.") },
            confirmButton = { TextButton(onClick = { scope.launch { repo.delete(task.id); onBack() } }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RepeatEditor(encoded: String?, onChange: (Recurrence?) -> Unit) {
    val rule = Recurrence.decode(encoded)
    var custom by remember { mutableStateOf("") }
    Text("Repeat: ${rule?.describe() ?: "never"}", style = MaterialTheme.typography.titleSmall)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        val presets = listOf(
            "Never" to null,
            "Daily" to Recurrence(Recurrence.Unit.DAY),
            "Weekdays" to Recurrence(Recurrence.Unit.WEEK, weekdays = Recurrence.WORKDAYS),
            "Weekly" to Recurrence(Recurrence.Unit.WEEK),
            "Monthly" to Recurrence(Recurrence.Unit.MONTH),
            "Yearly" to Recurrence(Recurrence.Unit.YEAR),
        )
        presets.forEach { (label, r) ->
            FilterChip(selected = rule == r, onClick = { onChange(r) }, label = { Text(label) })
        }
        if (rule != null) {
            FilterChip(
                selected = rule.fromCompletion,
                onClick = { onChange(rule.copy(fromCompletion = !rule.fromCompletion)) },
                label = { Text("Count from completion") },
            )
        }
    }
    OutlinedTextField(
        custom, { custom = it }, Modifier.fillMaxWidth(), singleLine = true,
        placeholder = { Text("Custom, e.g. \"every 2 weeks\" or \"every mon, thu\"") },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = {
            QuickAddParser.parse(custom, ZonedDateTime.now()).recurrence?.let(onChange)
            custom = ""
        }),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProjectPicker(options: List<Pair<String, String>>, selected: String?, onSelect: (String?) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val name = options.firstOrNull { it.first == selected }?.second ?: "Inbox"
    ExposedDropdownMenuBox(expanded, { expanded = it }) {
        OutlinedTextField(
            name, {}, readOnly = true, label = { Text("Project") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded, { expanded = false }) {
            DropdownMenuItem(text = { Text("Inbox") }, onClick = { onSelect(null); expanded = false })
            options.forEach { (id, n) -> DropdownMenuItem(text = { Text(n) }, onClick = { onSelect(id); expanded = false }) }
        }
    }
}

@Composable
private fun HistoryRow(entry: ActivityEntity) {
    val time = Formats.date(entry.at, true)
    Text(
        "$time — ${entry.type}${if (entry.detail.isNotBlank()) " (${entry.detail})" else ""}",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

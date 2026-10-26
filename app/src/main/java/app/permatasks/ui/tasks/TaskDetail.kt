package app.permatasks.ui.tasks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Notes
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Label
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import app.permatasks.container
import app.permatasks.data.ActivityEntity
import app.permatasks.data.ActivityType
import app.permatasks.data.TaskEntity
import app.permatasks.data.TaskStatus
import app.permatasks.data.Trigger
import app.permatasks.domain.Priority
import app.permatasks.domain.Recurrence
import app.permatasks.ui.components.GroupCard
import app.permatasks.ui.components.ListRow
import app.permatasks.ui.components.SectionHeader
import app.permatasks.ui.format.Formats
import app.permatasks.ui.theme.AppColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class Sheet { DATE, REPEAT, PRIORITY, PROJECT, PLACE, BLUETOOTH, EVENT, DEPENDENCY }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TaskDetailScreen(taskId: String, onBack: () -> Unit, onOpenTask: (String) -> Unit, onOpenNote: (String) -> Unit) {
    val c = LocalContext.current.container
    val repo = c.repo
    val scope = rememberCoroutineScope()
    val stored by remember(taskId) { repo.tasks.observe(taskId) }.collectAsState(initial = null)
    val subtasks by remember(taskId) { repo.tasks.observeSubtasks(taskId) }.collectAsState(initial = emptyList())
    val history by remember(taskId) { repo.activity.observeFor(taskId) }.collectAsState(initial = emptyList())
    val projects by remember { repo.projects.observeAll() }.collectAsState(initial = emptyList())
    val sections by remember { repo.sections.observeAll() }.collectAsState(initial = emptyList())
    val places by remember { repo.places.observeAll() }.collectAsState(initial = emptyList())

    var draft by remember(taskId) { mutableStateOf<TaskEntity?>(null) }
    var dirty by remember { mutableStateOf(false) }
    // Plain holder, updated synchronously, so leaving the screen saves exactly what's pending.
    val pending = remember { PendingEdit() }
    LaunchedEffect(stored) { if (!dirty) stored?.let { draft = it } }
    // Autosave shortly after the last edit.
    LaunchedEffect(draft, dirty) {
        if (dirty) {
            delay(500)
            pending.take()?.let { repo.save(it) }
            dirty = false
        }
    }
    DisposableEffect(Unit) {
        onDispose { pending.take()?.let { c.scope.launch { repo.save(it) } } }
    }

    val task = draft ?: return
    fun update(t: TaskEntity) { draft = t; dirty = true; pending.task = t }
    /** Saves pending edits first, then runs a state change (complete, skip...). */
    fun finish(block: suspend () -> Unit) {
        val edit = pending.take()
        dirty = false
        scope.launch { edit?.let { repo.save(it) }; block() }
    }
    var sheet by remember { mutableStateOf<Sheet?>(null) }
    var menu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var editLabels by remember { mutableStateOf<String?>(null) }
    var showHistory by remember { mutableStateOf(false) }
    val open = task.status == TaskStatus.OPEN
    val priority = Priority.fromLevel(task.priority)
    val pColor = Color(Formats.priorityColor(priority))

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(projects.firstOrNull { it.id == task.projectId }?.name ?: "Inbox", style = MaterialTheme.typography.titleMedium) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "More") }
                        DropdownMenu(menu, { menu = false }) {
                            DropdownMenuItem(text = { Text("Duplicate") }, leadingIcon = { Icon(Icons.Outlined.ContentCopy, null) }, onClick = {
                                menu = false
                                finish { repo.duplicate(task.id)?.let { onOpenTask(it.id) } }
                            })
                            DropdownMenuItem(text = { Text("Delete") }, leadingIcon = { Icon(Icons.Outlined.Delete, null) }, onClick = { menu = false; confirmDelete = true })
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
                Row(
                    Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (open) {
                        TextButton(onClick = { finish { repo.abandon(task.id, "Closed as won't do"); onBack() } }) { Text("Won't do") }
                        Spacer(Modifier.weight(1f))
                        if (task.recurrence != null) FilledTonalButton(onClick = { finish { repo.skip(task.id) } }) { Text("Skip") }
                        Button(onClick = { finish { repo.complete(task.id); onBack() } }) { Text("Complete") }
                    } else {
                        Text(
                            (if (task.status == TaskStatus.DONE) "Completed " else "Closed ") + (task.completedAt?.let { Formats.date(it, true) } ?: ""),
                            Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium,
                        )
                        Button(onClick = { finish { repo.reopen(task.id) } }) { Text("Reopen") }
                    }
                }
            }
        },
    ) { padding ->
        LazyColumn(Modifier.padding(padding)) {
            item {
                Row(Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp), verticalAlignment = Alignment.Top) {
                    Box(Modifier.padding(top = 6.dp)) { PriorityCheck(task) { finish { repo.complete(task.id); onBack() } } }
                    Spacer(Modifier.width(16.dp))
                    BasicTextField(
                        task.title, { update(task.copy(title = it.replace("\n", " "))) },
                        textStyle = MaterialTheme.typography.headlineSmall.copy(color = MaterialTheme.colorScheme.onSurface),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                        modifier = Modifier.weight(1f),
                    )
                }
                Box(Modifier.padding(start = 60.dp, end = 20.dp, top = 8.dp, bottom = 8.dp)) {
                    if (task.description.isEmpty()) Text("Add details", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    BasicTextField(
                        task.description, { update(task.copy(description = it)) },
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            if (open && (task.nagCount > 0 || task.snoozeCount > 0)) {
                item {
                    Surface(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.errorContainer,
                    ) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.NotificationsActive, null, tint = MaterialTheme.colorScheme.onErrorContainer)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text("Still waiting on you", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onErrorContainer)
                                Text(
                                    listOfNotNull(
                                        "reminded ${task.nagCount}×".takeIf { task.nagCount > 0 },
                                        "snoozed ${task.snoozeCount}×".takeIf { task.snoozeCount > 0 },
                                        task.nextNagAt?.let { "next at ${Formats.time(it)}" },
                                    ).joinToString(" · "),
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer,
                                )
                            }
                        }
                    }
                }
            }

            item {
                GroupCard {
                    ListRow(
                        task.dueAt?.let { Formats.date(it, task.hasTime) } ?: "Add date",
                        task.eventTitle?.let { "Follows event: $it" },
                        Icons.Outlined.CalendarToday, if (task.dueAt != null) dateColor(task.dueAt, task.hasTime, !open) else MaterialTheme.colorScheme.onSurfaceVariant,
                        onClick = { sheet = Sheet.DATE },
                    )
                    ListRow(
                        Recurrence.decode(task.recurrence)?.describe()?.replaceFirstChar(Char::uppercase) ?: "Doesn't repeat", null,
                        Icons.Outlined.Repeat, MaterialTheme.colorScheme.primary, onClick = { sheet = Sheet.REPEAT },
                    )
                    ListRow("Priority ${priority.level} · ${priority.label}", null, Icons.Outlined.Flag, pColor, onClick = { sheet = Sheet.PRIORITY })
                    val project = projects.firstOrNull { it.id == task.projectId }
                    val section = sections.firstOrNull { it.id == task.sectionId }
                    ListRow(
                        (project?.name ?: "Inbox") + (section?.let { " / ${it.name}" } ?: ""), null, Icons.Outlined.Folder,
                        project?.let { Color(it.color) } ?: MaterialTheme.colorScheme.primary, onClick = { sheet = Sheet.PROJECT },
                    )
                    ListRow(
                        if (task.labelList.isEmpty()) "Add labels" else task.labelList.joinToString("  ") { "@$it" }, null,
                        Icons.Outlined.Label, MaterialTheme.colorScheme.tertiary, onClick = { editLabels = task.labels },
                    )
                }
            }

            item { SectionHeader("Reminders") }
            item {
                GroupCard {
                    val policy = c.settings.policy(priority)
                    ListRow(
                        "Keep nagging until done",
                        "Every ${policy.repeatMinutes} min" + if (policy.fullScreen) " · full-screen after ${policy.escalateAfter} ignored" else "",
                        Icons.Outlined.NotificationsActive, MaterialTheme.colorScheme.error,
                        onClick = { update(task.copy(nagEnabled = !task.nagEnabled)) },
                        trailing = { Switch(task.nagEnabled, { update(task.copy(nagEnabled = it)) }) },
                    )
                    if (task.nagEnabled && task.dueAt != null) {
                        FlowRow(Modifier.padding(start = 72.dp, end = 16.dp, bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(0 to "On time", 10 to "10 min early", 30 to "30 min early", 60 to "1 h early", 1440 to "1 day early").forEach { (m, l) ->
                                FilterChip(task.remindBeforeMinutes == m, { update(task.copy(remindBeforeMinutes = m)) }, label = { Text(l) })
                            }
                        }
                    }
                    val place = places.firstOrNull { it.id == task.placeId }
                    ListRow(
                        place?.let { (if (task.placeTrigger == Trigger.LEAVE) "When I leave " else "When I arrive at ") + it.name } ?: "Location reminder",
                        if (place == null) "Arrive or leave a place" else null,
                        Icons.Outlined.Place, Color(0xFF43A047), onClick = { sheet = Sheet.PLACE },
                    )
                    ListRow(
                        task.bluetoothName?.let { "When $it connects" } ?: "Bluetooth reminder",
                        if (task.bluetoothAddress == null) "e.g. when you get into the car" else null,
                        Icons.Outlined.Bluetooth, Color(0xFF1E88E5), onClick = { sheet = Sheet.BLUETOOTH },
                    )
                    ListRow(
                        task.eventTitle ?: "Link to calendar event", if (task.eventId == null) "Due time follows the event" else null,
                        Icons.Outlined.Event, Color(0xFF8E24AA), onClick = { sheet = Sheet.EVENT },
                    )
                    val blocker = task.blockedBy?.let { id -> subtasks.firstOrNull { it.id == id } }
                    var blockerTitle by remember(task.blockedBy) { mutableStateOf(blocker?.title) }
                    LaunchedEffect(task.blockedBy) { blockerTitle = task.blockedBy?.let { repo.tasks.get(it)?.title } }
                    ListRow(
                        blockerTitle?.let { "After \"$it\" is done" } ?: "Start after another task",
                        if (task.blockedBy == null) "Dependencies" else null,
                        Icons.Outlined.Link, AppColors.Snooze, onClick = { sheet = Sheet.DEPENDENCY },
                    )
                    task.noteId?.let { nid ->
                        ListRow("Open linked note", null, Icons.AutoMirrored.Outlined.Notes, MaterialTheme.colorScheme.primary, onClick = { onOpenNote(nid) }, showChevron = true)
                    }
                }
            }

            item { SectionHeader("Sub-tasks", subtasks.count { it.status == TaskStatus.OPEN }.takeIf { subtasks.isNotEmpty() }) }
            items(subtasks, key = { it.id }) { sub ->
                TaskRow(sub, null, onOpen = { onOpenTask(sub.id) }, onComplete = { scope.launch { repo.complete(it.id) } }, showProject = false)
            }
            item {
                var subText by remember { mutableStateOf("") }
                OutlinedTextField(
                    subText, { subText = it }, Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    placeholder = { Text("+ Add sub-task") },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done, capitalization = KeyboardCapitalization.Sentences),
                    keyboardActions = KeyboardActions(onDone = {
                        val v = subText
                        subText = ""
                        scope.launch { repo.quickAdd(v, task.projectId, parentId = task.id) }
                    }),
                )
            }

            if (history.isNotEmpty()) {
                item {
                    SectionHeader("Activity", history.size, expanded = showHistory, onToggle = { showHistory = !showHistory })
                }
                if (showHistory) items(history, key = { it.id }) { HistoryRow(it) }
            }
            item { Spacer(Modifier.height(32.dp)) }
        }
    }

    when (sheet) {
        Sheet.DATE -> ScheduleSheet(task.dueAt, task.hasTime, showSnooze = open && task.dueAt != null, onDismiss = { sheet = null },
            onPick = { d, t -> update(task.copy(dueAt = d, hasTime = t, recurrence = if (d == null) null else task.recurrence)); sheet = null },
            onSnooze = { m -> finish { repo.snooze(task.id, m) }; sheet = null })
        Sheet.REPEAT -> RepeatSheet(Recurrence.decode(task.recurrence), { sheet = null }) { r ->
            var t = task.copy(recurrence = r?.encode())
            if (r != null && t.dueAt == null) t = t.copy(dueAt = repo.startOfToday(), hasTime = false)
            update(t); sheet = null
        }
        Sheet.PRIORITY -> ChoiceSheet(
            "Priority",
            Priority.entries.map { Triple("Priority ${it.level}", it.label, Color(Formats.priorityColor(it))) },
            task.priority - 1, { sheet = null },
        ) { update(task.copy(priority = it + 1)); sheet = null }
        Sheet.PROJECT -> {
            val options = buildList {
                add(Triple<String?, String?, String>(null, null, "Inbox"))
                sections.filter { it.projectId == null }.forEach { add(Triple(null, it.id, "Inbox / ${it.name}")) }
                projects.forEach { p ->
                    add(Triple(p.id, null, p.name))
                    sections.filter { it.projectId == p.id }.forEach { add(Triple(p.id, it.id, "${p.name} / ${it.name}")) }
                }
            }
            ChoiceSheet(
                "Move to",
                options.map { Triple(it.third, null, MaterialTheme.colorScheme.onSurface) },
                options.indexOfFirst { it.first == task.projectId && it.second == task.sectionId }, { sheet = null },
            ) { i -> update(task.copy(projectId = options[i].first, sectionId = options[i].second)); sheet = null }
        }
        Sheet.PLACE -> PlaceSheet(task, { sheet = null }) { id, trig -> update(task.copy(placeId = id, placeTrigger = trig)); sheet = null }
        Sheet.BLUETOOTH -> BluetoothSheet(task, { sheet = null }) { d -> update(task.copy(bluetoothAddress = d?.address, bluetoothName = d?.name)); sheet = null }
        Sheet.EVENT -> EventSheet(task, { sheet = null }) { e ->
            update(
                if (e == null) task.copy(eventId = null, eventTitle = null)
                else task.copy(eventId = e.eventId, eventTitle = e.title, dueAt = e.begin, hasTime = !e.allDay, remindBeforeMinutes = if (task.remindBeforeMinutes == 0) 10 else task.remindBeforeMinutes),
            )
            sheet = null
        }
        Sheet.DEPENDENCY -> DependencySheet(task, { sheet = null }) { t -> update(task.copy(blockedBy = t?.id)); sheet = null }
        null -> {}
    }

    editLabels?.let { value ->
        AlertDialog(
            onDismissRequest = { editLabels = null },
            title = { Text("Labels") },
            text = { OutlinedTextField(value, { editLabels = it }, placeholder = { Text("errands, phone, home") }, singleLine = true) },
            confirmButton = {
                TextButton(onClick = {
                    update(task.copy(labels = value.split(',', ' ').map { it.trim().removePrefix("@") }.filter { it.isNotEmpty() }.distinct().joinToString(",")))
                    editLabels = null
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { editLabels = null }) { Text("Cancel") } },
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete task?") },
            text = { Text("The task, its sub-tasks and history are removed permanently.") },
            confirmButton = { TextButton(onClick = { pending.take(); dirty = false; scope.launch { repo.delete(task.id); onBack() } }) { Text("Delete", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun HistoryRow(entry: ActivityEntity) {
    val label = when (entry.type) {
        ActivityType.CREATED -> "Created"
        ActivityType.NAGGED -> "Started reminding"
        ActivityType.SNOOZED -> "Snoozed"
        ActivityType.COMPLETED -> "Completed"
        ActivityType.SKIPPED -> "Skipped"
        ActivityType.ABANDONED -> "Closed (won't do)"
        ActivityType.RESCHEDULED -> "Rescheduled"
        ActivityType.REOPENED -> "Reopened"
        ActivityType.TRIGGERED -> "Triggered"
        else -> entry.type
    }
    ListRow(
        label + if (entry.detail.isNotBlank()) " · ${entry.detail}" else "",
        Formats.date(entry.at, true), Icons.Outlined.History, MaterialTheme.colorScheme.onSurfaceVariant,
    )
    HorizontalDivider(Modifier.padding(start = 72.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
}

private class PendingEdit {
    var task: TaskEntity? = null

    fun take(): TaskEntity? = task.also { task = null }
}

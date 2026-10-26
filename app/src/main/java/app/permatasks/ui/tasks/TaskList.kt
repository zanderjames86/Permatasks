package app.permatasks.ui.tasks

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.permatasks.container
import app.permatasks.data.ProjectEntity
import app.permatasks.data.TaskEntity
import app.permatasks.data.TaskStatus
import app.permatasks.domain.Priority
import app.permatasks.ui.components.EmptyState
import app.permatasks.ui.components.LocalSnackbar
import app.permatasks.ui.components.Pill
import app.permatasks.ui.components.SectionHeader
import app.permatasks.ui.format.Formats
import app.permatasks.ui.theme.AppColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class TaskSection(
    val title: String,
    val tasks: List<TaskEntity>,
    val highlight: Boolean = false,
    val collapsible: Boolean = true,
)

/** Completing with Undo, and a shared "when?" sheet for swipe-to-reschedule. */
class TaskActions(
    val complete: (TaskEntity) -> Unit,
    val reschedule: (TaskEntity) -> Unit,
)

@Composable
fun rememberTaskActions(): Pair<TaskActions, @Composable () -> Unit> {
    val repo = LocalContext.current.container.repo
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    var rescheduling by remember { mutableStateOf<TaskEntity?>(null) }
    val actions = remember {
        TaskActions(
            complete = { task ->
                scope.launch {
                    val snapshot = repo.tasks.get(task.id) ?: return@launch
                    repo.complete(task.id)
                    val result = snackbar.showSnackbar(
                        if (task.recurrence != null) "Done · next one scheduled" else "Completed",
                        actionLabel = "Undo",
                        duration = SnackbarDuration.Short,
                    )
                    if (result == SnackbarResult.ActionPerformed) repo.restore(snapshot)
                }
            },
            reschedule = { rescheduling = it },
        )
    }
    val sheet: @Composable () -> Unit = {
        rescheduling?.let { task ->
            ScheduleSheet(
                dueAt = task.dueAt,
                hasTime = task.hasTime,
                showSnooze = task.dueAt != null,
                onDismiss = { rescheduling = null },
                onPick = { due, hasTime -> scope.launch { repo.reschedule(task.id, due, hasTime) }; rescheduling = null },
                onSnooze = { minutes -> scope.launch { repo.snooze(task.id, minutes) }; rescheduling = null },
            )
        }
    }
    return actions to sheet
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TaskSections(
    sections: List<TaskSection>,
    projects: Map<String, ProjectEntity>,
    onOpen: (TaskEntity) -> Unit,
    emptyIcon: ImageVector = Icons.Outlined.TaskAlt,
    emptyTitle: String,
    emptyBody: String,
    showProject: Boolean = true,
    header: LazyListScope.() -> Unit = {},
) {
    val (actions, sheet) = rememberTaskActions()
    val collapsed = remember { mutableStateMapOf<String, Boolean>() }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 112.dp)) {
        header()
        val nonEmpty = sections.filter { it.tasks.isNotEmpty() }
        if (nonEmpty.isEmpty()) {
            item { EmptyState(emptyIcon, emptyTitle, emptyBody) }
        }
        nonEmpty.forEach { section ->
            val isCollapsed = collapsed[section.title] == true
            if (nonEmpty.size > 1 || section.highlight) {
                stickyHeader(key = "h_${section.title}") {
                    Box(Modifier.background(MaterialTheme.colorScheme.surface)) {
                        SectionHeader(
                            section.title,
                            section.tasks.size,
                            color = if (section.highlight) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                            expanded = if (section.collapsible) !isCollapsed else null,
                            onToggle = if (section.collapsible) ({ collapsed[section.title] = !isCollapsed }) else null,
                        )
                    }
                }
            }
            if (!isCollapsed) {
                items(section.tasks, key = { "${section.title}_${it.id}" }) { task ->
                    SwipeableTaskRow(task, projects[task.projectId], onOpen, actions, showProject, Modifier.animateItem())
                }
            }
        }
    }
    sheet()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwipeableTaskRow(
    task: TaskEntity,
    project: ProjectEntity?,
    onOpen: (TaskEntity) -> Unit,
    actions: TaskActions,
    showProject: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    val state = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            when (value) {
                SwipeToDismissBoxValue.StartToEnd -> { haptics.performHapticFeedback(HapticFeedbackType.LongPress); actions.complete(task) }
                SwipeToDismissBoxValue.EndToStart -> actions.reschedule(task)
                else -> {}
            }
            false
        },
        positionalThreshold = { it * 0.35f },
    )
    val open = task.status == TaskStatus.OPEN
    SwipeToDismissBox(
        state = state,
        modifier = modifier,
        enableDismissFromStartToEnd = open,
        enableDismissFromEndToStart = open,
        backgroundContent = {
            val dir = state.dismissDirection
            val color by animateColorAsState(
                when (dir) {
                    SwipeToDismissBoxValue.StartToEnd -> AppColors.Success
                    SwipeToDismissBoxValue.EndToStart -> AppColors.Snooze
                    else -> Color.Transparent
                },
                label = "swipe",
            )
            Row(
                Modifier.fillMaxSize().background(color).padding(horizontal = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = if (dir == SwipeToDismissBoxValue.EndToStart) Arrangement.End else Arrangement.Start,
            ) {
                if (dir == SwipeToDismissBoxValue.StartToEnd) {
                    Icon(Icons.Filled.Check, null, tint = Color.White)
                    Spacer(Modifier.width(8.dp)); Text("Done", color = Color.White, style = MaterialTheme.typography.labelLarge)
                } else if (dir == SwipeToDismissBoxValue.EndToStart) {
                    Text("Reschedule", color = Color.White, style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.width(8.dp)); Icon(Icons.Outlined.Schedule, null, tint = Color.White)
                }
            }
        },
    ) {
        Box(Modifier.background(MaterialTheme.colorScheme.surface)) {
            TaskRow(task, project, onOpen, onComplete = { actions.complete(it) }, showProject = showProject)
        }
    }
}

@Composable
fun PriorityCheck(task: TaskEntity, onCheck: () -> Unit) {
    val scope = rememberCoroutineScope()
    var ticking by remember(task.id, task.dueAt, task.status) { mutableStateOf(false) }
    val priority = Priority.fromLevel(task.priority)
    val color = Color(Formats.priorityColor(priority))
    val done = task.status != TaskStatus.OPEN || ticking
    Box(
        Modifier.size(24.dp).clip(CircleShape)
            .background(if (done) color else if (priority != Priority.P4) color.copy(alpha = 0.10f) else Color.Transparent)
            .border(if (priority == Priority.P4) 1.5.dp else 2.dp, color, CircleShape)
            .clickable(enabled = task.status == TaskStatus.OPEN) {
                ticking = true
                scope.launch { delay(220); onCheck() }
            },
        contentAlignment = Alignment.Center,
    ) {
        if (done) Icon(Icons.Filled.Check, "Complete", tint = Color.White, modifier = Modifier.size(16.dp))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TaskRow(
    task: TaskEntity,
    project: ProjectEntity?,
    onOpen: (TaskEntity) -> Unit,
    onComplete: (TaskEntity) -> Unit,
    showProject: Boolean = true,
) {
    val done = task.status != TaskStatus.OPEN
    Row(
        Modifier.fillMaxWidth().clickable { onOpen(task) }.padding(start = 20.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(Modifier.padding(top = 1.dp)) { PriorityCheck(task) { onComplete(task) } }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                task.title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                textDecoration = if (done) TextDecoration.LineThrough else null,
                color = if (done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            )
            if (task.description.isNotBlank()) {
                Text(
                    task.description, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            val muted = MaterialTheme.colorScheme.onSurfaceVariant
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                task.dueAt?.let {
                    Pill(Formats.date(it, task.hasTime), dateColor(it, task.hasTime, done), Icons.Outlined.CalendarToday)
                }
                if (task.recurrence != null) Icon(Icons.Outlined.Repeat, "Repeats", Modifier.size(15.dp).padding(top = 2.dp), tint = muted)
                if (task.nagCount > 0 && !done) Pill("${task.nagCount}", MaterialTheme.colorScheme.error, Icons.Outlined.NotificationsActive)
                if (task.placeId != null) Icon(Icons.Outlined.Place, "Location", Modifier.size(15.dp).padding(top = 2.dp), tint = muted)
                if (task.bluetoothAddress != null) Icon(Icons.Outlined.Bluetooth, "Bluetooth", Modifier.size(15.dp).padding(top = 2.dp), tint = muted)
                if (task.eventId != null) Icon(Icons.Outlined.Event, "Calendar", Modifier.size(15.dp).padding(top = 2.dp), tint = muted)
                if (task.blockedBy != null) Pill("Waiting", muted, Icons.Outlined.Link)
                task.labelList.forEach { Pill("@$it", MaterialTheme.colorScheme.tertiary) }
            }
        }
        if (showProject && project != null) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 8.dp, top = 3.dp)) {
                Text(project.name, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                Spacer(Modifier.width(6.dp))
                Box(Modifier.size(8.dp).clip(CircleShape).background(Color(project.color)))
            }
        }
    }
}

@Composable
fun dateColor(ms: Long, hasTime: Boolean, done: Boolean): Color {
    if (done) return MaterialTheme.colorScheme.onSurfaceVariant
    return when {
        Formats.isOverdue(ms, hasTime) -> MaterialTheme.colorScheme.error
        Formats.dayOffset(ms) == 0L -> AppColors.Success
        Formats.dayOffset(ms) == 1L -> AppColors.Warning
        Formats.dayOffset(ms) in 2..6 -> AppColors.Snooze
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
}

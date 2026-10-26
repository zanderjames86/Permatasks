package app.permatasks.ui.tasks

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.permatasks.data.ProjectEntity
import app.permatasks.data.TaskEntity
import app.permatasks.data.TaskStatus
import app.permatasks.domain.Priority
import app.permatasks.domain.Recurrence
import app.permatasks.ui.format.Formats
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class TaskSection(val title: String, val tasks: List<TaskEntity>, val highlight: Boolean = false)

@Composable
fun TaskSections(
    sections: List<TaskSection>,
    projects: Map<String, ProjectEntity>,
    onOpen: (TaskEntity) -> Unit,
    onComplete: suspend (TaskEntity) -> Unit,
    empty: String,
    header: LazyListScope.() -> Unit = {},
) {
    LazyColumn(contentPadding = PaddingValues(bottom = 96.dp)) {
        header()
        val nonEmpty = sections.filter { it.tasks.isNotEmpty() }
        if (nonEmpty.isEmpty()) {
            item {
                Text(
                    empty,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(24.dp),
                )
            }
        }
        nonEmpty.forEach { section ->
            item(key = "h_${section.title}") {
                Text(
                    "${section.title}  ${section.tasks.size}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (section.highlight) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
                )
            }
            items(section.tasks, key = { "${section.title}_${it.id}" }) { task ->
                TaskRow(task, projects[task.projectId], onOpen, onComplete)
                HorizontalDivider(Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            }
        }
    }
}

@Composable
fun TaskRow(
    task: TaskEntity,
    project: ProjectEntity?,
    onOpen: (TaskEntity) -> Unit,
    onComplete: suspend (TaskEntity) -> Unit,
    showProject: Boolean = true,
) {
    val scope = rememberCoroutineScope()
    var ticking by remember(task.id, task.dueAt) { mutableStateOf(false) }
    val priority = Priority.fromLevel(task.priority)
    val color = Color(Formats.priorityColor(priority))
    val done = task.status != TaskStatus.OPEN
    Row(
        Modifier.fillMaxWidth().clickable { onOpen(task) }.padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            Modifier
                .padding(top = 2.dp)
                .size(24.dp)
                .clip(CircleShape)
                .background(if (ticking || done) color else color.copy(alpha = 0.12f))
                .border(BorderStroke(2.dp, color), CircleShape)
                .clickable(enabled = !done) {
                    ticking = true
                    scope.launch {
                        delay(250)
                        onComplete(task)
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            if (ticking || done) Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(16.dp))
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                task.title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                textDecoration = if (done) TextDecoration.LineThrough else null,
            )
            if (task.description.isNotBlank()) {
                Text(task.description, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                task.dueAt?.let {
                    val overdue = !done && Formats.isOverdue(it, task.hasTime)
                    Text(
                        Formats.date(it, task.hasTime),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (overdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    )
                }
                if (task.recurrence != null) {
                    Icon(Icons.Outlined.Repeat, Recurrence.decode(task.recurrence)?.describe(), Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (task.nagCount > 0 && !done) {
                    Icon(Icons.Outlined.NotificationsActive, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.error)
                    Text("${task.nagCount}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                }
                task.labelList.forEach {
                    Text("@$it", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
                }
                Spacer(Modifier.weight(1f))
                if (showProject && project != null) {
                    Text("# ${project.name}", style = MaterialTheme.typography.labelSmall, color = Color(project.color))
                }
            }
        }
    }
}

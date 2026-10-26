package app.permatasks.ui.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.permatasks.container
import app.permatasks.data.ActivityType
import app.permatasks.data.ProjectEntity
import app.permatasks.data.TaskEntity
import app.permatasks.ui.components.ProgressRing
import app.permatasks.ui.settings.SetupChecklistCard
import app.permatasks.ui.settings.rememberSetupItems
import app.permatasks.ui.tasks.TaskSection
import app.permatasks.ui.tasks.TaskSections
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

@Composable
fun rememberProjects(): Map<String, ProjectEntity> {
    val repo = LocalContext.current.container.repo
    val list by remember { repo.projects.observeAll() }.collectAsState(initial = emptyList())
    return remember(list) { list.associateBy { it.id } }
}

@Composable
fun rememberOpenTasks(): List<TaskEntity> {
    val repo = LocalContext.current.container.repo
    val list by remember { repo.tasks.observeOpen() }.collectAsState(initial = emptyList())
    return list
}

@Composable
fun TodayContent(onOpen: (TaskEntity) -> Unit) {
    val repo = LocalContext.current.container.repo
    val tasks = rememberOpenTasks().filter { it.parentId == null }
    val projects = rememberProjects()
    val setup = rememberSetupItems()
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    val startOfToday = today.atStartOfDay(zone).toInstant().toEpochMilli()
    val endOfToday = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
    val now = System.currentTimeMillis()
    val doneToday by remember { repo.activity.observeSince(startOfToday) }.collectAsState(initial = emptyList())
    val completedCount = doneToday.count { it.type == ActivityType.COMPLETED }

    val overdue = tasks.filter { t -> t.dueAt != null && (if (t.hasTime) t.dueAt < now else t.dueAt < startOfToday) }
    val dueToday = tasks.filter { it.dueAt != null && it.dueAt < endOfToday && it !in overdue }
    val triggered = tasks.filter { it.dueAt == null && it.nagCount > 0 }

    TaskSections(
        sections = listOf(
            TaskSection("Overdue", overdue, highlight = true),
            TaskSection("Triggered", triggered, highlight = true),
            TaskSection("Today", dueToday, collapsible = false),
        ),
        projects = projects,
        onOpen = onOpen,
        emptyIcon = Icons.Outlined.WbSunny,
        emptyTitle = "You're all caught up",
        emptyBody = "Nothing due today. Enjoy it, or tap + to plan something.",
        header = {
            item { SetupChecklistCard(setup) }
            item { DaySummary(completedCount, overdue.size + dueToday.size + triggered.size) }
        },
    )
}

@Composable
private fun DaySummary(done: Int, remaining: Int) {
    val hour = LocalTime.now().hour
    val greeting = when (hour) {
        in 5..11 -> "Good morning"
        in 12..17 -> "Good afternoon"
        else -> "Good evening"
    }
    val total = done + remaining
    Surface(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
    ) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(greeting, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
                Text(
                    when {
                        total == 0 -> "A clear day ahead."
                        remaining == 0 -> "Everything done. Brilliant."
                        else -> "$remaining to go · $done done today"
                    },
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            Spacer(Modifier.width(12.dp))
            ProgressRing(if (total == 0) 1f else done.toFloat() / total, color = MaterialTheme.colorScheme.primary)
        }
    }
}

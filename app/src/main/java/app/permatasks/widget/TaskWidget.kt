package app.permatasks.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.CheckBox
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import app.permatasks.container
import app.permatasks.data.TaskEntity
import app.permatasks.domain.Priority
import app.permatasks.ui.MainActivity
import app.permatasks.ui.QuickAddActivity
import app.permatasks.ui.format.Formats
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.ZoneId

/** Home-screen list of what is overdue or due today; tick a box to complete the task. */
class TaskWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val repo = context.container.repo
        val initial = outstanding(repo.tasks.open())
        val flow = repo.tasks.observeOpen().map(::outstanding)
        provideContent {
            val tasks by flow.collectAsState(initial = initial)
            GlanceTheme { Content(context, tasks) }
        }
    }

    @Composable
    private fun Content(context: Context, tasks: List<TaskEntity>) {
        Column(
            GlanceModifier.fillMaxSize().background(GlanceTheme.colors.widgetBackground).cornerRadius(20.dp).padding(12.dp),
        ) {
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Today · ${tasks.size}",
                    style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold, color = GlanceTheme.colors.onSurface),
                    modifier = GlanceModifier.defaultWeight().clickable(actionStartActivity(Intent(context, MainActivity::class.java))),
                )
                Box(
                    GlanceModifier.size(36.dp).cornerRadius(18.dp).background(GlanceTheme.colors.primary)
                        .clickable(actionStartActivity(Intent(context, QuickAddActivity::class.java))),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("+", style = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold, color = GlanceTheme.colors.onPrimary))
                }
            }
            Spacer(GlanceModifier.height(8.dp))
            if (tasks.isEmpty()) {
                Text("All clear 🎉", style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant))
            } else {
                LazyColumn {
                    items(tasks, itemId = { it.id.hashCode().toLong() }) { task -> TaskRow(context, task) }
                }
            }
        }
    }

    @Composable
    private fun TaskRow(context: Context, task: TaskEntity) {
        val overdue = task.dueAt?.let { Formats.isOverdue(it, task.hasTime) } == true
        Row(GlanceModifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            CheckBox(
                checked = false,
                onCheckedChange = actionRunCallback<CompleteTaskAction>(actionParametersOf(TASK_KEY to task.id)),
            )
            Box(GlanceModifier.width(4.dp).height(28.dp).background(Color(Formats.priorityColor(Priority.fromLevel(task.priority))))) {}
            Spacer(GlanceModifier.width(8.dp))
            Column(
                GlanceModifier.defaultWeight().clickable(
                    actionStartActivity(Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_TASK_ID, task.id)),
                ),
            ) {
                Text(task.title, maxLines = 2, style = TextStyle(fontSize = 14.sp, color = GlanceTheme.colors.onSurface))
                task.dueAt?.let {
                    Text(
                        Formats.date(it, task.hasTime),
                        style = TextStyle(
                            fontSize = 12.sp,
                            color = if (overdue) ColorProvider(Color(0xFFD1453B)) else GlanceTheme.colors.onSurfaceVariant,
                        ),
                    )
                }
            }
        }
    }

    companion object {
        val TASK_KEY = ActionParameters.Key<String>("task_id")

        suspend fun refresh(context: Context) {
            runCatching { TaskWidget().updateAll(context) }
        }

        fun outstanding(open: List<TaskEntity>): List<TaskEntity> {
            val zone = ZoneId.systemDefault()
            val endOfToday = LocalDate.now(zone).plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
            return open.filter { it.parentId == null && it.dueAt != null && it.dueAt < endOfToday }
                .sortedWith(compareBy({ it.dueAt }, { it.priority }))
        }
    }
}

class CompleteTaskAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val id = parameters[TaskWidget.TASK_KEY] ?: return
        context.container.repo.complete(id)
    }
}

class TaskWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TaskWidget()
}

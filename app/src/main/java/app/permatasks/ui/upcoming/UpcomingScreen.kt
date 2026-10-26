package app.permatasks.ui.upcoming

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.permatasks.container
import app.permatasks.data.TaskEntity
import app.permatasks.triggers.CalendarEvent
import app.permatasks.ui.components.ListRow
import app.permatasks.ui.format.Formats
import app.permatasks.ui.tasks.TaskSection
import app.permatasks.ui.tasks.TaskSections
import app.permatasks.ui.today.rememberOpenTasks
import app.permatasks.ui.today.rememberProjects
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters

@Composable
fun UpcomingContent(onOpen: (TaskEntity) -> Unit) {
    var calendarMode by rememberSaveable { mutableStateOf(false) }
    Column {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
            SegmentedButton(!calendarMode, { calendarMode = false }, SegmentedButtonDefaults.itemShape(0, 2)) { Text("List") }
            SegmentedButton(calendarMode, { calendarMode = true }, SegmentedButtonDefaults.itemShape(1, 2)) { Text("Calendar") }
        }
        if (calendarMode) CalendarView(onOpen) else UpcomingList(onOpen)
    }
}

@Composable
private fun UpcomingList(onOpen: (TaskEntity) -> Unit) {
    val tasks = rememberOpenTasks().filter { it.parentId == null }
    val projects = rememberProjects()
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    val byDay = tasks.filter { it.dueAt != null }.groupBy { Instant.ofEpochMilli(it.dueAt!!).atZone(zone).toLocalDate() }
    val sections = buildList {
        add(TaskSection("Overdue", byDay.filterKeys { it.isBefore(today) }.values.flatten(), highlight = true))
        for (i in 0 until 14) {
            val d = today.plusDays(i.toLong())
            val label = when (i) {
                0 -> "Today · " + d.format(DateTimeFormatter.ofPattern("EEE d MMM"))
                1 -> "Tomorrow · " + d.format(DateTimeFormatter.ofPattern("EEE d MMM"))
                else -> d.format(DateTimeFormatter.ofPattern("EEEE d MMM"))
            }
            add(TaskSection(label, byDay[d].orEmpty()))
        }
        add(TaskSection("Later", byDay.filterKeys { it.isAfter(today.plusDays(13)) }.values.flatten()))
        add(TaskSection("Someday (no date)", tasks.filter { it.dueAt == null }))
    }
    TaskSections(sections, projects, onOpen, Icons.Outlined.CalendarMonth, "Nothing planned", "Tasks with dates show up here, grouped by day.")
}

@Composable
private fun CalendarView(onOpen: (TaskEntity) -> Unit) {
    val c = LocalContext.current.container
    val tasks = rememberOpenTasks().filter { it.parentId == null }
    val projects = rememberProjects()
    val zone = ZoneId.systemDefault()
    var month by remember { mutableStateOf(YearMonth.now(zone)) }
    var selected by rememberSaveable { mutableStateOf(LocalDate.now(zone).toEpochDay()) }
    val selectedDate = LocalDate.ofEpochDay(selected)
    val byDay = tasks.filter { it.dueAt != null }.groupBy { Instant.ofEpochMilli(it.dueAt!!).atZone(zone).toLocalDate() }
    var events by remember { mutableStateOf<List<CalendarEvent>>(emptyList()) }
    LaunchedEffect(month) {
        events = withContext(Dispatchers.IO) {
            val from = month.atDay(1).minusDays(7).atStartOfDay(zone).toInstant().toEpochMilli()
            val to = month.atEndOfMonth().plusDays(8).atStartOfDay(zone).toInstant().toEpochMilli()
            c.calendar.events(from, to)
        }
    }
    val eventsByDay = events.groupBy { Instant.ofEpochMilli(it.begin).atZone(zone).toLocalDate() }

    TaskSections(
        listOf(TaskSection(selectedDate.format(DateTimeFormatter.ofPattern("EEEE d MMMM")), byDay[selectedDate].orEmpty(), collapsible = false)),
        projects, onOpen, Icons.Outlined.CalendarMonth, "Free day", "No tasks on this day.",
        header = {
            item { MonthGrid(month, { month = it }, selectedDate, { selected = it.toEpochDay() }, byDay.mapValues { it.value.size }, eventsByDay.keys) }
            val dayEvents = eventsByDay[selectedDate].orEmpty()
            dayEvents.forEach { e ->
                item(key = "ev_${e.eventId}_${e.begin}") {
                    ListRow(e.title, if (e.allDay) "All day" else "${Formats.time(e.begin)} – ${Formats.time(e.end)}", Icons.Outlined.Event, Color(e.color or 0xFF000000.toInt()))
                }
            }
        },
    )
}

@Composable
private fun MonthGrid(
    month: YearMonth,
    onMonth: (YearMonth) -> Unit,
    selected: LocalDate,
    onSelect: (LocalDate) -> Unit,
    counts: Map<LocalDate, Int>,
    eventDays: Set<LocalDate>,
) {
    val today = LocalDate.now()
    Column(Modifier.padding(horizontal = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(month.format(DateTimeFormatter.ofPattern("MMMM yyyy")), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f).padding(start = 8.dp))
            IconButton(onClick = { onMonth(month.minusMonths(1)) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Previous month") }
            IconButton(onClick = { onMonth(month.plusMonths(1)) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Next month") }
        }
        Row {
            listOf("M", "T", "W", "T", "F", "S", "S").forEach {
                Text(it, Modifier.weight(1f).padding(vertical = 4.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
        }
        var day = month.atDay(1).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        repeat(6) {
            Row {
                repeat(7) {
                    val d = day
                    val inMonth = d.month == month.month
                    val isSel = d == selected
                    val isToday = d == today
                    Box(
                        Modifier.weight(1f).aspectRatio(1f).padding(2.dp).clip(CircleShape)
                            .background(if (isSel) MaterialTheme.colorScheme.primary else Color.Transparent)
                            .clickable { onSelect(d) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                "${d.dayOfMonth}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (isToday) FontWeight.Bold else null,
                                color = when {
                                    isSel -> MaterialTheme.colorScheme.onPrimary
                                    isToday -> MaterialTheme.colorScheme.primary
                                    inMonth -> MaterialTheme.colorScheme.onSurface
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                },
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                if ((counts[d] ?: 0) > 0) Dot(if (isSel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary)
                                if (d in eventDays) Dot(if (isSel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.tertiary)
                            }
                        }
                    }
                    day = day.plusDays(1)
                }
            }
        }
    }
}

@Composable
private fun Dot(color: Color) {
    Box(Modifier.size(5.dp).clip(CircleShape).background(color))
}

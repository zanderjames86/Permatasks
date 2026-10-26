package app.permatasks.ui.browse

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.FilterAlt
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.Label
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material.icons.outlined.ViewKanban
import androidx.compose.material.icons.outlined.ViewList
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.permatasks.container
import app.permatasks.data.ActivityType
import app.permatasks.data.FilterEntity
import app.permatasks.data.ProjectEntity
import app.permatasks.data.SectionEntity
import app.permatasks.data.TaskEntity
import app.permatasks.domain.FilterQuery
import app.permatasks.ui.components.GroupCard
import app.permatasks.ui.components.ListRow
import app.permatasks.ui.components.SectionHeader
import app.permatasks.ui.components.StatTile
import app.permatasks.ui.format.Formats
import app.permatasks.ui.tasks.PriorityCheck
import app.permatasks.ui.tasks.TaskSection
import app.permatasks.ui.tasks.TaskSections
import app.permatasks.ui.tasks.rememberTaskActions
import app.permatasks.ui.today.rememberOpenTasks
import app.permatasks.ui.today.rememberProjects
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

const val INBOX = "inbox"

val PROJECT_COLORS = listOf(0xFF7E57C2, 0xFF26A69A, 0xFFEF5350, 0xFF42A5F5, 0xFFFFA726, 0xFF8D6E63, 0xFFEC407A, 0xFF66BB6A, 0xFF5C6BC0, 0xFF78909C)

@Composable
fun BrowseContent(
    onProject: (String) -> Unit,
    onFilter: (String) -> Unit,
    onQuery: (String) -> Unit,
    onNewFilter: () -> Unit,
    onLogbook: () -> Unit,
    onStats: () -> Unit,
    onPlaces: () -> Unit,
) {
    val repo = LocalContext.current.container.repo
    val scope = rememberCoroutineScope()
    val tasks = rememberOpenTasks()
    val projects by remember { repo.projects.observeAll() }.collectAsState(initial = emptyList())
    val filters by remember { repo.filters.observeAll() }.collectAsState(initial = emptyList())
    var newProject by remember { mutableStateOf<String?>(null) }
    val labels = remember(tasks) { tasks.flatMap { it.labelList }.groupingBy { it.lowercase() }.eachCount().toSortedMap() }

    LazyColumn(contentPadding = PaddingValues(bottom = 112.dp)) {
        item {
            GroupCard {
                ListRow("Inbox", null, Icons.Outlined.Inbox, MaterialTheme.colorScheme.primary, onClick = { onProject(INBOX) },
                    trailing = { Count(tasks.count { it.projectId == null && it.parentId == null }) })
            }
        }
        item { SectionHeader("Projects", projects.size, trailing = { TextButton(onClick = { newProject = "" }) { Text("New") } }) }
        item {
            GroupCard {
                if (projects.isEmpty()) ListRow("Create your first project", "Group tasks: Work, Home, Health…", Icons.Outlined.Folder, onClick = { newProject = "" })
                projects.forEach { p ->
                    ListRow(p.name, null, Icons.Outlined.Folder, Color(p.color), onClick = { onProject(p.id) },
                        trailing = { Count(tasks.count { it.projectId == p.id && it.parentId == null }) })
                }
            }
        }
        item { SectionHeader("Filters", filters.size, trailing = { TextButton(onClick = onNewFilter) { Text("New") } }) }
        item {
            GroupCard {
                val ctxNow = FilterQuery.Context(System.currentTimeMillis(), ZoneId.systemDefault(), projects.associate { it.id to it.name })
                val builtIn = listOf(
                    "Priority 1" to "p1", "Nagging me" to "nagging", "Snoozed" to "snoozed",
                    "Next 7 days" to "7 days", "No date" to "no date", "Waiting on something" to "blocked | location | bluetooth",
                )
                builtIn.forEach { (name, q) ->
                    val fq = FilterQuery.parse(q)
                    ListRow(name, q, Icons.Outlined.FilterAlt, MaterialTheme.colorScheme.secondary, onClick = { onQuery(q) },
                        trailing = { Count(tasks.count { it.parentId == null && fq.matches(it, ctxNow) }) })
                }
                filters.forEach { f ->
                    val fq = FilterQuery.parseOrNull(f.query)
                    ListRow(f.name, f.query, Icons.Outlined.FilterAlt, Color(f.color), onClick = { onFilter(f.id) },
                        trailing = { Count(if (fq == null) 0 else tasks.count { fq.matches(it, ctxNow) }) })
                }
            }
        }
        if (labels.isNotEmpty()) {
            item { SectionHeader("Labels", labels.size) }
            item {
                GroupCard {
                    labels.forEach { (l, n) -> ListRow("@$l", null, Icons.Outlined.Label, MaterialTheme.colorScheme.tertiary, onClick = { onQuery("@$l") }, trailing = { Count(n) }) }
                }
            }
        }
        item { SectionHeader("More") }
        item {
            GroupCard {
                ListRow("Productivity", "Streaks, completions and focus", Icons.Outlined.BarChart, MaterialTheme.colorScheme.primary, onClick = onStats, showChevron = true)
                ListRow("Completed & closed", null, Icons.Outlined.History, MaterialTheme.colorScheme.primary, onClick = onLogbook, showChevron = true)
                ListRow("Places", "For location reminders", Icons.Outlined.Place, Color(0xFF43A047), onClick = onPlaces, showChevron = true)
            }
        }
    }
    newProject?.let { name ->
        AlertDialog(
            onDismissRequest = { newProject = null },
            title = { Text("New project") },
            text = { OutlinedTextField(name, { newProject = it }, singleLine = true, label = { Text("Name") }) },
            confirmButton = {
                TextButton(onClick = {
                    val n = name.trim()
                    if (n.isNotEmpty()) scope.launch { repo.projects.upsert(ProjectEntity(name = n, color = PROJECT_COLORS[projects.size % PROJECT_COLORS.size])) }
                    newProject = null
                }) { Text("Create") }
            },
            dismissButton = { TextButton(onClick = { newProject = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun Count(n: Int) {
    if (n > 0) Text("$n", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** A project as a list (grouped by section) or as a Kanban board. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectScreen(projectId: String?, onBack: () -> Unit, onOpen: (TaskEntity) -> Unit, onAdd: (projectId: String?, sectionId: String?) -> Unit) {
    val repo = LocalContext.current.container.repo
    val scope = rememberCoroutineScope()
    val projects = rememberProjects()
    val project = projectId?.let { projects[it] }
    val allSections by remember { repo.sections.observeAll() }.collectAsState(initial = emptyList())
    val sections = allSections.filter { it.projectId == projectId }
    val tasks = rememberOpenTasks().filter { it.projectId == projectId && it.parentId == null }
    var board by rememberSaveable { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var newSection by remember { mutableStateOf<String?>(null) }
    var rename by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (project != null) Box(Modifier.size(12.dp).clip(CircleShape).background(Color(project.color)))
                        if (project != null) Spacer(Modifier.width(10.dp))
                        Text(project?.name ?: "Inbox")
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    IconButton(onClick = { board = !board }) { Icon(if (board) Icons.Outlined.ViewList else Icons.Outlined.ViewKanban, if (board) "List view" else "Board view") }
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "More") }
                        DropdownMenu(menu, { menu = false }) {
                            DropdownMenuItem(text = { Text("Add section") }, onClick = { menu = false; newSection = "" })
                            if (project != null) {
                                DropdownMenuItem(text = { Text("Rename") }, onClick = { menu = false; rename = project.name })
                                DropdownMenuItem(text = { Text("Change colour") }, onClick = {
                                    menu = false
                                    val next = PROJECT_COLORS[(PROJECT_COLORS.indexOf(project.color) + 1).mod(PROJECT_COLORS.size)]
                                    scope.launch { repo.projects.upsert(project.copy(color = next)) }
                                })
                                DropdownMenuItem(text = { Text("Delete project") }, onClick = { menu = false; confirmDelete = true })
                            }
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = { onAdd(projectId, null) }, icon = { Icon(Icons.Filled.Add, null) }, text = { Text("Add task") })
        },
    ) { padding ->
        Box(Modifier.padding(padding)) {
            if (board) {
                Board(sections, tasks, onOpen, onAdd = { sid -> onAdd(projectId, sid) }, onAddSection = { newSection = "" })
            } else {
                TaskSections(
                    buildList {
                        add(TaskSection(if (sections.isEmpty()) "Tasks" else "No section", tasks.filter { t -> t.sectionId == null || sections.none { it.id == t.sectionId } }))
                        sections.forEach { s -> add(TaskSection(s.name, tasks.filter { it.sectionId == s.id })) }
                    },
                    projects, onOpen, Icons.Outlined.TaskAlt, "No open tasks", "Add tasks here, or say \"#${project?.name ?: "Inbox"}\" in quick add.",
                    showProject = false,
                )
            }
        }
    }
    newSection?.let { name ->
        AlertDialog(
            onDismissRequest = { newSection = null },
            title = { Text("New section") },
            text = { OutlinedTextField(name, { newSection = it }, singleLine = true, label = { Text("e.g. To do, Doing, Waiting") }) },
            confirmButton = {
                TextButton(onClick = {
                    val n = name.trim()
                    if (n.isNotEmpty()) scope.launch { repo.sections.upsert(SectionEntity(projectId = projectId, name = n)) }
                    newSection = null
                }) { Text("Add") }
            },
            dismissButton = { TextButton(onClick = { newSection = null }) { Text("Cancel") } },
        )
    }
    rename?.let { name ->
        AlertDialog(
            onDismissRequest = { rename = null },
            title = { Text("Rename project") },
            text = { OutlinedTextField(name, { rename = it }, singleLine = true) },
            confirmButton = {
                TextButton(onClick = {
                    if (project != null && name.isNotBlank()) scope.launch { repo.projects.upsert(project.copy(name = name.trim())) }
                    rename = null
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { rename = null }) { Text("Cancel") } },
        )
    }
    if (confirmDelete && project != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete ${project.name}?") },
            text = { Text("Its tasks are kept and moved to the Inbox.") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        repo.projects.detachTasks(project.id)
                        sections.forEach { repo.sections.detachTasks(it.id); repo.sections.delete(it) }
                        repo.projects.delete(project)
                    }
                    onBack()
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun Board(
    sections: List<SectionEntity>,
    tasks: List<TaskEntity>,
    onOpen: (TaskEntity) -> Unit,
    onAdd: (String?) -> Unit,
    onAddSection: () -> Unit,
) {
    val repo = LocalContext.current.container.repo
    val scope = rememberCoroutineScope()
    val (actions, sheet) = rememberTaskActions()
    val columns = listOf<SectionEntity?>(null) + sections
    LazyRow(contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items(columns, key = { it?.id ?: "none" }) { section ->
            val colTasks = tasks.filter { t -> if (section == null) t.sectionId == null || sections.none { it.id == t.sectionId } else t.sectionId == section.id }
            Surface(Modifier.width(290.dp).fillMaxHeight(), shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
                Column(Modifier.verticalScroll(rememberScrollState()).padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(6.dp)) {
                        Text(section?.name ?: "No section", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        Text("${colTasks.size}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    colTasks.forEach { t ->
                        var move by remember { mutableStateOf(false) }
                        Surface(Modifier.fillMaxWidth().clickable { onOpen(t) }, shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
                                PriorityCheck(t) { actions.complete(t) }
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(t.title, style = MaterialTheme.typography.bodyMedium, maxLines = 4, overflow = TextOverflow.Ellipsis)
                                    t.dueAt?.let { Text(Formats.date(it, t.hasTime), style = MaterialTheme.typography.labelSmall, color = app.permatasks.ui.tasks.dateColor(it, t.hasTime, false)) }
                                }
                                Box {
                                    IconButton(onClick = { move = true }, modifier = Modifier.size(28.dp)) { Icon(Icons.Filled.MoreVert, "Move", Modifier.size(18.dp)) }
                                    DropdownMenu(move, { move = false }) {
                                        columns.filter { it?.id != t.sectionId }.forEach { target ->
                                            DropdownMenuItem(text = { Text("Move to ${target?.name ?: "No section"}") }, onClick = {
                                                move = false
                                                scope.launch { repo.save(t.copy(sectionId = target?.id)) }
                                            })
                                        }
                                    }
                                }
                            }
                        }
                    }
                    TextButton(onClick = { onAdd(section?.id) }) { Text("+ Add task") }
                }
            }
        }
        item {
            Surface(Modifier.width(200.dp).height(64.dp).clickable(onClick = onAddSection), shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
                Box(contentAlignment = Alignment.Center) { Text("+ Add section", color = MaterialTheme.colorScheme.primary) }
            }
        }
    }
    sheet()
}

/** Tasks matching a saved filter or an ad-hoc query. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterResultsScreen(filterId: String?, query: String?, onBack: () -> Unit, onOpen: (TaskEntity) -> Unit, onEdit: (String) -> Unit) {
    val repo = LocalContext.current.container.repo
    val filters by remember { repo.filters.observeAll() }.collectAsState(initial = emptyList())
    val filter = filters.firstOrNull { it.id == filterId }
    val q = filter?.query ?: query ?: ""
    val fq = remember(q) { FilterQuery.parseOrNull(q) }
    val projects = rememberProjects()
    val tasks = rememberOpenTasks()
    val ctx = FilterQuery.Context(System.currentTimeMillis(), ZoneId.systemDefault(), projects.mapValues { it.value.name })
    val matches = if (fq == null) emptyList() else tasks.filter { fq.matches(it, ctx) }
    Scaffold(topBar = {
        TopAppBar(
            title = { Column { Text(filter?.name ?: q); if (filter != null) Text(q, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) } },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            actions = { if (filter != null) TextButton(onClick = { onEdit(filter.id) }) { Text("Edit") } },
        )
    }) { padding ->
        Box(Modifier.padding(padding)) {
            TaskSections(listOf(TaskSection("Matching", matches, collapsible = false)), projects, onOpen, Icons.Outlined.FilterAlt, "No matches", "Nothing matches \"$q\" right now.")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterEditorScreen(filterId: String?, onBack: () -> Unit) {
    val repo = LocalContext.current.container.repo
    val scope = rememberCoroutineScope()
    val filters by remember { repo.filters.observeAll() }.collectAsState(initial = null)
    val existing = filters?.firstOrNull { it.id == filterId }
    if (filterId != null && filters == null) return
    var name by remember(existing) { mutableStateOf(existing?.name ?: "") }
    var query by remember(existing) { mutableStateOf(existing?.query ?: "") }
    var color by remember(existing) { mutableStateOf(existing?.color ?: PROJECT_COLORS[3]) }
    val error = remember(query) { if (query.isBlank()) null else runCatching { FilterQuery.parse(query) }.exceptionOrNull()?.message }
    val tasks = rememberOpenTasks()
    val projects = rememberProjects()
    val count = remember(query, tasks) {
        FilterQuery.parseOrNull(query)?.let { fq -> tasks.count { fq.matches(it, FilterQuery.Context(System.currentTimeMillis(), ZoneId.systemDefault(), projects.mapValues { p -> p.value.name })) } }
    }
    Scaffold(topBar = {
        TopAppBar(
            title = { Text(if (existing == null) "New filter" else "Edit filter") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            actions = {
                if (existing != null) TextButton(onClick = { scope.launch { repo.filters.delete(existing); onBack() } }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                TextButton(enabled = name.isNotBlank() && query.isNotBlank() && error == null, onClick = {
                    scope.launch {
                        repo.filters.upsert((existing ?: FilterEntity(name = name, query = query)).copy(name = name.trim(), query = query.trim(), color = color))
                        onBack()
                    }
                }) { Text("Save") }
            },
        )
    }) { padding ->
        Column(Modifier.padding(padding).padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("Name") }, singleLine = true)
            OutlinedTextField(
                query, { query = it }, Modifier.fillMaxWidth(), label = { Text("Query") },
                isError = error != null,
                supportingText = { Text(error ?: count?.let { "$it open tasks match" } ?: "") },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PROJECT_COLORS.take(8).forEach { col ->
                    Box(
                        Modifier.size(32.dp).clip(CircleShape).background(Color(col)).clickable { color = col },
                        contentAlignment = Alignment.Center,
                    ) { if (col == color) Text("✓", color = Color.White) }
                }
            }
            Text("Examples", style = MaterialTheme.typography.titleSmall)
            listOf(
                "(today | overdue) & p1", "7 days & #Work", "@errands & !no date", "overdue & !recurring", "nagging | snoozed", "search: invoice",
            ).forEach { ex -> FilterChip(false, { query = ex }, label = { Text(ex) }) }
            Text(
                "Terms: today, tomorrow, overdue, no date, N days, p1–p4, #Project, @label, recurring, blocked, location, bluetooth, nagging, snoozed, subtask. Combine with & (and), | (or), ! (not) and brackets.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogbookScreen(onBack: () -> Unit, onOpen: (TaskEntity) -> Unit) {
    val repo = LocalContext.current.container.repo
    val closed by remember { repo.tasks.observeClosed() }.collectAsState(initial = emptyList())
    val projects = rememberProjects()
    val zone = ZoneId.systemDefault()
    val byDay = closed.groupBy { Instant.ofEpochMilli(it.completedAt ?: 0).atZone(zone).toLocalDate() }
    Scaffold(topBar = {
        TopAppBar(title = { Text("Completed & closed") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } })
    }) { padding ->
        Box(Modifier.padding(padding)) {
            TaskSections(
                byDay.entries.sortedByDescending { it.key }.map { (d, list) -> TaskSection(d.format(DateTimeFormatter.ofPattern("EEEE d MMM")), list) },
                projects, onOpen, Icons.Outlined.History, "Nothing completed yet", "Finished tasks are listed here.",
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(onBack: () -> Unit) {
    val repo = LocalContext.current.container.repo
    val zone = ZoneId.systemDefault()
    val since = LocalDate.now(zone).minusDays(89).atStartOfDay(zone).toInstant().toEpochMilli()
    val activity by remember { repo.activity.observeSince(since) }.collectAsState(initial = emptyList())
    val totalDone by remember { repo.tasks.observeDoneCount() }.collectAsState(initial = 0)
    val today = LocalDate.now(zone)
    val completions = activity.filter { it.type == ActivityType.COMPLETED }
    val perDay = completions.groupingBy { Instant.ofEpochMilli(it.at).atZone(zone).toLocalDate() }.eachCount()
    val streak = generateSequence(if ((perDay[today] ?: 0) > 0) today else today.minusDays(1)) { it.minusDays(1) }.takeWhile { (perDay[it] ?: 0) > 0 }.count()
    val week = (0 until 7).sumOf { perDay[today.minusDays(it.toLong())] ?: 0 }
    val snoozes = activity.count { it.type == ActivityType.SNOOZED && it.at > today.minusDays(6).atStartOfDay(zone).toInstant().toEpochMilli() }
    val last14 = (13 downTo 0).map { today.minusDays(it.toLong()) }
    val max = (last14.maxOfOrNull { perDay[it] ?: 0 } ?: 0).coerceAtLeast(1)

    Scaffold(topBar = {
        TopAppBar(title = { Text("Productivity") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } })
    }) { padding ->
        Column(Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile("$streak", "day streak", Modifier.weight(1f))
                StatTile("$week", "done this week", Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile("$totalDone", "completed all time", Modifier.weight(1f), MaterialTheme.colorScheme.tertiary)
                StatTile("$snoozes", "snoozes this week", Modifier.weight(1f), MaterialTheme.colorScheme.error)
            }
            Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
                Column(Modifier.padding(16.dp)) {
                    Text("Last 14 days", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(16.dp))
                    Row(Modifier.fillMaxWidth().height(140.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Bottom) {
                        last14.forEach { d ->
                            val n = perDay[d] ?: 0
                            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                                if (n > 0) Text("$n", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Box(
                                    Modifier.fillMaxWidth().height((100f * n / max).dp.coerceAtLeast(4.dp))
                                        .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                                        .background(if (d == today) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)),
                                )
                            }
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        last14.forEach { d ->
                            Text(d.dayOfWeek.name.take(1), Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        }
                    }
                }
            }
            Text(
                "Snoozing is fine — but if one task keeps getting snoozed, consider breaking it into a smaller first step.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.fillMaxSize())
        }
    }
}

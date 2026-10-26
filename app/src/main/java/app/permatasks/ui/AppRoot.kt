package app.permatasks.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import app.permatasks.container
import app.permatasks.data.ProjectEntity
import app.permatasks.data.TaskEntity
import app.permatasks.ui.drive.DriveScreen
import app.permatasks.ui.lock.LockScreen
import app.permatasks.ui.lock.PinPad
import app.permatasks.ui.lock.SetupPinScreen
import app.permatasks.ui.settings.SettingsScreen
import app.permatasks.ui.settings.SetupChecklistCard
import app.permatasks.ui.settings.rememberSetupItems
import app.permatasks.ui.tasks.QuickAddPanel
import app.permatasks.ui.tasks.TaskDetailScreen
import app.permatasks.ui.tasks.TaskSection
import app.permatasks.ui.tasks.TaskSections
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Deep-link requests coming from notifications and widgets. */
object PendingNav {
    val taskId = MutableStateFlow<String?>(null)
}

@Composable
fun AppRoot() {
    val c = LocalContext.current.container
    val unlocked by c.lock.unlocked.collectAsState()
    var pinSet by remember { mutableStateOf(c.pins.isSet) }
    when {
        !pinSet -> SetupPinScreen(onDone = { pinSet = true })
        !unlocked -> LockScreen()
        else -> MainNav()
    }
}

private enum class Tab(val route: String, val label: String, val icon: ImageVector) {
    Today("today", "Today", Icons.Outlined.Today),
    Upcoming("upcoming", "Upcoming", Icons.Outlined.CalendarMonth),
    Browse("browse", "Browse", Icons.Outlined.Folder),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainNav() {
    val nav = rememberNavController()
    val pending by PendingNav.taskId.collectAsState()
    LaunchedEffect(pending) {
        pending?.let { nav.navigate("task/$it"); PendingNav.taskId.value = null }
    }
    NavHost(nav, startDestination = Tab.Today.route) {
        composable(Tab.Today.route) { TabScaffold(nav, Tab.Today) { TodayContent(nav) } }
        composable(Tab.Upcoming.route) { TabScaffold(nav, Tab.Upcoming) { UpcomingContent(nav) } }
        composable(Tab.Browse.route) { TabScaffold(nav, Tab.Browse) { BrowseContent(nav) } }
        composable("project/{id}") { entry ->
            val id = entry.arguments?.getString("id")!!
            ProjectScreen(nav, id.takeIf { it != INBOX })
        }
        composable("task/{id}") { entry ->
            TaskDetailScreen(entry.arguments?.getString("id")!!, onBack = { nav.popBackStack() }, onOpenTask = { nav.navigate("task/$it") })
        }
        composable("settings") { SettingsScreen(onBack = { nav.popBackStack() }, onChangePin = { nav.navigate("changepin") }) }
        composable("changepin") { ChangePin(onDone = { nav.popBackStack() }) }
        composable("drive") { DriveScreen(onExit = { nav.popBackStack() }) }
        composable("logbook") { LogbookScreen(nav) }
    }
}

private const val INBOX = "inbox"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TabScaffold(nav: NavHostController, tab: Tab, content: @Composable () -> Unit) {
    var adding by remember { mutableStateOf(false) }
    val current by nav.currentBackStackEntryAsState()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(tab.label) },
                actions = {
                    IconButton(onClick = { nav.navigate("drive") }) { Icon(Icons.Outlined.DirectionsCar, "Drive mode") }
                    IconButton(onClick = { nav.navigate("settings") }) { Icon(Icons.Outlined.Settings, "Settings") }
                },
            )
        },
        floatingActionButton = { FloatingActionButton(onClick = { adding = true }) { Icon(Icons.Filled.Add, "Add task") } },
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = current?.destination?.route == t.route,
                        onClick = {
                            nav.navigate(t.route) {
                                popUpTo(Tab.Today.route) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(t.icon, null) },
                        label = { Text(t.label) },
                    )
                }
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding)) { content() }
    }
    if (adding) QuickAddSheet(null) { adding = false }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickAddSheet(projectId: String?, onClose: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onClose, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.imePadding().padding(bottom = 16.dp)) {
            QuickAddPanel(defaultProjectId = projectId, onAdded = {})
        }
    }
}

@Composable
private fun rememberProjects(): Map<String, ProjectEntity> {
    val repo = LocalContext.current.container.repo
    val list by remember { repo.projects.observeAll() }.collectAsState(initial = emptyList())
    return remember(list) { list.associateBy { it.id } }
}

@Composable
private fun rememberOpenTasks(): List<TaskEntity> {
    val repo = LocalContext.current.container.repo
    val list by remember { repo.tasks.observeOpen() }.collectAsState(initial = emptyList())
    return list
}

@Composable
private fun TodayContent(nav: NavHostController) {
    val repo = LocalContext.current.container.repo
    val tasks = rememberOpenTasks().filter { it.parentId == null }
    val projects = rememberProjects()
    val setup = rememberSetupItems()
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    val endOfToday = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
    val startOfToday = today.atStartOfDay(zone).toInstant().toEpochMilli()
    val overdue = tasks.filter { t -> t.dueAt != null && (if (t.hasTime) t.dueAt < System.currentTimeMillis() && t.dueAt < endOfToday else t.dueAt < startOfToday) }
    val dueToday = tasks.filter { it.dueAt != null && it.dueAt < endOfToday && it !in overdue }
    TaskSections(
        sections = listOf(
            TaskSection("Overdue", overdue, highlight = true),
            TaskSection(today.format(DateTimeFormatter.ofPattern("EEE d MMM")) + " · Today", dueToday),
        ),
        projects = projects,
        onOpen = { nav.navigate("task/${it.id}") },
        onComplete = { repo.complete(it.id) },
        empty = "Nothing due today. Tap + to add a task.",
        header = { item { SetupChecklistCard(setup) } },
    )
}

@Composable
private fun UpcomingContent(nav: NavHostController) {
    val repo = LocalContext.current.container.repo
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
                0 -> "Today"
                1 -> "Tomorrow"
                else -> d.format(DateTimeFormatter.ofPattern("EEEE d MMM"))
            }
            add(TaskSection(label, byDay[d].orEmpty()))
        }
        add(TaskSection("Later", byDay.filterKeys { it.isAfter(today.plusDays(13)) }.values.flatten()))
        add(TaskSection("No date", tasks.filter { it.dueAt == null }))
    }
    TaskSections(sections, projects, { nav.navigate("task/${it.id}") }, { repo.complete(it.id) }, "No tasks yet.")
}

@Composable
private fun BrowseContent(nav: NavHostController) {
    val repo = LocalContext.current.container.repo
    val scope = rememberCoroutineScope()
    val tasks = rememberOpenTasks()
    val projects by remember { repo.projects.observeAll() }.collectAsState(initial = emptyList())
    var newProject by remember { mutableStateOf<String?>(null) }
    LazyColumn {
        item {
            BrowseRow(Icons.Outlined.Inbox, "Inbox", tasks.count { it.projectId == null && it.parentId == null }, null) { nav.navigate("project/$INBOX") }
        }
        items(projects, key = { it.id }) { p ->
            BrowseRow(Icons.Outlined.Folder, p.name, tasks.count { it.projectId == p.id && it.parentId == null }, Color(p.color)) {
                nav.navigate("project/${p.id}")
            }
        }
        item { TextButton(onClick = { newProject = "" }, modifier = Modifier.padding(8.dp)) { Text("+ New project") } }
        item { BrowseRow(Icons.Outlined.History, "Completed & closed", null, null) { nav.navigate("logbook") } }
    }
    newProject?.let { name ->
        AlertDialog(
            onDismissRequest = { newProject = null },
            title = { Text("New project") },
            text = { OutlinedTextField(name, { newProject = it }, singleLine = true) },
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

private val PROJECT_COLORS = listOf(0xFF7E57C2, 0xFF26A69A, 0xFFEF5350, 0xFF42A5F5, 0xFFFFA726, 0xFF8D6E63, 0xFFEC407A, 0xFF66BB6A)

@Composable
private fun BrowseRow(icon: ImageVector, title: String, count: Int?, tint: Color?, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(icon, null, tint = tint ?: MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        count?.let { Text("$it", color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProjectScreen(nav: NavHostController, projectId: String?) {
    val repo = LocalContext.current.container.repo
    val scope = rememberCoroutineScope()
    val projects = rememberProjects()
    val project = projectId?.let { projects[it] }
    val tasks = rememberOpenTasks().filter { it.projectId == projectId && it.parentId == null }
    var adding by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(project?.name ?: "Inbox") },
                navigationIcon = { IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = { if (project != null) TextButton(onClick = { confirmDelete = true }) { Text("Delete") } },
            )
        },
        floatingActionButton = { FloatingActionButton(onClick = { adding = true }) { Icon(Icons.Filled.Add, "Add task") } },
    ) { padding ->
        Column(Modifier.padding(padding)) {
            TaskSections(
                listOf(
                    TaskSection("Scheduled", tasks.filter { it.dueAt != null }),
                    TaskSection("No date", tasks.filter { it.dueAt == null }),
                ),
                projects, { nav.navigate("task/${it.id}") }, { repo.complete(it.id) }, "No open tasks here.",
            )
        }
    }
    if (adding) QuickAddSheet(projectId) { adding = false }
    if (confirmDelete && project != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete ${project.name}?") },
            text = { Text("Its tasks are kept and moved to the Inbox.") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { repo.projects.detachTasks(project.id); repo.projects.delete(project) }
                    nav.popBackStack()
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LogbookScreen(nav: NavHostController) {
    val repo = LocalContext.current.container.repo
    val closed by remember { repo.tasks.observeClosed() }.collectAsState(initial = emptyList())
    val projects = rememberProjects()
    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Completed & closed") },
            navigationIcon = { IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
        )
    }) { padding ->
        Column(Modifier.padding(padding)) {
            TaskSections(listOf(TaskSection("Recently closed", closed)), projects, { nav.navigate("task/${it.id}") }, {}, "Nothing completed yet.")
        }
    }
}

@Composable
private fun ChangePin(onDone: () -> Unit) {
    val c = LocalContext.current.container
    val scope = rememberCoroutineScope()
    var verified by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    if (!verified) {
        PinPad("Enter current PIN", error ?: "", error != null, onSubmit = { pin ->
            scope.launch {
                if (withContext(Dispatchers.Default) { c.pins.verify(pin) }) verified = true else error = "Wrong PIN"
            }
        })
    } else {
        SetupPinScreen(onDone)
    }
}

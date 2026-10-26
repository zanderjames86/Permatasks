package app.permatasks.ui

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Notes
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Today
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material.icons.outlined.ViewAgenda
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.permatasks.container
import app.permatasks.data.TaskEntity
import app.permatasks.ui.browse.BrowseContent
import app.permatasks.ui.browse.FilterEditorScreen
import app.permatasks.ui.browse.FilterResultsScreen
import app.permatasks.ui.browse.INBOX
import app.permatasks.ui.browse.LogbookScreen
import app.permatasks.ui.browse.ProjectScreen
import app.permatasks.ui.browse.StatsScreen
import app.permatasks.ui.components.LocalSnackbar
import app.permatasks.ui.drive.DriveScreen
import app.permatasks.ui.lock.LockScreen
import app.permatasks.ui.lock.PinPad
import app.permatasks.ui.lock.SetupPinScreen
import app.permatasks.ui.notes.GraphScreen
import app.permatasks.ui.notes.NoteEditorScreen
import app.permatasks.ui.notes.NotesContent
import app.permatasks.ui.onboarding.OnboardingScreen
import app.permatasks.ui.search.SearchScreen
import app.permatasks.ui.settings.BackupScreen
import app.permatasks.ui.settings.PlacesScreen
import app.permatasks.ui.settings.SettingsScreen
import app.permatasks.ui.tasks.QuickAddPanel
import app.permatasks.ui.tasks.TaskDetailScreen
import app.permatasks.ui.today.TodayContent
import app.permatasks.ui.upcoming.UpcomingContent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URLEncoder
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** Deep-link requests coming from notifications and widgets. */
object PendingNav {
    val taskId = MutableStateFlow<String?>(null)
    val route = MutableStateFlow<String?>(null)
}

@Composable
fun AppRoot() {
    val c = LocalContext.current.container
    val unlocked by c.lock.unlocked.collectAsState()
    var onboarded by remember { mutableStateOf(c.settings.onboarded || c.pins.isSet) }
    var pinSet by remember { mutableStateOf(c.pins.isSet) }
    when {
        !onboarded -> OnboardingScreen { c.settings.onboarded = true; onboarded = true }
        !pinSet -> SetupPinScreen(onDone = { pinSet = true })
        !unlocked -> LockScreen()
        else -> MainNav()
    }
}

private enum class Tab(val route: String, val label: String, val icon: ImageVector, val selectedIcon: ImageVector) {
    Today("today", "Today", Icons.Outlined.Today, Icons.Filled.Today),
    Upcoming("upcoming", "Upcoming", Icons.Outlined.CalendarMonth, Icons.Filled.CalendarMonth),
    Browse("browse", "Browse", Icons.Outlined.Folder, Icons.Filled.Folder),
    Notes("notes", "Notes", Icons.AutoMirrored.Outlined.Notes, Icons.AutoMirrored.Outlined.Notes),
}

private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

@Composable
private fun MainNav() {
    val nav = rememberNavController()
    val snackbar = remember { SnackbarHostState() }
    val pendingTask by PendingNav.taskId.collectAsState()
    val pendingRoute by PendingNav.route.collectAsState()
    LaunchedEffect(pendingTask) { pendingTask?.let { nav.navigate("task/$it"); PendingNav.taskId.value = null } }
    LaunchedEffect(pendingRoute) { pendingRoute?.let { nav.navigate(it); PendingNav.route.value = null } }
    val openTask: (TaskEntity) -> Unit = { nav.navigate("task/${it.id}") }
    val slide = AnimatedContentTransitionScope.SlideDirection.Start
    CompositionLocalProvider(LocalSnackbar provides snackbar) {
        NavHost(
            nav, startDestination = Tab.Today.route,
            enterTransition = { fadeIn(tween(220)) + slideIntoContainer(slide, tween(260), initialOffset = { it / 12 }) },
            exitTransition = { fadeOut(tween(160)) },
            popEnterTransition = { fadeIn(tween(220)) },
            popExitTransition = { fadeOut(tween(160)) + slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.End, tween(240), targetOffset = { it / 12 }) },
        ) {
            composable(Tab.Today.route) { TabScaffold(nav, Tab.Today) { TodayContent(openTask) } }
            composable(Tab.Upcoming.route) { TabScaffold(nav, Tab.Upcoming) { UpcomingContent(openTask) } }
            composable(Tab.Browse.route) {
                TabScaffold(nav, Tab.Browse) {
                    BrowseContent(
                        onProject = { nav.navigate("project/$it") },
                        onFilter = { nav.navigate("filter/$it") },
                        onQuery = { nav.navigate("query/${enc(it)}") },
                        onNewFilter = { nav.navigate("filteredit/new") },
                        onLogbook = { nav.navigate("logbook") },
                        onStats = { nav.navigate("stats") },
                        onPlaces = { nav.navigate("places") },
                    )
                }
            }
            composable(Tab.Notes.route) { TabScaffold(nav, Tab.Notes) { NotesContent { nav.navigate("note/$it") } } }

            composable("task/{id}") { e ->
                TaskDetailScreen(
                    e.arguments?.getString("id")!!, onBack = { nav.popBackStack() },
                    onOpenTask = { nav.navigate("task/$it") }, onOpenNote = { nav.navigate("note/$it") },
                )
            }
            composable(
                "note/{id}?dictate={dictate}",
                arguments = listOf(navArgument("dictate") { type = NavType.BoolType; defaultValue = false }),
            ) { e ->
                NoteEditorScreen(
                    e.arguments?.getString("id")!!, e.arguments?.getBoolean("dictate") ?: false,
                    onBack = { nav.popBackStack() },
                    onOpenNote = { nav.navigate("note/$it") },
                    onOpenTask = { nav.navigate("task/$it") },
                )
            }
            composable("project/{id}") { e ->
                var adding by remember { mutableStateOf<Pair<String?, String?>?>(null) }
                val id = e.arguments?.getString("id")!!.takeIf { it != INBOX }
                ProjectScreen(id, onBack = { nav.popBackStack() }, onOpen = openTask, onAdd = { p, s -> adding = p to s })
                adding?.let { (p, s) -> QuickAddSheet(p, s) { adding = null } }
            }
            composable("filter/{id}") { e ->
                FilterResultsScreen(e.arguments?.getString("id"), null, { nav.popBackStack() }, openTask) { nav.navigate("filteredit/$it") }
            }
            composable("query/{q}") { e ->
                FilterResultsScreen(null, java.net.URLDecoder.decode(e.arguments?.getString("q") ?: "", "UTF-8"), { nav.popBackStack() }, openTask) {}
            }
            composable("filteredit/{id}") { e -> FilterEditorScreen(e.arguments?.getString("id")?.takeIf { it != "new" }) { nav.popBackStack() } }
            composable("logbook") { LogbookScreen({ nav.popBackStack() }, openTask) }
            composable("stats") { StatsScreen { nav.popBackStack() } }
            composable("places") { PlacesScreen { nav.popBackStack() } }
            composable("graph") { GraphScreen({ nav.popBackStack() }) { nav.navigate("note/$it") } }
            composable("search") { SearchScreen({ nav.popBackStack() }, openTask) { nav.navigate("note/$it") } }
            composable("settings") {
                SettingsScreen(
                    onBack = { nav.popBackStack() }, onChangePin = { nav.navigate("changepin") },
                    onBackup = { nav.navigate("backup") }, onPlaces = { nav.navigate("places") },
                )
            }
            composable("backup") { BackupScreen { nav.popBackStack() } }
            composable("changepin") { ChangePin(onDone = { nav.popBackStack() }) }
            composable("drive") { DriveScreen(onExit = { nav.popBackStack() }) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TabScaffold(nav: NavHostController, tab: Tab, content: @Composable () -> Unit) {
    val c = LocalContext.current.container
    val scope = rememberCoroutineScope()
    var adding by remember { mutableStateOf(false) }
    val current by nav.currentBackStackEntryAsState()
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    @Suppress("UNUSED_VARIABLE") val tick by c.settings.changes.collectAsState()

    fun newNote(dictate: Boolean) = scope.launch {
        val note = withContext(Dispatchers.IO) { c.notes.create() }
        nav.navigate("note/${note.id}?dictate=$dictate")
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        snackbarHost = { SnackbarHost(LocalSnackbar.current) },
        topBar = {
            LargeTopAppBar(
                title = {
                    Column {
                        Text(tab.label)
                        if (tab == Tab.Today && scroll.state.collapsedFraction < 0.5f) {
                            Text(
                                LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, d MMMM")),
                                style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { nav.navigate("search") }) { Icon(Icons.Outlined.Search, "Search") }
                    if (tab == Tab.Notes) {
                        IconButton(onClick = { nav.navigate("graph") }) { Icon(Icons.Outlined.Hub, "Graph") }
                        IconButton(onClick = { c.settings.notesGrid = !c.settings.notesGrid }) {
                            Icon(if (c.settings.notesGrid) Icons.Outlined.ViewAgenda else Icons.Outlined.GridView, "Layout")
                        }
                    } else {
                        IconButton(onClick = { nav.navigate("drive") }) { Icon(Icons.Outlined.DirectionsCar, "Drive mode") }
                    }
                    IconButton(onClick = { nav.navigate("settings") }) { Icon(Icons.Outlined.Settings, "Settings") }
                },
                scrollBehavior = scroll,
            )
        },
        floatingActionButton = {
            if (tab == Tab.Notes) {
                Column(horizontalAlignment = androidx.compose.ui.Alignment.End) {
                    SmallFloatingActionButton(onClick = { newNote(true) }, modifier = Modifier.padding(bottom = 12.dp)) { Icon(Icons.Outlined.Mic, "Voice note") }
                    ExtendedFloatingActionButton(onClick = { newNote(false) }, icon = { Icon(Icons.Filled.Add, null) }, text = { Text("New note") })
                }
            } else {
                ExtendedFloatingActionButton(onClick = { adding = true }, icon = { Icon(Icons.Filled.Add, null) }, text = { Text("Add task") })
            }
        },
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { t ->
                    val selected = current?.destination?.route == t.route
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            nav.navigate(t.route) {
                                popUpTo(Tab.Today.route) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(if (selected) t.selectedIcon else t.icon, null) },
                        label = { Text(t.label) },
                    )
                }
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding)) { content() }
    }
    if (adding) QuickAddSheet(null, null) { adding = false }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickAddSheet(projectId: String?, sectionId: String?, onClose: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onClose,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(Modifier.imePadding().padding(bottom = 8.dp)) {
            QuickAddPanel(defaultProjectId = projectId, sectionId = sectionId, onAdded = {})
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

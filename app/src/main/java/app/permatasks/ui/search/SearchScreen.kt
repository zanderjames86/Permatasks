package app.permatasks.ui.search

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Notes
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.permatasks.container
import app.permatasks.data.TaskEntity
import app.permatasks.ui.components.EmptyState
import app.permatasks.ui.components.ListRow
import app.permatasks.ui.components.SectionHeader
import app.permatasks.ui.tasks.SwipeableTaskRow
import app.permatasks.ui.tasks.rememberTaskActions
import app.permatasks.ui.today.rememberProjects
import kotlinx.coroutines.flow.flowOf

@Composable
fun SearchScreen(onBack: () -> Unit, onOpenTask: (TaskEntity) -> Unit, onOpenNote: (String) -> Unit) {
    val c = LocalContext.current.container
    var q by rememberSaveable { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    val tasks by remember(q) { if (q.length < 2) flowOf(emptyList()) else c.repo.tasks.search(q.trim()) }.collectAsState(initial = emptyList())
    val notes by remember(q) { if (q.length < 2) flowOf(emptyList()) else c.notes.notes.search(q.trim()) }.collectAsState(initial = emptyList())
    val projects = rememberProjects()
    val (actions, sheet) = rememberTaskActions()

    Scaffold(topBar = {
        Row(Modifier.statusBarsPadding().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
            TextField(
                q, { q = it }, Modifier.weight(1f).focusRequester(focus),
                placeholder = { Text("Search tasks and notes") },
                leadingIcon = { Icon(Icons.Outlined.Search, null) },
                singleLine = true,
                shape = RoundedCornerShape(28.dp),
                colors = TextFieldDefaults.colors(
                    focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh, unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                ),
            )
        }
    }) { padding ->
        Box(Modifier.padding(padding)) {
            if (q.length >= 2 && tasks.isEmpty() && notes.isEmpty()) {
                EmptyState(Icons.Outlined.Search, "No results", "Nothing matches \"$q\".")
            }
            LazyColumn {
                if (tasks.isNotEmpty()) {
                    item { SectionHeader("Tasks", tasks.size) }
                    items(tasks, key = { "t" + it.id }) { SwipeableTaskRow(it, projects[it.projectId], onOpenTask, actions) }
                }
                if (notes.isNotEmpty()) {
                    item { SectionHeader("Notes", notes.size) }
                    items(notes, key = { "n" + it.id }) { n ->
                        ListRow(n.title.ifBlank { "Untitled" }, n.plainText.take(120), Icons.AutoMirrored.Outlined.Notes, onClick = { onOpenNote(n.id) })
                    }
                }
            }
        }
    }
    sheet()
}

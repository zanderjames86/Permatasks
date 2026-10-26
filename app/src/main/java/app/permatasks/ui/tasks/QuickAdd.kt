package app.permatasks.ui.tasks

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import app.permatasks.container
import app.permatasks.domain.Priority
import app.permatasks.domain.QuickAddParser
import app.permatasks.ui.format.Formats
import app.permatasks.voice.VoiceInput
import kotlinx.coroutines.launch
import java.time.ZonedDateTime

/**
 * One-line task entry with a live preview of what was understood, e.g.
 * "Renew passport next monday 10am p1 #Admin".
 */
@Composable
fun QuickAddPanel(
    defaultProjectId: String? = null,
    sectionId: String? = null,
    startListening: Boolean = false,
    keepOpen: Boolean = true,
    onAdded: () -> Unit,
) {
    val context = LocalContext.current
    val repo = context.container.repo
    val scope = rememberCoroutineScope()
    val projects by remember { repo.projects.observeAll() }.collectAsState(initial = emptyList())
    var text by remember { mutableStateOf("") }
    var listening by remember { mutableStateOf(false) }
    var voiceError by remember { mutableStateOf<String?>(null) }
    var added by remember { mutableStateOf(0) }
    val focus = remember { FocusRequester() }
    val parsed = remember(text) { if (text.isBlank()) null else QuickAddParser.parse(text, ZonedDateTime.now()) }

    fun append(token: String) { text = (text.trimEnd() + " " + token + " ").trimStart() }

    fun submit() {
        val value = text
        if (value.isBlank()) return
        text = ""
        scope.launch {
            repo.quickAdd(value, defaultProjectId, sectionId = sectionId)
            added++
            onAdded()
        }
    }

    val voice = remember {
        VoiceInput(context, onPartial = { text = it }, onResult = { text = it }, onError = { voiceError = it }, onListening = { listening = it })
    }
    DisposableEffect(Unit) { onDispose { voice.stop() } }
    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) voice.start() else voiceError = "Microphone permission is needed for voice entry"
    }
    fun listen() {
        voiceError = null
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) voice.start()
        else micPermission.launch(Manifest.permission.RECORD_AUDIO)
    }
    LaunchedEffect(Unit) { if (startListening) listen() else focus.requestFocus() }

    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Box {
            if (text.isEmpty()) {
                Text(
                    if (listening) "Listening…" else "What needs doing?",
                    style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            BasicTextField(
                text, { text = it },
                textStyle = MaterialTheme.typography.titleLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { submit() }),
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
        }
        Text(
            "Try: tomorrow 6pm · every mon · p1 · #Project · @label",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        AnimatedVisibility(parsed != null && (parsed.due != null || parsed.recurrence != null || parsed.priority != Priority.P4 || parsed.project != null || parsed.labels.isNotEmpty())) {
            val p = parsed!!
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                p.due?.let {
                    val ms = it.toInstant().toEpochMilli()
                    PreviewChip(Formats.date(ms, p.hasTime), dateColor(ms, p.hasTime, false), Icons.Outlined.CalendarToday)
                }
                p.recurrence?.let { PreviewChip(it.describe(), MaterialTheme.colorScheme.primary, Icons.Outlined.Repeat) }
                if (p.priority != Priority.P4) PreviewChip("P${p.priority.level}", Color(Formats.priorityColor(p.priority)), Icons.Outlined.Flag)
                p.project?.let { PreviewChip(it, MaterialTheme.colorScheme.secondary, Icons.Outlined.Folder) }
                p.labels.forEach { PreviewChip("@$it", MaterialTheme.colorScheme.tertiary, null) }
            }
        }
        voiceError?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error) }
        Row(verticalAlignment = Alignment.CenterVertically) {
            var dateMenu by remember { mutableStateOf(false) }
            var prioMenu by remember { mutableStateOf(false) }
            var projMenu by remember { mutableStateOf(false) }
            Box {
                IconButton(onClick = { dateMenu = true }) { Icon(Icons.Outlined.CalendarToday, "Date") }
                DropdownMenu(dateMenu, { dateMenu = false }) {
                    listOf("today", "tomorrow", "tonight", "next week", "in 1 hour").forEach { d ->
                        DropdownMenuItem(text = { Text(d.replaceFirstChar(Char::uppercase)) }, onClick = { append(d); dateMenu = false })
                    }
                }
            }
            Box {
                IconButton(onClick = { prioMenu = true }) { Icon(Icons.Outlined.Flag, "Priority") }
                DropdownMenu(prioMenu, { prioMenu = false }) {
                    Priority.entries.forEach { p ->
                        DropdownMenuItem(
                            text = { Text("Priority ${p.level} · ${p.label}") },
                            leadingIcon = { Icon(Icons.Outlined.Flag, null, tint = Color(Formats.priorityColor(p))) },
                            onClick = { append("p${p.level}"); prioMenu = false },
                        )
                    }
                }
            }
            Box {
                IconButton(onClick = { projMenu = true }) { Icon(Icons.Outlined.Folder, "Project") }
                DropdownMenu(projMenu, { projMenu = false }) {
                    DropdownMenuItem(text = { Text("Inbox") }, onClick = { append("#Inbox"); projMenu = false })
                    projects.forEach { p ->
                        DropdownMenuItem(
                            text = { Text(p.name) },
                            leadingIcon = { Icon(Icons.Outlined.Folder, null, tint = Color(p.color)) },
                            onClick = { append("#" + p.name.replace(' ', '_')); projMenu = false },
                        )
                    }
                }
            }
            IconButton(onClick = { if (listening) voice.stop() else listen() }) {
                Icon(if (listening) Icons.Filled.Mic else Icons.Outlined.Mic, "Speak", tint = if (listening) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.weight(1f))
            if (added > 0 && keepOpen) Text("$added added", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(8.dp))
            FilledIconButton(onClick = { submit() }, enabled = text.isNotBlank()) { Icon(Icons.AutoMirrored.Filled.Send, "Add") }
        }
    }
}

@Composable
private fun PreviewChip(label: String, color: Color, icon: androidx.compose.ui.graphics.vector.ImageVector?) {
    AssistChip(
        onClick = {},
        label = { Text(label) },
        leadingIcon = icon?.let { { Icon(it, null, Modifier.size(16.dp), tint = color) } },
        colors = AssistChipDefaults.assistChipColors(labelColor = color),
    )
}

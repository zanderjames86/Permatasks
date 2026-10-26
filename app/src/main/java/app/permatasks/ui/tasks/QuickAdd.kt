package app.permatasks.ui.tasks

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicNone
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import app.permatasks.container
import app.permatasks.domain.QuickAddParser
import app.permatasks.ui.format.Formats
import app.permatasks.voice.VoiceInput
import kotlinx.coroutines.launch
import java.time.ZonedDateTime

/**
 * One-line task entry with live preview of what was understood, e.g.
 * "Renew passport next monday 10am p1 #admin every! 10 years".
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun QuickAddPanel(
    defaultProjectId: String? = null,
    startListening: Boolean = false,
    onAdded: () -> Unit,
) {
    val context = LocalContext.current
    val repo = context.container.repo
    val scope = rememberCoroutineScope()
    var text by remember { mutableStateOf("") }
    var listening by remember { mutableStateOf(false) }
    var voiceError by remember { mutableStateOf<String?>(null) }
    val focus = remember { FocusRequester() }
    val parsed = remember(text) { if (text.isBlank()) null else QuickAddParser.parse(text, ZonedDateTime.now()) }

    fun submit() {
        val value = text
        if (value.isBlank()) return
        text = ""
        scope.launch {
            repo.quickAdd(value, defaultProjectId)
            onAdded()
        }
    }

    val voice = remember {
        VoiceInput(
            context,
            onPartial = { text = it },
            onResult = { text = it },
            onError = { voiceError = it },
            onListening = { listening = it },
        )
    }
    DisposableEffect(Unit) { onDispose { voice.stop() } }
    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) voice.start() else voiceError = "Microphone permission is needed for voice entry"
    }
    fun listen() {
        voiceError = null
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            voice.start()
        } else {
            micPermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    LaunchedEffect(Unit) {
        if (startListening) listen() else focus.requestFocus()
    }

    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.weight(1f).focusRequester(focus),
                placeholder = { Text("e.g. Pay rent tomorrow 9am p1 #home") },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { submit() }),
                maxLines = 3,
            )
            IconButton(onClick = { if (listening) voice.stop() else listen() }) {
                Icon(if (listening) Icons.Filled.Mic else Icons.Filled.MicNone, "Speak", tint = if (listening) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
            }
            FilledIconButton(onClick = { submit() }, enabled = text.isNotBlank()) {
                Icon(Icons.AutoMirrored.Filled.Send, "Add")
            }
        }
        if (listening) Text("Listening… (on-device)", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        voiceError?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error) }
        parsed?.let { p ->
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                p.due?.let { AssistChip(onClick = {}, label = { Text(Formats.date(it.toInstant().toEpochMilli(), p.hasTime)) }) }
                p.recurrence?.let { AssistChip(onClick = {}, label = { Text(it.describe()) }) }
                if (p.priority.level < 4) AssistChip(onClick = {}, label = { Text("P${p.priority.level} ${p.priority.label}") })
                p.project?.let { AssistChip(onClick = {}, label = { Text("#$it") }) }
                p.labels.forEach { AssistChip(onClick = {}, label = { Text("@$it") }) }
            }
        }
    }
}

package app.permatasks.ui.drive

import android.Manifest
import android.content.pm.PackageManager
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import app.permatasks.container
import app.permatasks.ui.format.Formats
import app.permatasks.voice.VoiceInput
import app.permatasks.widget.TaskWidget
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** Big targets, voice in, speech out — for use on a dashboard mount. */
@Composable
fun DriveScreen(onExit: () -> Unit) {
    val context = LocalContext.current
    val c = context.container
    val scope = rememberCoroutineScope()
    val tasks by remember { c.repo.tasks.observeOpen().map(TaskWidget::outstanding) }.collectAsState(initial = emptyList())
    var index by remember { mutableIntStateOf(0) }
    var heard by remember { mutableStateOf<String?>(null) }
    var listening by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        val window = (context as FragmentActivity).window
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            c.speaker.stop()
        }
    }

    val voice = remember {
        VoiceInput(
            context,
            onPartial = { heard = it },
            onResult = { text ->
                heard = text
                scope.launch {
                    val task = c.repo.quickAdd(text)
                    if (task != null) {
                        val due = task.dueAt?.let { ", " + Formats.date(it, task.hasTime) } ?: ""
                        c.speaker.speak("Added: ${task.title}$due")
                    }
                }
            },
            onError = { heard = it; c.speaker.speak(it) },
            onListening = { listening = it },
        )
    }
    DisposableEffect(Unit) { onDispose { voice.stop() } }
    val mic = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { if (it) voice.start() }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxSize().safeDrawingPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Drive mode", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
                IconButton(onClick = onExit, modifier = Modifier.size(64.dp)) { Icon(Icons.Filled.Close, "Exit", Modifier.size(40.dp)) }
            }

            val current = tasks.getOrNull(index.coerceIn(0, (tasks.size - 1).coerceAtLeast(0)))
            Card(Modifier.fillMaxWidth().weight(1f)) {
                Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (current == null) {
                        Text("Nothing due. Drive safe.", fontSize = 28.sp)
                    } else {
                        Text("${index + 1} of ${tasks.size}", style = MaterialTheme.typography.titleMedium)
                        Text(current.title, fontSize = 32.sp, fontWeight = FontWeight.SemiBold, lineHeight = 38.sp)
                        current.dueAt?.let { Text(Formats.dueRelative(it, current.hasTime), fontSize = 20.sp) }
                        Spacer(Modifier.weight(1f))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Button(onClick = { scope.launch { c.repo.complete(current.id) } }, modifier = Modifier.weight(1f).height(80.dp)) {
                                Text("Done", fontSize = 24.sp)
                            }
                            FilledTonalButton(onClick = { scope.launch { c.repo.snooze(current.id, 60) } }, modifier = Modifier.weight(1f).height(80.dp)) {
                                Text("In 1 hour", fontSize = 22.sp)
                            }
                        }
                        if (tasks.size > 1) {
                            FilledTonalButton(onClick = { index = (index + 1) % tasks.size }, modifier = Modifier.fillMaxWidth().height(64.dp)) {
                                Text("Next task", fontSize = 20.sp)
                            }
                        }
                    }
                }
            }

            heard?.let { Text(it, fontSize = 20.sp, color = MaterialTheme.colorScheme.primary) }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = {
                        if (listening) voice.stop()
                        else if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) voice.start()
                        else mic.launch(Manifest.permission.RECORD_AUDIO)
                    },
                    modifier = Modifier.weight(1f).height(110.dp),
                    colors = if (listening) ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error) else ButtonDefaults.buttonColors(),
                ) {
                    Icon(Icons.Filled.Mic, null, Modifier.size(40.dp))
                    Text(if (listening) " Listening" else " Add task", fontSize = 22.sp)
                }
                FilledTonalButton(
                    onClick = {
                        c.speaker.speak(
                            if (tasks.isEmpty()) "Nothing is due today."
                            else "You have ${tasks.size} tasks. " + tasks.take(8).joinToString(". ") { it.title },
                        )
                    },
                    modifier = Modifier.weight(1f).height(110.dp),
                ) {
                    Icon(Icons.AutoMirrored.Filled.VolumeUp, null, Modifier.size(40.dp))
                    Text(" Read out", fontSize = 22.sp)
                }
            }
        }
    }
}

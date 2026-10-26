package app.permatasks.nag

import android.app.KeyguardManager
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import app.permatasks.container
import app.permatasks.data.TaskEntity
import app.permatasks.domain.Recurrence
import app.permatasks.ui.MainActivity
import app.permatasks.ui.format.Formats
import app.permatasks.ui.theme.PermatasksTheme
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * The "Have you really done it?" screen. Opens full-screen over the lock screen when a
 * reminder escalates, or from the notification's "More…" button.
 */
class NagActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE or WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val taskId = intent.getStringExtra(EXTRA_TASK_ID) ?: return finish()
        val c = container
        val keyguard = getSystemService(KeyguardManager::class.java)

        setContent {
            PermatasksTheme {
                val task by remember { c.repo.tasks.observe(taskId) }.collectAsState(initial = null)
                var revealed by remember { mutableStateOf(!(c.settings.hideOnLockScreen && keyguard.isKeyguardLocked)) }
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
                    val t = task
                    when {
                        !revealed -> Hidden(onUnlock = {
                            keyguard.requestDismissKeyguard(this, object : KeyguardManager.KeyguardDismissCallback() {
                                override fun onDismissSucceeded() { revealed = true }
                            })
                        })
                        t != null -> NagContent(t, ::act)
                    }
                }
            }
        }
    }

    private fun act(action: suspend () -> Unit) {
        lifecycleScope.launch {
            action()
            finish()
        }
    }

    @Composable
    private fun Hidden(onUnlock: () -> Unit) {
        Column(
            Modifier.fillMaxSize().safeDrawingPadding().padding(32.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text("A task needs your attention", style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(24.dp))
            Button(onClick = onUnlock, modifier = Modifier.fillMaxWidth().height(64.dp)) { Text("Unlock to see it", fontSize = 18.sp) }
        }
    }

    @OptIn(ExperimentalLayoutApi::class)
    @Composable
    private fun NagContent(task: TaskEntity, act: (suspend () -> Unit) -> Unit) {
        val repo = container.repo
        val zone = ZoneId.systemDefault()
        val recurring = Recurrence.decode(task.recurrence)
        Column(
            Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Spacer(Modifier.height(24.dp))
            Text("Have you done it?", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Text(task.title, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.SemiBold)
            task.dueAt?.let { Text(Formats.dueRelative(it, task.hasTime), style = MaterialTheme.typography.titleMedium) }
            val stats = listOfNotNull(
                "Reminded ${task.nagCount}×".takeIf { task.nagCount > 0 },
                "Snoozed ${task.snoozeCount}×".takeIf { task.snoozeCount > 0 },
                recurring?.describe(),
            ).joinToString(" · ")
            if (stats.isNotEmpty()) Text(stats, style = MaterialTheme.typography.bodyMedium)
            if (task.description.isNotBlank()) Text(task.description, style = MaterialTheme.typography.bodyLarge)

            Spacer(Modifier.height(8.dp))
            Button(onClick = { act { repo.complete(task.id) } }, modifier = Modifier.fillMaxWidth().height(72.dp)) {
                Text("Yes, it's done", fontSize = 20.sp)
            }

            Text("Not yet — remind me again in", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(10 to "10 min", 30 to "30 min", 60 to "1 hour", 180 to "3 hours").forEach { (m, label) ->
                    FilledTonalButton(onClick = { act { repo.snooze(task.id, m) } }) { Text(label) }
                }
                FilledTonalButton(onClick = {
                    act {
                        var at = LocalDate.now(zone).atTime(LocalTime.of(18, 0)).atZone(zone)
                        if (at.toInstant().toEpochMilli() <= System.currentTimeMillis()) at = at.plusHours(2).withHour(21)
                        repo.snoozeUntil(task.id, at.toInstant().toEpochMilli())
                    }
                }) { Text("This evening") }
                FilledTonalButton(onClick = {
                    act {
                        val start = container.settings.dayStart
                        val at = LocalDate.now(zone).plusDays(1).atTime(LocalTime.of(start / 60, start % 60)).atZone(zone)
                        repo.snoozeUntil(task.id, at.toInstant().toEpochMilli())
                    }
                }) { Text("Tomorrow morning") }
            }

            OutlinedButton(onClick = {
                startActivity(
                    Intent(this@NagActivity, MainActivity::class.java)
                        .putExtra(MainActivity.EXTRA_TASK_ID, task.id)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                )
                finish()
            }, modifier = Modifier.fillMaxWidth()) { Text("Move it to another date…") }

            if (recurring != null) {
                OutlinedButton(onClick = { act { repo.skip(task.id) } }, modifier = Modifier.fillMaxWidth()) {
                    Text("Skip this time (keep the repeat)")
                }
            }
            TextButton(onClick = { act { repo.abandon(task.id, "Marked won't do from reminder") } }, modifier = Modifier.fillMaxWidth()) {
                Text("I won't do this — close it")
            }
        }
    }

    companion object {
        const val EXTRA_TASK_ID = "task_id"
        const val EXTRA_ALARM = "alarm"
    }
}

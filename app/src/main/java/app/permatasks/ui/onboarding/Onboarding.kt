package app.permatasks.ui.onboarding

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

private data class Page(val icon: ImageVector, val title: String, val body: String)

private val pages = listOf(
    Page(Icons.Outlined.NotificationsActive, "Reminders that don't give up", "Permatasks keeps nudging until you say it's done, snooze it, or move it. Ignore it, and it escalates to a full-screen alarm."),
    Page(Icons.Outlined.AutoAwesome, "Everything in one place", "Tasks, projects, filters, a calendar and Notion-style notes with checklists that nag. Remind by time, place, Bluetooth or calendar event."),
    Page(Icons.Outlined.Lock, "Private by design", "No internet access, no accounts. Everything is encrypted on this phone, behind your PIN and fingerprint. Backups are encrypted with a passphrase only you know."),
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OnboardingScreen(onDone: () -> Unit) {
    val pager = rememberPagerState { pages.size }
    val scope = rememberCoroutineScope()
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp)) {
            Row { Spacer(Modifier.weight(1f)); TextButton(onClick = onDone) { Text("Skip") } }
            HorizontalPager(pager, Modifier.weight(1f)) { i ->
                val p = pages[i]
                Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Box(Modifier.size(160.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
                        Icon(p.icon, null, Modifier.size(80.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                    Spacer(Modifier.height(40.dp))
                    Text(p.title, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(16.dp))
                    Text(p.body, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                }
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalArrangement = Arrangement.Center) {
                repeat(pages.size) { i ->
                    Box(
                        Modifier.padding(4.dp).height(8.dp).width(if (pager.currentPage == i) 24.dp else 8.dp).clip(CircleShape)
                            .background(if (pager.currentPage == i) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest),
                    )
                }
            }
            Button(
                onClick = { if (pager.currentPage == pages.lastIndex) onDone() else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } },
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) { Text(if (pager.currentPage == pages.lastIndex) "Get started" else "Next") }
        }
    }
}

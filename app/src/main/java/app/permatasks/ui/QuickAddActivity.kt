package app.permatasks.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import app.permatasks.container
import app.permatasks.ui.tasks.QuickAddPanel
import app.permatasks.ui.theme.PermatasksTheme
import kotlinx.coroutines.launch

/**
 * Add a task without opening (or unlocking) the app: from the widget, the Quick Settings
 * tile, a launcher shortcut, or "Share → Permatasks" from any other app.
 * It can only add tasks, never show existing ones, so it doesn't need the app lock.
 */
class QuickAddActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (intent?.action == Intent.ACTION_SEND) {
            val shared = listOfNotNull(
                intent.getStringExtra(Intent.EXTRA_SUBJECT),
                intent.getStringExtra(Intent.EXTRA_TEXT),
            ).joinToString(" ").trim()
            if (shared.isNotEmpty()) {
                lifecycleScope.launch {
                    container.repo.quickAdd(shared)
                    Toast.makeText(this@QuickAddActivity, "Added to Permatasks", Toast.LENGTH_SHORT).show()
                    finish()
                }
                return
            }
        }
        val voice = intent?.getBooleanExtra(EXTRA_VOICE, false) == true
        setContent {
            PermatasksTheme {
                Box(
                    Modifier.fillMaxSize().clickable(remember { MutableInteractionSource() }, null) { finish() },
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    Surface(
                        Modifier.fillMaxWidth().imePadding().navigationBarsPadding().clickable(remember { MutableInteractionSource() }, null) {},
                        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                        tonalElevation = 6.dp,
                    ) {
                        QuickAddPanel(startListening = voice, onAdded = {
                            Toast.makeText(this@QuickAddActivity, "Task added", Toast.LENGTH_SHORT).show()
                            finish()
                        })
                    }
                }
            }
        }
    }

    companion object {
        const val EXTRA_VOICE = "voice"
    }
}

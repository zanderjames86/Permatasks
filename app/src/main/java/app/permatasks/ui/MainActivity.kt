package app.permatasks.ui

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import app.permatasks.ui.theme.PermatasksTheme

/** A FragmentActivity because BiometricPrompt needs one. */
class MainActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // No screenshots, screen recording, or previews in the recent-apps list.
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        enableEdgeToEdge()
        handle(intent)
        setContent { PermatasksTheme { AppRoot() } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    private fun handle(intent: Intent?) {
        intent?.getStringExtra(EXTRA_TASK_ID)?.let { PendingNav.taskId.value = it }
        if (intent?.getBooleanExtra(EXTRA_DRIVE, false) == true) PendingNav.route.value = "drive"
    }

    companion object {
        const val EXTRA_TASK_ID = "task_id"
        const val EXTRA_DRIVE = "drive"
    }
}

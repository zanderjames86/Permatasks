package app.permatasks.security

import android.os.SystemClock
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import app.permatasks.data.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Tracks whether the UI is unlocked. The app starts locked on every cold start and relocks
 * once it has been in the background longer than the configured grace period.
 */
class AppLock(private val settings: Settings) : DefaultLifecycleObserver {
    private val _unlocked = MutableStateFlow(false)
    val unlocked: StateFlow<Boolean> = _unlocked
    private var backgroundedAt: Long? = null

    fun install() {
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }

    fun unlock() {
        _unlocked.value = true
    }

    fun lock() {
        _unlocked.value = false
    }

    override fun onStop(owner: LifecycleOwner) {
        backgroundedAt = SystemClock.elapsedRealtime()
    }

    override fun onStart(owner: LifecycleOwner) {
        val since = backgroundedAt ?: return
        backgroundedAt = null
        if (SystemClock.elapsedRealtime() - since >= settings.lockTimeoutSeconds * 1000L) lock()
    }
}

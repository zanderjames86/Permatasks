package app.permatasks.data

import android.content.Context
import app.permatasks.domain.NagPolicy
import app.permatasks.domain.Priority
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Non-secret preferences. [changes] ticks whenever anything changes so Compose can recompose. */
class Settings(context: Context) {
    private val prefs = context.getSharedPreferences("permatasks_settings", Context.MODE_PRIVATE)
    private val _changes = MutableStateFlow(0)
    val changes: StateFlow<Int> = _changes

    private fun edit(block: android.content.SharedPreferences.Editor.() -> Unit) {
        prefs.edit().apply(block).apply()
        _changes.value++
    }

    var biometricEnabled: Boolean
        get() = prefs.getBoolean("biometric", false)
        set(v) = edit { putBoolean("biometric", v) }

    /** 0 = lock as soon as the app leaves the screen. */
    var lockTimeoutSeconds: Int
        get() = prefs.getInt("lock_timeout", 60)
        set(v) = edit { putInt("lock_timeout", v) }

    var hideOnLockScreen: Boolean
        get() = prefs.getBoolean("hide_lockscreen", true)
        set(v) = edit { putBoolean("hide_lockscreen", v) }

    /** Pause nags while Bedtime mode / Do Not Disturb is on, and catch up afterwards. */
    var followBedtime: Boolean
        get() = prefs.getBoolean("follow_bedtime", true)
        set(v) = edit { putBoolean("follow_bedtime", v) }

    var quietHoursEnabled: Boolean
        get() = prefs.getBoolean("quiet_enabled", false)
        set(v) = edit { putBoolean("quiet_enabled", v) }

    /** Minutes after midnight. */
    var quietStart: Int
        get() = prefs.getInt("quiet_start", 22 * 60 + 30)
        set(v) = edit { putInt("quiet_start", v) }

    var quietEnd: Int
        get() = prefs.getInt("quiet_end", 7 * 60)
        set(v) = edit { putInt("quiet_end", v) }

    var urgentBreaksQuiet: Boolean
        get() = prefs.getBoolean("urgent_breaks_quiet", false)
        set(v) = edit { putBoolean("urgent_breaks_quiet", v) }

    /** When date-only tasks start nagging, minutes after midnight. */
    var dayStart: Int
        get() = prefs.getInt("day_start", 9 * 60)
        set(v) = edit { putInt("day_start", v) }

    var speakNags: Boolean
        get() = prefs.getBoolean("speak_nags", false)
        set(v) = edit { putBoolean("speak_nags", v) }

    var carAddress: String?
        get() = prefs.getString("car_address", null)
        set(v) = edit { putString("car_address", v) }

    var carName: String?
        get() = prefs.getString("car_name", null)
        set(v) = edit { putString("car_name", v) }

    var onboarded: Boolean
        get() = prefs.getBoolean("onboarded", false)
        set(v) = edit { putBoolean("onboarded", v) }

    /** 0 = follow system, 1 = light, 2 = dark. */
    var themeMode: Int
        get() = prefs.getInt("theme_mode", 0)
        set(v) = edit { putInt("theme_mode", v) }

    /** "dynamic" (wallpaper) or one of the built-in palettes. */
    var palette: String
        get() = prefs.getString("palette", "dynamic") ?: "dynamic"
        set(v) = edit { putString("palette", v) }

    var autoBackup: Boolean
        get() = prefs.getBoolean("auto_backup", false)
        set(v) = edit { putBoolean("auto_backup", v) }

    var backupUri: String?
        get() = prefs.getString("backup_uri", null)
        set(v) = edit { putString("backup_uri", v) }

    var lastBackupAt: Long
        get() = prefs.getLong("last_backup", 0)
        set(v) = edit { putLong("last_backup", v) }

    var lastBackupError: String?
        get() = prefs.getString("last_backup_error", null)
        set(v) = edit { putString("last_backup_error", v) }

    var notesGrid: Boolean
        get() = prefs.getBoolean("notes_grid", true)
        set(v) = edit { putBoolean("notes_grid", v) }

    fun policy(priority: Priority): NagPolicy {
        val d = NagPolicy.default(priority)
        val k = priority.name.lowercase()
        return NagPolicy(
            repeatMinutes = prefs.getInt("nag_${k}_repeat", d.repeatMinutes),
            fullScreen = prefs.getBoolean("nag_${k}_fullscreen", d.fullScreen),
            escalateAfter = prefs.getInt("nag_${k}_escalate", d.escalateAfter),
        )
    }

    fun setPolicy(priority: Priority, policy: NagPolicy) {
        val k = priority.name.lowercase()
        edit {
            putInt("nag_${k}_repeat", policy.repeatMinutes)
            putBoolean("nag_${k}_fullscreen", policy.fullScreen)
            putInt("nag_${k}_escalate", policy.escalateAfter)
        }
    }
}

package app.permatasks.security

import android.content.Context
import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/** Stores a salted PBKDF2 hash of the PIN and applies an escalating lockout after wrong guesses. */
class PinStore(context: Context) {
    private val prefs = context.getSharedPreferences("permatasks_lock", Context.MODE_PRIVATE)

    val isSet: Boolean get() = prefs.contains(KEY_HASH)

    fun set(pin: String) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        prefs.edit()
            .putString(KEY_SALT, Base64.encodeToString(salt, Base64.NO_WRAP))
            .putString(KEY_HASH, Base64.encodeToString(hash(pin, salt), Base64.NO_WRAP))
            .putInt(KEY_FAILS, 0)
            .putLong(KEY_LOCKED_UNTIL, 0)
            .apply()
    }

    /** Milliseconds the user must wait before trying again, or 0. */
    fun lockoutRemaining(now: Long = System.currentTimeMillis()): Long =
        (prefs.getLong(KEY_LOCKED_UNTIL, 0) - now).coerceAtLeast(0)

    fun verify(pin: String, now: Long = System.currentTimeMillis()): Boolean {
        if (lockoutRemaining(now) > 0) return false
        val salt = Base64.decode(prefs.getString(KEY_SALT, null) ?: return false, Base64.NO_WRAP)
        val expected = Base64.decode(prefs.getString(KEY_HASH, null) ?: return false, Base64.NO_WRAP)
        val ok = MessageDigest.isEqual(expected, hash(pin, salt))
        if (ok) {
            prefs.edit().putInt(KEY_FAILS, 0).putLong(KEY_LOCKED_UNTIL, 0).apply()
        } else {
            val fails = prefs.getInt(KEY_FAILS, 0) + 1
            // 5 free attempts, then 30s, 60s, 120s ... capped at one hour.
            val wait = if (fails < 5) 0L else (30_000L shl (fails - 5).coerceAtMost(7)).coerceAtMost(3_600_000L)
            prefs.edit().putInt(KEY_FAILS, fails).putLong(KEY_LOCKED_UNTIL, now + wait).apply()
        }
        return ok
    }

    private fun hash(pin: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, 150_000, 256)
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
    }

    private companion object {
        const val KEY_HASH = "pin_hash"
        const val KEY_SALT = "pin_salt"
        const val KEY_FAILS = "pin_fails"
        const val KEY_LOCKED_UNTIL = "pin_locked_until"
    }
}

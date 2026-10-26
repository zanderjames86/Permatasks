package app.permatasks.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.security.keystore.StrongBoxUnavailableException
import android.util.Base64
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * The SQLCipher passphrase is 32 random bytes, stored only after being wrapped with an
 * AES-GCM key that lives in the Android Keystore (the Pixel's Titan M2 chip when available).
 * The wrapping key never leaves the hardware, so a copied database file is useless.
 *
 * The key deliberately does not require user authentication: reminders must be able to read
 * the database in the background while the phone is locked. The app lock guards the UI.
 */
object DatabaseKey {
    private const val ALIAS = "permatasks_db_kek"
    private const val PREFS = "permatasks_keys"
    private const val PREF_WRAPPED = "db_key"

    @Volatile private var cached: ByteArray? = null

    @Synchronized
    fun passphrase(context: Context): ByteArray {
        cached?.let { return it.copyOf() }
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val stored = prefs.getString(PREF_WRAPPED, null)
        val key = if (stored != null) {
            unwrap(Base64.decode(stored, Base64.NO_WRAP))
        } else {
            ByteArray(32).also { SecureRandom().nextBytes(it) }.also { raw ->
                prefs.edit().putString(PREF_WRAPPED, Base64.encodeToString(wrap(raw), Base64.NO_WRAP)).commit()
            }
        }
        cached = key
        return key.copyOf()
    }

    private fun keystoreKey(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        fun spec(strongBox: Boolean) = KeyGenParameterSpec.Builder(
            ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setIsStrongBoxBacked(strongBox)
            .build()
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        return try {
            gen.init(spec(strongBox = true))
            gen.generateKey()
        } catch (_: StrongBoxUnavailableException) {
            gen.init(spec(strongBox = false))
            gen.generateKey()
        }
    }

    private fun wrap(raw: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, keystoreKey())
        return cipher.iv + cipher.doFinal(raw)
    }

    private fun unwrap(blob: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, keystoreKey(), GCMParameterSpec(128, blob, 0, 12))
        return cipher.doFinal(blob, 12, blob.size - 12)
    }
}

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

    private val cache = mutableMapOf<String, ByteArray>()

    fun passphrase(context: Context): ByteArray = secret(context, "db_key")

    /**
     * A random 256-bit secret named [name], created on first use and stored only in wrapped
     * form. Used for the database, the encrypted file vault and the backup key.
     */
    @Synchronized
    fun secret(context: Context, name: String): ByteArray {
        cache[name]?.let { return it.copyOf() }
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val stored = prefs.getString(name, null)
        val key = if (stored != null) {
            unwrap(Base64.decode(stored, Base64.NO_WRAP))
        } else {
            ByteArray(32).also { SecureRandom().nextBytes(it) }.also { store(context, name, it) }
        }
        cache[name] = key
        return key.copyOf()
    }

    /** Stores caller-provided key material (e.g. a passphrase-derived backup key). */
    @Synchronized
    fun store(context: Context, name: String, raw: ByteArray) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(name, Base64.encodeToString(wrap(raw), Base64.NO_WRAP)).commit()
        cache[name] = raw.copyOf()
    }

    @Synchronized
    fun has(context: Context, name: String): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).contains(name)

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

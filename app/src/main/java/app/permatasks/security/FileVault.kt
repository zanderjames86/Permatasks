package app.permatasks.security

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.SecureRandom
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Encrypted storage for note images (AES-256-GCM, key sealed by the Keystore).
 * Files live in the app's private directory and are unreadable even if copied off the phone.
 */
class FileVault(private val context: Context) {
    private val dir = File(context.filesDir, "vault").apply { mkdirs() }
    private val key by lazy { SecretKeySpec(DatabaseKey.secret(context, "files_key"), "AES") }

    fun write(bytes: ByteArray, name: String = UUID.randomUUID().toString() + ".img"): String {
        File(dir, name).writeBytes(encrypt(bytes))
        return name
    }

    fun read(name: String): ByteArray? = runCatching { decrypt(File(dir, name).readBytes()) }.getOrNull()

    fun delete(name: String) {
        File(dir, name).delete()
    }

    fun names(): List<String> = dir.list()?.toList().orEmpty()

    fun bitmap(name: String): Bitmap? = read(name)?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }

    /** Imports a picked photo, scaled down to at most 2048px and re-encoded, then encrypted. */
    fun importImage(uri: Uri): String? {
        val raw = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(raw, 0, raw.size, bounds)
        var sample = 1
        while (bounds.outWidth / sample > 2048 || bounds.outHeight / sample > 2048) sample *= 2
        val bmp = BitmapFactory.decodeByteArray(raw, 0, raw.size, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
        val out = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.JPEG, 85, out)
        return write(out.toByteArray())
    }

    private fun encrypt(plain: ByteArray): ByteArray {
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
        return iv + c.doFinal(plain)
    }

    private fun decrypt(blob: ByteArray): ByteArray {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, blob, 0, 12))
        return c.doFinal(blob, 12, blob.size - 12)
    }
}

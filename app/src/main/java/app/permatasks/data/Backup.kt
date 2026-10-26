package app.permatasks.data

import android.content.Context
import android.net.Uri
import android.util.Base64
import androidx.room.withTransaction
import app.permatasks.security.DatabaseKey
import app.permatasks.security.FileVault
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.security.SecureRandom
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

@Serializable
data class BackupData(
    val version: Int = 1,
    val createdAt: Long = System.currentTimeMillis(),
    val tasks: List<TaskEntity> = emptyList(),
    val projects: List<ProjectEntity> = emptyList(),
    val sections: List<SectionEntity> = emptyList(),
    val filters: List<FilterEntity> = emptyList(),
    val places: List<PlaceEntity> = emptyList(),
    val notes: List<NoteEntity> = emptyList(),
    val noteLinks: List<NoteLinkEntity> = emptyList(),
    val activity: List<ActivityEntity> = emptyList(),
    /** Image name -> base64 of the decrypted image bytes. */
    val images: Map<String, String> = emptyMap(),
)

/**
 * Encrypted backups. File layout:
 * "PTBK" | version (1 byte) | PBKDF2 iterations (int) | salt (16) | IV (12) | AES-256-GCM(gzip(JSON)).
 *
 * The key comes from a passphrase only you know (PBKDF2-HMAC-SHA256). The derived key is kept,
 * sealed by the Keystore, so nightly backups run without asking; restoring on a new phone
 * only needs the passphrase. Google Drive / OneDrive only ever see ciphertext.
 */
class Backup(private val context: Context, private val db: AppDatabase, private val vault: FileVault, private val settings: Settings) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    val hasKey: Boolean get() = DatabaseKey.has(context, KEY_NAME) && saltPrefs().contains("salt")

    private fun saltPrefs() = context.getSharedPreferences("permatasks_backup", Context.MODE_PRIVATE)

    /** Sets (or changes) the backup passphrase. */
    fun setPassphrase(passphrase: String) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        DatabaseKey.store(context, KEY_NAME, derive(passphrase, salt, ITERATIONS))
        saltPrefs().edit().putString("salt", Base64.encodeToString(salt, Base64.NO_WRAP)).commit()
    }

    suspend fun snapshot(): BackupData = BackupData(
        tasks = db.tasks().all(),
        projects = db.projects().all(),
        sections = db.sections().all(),
        filters = db.filters().all(),
        places = db.places().all(),
        notes = db.notes().all(),
        noteLinks = db.notes().allLinks(),
        activity = db.activity().all(),
        images = vault.names().mapNotNull { n -> vault.read(n)?.let { n to Base64.encodeToString(it, Base64.NO_WRAP) } }.toMap(),
    )

    suspend fun writeTo(uri: Uri) {
        check(hasKey) { "Set a backup passphrase first" }
        val salt = Base64.decode(saltPrefs().getString("salt", null), Base64.NO_WRAP)
        val key = DatabaseKey.secret(context, KEY_NAME)
        val sealed = seal(json.encodeToString(BackupData.serializer(), snapshot()).toByteArray(), key, salt, ITERATIONS)
        val out = context.contentResolver.openOutputStream(uri, "wt") ?: error("Can't write to the backup location")
        out.use { it.write(sealed) }
        settings.lastBackupAt = System.currentTimeMillis()
        settings.lastBackupError = null
    }

    /** Decrypts a backup file. Throws [WrongPassphrase] if the passphrase doesn't match. */
    fun read(uri: Uri, passphrase: String): BackupData {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: error("Can't open the file")
        return json.decodeFromString(BackupData.serializer(), String(open(bytes, passphrase)))
    }

    /** Replaces everything on this phone with the backup's contents. */
    suspend fun restore(data: BackupData) {
        db.withTransaction {
            db.clearAllTablesInTransaction()
            data.projects.forEach { db.projects().upsert(it) }
            data.sections.forEach { db.sections().upsert(it) }
            data.filters.forEach { db.filters().upsert(it) }
            data.places.forEach { db.places().upsert(it) }
            data.tasks.forEach { db.tasks().upsert(it) }
            data.notes.forEach { db.notes().upsert(it) }
            db.notes().insertLinks(data.noteLinks)
            data.activity.forEach { db.activity().insert(it) }
        }
        vault.names().forEach(vault::delete)
        data.images.forEach { (name, b64) -> vault.write(Base64.decode(b64, Base64.NO_WRAP), name) }
    }

    class WrongPassphrase : Exception("Wrong passphrase")

    companion object {
        private const val KEY_NAME = "backup_key"
        private const val ITERATIONS = 310_000
        private val MAGIC = "PTBK".toByteArray()

        fun derive(passphrase: String, salt: ByteArray, iterations: Int): ByteArray =
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                .generateSecret(PBEKeySpec(passphrase.toCharArray(), salt, iterations, 256)).encoded

        /** Encrypts [plain] (gzipped first) into the backup file format. */
        fun seal(plain: ByteArray, key: ByteArray, salt: ByteArray, iterations: Int): ByteArray {
            val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
            val header = ByteArrayOutputStream().also { bos ->
                DataOutputStream(bos).apply {
                    write(MAGIC)
                    writeByte(1)
                    writeInt(iterations)
                    write(salt)
                    write(iv)
                }
            }.toByteArray()
            cipher.updateAAD(header)
            return header + cipher.doFinal(gzip(plain))
        }

        /** Decrypts a backup file. Throws [WrongPassphrase] if the passphrase doesn't match. */
        fun open(bytes: ByteArray, passphrase: String): ByteArray {
            val input = DataInputStream(ByteArrayInputStream(bytes))
            val magic = ByteArray(4).also { input.readFully(it) }
            require(magic.contentEquals(MAGIC)) { "This isn't a Permatasks backup" }
            val version = input.readByte().toInt()
            require(version == 1) { "Unsupported backup version $version" }
            val iterations = input.readInt()
            val salt = ByteArray(16).also { input.readFully(it) }
            val iv = ByteArray(12).also { input.readFully(it) }
            val headerLen = 4 + 1 + 4 + 16 + 12
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(derive(passphrase, salt, iterations), "AES"), GCMParameterSpec(128, iv))
            cipher.updateAAD(bytes, 0, headerLen)
            val plain = try {
                cipher.doFinal(bytes, headerLen, bytes.size - headerLen)
            } catch (_: AEADBadTagException) {
                throw WrongPassphrase()
        }
        return gunzip(plain)
        }

        private fun gzip(b: ByteArray) = ByteArrayOutputStream().also { bos -> GZIPOutputStream(bos).use { it.write(b) } }.toByteArray()
        private fun gunzip(b: ByteArray) = GZIPInputStream(ByteArrayInputStream(b)).use { it.readBytes() }
    }
}

/** Deletes all rows inside the current transaction (Room's clearAllTables can't run in one). */
private suspend fun AppDatabase.clearAllTablesInTransaction() {
    val sqlDb = openHelper.writableDatabase
    listOf("activity", "note_links", "notes", "tasks", "sections", "filters", "places", "projects").forEach {
        sqlDb.execSQL("DELETE FROM $it")
    }
}

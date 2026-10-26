package app.permatasks

import app.permatasks.data.Backup
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.Assert.assertThrows

class BackupCryptoTest {
    private val salt = ByteArray(16) { it.toByte() }
    private val iterations = 1000 // low for speed; the app uses 310,000
    private val plain = """{"tasks":[{"title":"Pay rent"}]}""".toByteArray()

    @Test fun roundTripWithPassphrase() {
        val key = Backup.derive("correct horse battery", salt, iterations)
        val sealed = Backup.seal(plain, key, salt, iterations)
        assertFalse("ciphertext doesn't contain the plain text", String(sealed, Charsets.ISO_8859_1).contains("Pay rent"))
        assertArrayEquals(plain, Backup.open(sealed, "correct horse battery"))
    }

    @Test fun wrongPassphraseIsRejected() {
        val sealed = Backup.seal(plain, Backup.derive("right", salt, iterations), salt, iterations)
        assertThrows(Backup.WrongPassphrase::class.java) { Backup.open(sealed, "wrong") }
    }

    @Test fun tamperingIsDetected() {
        val sealed = Backup.seal(plain, Backup.derive("pw", salt, iterations), salt, iterations)
        sealed[sealed.size - 5] = (sealed[sealed.size - 5].toInt() xor 1).toByte()
        assertThrows(Backup.WrongPassphrase::class.java) { Backup.open(sealed, "pw") }
    }

    @Test fun notABackup() {
        assertThrows(IllegalArgumentException::class.java) { Backup.open("hello world, not a backup at all".toByteArray(), "pw") }
    }
}

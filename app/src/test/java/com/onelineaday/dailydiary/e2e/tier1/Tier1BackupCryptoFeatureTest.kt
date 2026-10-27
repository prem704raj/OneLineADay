package com.onelineaday.dailydiary.e2e.tier1

import com.onelineaday.dailydiary.backup.BackupArchiveManager
import com.onelineaday.dailydiary.e2e.engine.BackupContainerType
import com.onelineaday.dailydiary.e2e.engine.EncryptedBackupHeader
import com.onelineaday.dailydiary.e2e.engine.ReferenceBackupCryptoEngine
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

/**
 * Tier 1: Core Feature Coverage for Cryptographic Backup Engine (F1, F2, F4).
 */
class Tier1BackupCryptoFeatureTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    // Using 1,000 iterations in unit test runner for sub-second execution while preserving full JCA logic
    private val engine = ReferenceBackupCryptoEngine(iterations = 1_000)

    @Test
    fun `header serialization and deserialization preserves all fields`() {
        val magic = EncryptedBackupHeader.EXPECTED_MAGIC
        val salt = ByteArray(16) { (it + 1).toByte() }
        val nonce = ByteArray(12) { (it + 5).toByte() }
        val header = EncryptedBackupHeader(
            magic = magic,
            version = EncryptedBackupHeader.CURRENT_VERSION,
            kdfSalt = salt,
            kdfIterations = 120_000,
            nonce = nonce
        )

        val serialized = EncryptedBackupHeader.serialize(header)
        assertEquals(EncryptedBackupHeader.HEADER_SIZE, serialized.size)

        val restored = EncryptedBackupHeader.deserialize(serialized)
        assertEquals(header, restored)
        assertArrayEquals(magic, restored.magic)
        assertEquals(EncryptedBackupHeader.CURRENT_VERSION, restored.version)
        assertArrayEquals(salt, restored.kdfSalt)
        assertEquals(120_000, restored.kdfIterations)
        assertArrayEquals(nonce, restored.nonce)
    }

    @Test
    fun `aes 256 gcm roundtrip encrypts and decrypts exact plaintext`() {
        val passphrase = "SafeJournalRecovery2026!".toCharArray()
        val originalText = "Dear Diary, today was a breakthrough day in privacy-first Android architecture."
        val plainBytes = originalText.toByteArray(Charsets.UTF_8)

        val cipherOut = ByteArrayOutputStream()
        val encryptResult = engine.encryptStream(
            passphrase = passphrase,
            plainInput = ByteArrayInputStream(plainBytes),
            cipherOutput = cipherOut
        )
        assertTrue("Encryption must succeed", encryptResult.isSuccess)

        val cipherBytes = cipherOut.toByteArray()
        assertTrue("Ciphertext must exceed plaintext due to 40-byte header and 16-byte GCM tag", cipherBytes.size > plainBytes.size + 40)

        val restoredOut = ByteArrayOutputStream()
        val decryptResult = engine.decryptStream(
            passphrase = passphrase,
            cipherInput = ByteArrayInputStream(cipherBytes),
            plainOutput = restoredOut
        )
        assertTrue("Decryption must succeed", decryptResult.isSuccess)
        assertEquals(originalText, restoredOut.toString(Charsets.UTF_8.name()))
    }

    @Test
    fun `detectContainerType identifies encrypted olad archive`() {
        val file = tempFolder.newFile("test_backup.olad.enc")
        FileOutputStream(file).use { out ->
            val header = EncryptedBackupHeader(
                magic = EncryptedBackupHeader.EXPECTED_MAGIC,
                version = 1,
                kdfSalt = ByteArray(16),
                kdfIterations = 100_000,
                nonce = ByteArray(12)
            )
            out.write(EncryptedBackupHeader.serialize(header))
            out.write("dummy_encrypted_payload".toByteArray())
        }

        val containerType = engine.detectContainerType(file)
        assertTrue("Container must be detected as Encrypted", containerType is BackupContainerType.Encrypted)
        val encrypted = containerType as BackupContainerType.Encrypted
        assertEquals(1, encrypted.header.version)
    }

    @Test
    fun `detectContainerType identifies legacy zip archive`() {
        val file = tempFolder.newFile("legacy_backup.zip")
        FileOutputStream(file).use { out ->
            // Write ZIP magic PK\x03\x04
            out.write(byteArrayOf(0x50, 0x4B, 0x03, 0x04))
            out.write(ByteArray(20))
        }

        val containerType = engine.detectContainerType(file)
        assertEquals(BackupContainerType.LegacyZip, containerType)
    }

    @Test
    fun `detectContainerType rejects corrupt or non-container file`() {
        val file = tempFolder.newFile("random_data.bin")
        file.writeBytes(byteArrayOf(0x00, 0x01, 0x02, 0x03, 0x04, 0x05))

        val containerType = engine.detectContainerType(file)
        assertEquals(BackupContainerType.Invalid, containerType)
    }

    @Test
    fun `streaming encryption handles large multi-block payload without loss`() {
        val passphrase = "LargePayloadPassphrase2026".toCharArray()
        val largeData = ByteArray(65_536) { (it % 256).toByte() }

        val cipherOut = ByteArrayOutputStream()
        val encryptResult = engine.encryptStream(
            passphrase = passphrase,
            plainInput = ByteArrayInputStream(largeData),
            cipherOutput = cipherOut
        )
        assertTrue(encryptResult.isSuccess)

        val plainOut = ByteArrayOutputStream()
        val decryptResult = engine.decryptStream(
            passphrase = passphrase,
            cipherInput = ByteArrayInputStream(cipherOut.toByteArray()),
            plainOutput = plainOut
        )
        assertTrue(decryptResult.isSuccess)
        assertArrayEquals(largeData, plainOut.toByteArray())
    }

    @Test
    fun `archive path validation protects against zip traversal`() {
        val valid = "files/550e8400-e29b-41d4-a716-446655440000.jpg"
        assertEquals(valid, BackupArchiveManager.validateArchivePath(valid))

        val invalid = "../etc/shadow"
        var caught = false
        try {
            BackupArchiveManager.validateArchivePath(invalid)
        } catch (e: IllegalArgumentException) {
            caught = true
        }
        assertTrue("Zip-slip path must be rejected", caught)
    }
}

package com.onelineaday.dailydiary.backup.crypto

import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.security.SecureRandom
import java.util.Arrays
import javax.crypto.AEADBadTagException
import javax.crypto.BadPaddingException
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Production implementation of AES-256-GCM BackupCryptoEngine.
 * - PBKDF2-HMAC-SHA256 key derivation with 120,000 iterations (default) and 16-byte random salt.
 * - AES-256-GCM authenticated encryption/decryption with 12-byte nonce and 128-bit MAC tag.
 * - AAD header binding: The entire 40-byte header is fed as Additional Authenticated Data.
 * - Atomic two-phase decryption: Plain output is guaranteed 0 bytes written if authentication fails.
 */
class AesGcmBackupCryptoEngine(
    private val defaultIterations: Int = EncryptedBackupHeader.DEFAULT_ITERATIONS,
    private val random: SecureRandom = SecureRandom()
) : BackupCryptoEngine {

    companion object {
        private const val ALGORITHM_CIPHER = "AES/GCM/NoPadding"
        private const val ALGORITHM_KDF = "PBKDF2WithHmacSHA256"
        private const val KEY_BIT_LENGTH = 256
        private const val GCM_TAG_BIT_LENGTH = 128
        private const val BUFFER_SIZE = 64 * 1024
    }

    override fun detectContainerType(file: File): BackupContainerType {
        if (!file.exists() || !file.isFile || file.length() < 4) {
            return BackupContainerType.Invalid
        }
        return FileInputStream(file).use { detectContainerType(it) }
    }

    override fun detectContainerType(stream: InputStream): BackupContainerType {
        val buffered = if (stream.markSupported()) stream else BufferedInputStream(stream)
        buffered.mark(EncryptedBackupHeader.HEADER_SIZE)
        val magic = ByteArray(4)
        val read = buffered.read(magic)
        if (read < 4) return BackupContainerType.Invalid

        return when {
            magic.contentEquals(EncryptedBackupHeader.EXPECTED_MAGIC) -> {
                buffered.reset()
                runCatching {
                    val header = EncryptedBackupHeader.readFromStream(buffered)
                    BackupContainerType.Encrypted(header)
                }.getOrElse { BackupContainerType.Invalid }
            }
            magic.contentEquals(EncryptedBackupHeader.ZIP_MAGIC_BYTES) -> {
                BackupContainerType.LegacyZip
            }
            else -> BackupContainerType.Invalid
        }
    }

    override fun createEncryptingOutputStream(
        passphrase: CharArray,
        cipherOutput: OutputStream,
        iterations: Int
    ): OutputStream {
        require(passphrase.isNotEmpty()) { "Passphrase cannot be empty" }

        val header = EncryptedBackupHeader.createNew(iterations = iterations, random = random)
        val headerBytes = EncryptedBackupHeader.serialize(header)
        cipherOutput.write(headerBytes)
        cipherOutput.flush()

        val secretKey = deriveKey(passphrase, header.kdfSalt, header.kdfIterations)
        val cipher = Cipher.getInstance(ALGORITHM_CIPHER)
        val gcmSpec = GCMParameterSpec(GCM_TAG_BIT_LENGTH, header.nonce)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, gcmSpec)
        cipher.updateAAD(headerBytes)

        return CipherOutputStream(cipherOutput, cipher)
    }

    override fun encryptStream(
        passphrase: CharArray,
        plainInput: InputStream,
        cipherOutput: OutputStream
    ): Result<Unit> = runCatching {
        require(passphrase.isNotEmpty()) { "Passphrase cannot be empty" }

        createEncryptingOutputStream(passphrase, cipherOutput, defaultIterations).use { cos ->
            val buffer = ByteArray(BUFFER_SIZE)
            var bytesRead: Int
            while (plainInput.read(buffer).also { bytesRead = it } != -1) {
                cos.write(buffer, 0, bytesRead)
            }
            cos.flush()
        }
    }

    override fun decryptStream(
        passphrase: CharArray,
        cipherInput: InputStream,
        plainOutput: OutputStream
    ): Result<Unit> = runCatching {
        require(passphrase.isNotEmpty()) { "Passphrase cannot be empty" }

        val header = EncryptedBackupHeader.readFromStream(cipherInput)
        val headerBytes = EncryptedBackupHeader.serialize(header)

        val secretKey = deriveKey(passphrase, header.kdfSalt, header.kdfIterations)
        val cipher = Cipher.getInstance(ALGORITHM_CIPHER)
        val gcmSpec = GCMParameterSpec(GCM_TAG_BIT_LENGTH, header.nonce)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmSpec)
        cipher.updateAAD(headerBytes)

        // Stage into temporary file to ensure atomic bad-tag rejection with zero partial output bytes
        val tempFile = File.createTempFile("olad_restore_stage_", ".bin")
        try {
            try {
                FileOutputStream(tempFile).use { tempOut ->
                    CipherInputStream(cipherInput, cipher).use { cis ->
                        val buffer = ByteArray(BUFFER_SIZE)
                        var bytesRead: Int
                        while (cis.read(buffer).also { bytesRead = it } != -1) {
                            tempOut.write(buffer, 0, bytesRead)
                        }
                        tempOut.flush()
                    }
                }
            } catch (e: Exception) {
                if (e is AEADBadTagException ||
                    e is BadPaddingException ||
                    e.cause is AEADBadTagException ||
                    e.cause is BadPaddingException
                ) {
                    throw InvalidPassphraseOrCorruptArchiveException(
                        "Incorrect recovery passphrase or corrupted backup archive.",
                        e
                    )
                }
                throw e
            }

            // Tag verification passed! Now pipe verified plaintext to caller's output stream
            FileInputStream(tempFile).use { tempIn ->
                val buffer = ByteArray(BUFFER_SIZE)
                var bytesRead: Int
                while (tempIn.read(buffer).also { bytesRead = it } != -1) {
                    plainOutput.write(buffer, 0, bytesRead)
                }
                plainOutput.flush()
            }
        } finally {
            tempFile.delete()
        }
    }

    private fun deriveKey(passphrase: CharArray, salt: ByteArray, iterations: Int): SecretKeySpec {
        val factory = SecretKeyFactory.getInstance(ALGORITHM_KDF)
        val spec = PBEKeySpec(passphrase, salt, iterations, KEY_BIT_LENGTH)
        return try {
            val keyBytes = factory.generateSecret(spec).encoded
            SecretKeySpec(keyBytes, "AES").also {
                Arrays.fill(keyBytes, 0.toByte())
            }
        } finally {
            spec.clearPassword()
        }
    }
}

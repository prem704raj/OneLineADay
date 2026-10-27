package com.onelineaday.dailydiary.e2e.engine

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Public contract and specification for OneLineADay encrypted backups.
 * Strictly implements PROJECT.md §Interface Contracts 1:
 * - Magic: 4 bytes 'O', 'L', 'A', 'D' (0x4F, 0x4C, 0x41, 0x44)
 * - Version: 1
 * - PBKDF2-HMAC-SHA256 key derivation with 120,000 iterations and 16-byte random salt
 * - AES-256-GCM authenticated streaming encryption with 12-byte nonce and 128-bit tag
 */
data class EncryptedBackupHeader(
    val magic: ByteArray,
    val version: Int,
    val kdfSalt: ByteArray,
    val kdfIterations: Int,
    val nonce: ByteArray
) {
    companion object {
        val EXPECTED_MAGIC = byteArrayOf('O'.code.toByte(), 'L'.code.toByte(), 'A'.code.toByte(), 'D'.code.toByte())
        const val CURRENT_VERSION = 1
        const val DEFAULT_ITERATIONS = 120_000
        const val SALT_BYTES = 16
        const val NONCE_BYTES = 12
        const val HEADER_SIZE = 4 + 4 + SALT_BYTES + 4 + NONCE_BYTES // 40 bytes

        fun serialize(header: EncryptedBackupHeader): ByteArray {
            val buffer = ByteBuffer.allocate(HEADER_SIZE)
            buffer.put(header.magic)
            buffer.putInt(header.version)
            buffer.put(header.kdfSalt)
            buffer.putInt(header.kdfIterations)
            buffer.put(header.nonce)
            return buffer.array()
        }

        fun deserialize(bytes: ByteArray): EncryptedBackupHeader {
            require(bytes.size >= HEADER_SIZE) { "Header truncated: expected at least $HEADER_SIZE bytes, got ${bytes.size}" }
            val buffer = ByteBuffer.wrap(bytes)
            val magic = ByteArray(4).also { buffer.get(it) }
            require(magic.contentEquals(EXPECTED_MAGIC)) { "Invalid magic bytes: ${magic.joinToString("") { "%02x".format(it) }}" }
            val version = buffer.int
            require(version == CURRENT_VERSION) { "Unsupported container version: $version (expected $CURRENT_VERSION)" }
            val salt = ByteArray(SALT_BYTES).also { buffer.get(it) }
            val iterations = buffer.int
            require(iterations >= 1_000) { "Insufficient KDF iterations: $iterations" }
            val nonce = ByteArray(NONCE_BYTES).also { buffer.get(it) }
            return EncryptedBackupHeader(magic, version, salt, iterations, nonce)
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as EncryptedBackupHeader
        if (!magic.contentEquals(other.magic)) return false
        if (version != other.version) return false
        if (!kdfSalt.contentEquals(other.kdfSalt)) return false
        if (kdfIterations != other.kdfIterations) return false
        return nonce.contentEquals(other.nonce)
    }

    override fun hashCode(): Int {
        var result = magic.contentHashCode()
        result = 31 * result + version
        result = 31 * result + kdfSalt.contentHashCode()
        result = 31 * result + kdfIterations
        result = 31 * result + nonce.contentHashCode()
        return result
    }
}

sealed class BackupContainerType {
    data class Encrypted(val header: EncryptedBackupHeader) : BackupContainerType()
    object LegacyZip : BackupContainerType()
    object Invalid : BackupContainerType()
}

interface BackupCryptoEngine {
    fun detectContainerType(file: File): BackupContainerType
    fun encryptStream(passphrase: CharArray, plainInput: InputStream, cipherOutput: OutputStream): Result<Unit>
    fun decryptStream(passphrase: CharArray, cipherInput: InputStream, plainOutput: OutputStream): Result<Unit>
}

/**
 * Authoritative reference implementation of the BackupCryptoEngine specification.
 */
class ReferenceBackupCryptoEngine(
    private val iterations: Int = EncryptedBackupHeader.DEFAULT_ITERATIONS
) : BackupCryptoEngine {

    private val secureRandom = SecureRandom()

    override fun detectContainerType(file: File): BackupContainerType {
        if (!file.exists() || file.length() < 4) return BackupContainerType.Invalid
        val headerBytes = ByteArray(EncryptedBackupHeader.HEADER_SIZE.coerceAtMost(file.length().toInt()))
        FileInputStream(file).use { it.read(headerBytes) }

        // Check for OLAD magic
        if (headerBytes.size >= 4 &&
            headerBytes[0] == 'O'.code.toByte() &&
            headerBytes[1] == 'L'.code.toByte() &&
            headerBytes[2] == 'A'.code.toByte() &&
            headerBytes[3] == 'D'.code.toByte()
        ) {
            return runCatching {
                val header = EncryptedBackupHeader.deserialize(headerBytes)
                BackupContainerType.Encrypted(header)
            }.getOrElse { BackupContainerType.Invalid }
        }

        // Check for ZIP magic PK\x03\x04
        if (headerBytes.size >= 4 &&
            headerBytes[0] == 0x50.toByte() && // 'P'
            headerBytes[1] == 0x4B.toByte() && // 'K'
            headerBytes[2] == 0x03.toByte() &&
            headerBytes[3] == 0x04.toByte()
        ) {
            return BackupContainerType.LegacyZip
        }

        return BackupContainerType.Invalid
    }

    override fun encryptStream(
        passphrase: CharArray,
        plainInput: InputStream,
        cipherOutput: OutputStream
    ): Result<Unit> = runCatching {
        require(passphrase.isNotEmpty()) { "Passphrase cannot be empty" }

        val salt = ByteArray(EncryptedBackupHeader.SALT_BYTES).also { secureRandom.nextBytes(it) }
        val nonce = ByteArray(EncryptedBackupHeader.NONCE_BYTES).also { secureRandom.nextBytes(it) }

        val header = EncryptedBackupHeader(
            magic = EncryptedBackupHeader.EXPECTED_MAGIC,
            version = EncryptedBackupHeader.CURRENT_VERSION,
            kdfSalt = salt,
            kdfIterations = iterations,
            nonce = nonce
        )

        // Write 40-byte header first
        cipherOutput.write(EncryptedBackupHeader.serialize(header))

        val key = deriveKey(passphrase, salt, iterations)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val spec = GCMParameterSpec(128, nonce)
        cipher.init(Cipher.ENCRYPT_MODE, key, spec)

        CipherOutputStream(cipherOutput, cipher).use { cos ->
            val buffer = ByteArray(8192)
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

        val headerBytes = ByteArray(EncryptedBackupHeader.HEADER_SIZE)
        var totalRead = 0
        while (totalRead < headerBytes.size) {
            val r = cipherInput.read(headerBytes, totalRead, headerBytes.size - totalRead)
            if (r == -1) break
            totalRead += r
        }
        require(totalRead == headerBytes.size) { "Premature end of stream while reading header: read $totalRead bytes" }

        val header = EncryptedBackupHeader.deserialize(headerBytes)
        val key = deriveKey(passphrase, header.kdfSalt, header.kdfIterations)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val spec = GCMParameterSpec(128, header.nonce)
        cipher.init(Cipher.DECRYPT_MODE, key, spec)

        val tempOut = ByteArrayOutputStream()
        CipherInputStream(cipherInput, cipher).use { cis ->
            val buffer = ByteArray(8192)
            var bytesRead: Int
            while (cis.read(buffer).also { bytesRead = it } != -1) {
                tempOut.write(buffer, 0, bytesRead)
            }
            tempOut.flush()
        }
        plainOutput.write(tempOut.toByteArray())
        plainOutput.flush()
    }

    private fun deriveKey(passphrase: CharArray, salt: ByteArray, iterations: Int): SecretKeySpec {
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = PBEKeySpec(passphrase, salt, iterations, 256)
        val secret = factory.generateSecret(spec)
        return SecretKeySpec(secret.encoded, "AES")
    }
}

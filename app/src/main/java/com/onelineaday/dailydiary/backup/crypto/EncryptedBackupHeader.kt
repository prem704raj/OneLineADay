package com.onelineaday.dailydiary.backup.crypto

import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.security.SecureRandom

/**
 * 40-byte binary header for authenticated .olad.enc backup archives.
 *
 * Binary Layout:
 * - 0..3   (4 bytes):  Magic 'O', 'L', 'A', 'D' (0x4F, 0x4C, 0x41, 0x44)
 * - 4..7   (4 bytes):  Format version Int (1)
 * - 8..23  (16 bytes): PBKDF2 random salt
 * - 24..27 (4 bytes):  PBKDF2 iteration count Int (120,000)
 * - 28..39 (12 bytes): AES-GCM 96-bit nonce/IV
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
        val ZIP_MAGIC_BYTES = byteArrayOf(0x50.toByte(), 0x4B.toByte(), 0x03.toByte(), 0x04.toByte())

        const val CURRENT_VERSION = 1
        const val DEFAULT_ITERATIONS = 120_000
        const val MIN_ITERATIONS = 1_000
        const val MAX_ITERATIONS = 1_000_000
        const val SALT_BYTES = 16
        const val NONCE_BYTES = 12
        const val HEADER_SIZE = 4 + 4 + SALT_BYTES + 4 + NONCE_BYTES // Exactly 40 bytes

        fun createNew(
            iterations: Int = DEFAULT_ITERATIONS,
            random: SecureRandom = SecureRandom()
        ): EncryptedBackupHeader {
            val salt = ByteArray(SALT_BYTES)
            val nonce = ByteArray(NONCE_BYTES)
            random.nextBytes(salt)
            random.nextBytes(nonce)
            return EncryptedBackupHeader(
                magic = EXPECTED_MAGIC.clone(),
                version = CURRENT_VERSION,
                kdfSalt = salt,
                kdfIterations = iterations,
                nonce = nonce
            )
        }

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
            require(bytes.size >= HEADER_SIZE) {
                "Header truncated: expected at least $HEADER_SIZE bytes, got ${bytes.size}"
            }
            val buffer = ByteBuffer.wrap(bytes)
            val magic = ByteArray(4).also { buffer.get(it) }
            require(magic.contentEquals(EXPECTED_MAGIC)) {
                "Invalid magic bytes."
            }
            val version = buffer.int
            require(version == CURRENT_VERSION) {
                "Unsupported backup format version: $version (expected $CURRENT_VERSION)"
            }
            val salt = ByteArray(SALT_BYTES).also { buffer.get(it) }
            val iterations = buffer.int
            require(iterations in MIN_ITERATIONS..MAX_ITERATIONS) {
                "Invalid KDF iteration count: $iterations (must be $MIN_ITERATIONS..$MAX_ITERATIONS)"
            }
            val nonce = ByteArray(NONCE_BYTES).also { buffer.get(it) }
            return EncryptedBackupHeader(magic, version, salt, iterations, nonce)
        }

        fun toByteArray(header: EncryptedBackupHeader): ByteArray = serialize(header)

        fun writeToStream(header: EncryptedBackupHeader, output: OutputStream) {
            output.write(serialize(header))
            output.flush()
        }

        fun readFromStream(input: InputStream): EncryptedBackupHeader {
            val headerBytes = ByteArray(HEADER_SIZE)
            var totalRead = 0
            while (totalRead < HEADER_SIZE) {
                val r = input.read(headerBytes, totalRead, HEADER_SIZE - totalRead)
                if (r == -1) break
                totalRead += r
            }
            require(totalRead == HEADER_SIZE) {
                "Premature end of stream while reading header: read $totalRead bytes"
            }
            return deserialize(headerBytes)
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

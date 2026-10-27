package com.onelineaday.dailydiary.backup.crypto

import java.io.File
import java.io.InputStream
import java.io.OutputStream

/**
 * Public cryptographic engine contract for backup archive containerization.
 * Implements AES-256-GCM + PBKDF2-HMAC-SHA256 with stream processing.
 */
interface BackupCryptoEngine {
    fun detectContainerType(file: File): BackupContainerType
    fun detectContainerType(stream: InputStream): BackupContainerType
    fun encryptStream(passphrase: CharArray, plainInput: InputStream, cipherOutput: OutputStream): Result<Unit>
    fun decryptStream(passphrase: CharArray, cipherInput: InputStream, plainOutput: OutputStream): Result<Unit>
    fun createEncryptingOutputStream(
        passphrase: CharArray,
        cipherOutput: OutputStream,
        iterations: Int = EncryptedBackupHeader.DEFAULT_ITERATIONS
    ): OutputStream
}

package com.onelineaday.dailydiary.backup.crypto

import java.io.IOException

open class CryptoBackupException(message: String, cause: Throwable? = null) : IOException(message, cause)

class InvalidPassphraseOrCorruptArchiveException(message: String = "Incorrect recovery passphrase or corrupted backup archive.", cause: Throwable? = null) :
    CryptoBackupException(message, cause)

class UnsupportedBackupVersionException(val version: Int) :
    CryptoBackupException("Unsupported backup format version: $version")

class InvalidContainerFormatException(message: String) :
    CryptoBackupException(message)

package com.onelineaday.dailydiary.backup.crypto

/**
 * Sniffed backup archive classification.
 */
sealed class BackupContainerType {
    data class Encrypted(val header: EncryptedBackupHeader) : BackupContainerType()
    object LegacyZip : BackupContainerType()
    object Invalid : BackupContainerType()
}

package io.github.magisk317.smscode.runtime.common.backup

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class BackupManagerCoreTest {

    private val smscodeConfig = BackupManagerConfig(
        backupDirectoryName = "SmsCode",
        backupFileNamePrefix = "SmsCode-",
        databaseFileName = "xsmscode_room.db",
        fileProviderAuthority = "com.github.tianma8023.xposed.smscode.files",
    )

    private val relayConfig = BackupManagerConfig(
        backupDirectoryName = "Relay",
        backupFileNamePrefix = "Relay-",
        databaseFileName = "relay_room.db",
        legacyDatabaseFileNames = listOf("xrelay_room.db", "xsmscode_room.db"),
        fileProviderAuthority = "io.github.magisk317.xinyi.relay.files",
    )

    @Test
    fun buildBackupFilename_usesConfiguredPrefixes() {
        assertEquals(
            "SmsCode-20260405-0830-schema2.scebak",
            BackupManagerCore.buildBackupFilename(
                config = smscodeConfig,
                dateStr = "20260405-0830",
                includeDatabase = false,
                existingNames = emptySet(),
            ),
        )
        assertEquals(
            "Relay-20260405-0830-schema2-db.zip",
            BackupManagerCore.buildBackupFilename(
                config = relayConfig,
                dateStr = "20260405-0830",
                includeDatabase = true,
                existingNames = emptySet(),
            ),
        )
    }

    @Test
    fun buildBackupFilename_avoidsExistingCollision() {
        assertEquals(
            "Relay-20260405-0830-schema2-2.scebak",
            BackupManagerCore.buildBackupFilename(
                config = relayConfig,
                dateStr = "20260405-0830",
                includeDatabase = false,
                existingNames = setOf("Relay-20260405-0830-schema2.scebak"),
            ),
        )
    }

    @Test
    fun normalizeBackupDbName_supportsRelayLegacyFiles() {
        assertEquals(
            "relay_room.db",
            BackupManagerCore.normalizeBackupDbName(relayConfig, "xrelay_room.db"),
        )
        assertEquals(
            "relay_room.db-wal",
            BackupManagerCore.normalizeBackupDbName(relayConfig, "xsmscode_room.db-wal"),
        )
        assertEquals(
            "relay_room.db-shm",
            BackupManagerCore.normalizeBackupDbName(relayConfig, "xrelay_room.db-shm"),
        )
    }

    @Test
    fun normalizeBackupDbName_doesNotMapUnknownSmsCodeLegacyFiles() {
        assertNull(BackupManagerCore.normalizeBackupDbName(smscodeConfig, "relay_room.db"))
    }
}

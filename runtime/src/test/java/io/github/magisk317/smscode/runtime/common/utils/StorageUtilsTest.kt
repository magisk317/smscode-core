package io.github.magisk317.smscode.runtime.common.utils

import java.io.File
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class StorageUtilsTest {
    @Test
    fun `external app-data scope is recognized without matching unrelated paths`() {
        assertTrue(
            StorageUtils.isAndroidAppDataPath(
                File("/storage/emulated/0/Android/data/example.app/files/app_configs"),
            ),
        )
        assertFalse(StorageUtils.isAndroidAppDataPath(File("/data/user/0/example.app/files")))
        assertFalse(StorageUtils.isAndroidAppDataPath(File("/storage/emulated/0/Documents/example.app")))
    }

    @Test
    fun `repair modes match Android media-provider defaults`() {
        assertEquals(1_528, StorageUtils.DIRECTORY_MODE) // octal 02770
        assertEquals(432, StorageUtils.FILE_MODE) // octal 0660
    }
}

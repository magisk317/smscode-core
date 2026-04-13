package io.github.magisk317.smscode.runtime.common.packageenv

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PackageEnvCoreTest {

    @Test
    fun packageState_enumOrderIsStable() {
        assertEquals(listOf("NOT_INSTALLED", "DISABLED", "ENABLED"), PackageState.entries.map { it.name })
    }

    @Test
    fun packageVersionInfo_holdsVersionData() {
        val info = PackageVersionInfo(versionName = "1.2.3", versionCode = 123)
        assertEquals("1.2.3", info.versionName)
        assertEquals(123L, info.versionCode)
    }
}

package io.github.magisk317.forward.engine.service

import io.github.magisk317.forward.engine.model.SystemEnvironment

interface SystemInfoProvider {
    fun getSnapshot(deviceName: String): SystemEnvironment
    fun resolveAppName(packageName: String): String
}

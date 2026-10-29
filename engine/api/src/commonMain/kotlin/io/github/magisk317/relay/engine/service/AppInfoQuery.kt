package io.github.magisk317.forward.engine.service

import io.github.magisk317.forward.engine.model.AppInfoData

interface AppInfoQuery {
    suspend fun getByPackageName(packageName: String): AppInfoData?
}

package io.github.magisk317.forward.engine.service

import io.github.magisk317.forward.engine.model.RuntimeAnalyticsSnapshot
import io.github.magisk317.forward.engine.model.SenderConfigurationSnapshot

interface RuntimeAnalyticsProvider {
    suspend fun snapshot(fromMs: Long): RuntimeAnalyticsSnapshot
    suspend fun senderConfigurationSnapshot(): SenderConfigurationSnapshot
}

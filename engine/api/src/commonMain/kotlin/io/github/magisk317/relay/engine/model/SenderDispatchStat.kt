package io.github.magisk317.forward.engine.model

interface SenderDispatchStat {
    val senderType: Int
    val sent: Long
    val success: Long
    val failed: Long
}

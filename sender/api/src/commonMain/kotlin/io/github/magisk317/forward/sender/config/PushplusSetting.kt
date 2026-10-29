package io.github.magisk317.forward.sender.config

import kotlinx.serialization.Serializable as KotlinSerializable


@KotlinSerializable
data class PushplusSetting(
    var website: String = "www.pushplus.plus",
    var token: String = "",
    val topic: String = "",
    val template: String = "",
    val channel: String = "",
    val webhook: String = "",
    val callbackUrl: String = "",
    val validTime: String = "",
    val titleTemplate: String = "",
)

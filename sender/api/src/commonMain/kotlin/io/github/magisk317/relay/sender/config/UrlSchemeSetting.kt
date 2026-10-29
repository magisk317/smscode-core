package io.github.magisk317.forward.sender.config

import kotlinx.serialization.Serializable as KotlinSerializable


@KotlinSerializable
data class UrlSchemeSetting(
    var urlScheme: String = "",
)

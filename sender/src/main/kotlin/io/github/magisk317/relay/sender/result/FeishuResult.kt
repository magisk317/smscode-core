package io.github.magisk317.forward.sender.result

import androidx.annotation.Keep
import kotlinx.serialization.Serializable as KotlinSerializable
import kotlinx.serialization.json.JsonElement

@Keep
@KotlinSerializable
data class FeishuResult(
    var code: Long = -1L,
    var msg: String = "",
    var data: JsonElement? = null,
)

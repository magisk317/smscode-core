package io.github.magisk317.forward.sender.config

import kotlinx.serialization.Serializable as KotlinSerializable


@KotlinSerializable
data class SmsSetting(
    var simSlot: Int = 0,
    var mobiles: String = "",
    var onlyNoNetwork: Boolean = false,
) {

}

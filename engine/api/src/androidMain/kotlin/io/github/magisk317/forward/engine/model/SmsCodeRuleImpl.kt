package io.github.magisk317.forward.engine.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable

/**
 * Domain implementation of [SmsCodeRuleData].
 *
 * The Room entity lives in the consuming product's database module; forward-core must not
 * depend on it (that would couple the shared engine to one product and create a dependency
 * cycle). This class bridges the two; see `ConfigMapper` for the entity <-> domain
 * conversions.
 */
@Parcelize
@Serializable
data class SmsCodeRuleImpl(
    override val id: Long = 0L,
    override val company: String? = null,
    override val codeKeyword: String = "",
    override val codeRegex: String = "",
) : Parcelable, SmsCodeRuleData

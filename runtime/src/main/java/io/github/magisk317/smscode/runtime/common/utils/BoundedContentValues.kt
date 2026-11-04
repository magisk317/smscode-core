package io.github.magisk317.smscode.runtime.common.utils

import android.content.ContentValues

/**
 * Bounded accessors for untrusted provider ContentValues: every field is length-capped,
 * range-checked or defaulted before it reaches the database layer.
 */
fun ContentValues?.getBoundedValue(key: String, maxLength: Int): String? {
    val raw = this?.getAsString(key) ?: return null
    if (raw.isEmpty()) return ""
    return raw.take(maxLength)
}

fun ContentValues?.getNonNegativeLongValue(key: String, default: Long): Long =
    this?.getAsLong(key)?.takeIf { it >= 0L } ?: default

fun ContentValues?.getNonNegativeIntValue(key: String, default: Int): Int =
    this?.getAsInteger(key)?.takeIf { it >= 0 } ?: default

fun ContentValues?.getIntInRangeValue(key: String, min: Int, max: Int, default: Int): Int =
    this?.getAsInteger(key)?.takeIf { it in min..max } ?: default

fun ContentValues?.getAllowedIntValue(key: String, allowed: Set<Int>, default: Int): Int =
    this?.getAsInteger(key)?.takeIf { it in allowed } ?: default

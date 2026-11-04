package io.github.magisk317.smscode.runtime.common.prefs

/** Value types a backup restore can classify preference keys into. */
enum class PrefValueType {
    BOOLEAN,
    INT,
    FLOAT,
    STRING,
}

/** Classifies a preference key against host-supplied key sets; unlisted keys are strings. */
fun prefValueTypeOf(
    key: String,
    booleanKeys: Set<String>,
    intKeys: Set<String>,
    floatKeys: Set<String> = emptySet(),
): PrefValueType = when {
    key in booleanKeys -> PrefValueType.BOOLEAN
    key in intKeys -> PrefValueType.INT
    key in floatKeys -> PrefValueType.FLOAT
    else -> PrefValueType.STRING
}

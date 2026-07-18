package io.github.magisk317.smscode.runtime.common.prefs

/** Primitive value kinds supported by the shared preference contract. */
enum class PreferenceValueKind {
    BOOLEAN,
    STRING,
    INT,
    FLOAT,
}

/**
 * A typed preference declaration.
 *
 * Parent repositories own the actual declarations so their keys and defaults remain business
 * policy. This shared type only binds a key to its primitive value kind and default value.
 */
class PreferenceSpec<T : Any> private constructor(
    val key: String,
    val defaultValue: T,
    val kind: PreferenceValueKind,
    private val decoder: (Any) -> T?,
    private val changeFactory: (String, T) -> PreferenceChange,
) {
    init {
        require(key.isNotBlank()) { "Preference key must not be blank" }
    }

    internal fun decode(rawValue: Any): T? = decoder(rawValue)

    internal fun toChange(value: T): PreferenceChange = changeFactory(key, value)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        return other is PreferenceSpec<*> &&
            key == other.key &&
            defaultValue == other.defaultValue &&
            kind == other.kind
    }

    override fun hashCode(): Int {
        var result = key.hashCode()
        result = 31 * result + defaultValue.hashCode()
        result = 31 * result + kind.hashCode()
        return result
    }

    override fun toString(): String =
        "PreferenceSpec(key=$key, defaultValue=$defaultValue, kind=$kind)"

    companion object {
        fun boolean(key: String, defaultValue: Boolean): PreferenceSpec<Boolean> =
            PreferenceSpec(
                key = key,
                defaultValue = defaultValue,
                kind = PreferenceValueKind.BOOLEAN,
                decoder = { rawValue -> rawValue as? Boolean },
                changeFactory = PreferenceChange::PutBoolean,
            )

        fun string(key: String, defaultValue: String): PreferenceSpec<String> =
            PreferenceSpec(
                key = key,
                defaultValue = defaultValue,
                kind = PreferenceValueKind.STRING,
                decoder = { rawValue -> rawValue as? String },
                changeFactory = PreferenceChange::PutString,
            )

        fun int(key: String, defaultValue: Int): PreferenceSpec<Int> =
            PreferenceSpec(
                key = key,
                defaultValue = defaultValue,
                kind = PreferenceValueKind.INT,
                decoder = { rawValue -> rawValue as? Int },
                changeFactory = PreferenceChange::PutInt,
            )

        fun float(key: String, defaultValue: Float): PreferenceSpec<Float> =
            PreferenceSpec(
                key = key,
                defaultValue = defaultValue,
                kind = PreferenceValueKind.FLOAT,
                decoder = { rawValue -> rawValue as? Float },
                changeFactory = PreferenceChange::PutFloat,
            )
    }
}

/** Explains whether a typed snapshot read came from storage or from its declared default. */
enum class PreferenceValueOrigin {
    STORED,
    DEFAULT_MISSING,
    DEFAULT_TYPE_MISMATCH,
}

/** The value returned from a [PreferenceReadView], together with its resolution origin. */
data class PreferenceValue<T : Any>(
    val value: T,
    val origin: PreferenceValueOrigin,
) {
    val isStored: Boolean
        get() = origin == PreferenceValueOrigin.STORED
}

/** Read-only typed view over a consistent preference snapshot. */
interface PreferenceReadView {
    fun <T : Any> read(spec: PreferenceSpec<T>): PreferenceValue<T>

    operator fun <T : Any> get(spec: PreferenceSpec<T>): T = read(spec).value
}

/**
 * Immutable raw-value snapshot exposed through typed [PreferenceSpec] declarations.
 *
 * Missing keys and incompatible stored types resolve to the spec default, while [read] preserves
 * the reason so callers can diagnose or repair malformed storage without unsafe casts.
 */
class PreferenceSnapshot private constructor(
    private val values: Map<String, Any?>,
) : PreferenceReadView {
    val keys: Set<String> = values.keys.toSet()

    override fun <T : Any> read(spec: PreferenceSpec<T>): PreferenceValue<T> {
        if (!values.containsKey(spec.key)) {
            return PreferenceValue(spec.defaultValue, PreferenceValueOrigin.DEFAULT_MISSING)
        }
        val rawValue = values[spec.key]
        val decodedValue = rawValue?.let(spec::decode)
        return if (decodedValue != null) {
            PreferenceValue(decodedValue, PreferenceValueOrigin.STORED)
        } else {
            PreferenceValue(spec.defaultValue, PreferenceValueOrigin.DEFAULT_TYPE_MISMATCH)
        }
    }

    fun contains(spec: PreferenceSpec<*>): Boolean = values.containsKey(spec.key)

    companion object {
        val EMPTY: PreferenceSnapshot = PreferenceSnapshot(emptyMap())

        fun from(values: Map<String, *>): PreferenceSnapshot {
            val copy = LinkedHashMap<String, Any?>(values.size)
            values.forEach { (key, value) -> copy[key] = value }
            return PreferenceSnapshot(copy)
        }
    }
}

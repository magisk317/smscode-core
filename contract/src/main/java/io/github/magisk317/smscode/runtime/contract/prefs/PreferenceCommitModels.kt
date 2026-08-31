package io.github.magisk317.smscode.runtime.contract.prefs

/** One primitive preference mutation in an atomic [PreferenceChangeSet]. */
sealed interface PreferenceChange {
    val key: String

    data class PutBoolean(override val key: String, val value: Boolean) : PreferenceChange

    data class PutString(override val key: String, val value: String) : PreferenceChange

    data class PutInt(override val key: String, val value: Int) : PreferenceChange

    data class PutFloat(override val key: String, val value: Float) : PreferenceChange

    data class Remove(override val key: String) : PreferenceChange
}

/** Immutable, ordered batch that a backend must persist atomically. */
class PreferenceChangeSet internal constructor(changes: List<PreferenceChange>) {
    val changes: List<PreferenceChange> = changes.toList()
    val changedKeys: Set<String> = changes.mapTo(linkedSetOf(), PreferenceChange::key)
    val isEmpty: Boolean
        get() = changes.isEmpty()

    companion object {
        val EMPTY: PreferenceChangeSet = PreferenceChangeSet(emptyList())
    }
}

/**
 * Stages an atomic preference batch. Repeating a key uses last-write-wins semantics while keeping
 * the resulting change at the position of its first occurrence.
 */
class PreferenceEditScope internal constructor() {
    private val pendingChanges = linkedMapOf<String, PreferenceChange>()

    fun <T : Any> set(spec: PreferenceSpec<T>, value: T) {
        pendingChanges[spec.key] = spec.toChange(value)
    }

    fun remove(spec: PreferenceSpec<*>) {
        pendingChanges[spec.key] = PreferenceChange.Remove(spec.key)
    }

    internal fun build(): PreferenceChangeSet = PreferenceChangeSet(pendingChanges.values.toList())
}

fun preferenceChangeSet(edit: PreferenceEditScope.() -> Unit): PreferenceChangeSet =
    PreferenceEditScope().apply(edit).build()

/**
 * Backend boundary for one all-or-nothing write.
 *
 * Implementations must not return `false` after partially applying [changes]. DataStore adapters
 * should perform one `edit` transaction; SharedPreferences adapters should populate one editor and
 * return its synchronous `commit()` result.
 */
fun interface AtomicPreferencePersistence {
    suspend fun persist(changes: PreferenceChangeSet): Boolean
}

/** A successfully persisted batch passed to post-commit hooks. */
data class PreferenceCommit(val changes: PreferenceChangeSet)

/** Named side effect that is eligible to run only after persistence succeeds. */
class PreferencePostCommitHook(
    val name: String,
    private val action: suspend (PreferenceCommit) -> Unit,
) {
    init {
        require(name.isNotBlank()) { "Post-commit hook name must not be blank" }
    }

    suspend fun run(commit: PreferenceCommit) = action(commit)
}

/** Factories for the two common parent-owned post-commit side effects. */
object PreferencePostCommitHooks {
    fun invalidate(action: suspend (PreferenceCommit) -> Unit): PreferencePostCommitHook =
        PreferencePostCommitHook(name = "invalidate", action = action)

    fun publish(action: suspend (PreferenceCommit) -> Unit): PreferencePostCommitHook =
        PreferencePostCommitHook(name = "publish", action = action)

    fun named(
        name: String,
        action: suspend (PreferenceCommit) -> Unit,
    ): PreferencePostCommitHook = PreferencePostCommitHook(name = name, action = action)
}

/** A failed post-commit side effect; the preference batch itself remains persisted. */
data class PreferencePostCommitFailure(
    val hookName: String,
    val error: Exception,
)

/** Complete outcome of an atomic preference commit attempt. */
sealed interface PreferenceCommitResult {
    val changes: PreferenceChangeSet

    /** Persistence succeeded. Hook failures are reported separately and never imply rollback. */
    data class Persisted(
        override val changes: PreferenceChangeSet,
        val postCommitFailures: List<PreferencePostCommitFailure> = emptyList(),
    ) : PreferenceCommitResult {
        val postCommitSucceeded: Boolean
            get() = postCommitFailures.isEmpty()
    }

    /** Persistence returned false or threw [error]; no post-commit hook was invoked. */
    data class NotPersisted(
        override val changes: PreferenceChangeSet,
        val error: Exception? = null,
    ) : PreferenceCommitResult

    /** The staged batch was empty, so neither persistence nor hooks were invoked. */
    data class NoChanges(
        override val changes: PreferenceChangeSet = PreferenceChangeSet.EMPTY,
    ) : PreferenceCommitResult
}

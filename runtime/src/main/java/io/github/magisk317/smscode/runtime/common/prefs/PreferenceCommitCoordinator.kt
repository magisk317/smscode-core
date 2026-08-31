package io.github.magisk317.smscode.runtime.common.prefs

import io.github.magisk317.smscode.runtime.contract.prefs.AtomicPreferencePersistence
import io.github.magisk317.smscode.runtime.contract.prefs.PreferenceChangeSet
import io.github.magisk317.smscode.runtime.contract.prefs.PreferenceCommit
import io.github.magisk317.smscode.runtime.contract.prefs.PreferenceCommitResult
import io.github.magisk317.smscode.runtime.contract.prefs.PreferenceEditScope
import io.github.magisk317.smscode.runtime.contract.prefs.PreferencePostCommitFailure
import io.github.magisk317.smscode.runtime.contract.prefs.PreferencePostCommitHook
import io.github.magisk317.smscode.runtime.contract.prefs.preferenceChangeSet
import kotlinx.coroutines.CancellationException
import io.github.magisk317.xposed.logging.MagiskOtel

/**
 * Coordinates atomic persistence and ordered post-success effects.
 *
 * Hook exceptions are isolated so a failed publish does not hide a successful durable write and
 * does not prevent later hooks from running. Coroutine cancellation is always propagated.
 */
class PreferenceCommitCoordinator(
    private val persistence: AtomicPreferencePersistence,
    hooks: List<PreferencePostCommitHook> = emptyList(),
) {
    private val postCommitHooks = hooks.toList()

    suspend fun commit(edit: PreferenceEditScope.() -> Unit): PreferenceCommitResult =
        commit(preferenceChangeSet(edit))

    @Suppress("TooGenericExceptionCaught")
    suspend fun commit(changes: PreferenceChangeSet): PreferenceCommitResult {
        val startedAt = System.nanoTime()
        fun emit(result: String, statusOk: Boolean = true, reason: String? = null) {
            val durationMs = ((System.nanoTime() - startedAt) / 1_000_000L).coerceAtLeast(0L)
            val attrs = mutableMapOf(
                "result" to result,
                "duration_ms" to durationMs.toString(),
                "process" to "app",
                "change_count" to changes.changes.size.toString(),
            )
            if (reason != null) attrs["reason"] = reason
            MagiskOtel.event(name = "prefs.commit", attributes = attrs, statusOk = statusOk)
        }

        if (changes.isEmpty) {
            emit(result = "skip", reason = "no_changes")
            return PreferenceCommitResult.NoChanges(changes)
        }

        val persisted = try {
            persistence.persist(changes)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            emit(result = "error", statusOk = false, reason = error.javaClass.simpleName)
            return PreferenceCommitResult.NotPersisted(changes, error)
        }
        if (!persisted) {
            emit(result = "error", statusOk = false, reason = "not_persisted")
            return PreferenceCommitResult.NotPersisted(changes)
        }

        val commit = PreferenceCommit(changes)
        val failures = buildList {
            postCommitHooks.forEach { hook ->
                try {
                    hook.run(commit)
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    add(PreferencePostCommitFailure(hook.name, error))
                }
            }
        }
        emit(
            result = "ok",
            reason = if (failures.isEmpty()) "persisted" else "persisted_with_hook_failures",
        )
        return PreferenceCommitResult.Persisted(changes, failures)
    }
}

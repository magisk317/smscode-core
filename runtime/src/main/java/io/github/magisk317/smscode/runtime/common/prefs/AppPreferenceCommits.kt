package io.github.magisk317.smscode.runtime.common.prefs

import android.content.Context
import io.github.magisk317.smscode.runtime.contract.prefs.AtomicPreferencePersistence

/** Factory for the host app preference commit coordinators (write -> invalidate -> publish). */
object AppPreferenceCommits {
    fun createCommitCoordinator(
        context: Context,
        invalidate: () -> Unit,
        publish: suspend (Context) -> Boolean,
        persistence: AtomicPreferencePersistence = AtomicPreferencePersistence { changes ->
            AppPreferencesDataStore.persistChanges(context, changes)
        },
    ): PreferenceCommitCoordinator = createPreferenceCommitCoordinator(
        persistence = persistence,
        invalidate = invalidate,
        publish = {
            check(publish(context)) { "Hook preference publication failed" }
        },
    )
}

package io.github.magisk317.smscode.runtime.common.prefs

import android.content.Context
import android.content.SharedPreferences
import java.io.File

/**
 * Repository-specific extension points for [AppPreferencesDataStore].
 *
 * The shared store owns the DataStore plumbing, while each consumer keeps the
 * parts that are genuinely its own: which keys are mirrored into the
 * cross-process SharedPreferences, how the DataStore file is resolved, and any
 * value coercion its own keys require.
 *
 * Install once, as early as possible (Application.onCreate) and before the
 * first preference read:
 * ```
 * AppPreferencesDataStore.install(MyHooks)
 * ```
 */
interface AppPreferencesHooks {
    /**
     * Writes the repository's mirrored preference set into [editor].
     *
     * This is the cross-process channel: hook/child processes read these values
     * through a plain SharedPreferences, because DataStore cannot be shared
     * across processes. The list of keys is repository specific — it is the
     * union of everything a non-app process needs to observe.
     *
     * Values should be read through [AppPreferencesDataStore] accessors so the
     * defaults match the app process exactly.
     */
    suspend fun publishRemoteEntries(context: Context, editor: SharedPreferences.Editor)

    /**
     * Coerces a boolean before it is written or read back.
     *
     * Consumers use this to gate keys that are not supported on every platform
     * (for example a sensitive-debug-log switch on older Android versions), so
     * an unsupported key always reads `false` no matter what is stored.
     */
    fun coerceBoolean(key: String, value: Boolean): Boolean = value

    /**
     * Overrides DataStore file resolution.
     *
     * Return `null` to use the shared default, which prefers
     * `dataDir/datastore/app_preferences.preferences_pb` and falls back to the
     * files directory when the data directory is not usable.
     */
    fun resolveDataStoreFile(context: Context): File? = null

    /**
     * Extra keys to omit from [AppPreferencesDataStore.snapshotForBackup].
     *
     * Secrets that must never travel inside a user-visible backup belong here.
     * Entries whose name starts with `internal_` are always omitted.
     */
    fun excludeFromBackup(): Set<String> = emptySet()

    /**
     * Runs after the remote mirror has been committed, for consumers that need
     * to verify the published payload (for example an IPC token round trip).
     */
    suspend fun verifyRemoteSync(context: Context, prefs: SharedPreferences) = Unit

    companion object {
        /** No-op hooks: no mirroring, no coercion, default file resolution. */
        val Default: AppPreferencesHooks = object : AppPreferencesHooks {
            override suspend fun publishRemoteEntries(
                context: Context,
                editor: SharedPreferences.Editor,
            ) = Unit
        }
    }
}

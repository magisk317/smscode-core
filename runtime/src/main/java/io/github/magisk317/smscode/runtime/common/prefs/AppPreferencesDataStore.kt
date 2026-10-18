package io.github.magisk317.smscode.runtime.common.prefs

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import androidx.annotation.VisibleForTesting
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.magisk317.smscode.runtime.contract.prefs.PreferenceChange
import io.github.magisk317.smscode.runtime.contract.prefs.PreferenceChangeSet
import io.github.magisk317.xposed.logging.XLog
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.File

/**
 * Shared preference store for the smscode-core consumers.
 *
 * Backed by Preferences DataStore (not SharedPreferences), with an optional
 * one-way mirror into a plain SharedPreferences so non-app processes can read
 * the values — DataStore itself cannot be shared across processes.
 *
 * Repository-specific behaviour is supplied through [AppPreferencesHooks];
 * everything else (typed accessors, Flow variants, atomic batch writes, backup
 * snapshots, mirror publishing) lives here so the consumers cannot drift apart.
 */
object AppPreferencesDataStore {
    private const val DATASTORE_FILE_NAME = "app_preferences.preferences_pb"
    private const val TAG_INTERNAL_PREFIX = "internal_"

    @Volatile
    private var hooks: AppPreferencesHooks = AppPreferencesHooks.Default

    /**
     * Installs the repository hooks. Call from `Application.onCreate` before the
     * first preference access; later calls replace the hooks without discarding
     * an already active [DataStore] instance, avoiding multiple active DataStore
     * instances on the same file.
     */
    fun install(hooks: AppPreferencesHooks) {
        this.hooks = hooks
    }

    @VisibleForTesting
    internal fun resetForTesting() {
        synchronized(this) {
            INSTANCE = null
            resolvedDataStoreFile = null
        }
    }

    @Volatile
    private var INSTANCE: DataStore<Preferences>? = null

    @Volatile
    private var resolvedDataStoreFile: File? = null

    private fun getInstance(context: Context): DataStore<Preferences> {
        INSTANCE?.let { return it }
        return synchronized(this) {
            INSTANCE ?: PreferenceDataStoreFactory.create {
                resolveDataStoreFile(context)
            }.also { INSTANCE = it }
        }
    }

    // ------------------------------------------------------------------
    // File resolution
    // ------------------------------------------------------------------

    private fun resolveDataStoreFile(context: Context): File {
        resolvedDataStoreFile?.let { return it }
        return synchronized(this) {
            resolvedDataStoreFile ?: buildDataStoreFile(context).also { resolvedDataStoreFile = it }
        }
    }

    private fun buildDataStoreFile(context: Context): File {
        hooks.resolveDataStoreFile(context)?.let { return it }

        val safeContext = context.applicationContext ?: context
        val primary = File(safeContext.dataDir, "datastore/$DATASTORE_FILE_NAME")
        prepareDataStoreCandidate(primary, label = "primary")?.let { return it }

        val fallback = File(safeContext.filesDir, "datastore/$DATASTORE_FILE_NAME")
        prepareDataStoreCandidate(fallback, label = "fallback")?.let { return it }

        XLog.w(
            message = "DataStore path fallback unresolved, using primary path anyway: %s",
            primary.absolutePath,
        )
        return primary
    }

    private fun prepareDataStoreCandidate(targetFile: File, label: String): File? {
        val parent = targetFile.parentFile ?: return targetFile
        if (parent.exists() && !parent.isDirectory) {
            val backup = File(
                parent.parentFile ?: targetFile.parentFile ?: targetFile,
                "${parent.name}.corrupted.${System.currentTimeMillis()}",
            )
            val moved = runCatching { parent.renameTo(backup) }.getOrDefault(false)
            XLog.w(
                message = "DataStore parent path is not a directory: label=%s path=%s moved=%s backup=%s",
                label,
                parent.absolutePath,
                moved,
                backup.absolutePath,
            )
            if (!moved) return null
        }
        if (!parent.exists() && !parent.mkdirs()) {
            XLog.w(
                message = "DataStore parent mkdirs failed: label=%s path=%s",
                label,
                parent.absolutePath,
            )
            return null
        }
        return targetFile
    }

    // ------------------------------------------------------------------
    // Cross-process mirror plumbing
    // ------------------------------------------------------------------

    @Volatile
    private var remotePrefsProvider: (() -> SharedPreferences?)? = null

    @Volatile
    private var remoteProviderLogged = false

    @Volatile
    private var remotePrefsPublishPending = false

    @Volatile
    private var remotePrefsPublishPendingLogged = false

    fun setRemotePrefsProvider(provider: (() -> SharedPreferences?)?) {
        remotePrefsProvider = provider
        remoteProviderLogged = false
        if (provider == null) {
            // Keep the pending flag for the next service bind.
            remotePrefsPublishPendingLogged = false
        }
    }

    fun hasPendingRemotePrefsPublish(): Boolean = remotePrefsPublishPending

    private fun getRemotePrefs(): SharedPreferences? {
        val provider = remotePrefsProvider ?: return null
        return runCatching { provider.invoke() }.getOrElse { t ->
            if (!remoteProviderLogged) {
                remoteProviderLogged = true
                XLog.w(message = "RemotePrefs provider failed: %s", t.message ?: t.javaClass.simpleName)
            }
            null
        }
    }

    private fun coerceBooleanValue(key: String, value: Boolean): Boolean =
        hooks.coerceBoolean(key, value)

    // ------------------------------------------------------------------
    // Typed accessors
    // ------------------------------------------------------------------

    suspend fun isBackupCompatTipShown(context: Context): Boolean = getInstance(context).data
        .map { prefs: Preferences -> prefs[booleanPreferencesKey(SharedPreferenceKeys.BACKUP_COMPAT_TIP_SHOWN)] ?: false }
        .first()

    suspend fun setBackupCompatTipShown(context: Context, shown: Boolean) {
        val prefKey = booleanPreferencesKey(SharedPreferenceKeys.BACKUP_COMPAT_TIP_SHOWN)
        getInstance(context).edit { prefs ->
            prefs[prefKey] = shown
        }
    }

    suspend fun getBoolean(context: Context, key: String, defaultValue: Boolean): Boolean {
        val prefKey = booleanPreferencesKey(key)
        return getInstance(context).data
            .map { prefs: Preferences -> coerceBooleanValue(key, safeRead(prefs, prefKey, defaultValue)) }
            .first()
    }

    suspend fun setBoolean(context: Context, key: String, value: Boolean) {
        val safeValue = coerceBooleanValue(key, value)
        val prefKey = booleanPreferencesKey(key)
        getInstance(context).edit { prefs ->
            prefs[prefKey] = safeValue
        }
    }

    suspend fun getString(context: Context, key: String, defaultValue: String): String {
        val prefKey = stringPreferencesKey(key)
        return getInstance(context).data
            .map { prefs: Preferences -> safeRead(prefs, prefKey, defaultValue) }
            .first()
    }

    suspend fun setString(context: Context, key: String, value: String) {
        val prefKey = stringPreferencesKey(key)
        getInstance(context).edit { prefs ->
            prefs[prefKey] = value
        }
    }

    @Suppress("TooGenericExceptionCaught")
    suspend fun getInt(context: Context, key: String, defaultValue: Int): Int {
        val prefKey = intPreferencesKey(key)
        return runCatching {
            getInstance(context).data
                .map { prefs: Preferences -> safeRead(prefs, prefKey, defaultValue) }
                .first()
        }.getOrElse {
            XLog.w(
                message = "DataStore int read failed key=%s err=%s",
                key,
                it.message ?: it.javaClass.simpleName,
            )
            defaultValue
        }
    }

    suspend fun setInt(context: Context, key: String, value: Int) {
        val prefKey = intPreferencesKey(key)
        getInstance(context).edit { prefs ->
            prefs[prefKey] = value
        }
    }

    suspend fun getFloat(context: Context, key: String, defaultValue: Float): Float {
        val prefKey = floatPreferencesKey(key)
        return getInstance(context).data
            .map { prefs: Preferences -> safeRead(prefs, prefKey, defaultValue) }
            .first()
    }

    suspend fun setFloat(context: Context, key: String, value: Float) {
        val prefKey = floatPreferencesKey(key)
        getInstance(context).edit { prefs ->
            prefs[prefKey] = value
        }
    }

    // ------------------------------------------------------------------
    // Flow variants
    // ------------------------------------------------------------------

    fun getBooleanFlow(context: Context, key: String, defaultValue: Boolean): Flow<Boolean> {
        val prefKey = booleanPreferencesKey(key)
        return runCatching {
            getInstance(context).data
                .map { prefs: Preferences -> coerceBooleanValue(key, safeRead(prefs, prefKey, defaultValue)) }
        }.getOrElse { _ ->
            kotlinx.coroutines.flow.flowOf(defaultValue)
        }
    }

    fun getStringFlow(context: Context, key: String, defaultValue: String): Flow<String> {
        val prefKey = stringPreferencesKey(key)
        return runCatching {
            getInstance(context).data
                .map { prefs: Preferences -> safeRead(prefs, prefKey, defaultValue) }
        }.getOrElse { _ ->
            kotlinx.coroutines.flow.flowOf(defaultValue)
        }
    }

    fun getIntFlow(context: Context, key: String, defaultValue: Int): Flow<Int> {
        val prefKey = intPreferencesKey(key)
        return runCatching {
            getInstance(context).data
                .map { prefs: Preferences -> safeRead(prefs, prefKey, defaultValue) }
        }.getOrElse { _ ->
            kotlinx.coroutines.flow.flowOf(defaultValue)
        }
    }

    fun getFloatFlow(context: Context, key: String, defaultValue: Float): Flow<Float> {
        val prefKey = floatPreferencesKey(key)
        return runCatching {
            getInstance(context).data
                .map { prefs: Preferences -> safeRead(prefs, prefKey, defaultValue) }
        }.getOrElse { _ ->
            kotlinx.coroutines.flow.flowOf(defaultValue)
        }
    }

    // ------------------------------------------------------------------
    // Atomic writes
    // ------------------------------------------------------------------

    /** Applies one shared change set in a single DataStore transaction. */
    suspend fun persistChanges(context: Context, changes: PreferenceChangeSet): Boolean {
        if (changes.isEmpty) return true
        getInstance(context).edit { prefs ->
            applyPreferenceChanges(prefs, changes)
        }
        return true
    }

    /**
     * Maps a shared change set onto a [MutablePreferences] instance.
     *
     * Exposed (rather than internal) so consumers and the shared module's own
     * tests can verify the key/type mapping without standing up a real
     * DataStore file.
     */
    @VisibleForTesting
    fun applyPreferenceChanges(
        prefs: MutablePreferences,
        changes: PreferenceChangeSet,
    ) {
        changes.changes.forEach { change ->
            when (change) {
                is PreferenceChange.PutBoolean -> {
                    prefs[booleanPreferencesKey(change.key)] = coerceBooleanValue(change.key, change.value)
                }
                is PreferenceChange.PutString -> prefs[stringPreferencesKey(change.key)] = change.value
                is PreferenceChange.PutInt -> prefs[intPreferencesKey(change.key)] = change.value
                is PreferenceChange.PutFloat -> prefs[floatPreferencesKey(change.key)] = change.value
                is PreferenceChange.Remove -> {
                    prefs.remove(booleanPreferencesKey(change.key))
                    prefs.remove(stringPreferencesKey(change.key))
                    prefs.remove(intPreferencesKey(change.key))
                    prefs.remove(floatPreferencesKey(change.key))
                }
            }
        }
    }

    /**
     * Execute multiple writes atomically in a single DataStore transaction.
     * All writes inside [block] are committed together; if any fails, all are rolled back.
     */
    suspend fun batchEdit(
        context: Context,
        block: suspend BatchEditScope.() -> Unit,
    ) {
        val scope = BatchEditScope()
        scope.block()
        getInstance(context).edit { prefs ->
            for (op in scope.operations) {
                when (op) {
                    is BatchOp.SetBool -> prefs[booleanPreferencesKey(op.key)] = coerceBooleanValue(op.key, op.value)
                    is BatchOp.SetStr -> prefs[stringPreferencesKey(op.key)] = op.value
                    is BatchOp.SetInt -> prefs[intPreferencesKey(op.key)] = op.value
                    is BatchOp.SetFlt -> prefs[floatPreferencesKey(op.key)] = op.value
                }
            }
        }
    }

    class BatchEditScope {
        internal val operations = mutableListOf<BatchOp>()

        suspend fun setBoolean(key: String, value: Boolean) { operations.add(BatchOp.SetBool(key, value)) }
        suspend fun setString(key: String, value: String) { operations.add(BatchOp.SetStr(key, value)) }
        suspend fun setInt(key: String, value: Int) { operations.add(BatchOp.SetInt(key, value)) }
        suspend fun setFloat(key: String, value: Float) { operations.add(BatchOp.SetFlt(key, value)) }
    }

    internal sealed interface BatchOp {
        data class SetBool(val key: String, val value: Boolean) : BatchOp
        data class SetStr(val key: String, val value: String) : BatchOp
        data class SetInt(val key: String, val value: Int) : BatchOp
        data class SetFlt(val key: String, val value: Float) : BatchOp
    }

    // ------------------------------------------------------------------
    // Mirror + backup
    // ------------------------------------------------------------------

    /**
     * Publishes the repository's mirrored keys to the cross-process
     * SharedPreferences. Returns `null` when no provider is bound yet (the
     * publish is marked pending and retried by the consumer), `false` when the
     * mirror could not be reached or committed, `true` on success.
     */
    @SuppressLint("ApplySharedPref")
    @Suppress("TooGenericExceptionCaught")
    suspend fun syncToRemotePrefs(context: Context): Boolean? {
        if (remotePrefsProvider == null) {
            remotePrefsPublishPending = true
            if (!remotePrefsPublishPendingLogged) {
                remotePrefsPublishPendingLogged = true
                XLog.w("RemotePrefs sync pending: provider not available")
            }
            return null
        }
        val prefs = getRemotePrefs() ?: run {
            remotePrefsPublishPending = true
            if (!remotePrefsPublishPendingLogged) {
                remotePrefsPublishPendingLogged = true
                XLog.w("RemotePrefs sync failed: provider unavailable")
            }
            return false
        }
        return try {
            val editor = prefs.edit()
            hooks.publishRemoteEntries(context, editor)
            val committed = editor.commit()
            if (!committed) {
                remotePrefsPublishPending = true
                XLog.w("RemotePrefs sync failed: commit returned false")
                false
            } else {
                remotePrefsPublishPending = false
                remotePrefsPublishPendingLogged = false
                hooks.verifyRemoteSync(context, prefs)
                true
            }
        } catch (e: Exception) {
            remotePrefsPublishPending = true
            XLog.w(message = "RemotePrefs sync failed: %s", e.message ?: e.javaClass.simpleName)
            false
        }
    }

    suspend fun snapshotForBackup(context: Context): Map<String, String?> {
        val excluded = SharedPreferenceKeys.BACKUP_EXCLUDED + hooks.excludeFromBackup()
        return getInstance(context).data.first().asMap()
            .asSequence()
            .filterNot { (key, _) ->
                key.name.startsWith(TAG_INTERNAL_PREFIX) || key.name in excluded
            }
            .associate { (key, value) -> key.name to value.toString() }
    }

    private fun <T> safeRead(
        prefs: Preferences,
        key: Preferences.Key<T>,
        defaultValue: T,
    ): T {
        return runCatching {
            prefs[key] ?: defaultValue
        }.getOrElse {
            XLog.w(
                message = "DataStore type mismatch key=%s err=%s",
                key.name,
                it.message ?: it.javaClass.simpleName,
            )
            defaultValue
        }
    }
}

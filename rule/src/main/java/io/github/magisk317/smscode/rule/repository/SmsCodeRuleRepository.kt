package io.github.magisk317.smscode.rule.repository

import io.github.magisk317.smscode.rule.model.SmsCodeRuleSpec
import kotlinx.coroutines.flow.Flow

/**
 * A stored SMS code rule together with the identity the UI edits it by.
 *
 * [spec] holds the matching configuration; [id] is the storage identity and is 0 for a
 * rule that has not been persisted yet.
 */
data class SmsCodeRuleRecord(
    val id: Long,
    val spec: SmsCodeRuleSpec,
)

/**
 * Storage contract for user-defined SMS code rules.
 *
 * Both apps need the same rule operations, but they store them differently: one goes
 * straight to a Room DAO from the UI, the other through a repository with an explicit
 * checkpoint after each write. Putting the contract here lets the screens talk to one
 * interface, and lets each host keep whatever storage and cache-invalidation strategy
 * it already has.
 *
 * Implementations must be safe to call from the main thread; suspending members are
 * expected to switch to their own dispatcher.
 */
interface SmsCodeRuleRepository {

    /** Emits the full rule set and re-emits whenever it changes. */
    fun observeRules(): Flow<List<SmsCodeRuleRecord>>

    suspend fun getAll(): List<SmsCodeRuleRecord>

    suspend fun getById(id: Long): SmsCodeRuleRecord?

    /** Inserts or updates [record] and returns its storage id. */
    suspend fun upsert(record: SmsCodeRuleRecord): Long

    suspend fun delete(record: SmsCodeRuleRecord)

    suspend fun insertAll(records: List<SmsCodeRuleRecord>)

    suspend fun clearAll()

    /**
     * Publishes pending changes to whoever else reads the rules.
     *
     * Hosts that write through a DAO with its own change notification can leave this
     * empty; hosts that buffer writes call it after a mutation so the app process and
     * the hook process converge.
     */
    suspend fun checkpoint() = Unit
}

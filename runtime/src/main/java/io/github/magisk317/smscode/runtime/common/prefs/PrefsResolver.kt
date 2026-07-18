package io.github.magisk317.smscode.runtime.common.prefs

import android.os.SystemClock
import io.github.magisk317.smscode.runtime.contract.prefs.PrefRead
import io.github.magisk317.smscode.runtime.contract.prefs.PrefReadResult
import io.github.magisk317.smscode.runtime.contract.prefs.PrefSources
import io.github.magisk317.smscode.runtime.contract.prefs.PrefsSource
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * 三态偏好解析器：把多个 [PrefsSource] 组成解析链，并在进程内做带 TTL 的单飞缓存。
 *
 * 统一自 XposedSmsCode 与 xinyi-relay 两套历史实现，保留双方的关键语义：
 * - 三态区分（[PrefRead.Hit] / [PrefRead.Miss] / [PrefRead.Unavailable]），确保跨进程模块进程冻结时
 *   不会用默认值毒化缓存（沿用 XposedSmsCode）。
 * - source 溯源：[PrefReadResult.source] 供业务回退判断与诊断（沿用 xinyi-relay）。
 * - 单飞：每 key 一把锁，并发 miss 只触发一次实际读取（沿用 XposedSmsCode）。
 *
 * @param cacheTtlMs 缓存有效期。命中窗口内直接返回缓存值。
 * @param clock 单调时钟（毫秒），默认 [SystemClock.elapsedRealtime]；测试可注入可控时钟。
 * @param missFallsThrough 解析链遇到 [PrefRead.Miss] 时的策略：
 *   - `false`（XposedSmsCode 语义）：首个「可达」源即权威，其 Miss 直接落到默认值，不再查后续源。
 *   - `true`（xinyi-relay 语义）：Miss 继续尝试下一个源，全部 Miss 才落默认值。
 *   两种语义下 [PrefRead.Unavailable] 都会继续尝试下一个源。
 */
class PrefsResolver(
    private val cacheTtlMs: Long,
    private val clock: () -> Long = SystemClock::elapsedRealtime,
    private val missFallsThrough: Boolean = false,
) {

    private class CachedValue(val value: Any?, val source: String, val at: Long, val generation: Long)

    /** 解析值缓存，键为 "<type>:<prefKey>"，可安全供并发线程访问。 */
    private val valueCache = ConcurrentHashMap<String, CachedValue>()

    /** 每 key 单飞锁，让并发的缓存 miss 只触发一次实际读取。 */
    private val keyLocks = ConcurrentHashMap<String, Any>()

    /** 失效代次，防止失效前已开始的慢读取重新写回缓存。 */
    private val cacheGeneration = AtomicLong(0L)

    /** 丢弃所有缓存值，使下一次读取直达后端源。换源或收到设置变更信号时调用。 */
    fun invalidate() {
        cacheGeneration.incrementAndGet()
        valueCache.clear()
    }

    fun resolveBoolean(
        key: String,
        defaultValue: Boolean,
        sources: List<PrefsSource>,
    ): PrefReadResult<Boolean> = cached("bool:$key", defaultValue) {
        chain(sources, defaultValue) { source -> source.readBoolean(key, defaultValue) }
    }

    fun resolveString(
        key: String,
        defaultValue: String,
        sources: List<PrefsSource>,
    ): PrefReadResult<String> = cached("string:$key", defaultValue) {
        chain(sources, defaultValue) { source -> source.readString(key, defaultValue) }
    }

    fun resolveInt(
        key: String,
        defaultValue: Int,
        sources: List<PrefsSource>,
    ): PrefReadResult<Int> = cached("int:$key", defaultValue) {
        chain(sources, defaultValue) { source -> source.readInt(key, defaultValue) }
    }

    /**
     * 沿解析链求值，返回三态之一：
     * - 任一源 [PrefRead.Hit] → 该命中。
     * - 遇 [PrefRead.Miss]：`missFallsThrough=false` 时立即判定为整体 Miss（首个可达源权威）；
     *   `true` 时记录「至少有一个源可达」后继续。
     * - [PrefRead.Unavailable] 始终继续尝试下一源。
     * - 链耗尽：只要过程中出现过 Miss（源可达但无此 key）→ 整体 [PrefRead.Miss]；
     *   否则（全部 Unavailable）→ 整体 [PrefRead.Unavailable]。
     */
    private inline fun <T> chain(
        sources: List<PrefsSource>,
        @Suppress("UNUSED_PARAMETER") defaultValue: T,
        read: (PrefsSource) -> PrefRead<T>,
    ): PrefRead<T> {
        var sawMiss = false
        for (source in sources) {
            when (val result = read(source)) {
                is PrefRead.Hit -> return result
                is PrefRead.Miss -> {
                    sawMiss = true
                    if (!missFallsThrough) return PrefRead.Miss
                }
                is PrefRead.Unavailable -> Unit
            }
        }
        return if (sawMiss) PrefRead.Miss else PrefRead.Unavailable
    }

    /**
     * 单飞 + TTL + 三态缓存。
     *
     * - [PrefRead.Hit]：缓存并返回真实值与来源。
     * - [PrefRead.Miss]：确认缺失，缓存并返回默认值，来源标记为 [PrefSources.SOURCE_DEFAULT]。
     * - [PrefRead.Unavailable]：源不可用；若有上一个可用值则续期并返回该 stale 值，
     *   否则返回默认值但**不入缓存**（避免用「模块禁用」毒化整个窗口）。
     */
    @Suppress("UNCHECKED_CAST")
    private fun <T> cached(
        cacheKey: String,
        defaultValue: T,
        load: () -> PrefRead<T>,
    ): PrefReadResult<T> {
        val now = clock()
        val hit = currentCached(cacheKey)
        if (hit != null && now - hit.at < cacheTtlMs) {
            return PrefReadResult(hit.value as T, hit.source)
        }
        val lock = keyLocks.computeIfAbsent(cacheKey) { Any() }
        synchronized(lock) {
            val loadGeneration = cacheGeneration.get()
            val now2 = clock()
            val again = currentCached(cacheKey)
            if (again != null && now2 - again.at < cacheTtlMs) {
                return PrefReadResult(again.value as T, again.source)
            }
            return when (val read = load()) {
                is PrefRead.Hit -> {
                    cacheIfCurrent(
                        loadGeneration,
                        cacheKey,
                        CachedValue(read.value, read.source, now2, loadGeneration),
                    )
                    PrefReadResult(read.value, read.source)
                }
                is PrefRead.Miss -> {
                    cacheIfCurrent(
                        loadGeneration,
                        cacheKey,
                        CachedValue(defaultValue, PrefSources.SOURCE_DEFAULT, now2, loadGeneration),
                    )
                    PrefReadResult(defaultValue, PrefSources.SOURCE_DEFAULT)
                }
                is PrefRead.Unavailable -> {
                    if (again != null) {
                        // 把上一个可用值时间戳前推续期，避免窗口内对冻结源反复重试。
                        cacheIfCurrent(
                            loadGeneration,
                            cacheKey,
                            CachedValue(again.value, again.source, now2, loadGeneration),
                        )
                        PrefReadResult(again.value as T, again.source)
                    } else {
                        PrefReadResult(defaultValue, PrefSources.SOURCE_DEFAULT)
                    }
                }
            }
        }
    }

    private fun cacheIfCurrent(generation: Long, key: String, value: CachedValue) {
        if (cacheGeneration.get() == generation) {
            valueCache[key] = value
        }
    }

    private fun currentCached(key: String): CachedValue? {
        val value = valueCache[key] ?: return null
        return value.takeIf { it.generation == cacheGeneration.get() }
    }
}

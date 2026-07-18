package io.github.magisk317.smscode.runtime.common.prefs

import io.github.magisk317.smscode.runtime.contract.prefs.PrefRead
import io.github.magisk317.smscode.runtime.contract.prefs.PrefSources
import io.github.magisk317.smscode.runtime.contract.prefs.PrefsSource
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PrefsResolverTest {

    private var clock = 0L
    private fun resolver(ttl: Long = 5_000L, missFallsThrough: Boolean = false) =
        PrefsResolver(cacheTtlMs = ttl, clock = { clock }, missFallsThrough = missFallsThrough)

    /** 可控三态 fake 源；记录 boolean 读取次数以验证单飞与缓存。 */
    private class FakeSource(
        override val sourceName: String,
        var boolRead: (String, Boolean) -> PrefRead<Boolean> = { _, _ -> PrefRead.Unavailable },
        var stringRead: (String, String) -> PrefRead<String> = { _, _ -> PrefRead.Unavailable },
        var intRead: (String, Int) -> PrefRead<Int> = { _, _ -> PrefRead.Unavailable },
    ) : PrefsSource {
        val boolCalls = AtomicInteger(0)
        override fun readBoolean(key: String, defaultValue: Boolean): PrefRead<Boolean> {
            boolCalls.incrementAndGet()
            return boolRead(key, defaultValue)
        }
        override fun readString(key: String, defaultValue: String): PrefRead<String> = stringRead(key, defaultValue)
        override fun readInt(key: String, defaultValue: Int): PrefRead<Int> = intRead(key, defaultValue)
    }

    private fun hitBool(value: Boolean, source: String) = FakeSource(
        sourceName = source,
        boolRead = { _, _ -> PrefRead.Hit(value, source) },
    )

    private fun missSource(name: String) = FakeSource(
        sourceName = name,
        boolRead = { _, _ -> PrefRead.Miss },
        stringRead = { _, _ -> PrefRead.Miss },
        intRead = { _, _ -> PrefRead.Miss },
    )

    private fun unavailableSource(name: String) = FakeSource(name)

    @Test
    fun hit_returnsValueAndSource() {
        val r = resolver()
        val result = r.resolveBoolean("k", false, listOf(hitBool(true, "remote")))
        assertTrue(result.value)
        assertEquals("remote", result.source)
    }

    @Test
    fun firstAvailableSourceWins_remoteHitBeatsLocal() {
        val r = resolver()
        val result = r.resolveBoolean(
            "k",
            false,
            listOf(hitBool(true, "remote"), hitBool(false, "local")),
        )
        assertTrue(result.value)
        assertEquals("remote", result.source)
    }

    @Test
    fun unavailableSourceFallsThroughToNext() {
        val r = resolver()
        val result = r.resolveBoolean(
            "k",
            false,
            listOf(unavailableSource("remote"), hitBool(true, "local")),
        )
        assertTrue(result.value)
        assertEquals("local", result.source)
    }

    @Test
    fun allSourcesMiss_returnsDefaultWithDefaultSource() {
        val r = resolver()
        val result = r.resolveInt("k", 7, listOf(missSource("remote"), missSource("local")))
        assertEquals(7, result.value)
        assertEquals(PrefSources.SOURCE_DEFAULT, result.source)
    }

    @Test
    fun allSourcesUnavailable_returnsDefaultButDoesNotCache() {
        val r = resolver()
        val src = unavailableSource("remote")
        // 冷启动 + 全不可用：返回默认值，且不缓存（每次重探）。
        assertEquals(false, r.resolveBoolean("k", false, listOf(src)).value)
        assertEquals(PrefSources.SOURCE_DEFAULT, r.resolveBoolean("k", false, listOf(src)).source)
        assertEquals(2, src.boolCalls.get())
    }

    @Test
    fun missFallsThroughFalse_firstReachableSourceIsAuthoritative() {
        // XposedSmsCode 语义：remote 可达但无此 key → Miss 直接落默认，不查 local。
        val r = resolver(missFallsThrough = false)
        val remote = missSource("remote")
        val local = hitBool(true, "local")
        val result = r.resolveBoolean("k", false, listOf(remote, local))
        assertFalse(result.value)
        assertEquals(PrefSources.SOURCE_DEFAULT, result.source)
        assertEquals(0, local.boolCalls.get())
    }

    @Test
    fun missFallsThroughTrue_missContinuesToNextSource() {
        // xinyi-relay 语义：remote Miss → 继续查 local。
        val r = resolver(missFallsThrough = true)
        val remote = missSource("remote")
        val local = hitBool(true, "local")
        val result = r.resolveBoolean("k", false, listOf(remote, local))
        assertTrue(result.value)
        assertEquals("local", result.source)
    }

    @Test
    fun repeatedReadsWithinTtlHitSourceOnce() {
        val r = resolver(ttl = 5_000L)
        val src = hitBool(true, "remote")
        repeat(20) { r.resolveBoolean("k", false, listOf(src)) }
        assertEquals(1, src.boolCalls.get())
    }

    @Test
    fun readAfterTtlExpiryRefreshes() {
        val r = resolver(ttl = 5_000L)
        val src = hitBool(true, "remote")
        r.resolveBoolean("k", false, listOf(src))
        clock += 4_000L
        r.resolveBoolean("k", false, listOf(src))
        assertEquals(1, src.boolCalls.get())
        clock += 2_000L // 累计 6s，超过 TTL
        r.resolveBoolean("k", false, listOf(src))
        assertEquals(2, src.boolCalls.get())
    }

    @Test
    fun invalidateForcesReload() {
        val r = resolver()
        val src = hitBool(true, "remote")
        r.resolveBoolean("k", false, listOf(src))
        r.invalidate()
        r.resolveBoolean("k", false, listOf(src))
        assertEquals(2, src.boolCalls.get())
    }

    @Test
    fun confirmedMissCachesDefaultForTtlWindow() {
        val r = resolver()
        val src = missSource("remote")
        r.resolveBoolean("k", false, listOf(src))
        r.resolveBoolean("k", false, listOf(src))
        // Miss 负缓存：窗口内不重探。
        assertEquals(1, src.boolCalls.get())
    }

    @Test
    fun unavailableAfterHitKeepsStaleValue() {
        val r = resolver(ttl = 5_000L)
        val flip = AtomicInteger(0)
        val src = FakeSource(
            sourceName = "remote",
            boolRead = { _, _ ->
                if (flip.get() == 0) PrefRead.Hit(true, "remote") else PrefRead.Unavailable
            },
        )
        assertTrue(r.resolveBoolean("k", false, listOf(src)).value) // 命中缓存 true
        clock += 6_000L // 过期
        flip.set(1) // 源转为不可用
        val stale = r.resolveBoolean("k", false, listOf(src))
        assertTrue(stale.value) // 保留上次好值
        assertEquals("remote", stale.source)
    }

    @Test
    fun concurrentMissLoadsSourceOnce() {
        val r = resolver()
        val threads = 12
        val barrier = CyclicBarrier(threads)
        val src = hitBool(true, "remote")
        val workers = (0 until threads).map {
            Thread {
                barrier.await()
                r.resolveBoolean("k", false, listOf(src))
            }.apply { start() }
        }
        workers.forEach { it.join() }
        assertEquals(1, src.boolCalls.get())
    }

    @Test
    fun invalidateDuringLoad_doesNotRestoreStaleCache() {
        val r = resolver()
        val loadStarted = CountDownLatch(1)
        val releaseFirstLoad = CountDownLatch(1)
        val calls = AtomicInteger(0)
        val src = FakeSource(
            sourceName = "remote",
            boolRead = { _, _ ->
                if (calls.incrementAndGet() == 1) {
                    loadStarted.countDown()
                    releaseFirstLoad.await()
                    PrefRead.Hit(false, "remote")
                } else {
                    PrefRead.Hit(true, "remote")
                }
            },
        )

        val first = Thread { r.resolveBoolean("k", false, listOf(src)) }.apply { start() }
        loadStarted.await()
        r.invalidate()
        releaseFirstLoad.countDown()
        first.join()

        assertTrue(r.resolveBoolean("k", false, listOf(src)).value)
        assertTrue(r.resolveBoolean("k", false, listOf(src)).value)
        assertEquals(2, calls.get())
    }
}

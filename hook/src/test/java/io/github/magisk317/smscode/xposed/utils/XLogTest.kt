package io.github.magisk317.smscode.xposed.utils

import io.github.magisk317.smscode.xposed.runtime.CoreLogSink
import io.github.magisk317.smscode.xposed.runtime.CoreLogSinkHolder
import io.github.magisk317.smscode.runtime.contract.logging.LogRoute
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class XLogTest {

    @AfterEach
    fun tearDown() {
        XLog.setTestSink(null)
        XLog.setLogLevel(4)
        CoreLogSinkHolder.install(null)
    }

    @Test
    fun testSinkReceivesFormattedThrowableWithoutAndroidLogStub() {
        var captured: Pair<Int, String>? = null
        XLog.setLogLevel(2)
        XLog.setTestSink { priority, message -> captured = priority to message }

        XLog.e("hook failed: %s", "sms", IllegalStateException("boom"))

        assertEquals(6, captured?.first)
        assertTrue(captured?.second.orEmpty().contains("hook failed: sms"))
        assertTrue(captured?.second.orEmpty().contains("java.lang.IllegalStateException: boom"))
    }

    @Test
    fun coreSinkReceivesFormattedMessageWhenLogcatIsUnavailableInJvm() {
        var captured: CapturedLog? = null
        XLog.setLogLevel(2)
        CoreLogSinkHolder.install(
            object : CoreLogSink {
                override fun append(
                    priority: Int,
                    tag: String,
                    message: String,
                    force: Boolean,
                    route: String?,
                    sensitive: Boolean,
                ) {
                    captured = CapturedLog(priority, tag, message, force, route, sensitive)
                }
            },
        )

        XLog.i("loaded %s", "package")

        assertEquals(4, captured?.priority)
        assertEquals("smscode-core", captured?.tag)
        assertEquals("loaded package", captured?.message)
        assertFalse(captured?.force ?: true)
        assertEquals(null, captured?.route)
        assertTrue(captured?.sensitive ?: false)
    }

    @Test
    fun routeScopeAppliesExplicitRouteAndForcePolicy() {
        var captured: CapturedLog? = null
        XLog.setLogLevel(2)
        CoreLogSinkHolder.install(
            object : CoreLogSink {
                override fun append(
                    priority: Int,
                    tag: String,
                    message: String,
                    force: Boolean,
                    route: String?,
                    sensitive: Boolean,
                ) {
                    captured = CapturedLog(priority, tag, message, force, route, sensitive)
                }
            },
        )

        XLog.withRoute(LogRoute.SMS_HOOK) {
            XLog.w("sms hook failed")
        }

        assertEquals(5, captured?.priority)
        assertEquals("sms_hook", captured?.route)
        assertTrue(captured?.force ?: false)
    }

    @Test
    fun explicitRouteOverloadsWinOverRouteScope() {
        var captured: CapturedLog? = null
        XLog.setLogLevel(2)
        CoreLogSinkHolder.install(
            object : CoreLogSink {
                override fun append(
                    priority: Int,
                    tag: String,
                    message: String,
                    force: Boolean,
                    route: String?,
                    sensitive: Boolean,
                ) {
                    captured = CapturedLog(priority, tag, message, force, route, sensitive)
                }
            },
        )

        XLog.withRoute(LogRoute.SMS_HOOK) {
            XLog.i(LogRoute.FORWARD, "forward event")
        }

        assertEquals(4, captured?.priority)
        assertEquals("forward", captured?.route)
        assertFalse(captured?.force ?: true)
    }

    @Test
    fun routeScopeRestoresPreviousRouteAfterNestedBlock() {
        val captured = mutableListOf<CapturedLog>()
        XLog.setLogLevel(2)
        CoreLogSinkHolder.install(
            object : CoreLogSink {
                override fun append(
                    priority: Int,
                    tag: String,
                    message: String,
                    force: Boolean,
                    route: String?,
                    sensitive: Boolean,
                ) {
                    captured += CapturedLog(priority, tag, message, force, route, sensitive)
                }
            },
        )

        XLog.withRoute(LogRoute.SMS_HOOK) {
            XLog.i("outer")
            XLog.withRoute(LogRoute.FORWARD) {
                XLog.i("inner")
            }
            XLog.i("outer again")
        }
        XLog.i("no route")

        assertEquals(listOf("sms_hook", "forward", "sms_hook", null), captured.map { it.route })
    }

    private data class CapturedLog(
        val priority: Int,
        val tag: String,
        val message: String,
        val force: Boolean,
        val route: String?,
        val sensitive: Boolean,
    )
}

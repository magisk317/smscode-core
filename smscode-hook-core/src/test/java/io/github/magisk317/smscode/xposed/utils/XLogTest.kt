package io.github.magisk317.smscode.xposed.utils

import io.github.magisk317.smscode.xposed.runtime.CoreLogSink
import io.github.magisk317.smscode.xposed.runtime.CoreLogSinkHolder
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

    private data class CapturedLog(
        val priority: Int,
        val tag: String,
        val message: String,
        val force: Boolean,
        val route: String?,
        val sensitive: Boolean,
    )
}

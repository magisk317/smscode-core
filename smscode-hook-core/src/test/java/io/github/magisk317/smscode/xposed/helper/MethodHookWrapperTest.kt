package io.github.magisk317.smscode.xposed.helper

import io.github.magisk317.smscode.runtime.contract.logging.LogRoute
import io.github.magisk317.smscode.xposed.hookapi.MethodHookParam
import io.github.magisk317.smscode.xposed.hookapi.SimpleMethodHookParam
import io.github.magisk317.smscode.xposed.runtime.CoreLogSink
import io.github.magisk317.smscode.xposed.runtime.CoreLogSinkHolder
import io.github.magisk317.smscode.xposed.utils.XLog
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class MethodHookWrapperTest {

    @AfterEach
    fun tearDown() {
        XLog.setLogLevel(4)
        XLog.setTestSink(null)
        CoreLogSinkHolder.install(null)
    }

    @Test
    fun wrapperAppliesRouteToHookCallbackLogs() {
        var capturedRoute: String? = null
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
                    capturedRoute = route
                }
            },
        )
        val hook = object : MethodHookWrapper(LogRoute.PERMISSION_HOOK) {
            override fun after(param: MethodHookParam) {
                XLog.i("permission callback")
            }
        }

        hook.afterHookedMethod(
            SimpleMethodHookParam(
                method = String::class.java.getMethod("length"),
                thisObject = "sms",
                args = emptyArray(),
            ),
        )

        assertEquals("permission_hook", capturedRoute)
    }
}

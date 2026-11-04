package io.github.magisk317.smscode.xposed.hook.telephony

import android.content.Context
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class SmsHookRuntimeSessionTest {

    @Test
    fun initialize_resolvesPluginContextOnce_andRunsInitializationCallbacks() {
        val phoneContext = mockk<Context>(relaxed = true)
        val pluginContext = mockk<Context>(relaxed = true)
        var resolveCalls = 0
        var initializedContext: Context? = null
        var resolvedPair: Pair<Context, Context>? = null
        val session = SmsHookRuntimeSession(
            applicationId = "io.github.magisk317.test",
            pluginContextResolver = { context, applicationId ->
                resolveCalls += 1
                assertEquals(phoneContext, context)
                assertEquals("io.github.magisk317.test", applicationId)
                pluginContext
            },
            onPhoneContextInitialized = { initializedContext = it },
            onPluginContextResolved = { resolvedPlugin, resolvedPhone ->
                resolvedPair = resolvedPlugin to resolvedPhone
            },
        )

        val first = session.initialize(phoneContext)
        val second = session.currentOrResolve()

        assertNotNull(first)
        assertEquals(phoneContext, first!!.phoneContext)
        assertEquals(pluginContext, first.pluginContext)
        assertEquals(first, second)
        assertEquals(1, resolveCalls)
        assertEquals(phoneContext, initializedContext)
        assertEquals(pluginContext to phoneContext, resolvedPair)
    }

    @Test
    fun currentOrResolve_returnsNullBeforeInitialize() {
        val session = SmsHookRuntimeSession(applicationId = "io.github.magisk317.test")

        assertNull(session.currentOrResolve())
    }

    @Test
    fun currentOrResolveWithFallback_initializesRecoveredContext() {
        val recoveredPhoneContext = mockk<Context>(relaxed = true)
        val pluginContext = mockk<Context>(relaxed = true)
        var recoveredCallbackContext: Context? = null
        val session = SmsHookRuntimeSession(
            applicationId = "io.github.magisk317.test",
            pluginContextResolver = { _, _ -> pluginContext },
            fallbackPhoneContextResolver = { owner ->
                assertEquals("handler", owner)
                recoveredPhoneContext
            },
            onPhoneContextRecovered = { recoveredCallbackContext = it },
        )

        val runtime = session.currentOrResolveWithFallback("handler")

        assertNotNull(runtime)
        assertEquals(recoveredPhoneContext, runtime!!.phoneContext)
        assertEquals(pluginContext, runtime.pluginContext)
        assertEquals(recoveredPhoneContext, recoveredCallbackContext)
    }

    @Test
    fun recordHeartbeat_usesResolvedRuntimeContexts() {
        val phoneContext = mockk<Context>(relaxed = true)
        val pluginContext = mockk<Context>(relaxed = true)
        every { phoneContext.packageName } returns "com.xiaomi.phone"
        var recordedSource: String? = null
        var recordedPackageName: String? = null
        val session = SmsHookRuntimeSession(
            applicationId = "io.github.magisk317.test",
            pluginContextResolver = { _, _ -> pluginContext },
            heartbeatRecorder = { recordedPluginContext, recordedPhoneContext, packageName, source ->
                assertEquals(pluginContext, recordedPluginContext)
                assertEquals(phoneContext, recordedPhoneContext)
                recordedPackageName = packageName
                recordedSource = source
            },
        )

        val runtime = session.initialize(phoneContext)
        val heartbeatRuntime = session.recordHeartbeat("sms_forward_dispatch")

        assertEquals(runtime, heartbeatRuntime)
        assertEquals("com.xiaomi.phone", recordedPackageName)
        assertEquals("sms_forward_dispatch", recordedSource)
    }

    @Test
    fun reset_clearsCachedContexts_andAllowsFreshInitialization() {
        val firstPhoneContext = mockk<Context>(relaxed = true)
        val secondPhoneContext = mockk<Context>(relaxed = true)
        val pluginContext = mockk<Context>(relaxed = true)
        val session = SmsHookRuntimeSession(
            applicationId = "io.github.magisk317.test",
            pluginContextResolver = { _, _ -> pluginContext },
        )

        session.initialize(firstPhoneContext)
        session.reset()

        assertNull(session.currentOrResolve())
        val runtime = session.initialize(secondPhoneContext)
        assertEquals(secondPhoneContext, runtime!!.phoneContext)
    }
}

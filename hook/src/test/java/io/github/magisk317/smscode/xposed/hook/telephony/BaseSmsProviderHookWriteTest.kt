package io.github.magisk317.smscode.xposed.hook.telephony

import android.content.ContentValues
import android.content.Context
import io.github.magisk317.xposed.MethodHookParam
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BaseSmsProviderHookWriteTest {
    @Test
    fun repeatedWrites_reuseSessionContextsAndInitializeOnce_butHeartbeatEachWrite() {
        val fixture = Fixture()
        var resolutions = 0
        var initializations = 0
        var heartbeats = 0
        fixture.session = SmsHookRuntimeSession(
            applicationId = APPLICATION_ID,
            pluginContextResolver = { _, _ -> resolutions++; fixture.pluginContext },
            onPluginContextResolved = { _, _ -> initializations++ },
            heartbeatRecorder = { _, _, _, _ -> heartbeats++ },
        )

        fixture.writeAndAssertBlocked()
        fixture.writeAndAssertBlocked()

        assertEquals(1, resolutions)
        assertEquals(1, initializations)
        assertEquals(2, heartbeats)
        assertEquals(0, fixture.legacyResolutions)
        assertEquals(2, fixture.decisions)
    }

    @Test
    fun unresolvedSession_usesLegacyContextAndStillBlocksInboundWrite() {
        val fixture = Fixture()
        fixture.session = SmsHookRuntimeSession(
            applicationId = APPLICATION_ID,
            pluginContextResolver = { _, _ -> null },
        )

        fixture.writeAndAssertBlocked()

        assertEquals(1, fixture.legacyResolutions)
        assertEquals(1, fixture.legacyHeartbeats)
    }

    @Test
    fun sessionInitializationFailure_usesLegacyContextAndStillBlocksInboundWrite() {
        val fixture = Fixture()
        fixture.session = SmsHookRuntimeSession(
            applicationId = APPLICATION_ID,
            onPhoneContextInitialized = { error("initialization callback failed") },
        )

        fixture.writeAndAssertBlocked()

        assertEquals(1, fixture.legacyResolutions)
        assertEquals(1, fixture.legacyHeartbeats)
    }

    @Test
    fun sessionHeartbeatFailure_keepsCachedContextAndStillBlocksEveryWrite() {
        val fixture = Fixture()
        var heartbeats = 0
        fixture.session = SmsHookRuntimeSession(
            applicationId = APPLICATION_ID,
            pluginContextResolver = { _, _ -> fixture.pluginContext },
            heartbeatRecorder = { _, _, _, _ -> heartbeats++; error("heartbeat callback failed") },
        )

        fixture.writeAndAssertBlocked()
        fixture.writeAndAssertBlocked()

        assertEquals(2, heartbeats)
        assertEquals(0, fixture.legacyResolutions)
        assertEquals(2, fixture.decisions)
    }

    @Test
    fun legacyInitializationFailure_doesNotSkipHeartbeatOrBlacklist() {
        val fixture = Fixture()
        fixture.failLegacyInitialization = true

        fixture.writeAndAssertBlocked()

        assertEquals(1, fixture.legacyHeartbeats)
    }

    @Test
    fun legacyHeartbeatFailure_doesNotSkipBlacklist() {
        val fixture = Fixture()
        fixture.failLegacyHeartbeat = true

        fixture.writeAndAssertBlocked()

        assertEquals(1, fixture.legacyHeartbeats)
    }

    @Test
    fun verboseDiagnosticFailure_doesNotSkipBlacklist() {
        val fixture = Fixture()
        fixture.failVerboseRead = true

        fixture.writeAndAssertBlocked()
    }

    private class Fixture {
        val phoneContext = mockk<Context>(relaxed = true)
        val pluginContext = mockk<Context>(relaxed = true)
        var session: SmsHookRuntimeSession? = null
        var legacyResolutions = 0
        var legacyHeartbeats = 0
        var decisions = 0
        var failLegacyInitialization = false
        var failVerboseRead = false
        var failLegacyHeartbeat = false
        private val host = object : SmsProviderHookHost {
            override val applicationId = APPLICATION_ID
            override val capabilities = mockk<SmsHookCapabilities>(relaxed = true)
            override val runtimeSession get() = session
            override fun onPluginContextReady(pluginContext: Context, phoneContext: Context) {
                if (failLegacyInitialization) error("legacy initialization callback failed")
            }
            override fun recordHeartbeat(
                pluginContext: Context,
                phoneContext: Context,
                processName: String,
                source: String,
            ) {
                legacyHeartbeats++
                if (failLegacyHeartbeat) error("legacy heartbeat callback failed")
            }
            override fun isVerboseLogMode(pluginContext: Context): Boolean {
                if (failVerboseRead) error("verbose preference unavailable")
                return false
            }
            override fun onInboundSmsProviderWrite(
                pluginContext: Context,
                phoneContext: Context,
                drafts: List<SmsProviderDraft>,
                call: SmsProviderHookInstaller.WriteCall,
            ): SmsProviderWriteDecision {
                assertEquals(this@Fixture.pluginContext, pluginContext)
                assertEquals(this@Fixture.phoneContext, phoneContext)
                assertEquals("blocked body", drafts.single().body)
                decisions++
                return SmsProviderWriteDecision.Block("test blacklist")
            }
        }
        private val hook = object : BaseSmsProviderHook(host) {}

        init {
            every { phoneContext.createPackageContext(APPLICATION_ID, Context.CONTEXT_IGNORE_SECURITY) } answers {
                legacyResolutions++
                pluginContext
            }
        }

        fun writeAndAssertBlocked() {
            val values = mockk<ContentValues>()
            every { values.valueSet() } returns mutableMapOf<String, Any>(
                SmsProviderHookInstaller.KEY_ADDRESS to "10690",
                SmsProviderHookInstaller.KEY_BODY to "blocked body",
                SmsProviderHookInstaller.KEY_TYPE to 1,
            ).entries
            val param = MethodHookParam.simple(Any::class.java.getDeclaredMethod("toString"), null)
            val call = SmsProviderHookInstaller.WriteCall(
                methodName = "bulkInsert",
                uri = mockk(relaxed = true),
                provider = null,
                context = phoneContext,
                hookParam = param,
                contentValues = listOf(values),
            )
            // Exercise the real write callback without introducing a production test seam.
            BaseSmsProviderHook::class.java.getDeclaredMethod("handleWrite", SmsProviderHookInstaller.WriteCall::class.java)
                .apply { isAccessible = true }.invoke(hook, call)
            assertTrue(param.returnEarly)
            assertEquals(0, param.result)
        }
    }

    companion object {
        private const val APPLICATION_ID = "io.github.magisk317.test"
    }
}

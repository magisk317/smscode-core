package io.github.magisk317.smscode.xposed.hook.telephony

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Binder
import io.github.magisk317.smscode.xposed.utils.XLog
import io.github.magisk317.xposed.HookEnv
import io.github.magisk317.xposed.HookHelpers
import io.github.magisk317.xposed.MethodHook
import io.github.magisk317.xposed.MethodHookParam
import io.github.magisk317.xposed.logging.MagiskOtel

object SmsProviderHookInstaller {
    const val TARGET_PACKAGE = "com.android.providers.telephony"
    const val TARGET_CLASS = "com.android.providers.telephony.TelephonyProvider"

    data class WriteCall(
        val methodName: String,
        val uri: Uri,
        val provider: ContentProvider?,
        val context: Context?,
        val hookParam: MethodHookParam,
    )

    data class Result(
        val hookedMethodCount: Int,
        val classFound: Boolean,
    )

    fun install(
        classLoader: ClassLoader,
        beforeSmsWrite: (WriteCall) -> Unit,
    ): Result {
        val providerClass = runCatching {
            HookHelpers.findClass(TARGET_CLASS, classLoader)
        }.getOrNull() ?: return Result(hookedMethodCount = 0, classFound = false)

        var hookedMethodCount = 0
        METHOD_NAMES.forEach { methodName ->
            providerClass.declaredMethods
                .filter { it.name == methodName }
                .forEach { method ->
                    HookEnv.api.hookMethod(
                        method,
                        object : MethodHook() {
                            override fun beforeHookedMethod(param: MethodHookParam) {
                                val uri = param.args.getOrNull(0) as? Uri ?: return
                                if (!isSmsUri(uri)) return
                                val provider = param.thisObject as? ContentProvider
                                beforeSmsWrite(
                                    WriteCall(
                                        methodName = methodName,
                                        uri = uri,
                                        provider = provider,
                                        context = provider?.context,
                                        hookParam = param,
                                    ),
                                )
                            }
                        },
                    )
                    hookedMethodCount += 1
                }
        }
        return Result(hookedMethodCount = hookedMethodCount, classFound = true)
    }

    /**
     * Verbose-gated write diagnostics shared by host hooks. Hosts previously
     * drifted here (one gated on verbose mode, one logging every provider
     * write); both now go through this gate and the same format.
     */
    fun logWriteDiag(call: WriteCall, verbose: Boolean) {
        if (!verbose) return
        val callingUid = Binder.getCallingUid()
        val callingPid = Binder.getCallingPid()
        val packages = runCatching {
            call.context?.packageManager?.getPackagesForUid(callingUid)?.toList()
        }.getOrNull().orEmpty()
        XLog.w(
            "Diag sms provider %s: class=%s process=%s uri=%s uid=%d pid=%d pkgs=%s values=%s",
            call.methodName,
            call.provider?.javaClass?.name ?: TARGET_CLASS,
            call.context?.applicationInfo?.processName ?: TARGET_PACKAGE,
            call.uri,
            callingUid,
            callingPid,
            if (packages.isEmpty()) "<none>" else packages.joinToString(","),
            summarizeValues(call),
        )
    }

    fun summarizeValues(call: WriteCall): String = when (call.methodName) {
        "insert", "update" -> {
            val values = call.hookParam.args.getOrNull(1) as? ContentValues
            values?.keySet()?.joinToString(",") ?: "<none>"
        }
        "bulkInsert" -> {
            val values = call.hookParam.args.getOrNull(1) as? Array<*>
            "count=${values?.size ?: 0}"
        }
        else -> "<none>"
    }

    fun recordLoad(result: String, reason: String) {
        MagiskOtel.event(
            name = "hook.load",
            attributes = mapOf(
                "result" to result,
                "duration_ms" to "0",
                "process" to "hook",
                "stage" to "sms_provider",
                "reason" to reason,
                "target_package" to TARGET_PACKAGE,
            ),
            statusOk = true,
        )
    }

    fun isSmsUri(uri: Uri): Boolean {
        val authority = uri.authority.orEmpty()
        return authority in SMS_AUTHORITIES ||
            uri.toString().contains("content://sms") ||
            uri.toString().contains("content://mms-sms")
    }

    private val METHOD_NAMES = listOf("insert", "bulkInsert", "update")
    private val SMS_AUTHORITIES = setOf("sms", "mms-sms", TARGET_PACKAGE)
}

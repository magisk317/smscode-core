package io.github.magisk317.smscode.xposed.hook.telephony

import android.content.ContentProvider
import android.content.Context
import android.net.Uri
import io.github.magisk317.xposed.HookEnv
import io.github.magisk317.xposed.HookHelpers
import io.github.magisk317.xposed.MethodHook
import io.github.magisk317.xposed.MethodHookParam

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

    fun isSmsUri(uri: Uri): Boolean {
        val authority = uri.authority.orEmpty()
        return authority in SMS_AUTHORITIES ||
            uri.toString().contains("content://sms") ||
            uri.toString().contains("content://mms-sms")
    }

    private val METHOD_NAMES = listOf("insert", "bulkInsert", "update")
    private val SMS_AUTHORITIES = setOf("sms", "mms-sms", TARGET_PACKAGE)
}

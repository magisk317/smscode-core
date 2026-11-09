package io.github.magisk317.smscode.xposed.hook.telephony

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Binder
import io.github.magisk317.xposed.logging.XLog
import io.github.magisk317.xposed.HookEnv
import io.github.magisk317.xposed.HookHelpers
import io.github.magisk317.xposed.MethodHook
import io.github.magisk317.xposed.MethodHookParam
import io.github.magisk317.xposed.logging.MagiskOtel

object SmsProviderHookInstaller {
    const val TARGET_PACKAGE = "com.android.providers.telephony"
    const val TARGET_CLASS = "com.android.providers.telephony.TelephonyProvider"

    /**
     * The provider that actually serves `content://sms`. Xiaomi network SMS lands
     * in the database straight through [TARGET_SMS_PROVIDER_CLASS] without ever
     * passing the telephony dispatch chain, so both classes must be covered:
     * TelephonyProvider keeps the legacy diagnostics surface, SmsProvider is the
     * one whose writes can be intercepted.
     */
    const val TARGET_SMS_PROVIDER_CLASS = "com.android.providers.telephony.SmsProvider"

    /** Methods whose write can be cancelled before the row lands in the database. */
    val BLOCKABLE_METHOD_NAMES = listOf("insert", "bulkInsert")

    /** Well-known sms-table columns read for blacklist matching. */
    const val KEY_ADDRESS = "address"
    const val KEY_BODY = "body"
    const val KEY_TYPE = "type"
    const val KEY_DATE = "date"

    /** Inbox row type per SmsContract; absent type is treated as inbound. */
    const val SMS_MESSAGE_TYPE_INBOX = 1

    data class WriteCall(
        val methodName: String,
        val uri: Uri,
        val provider: ContentProvider?,
        val context: Context?,
        val hookParam: MethodHookParam,
        val targetClassName: String = TARGET_CLASS,
        val contentValues: List<ContentValues> = emptyList(),
    )

    data class Result(
        val hookedMethodCount: Int,
        val classFound: Boolean,
        val smsProviderHookedMethodCount: Int = 0,
        val smsProviderClassFound: Boolean = false,
    )

    private data class ClassInstallResult(
        val hookedMethodCount: Int,
        val classFound: Boolean,
    )

    fun install(
        classLoader: ClassLoader,
        beforeSmsWrite: (WriteCall) -> Unit,
    ): Result {
        val telephony = installClass(classLoader, TARGET_CLASS, "telephony_provider", beforeSmsWrite)
        val smsProvider = installClass(classLoader, TARGET_SMS_PROVIDER_CLASS, "sms_provider", beforeSmsWrite)
        return Result(
            hookedMethodCount = telephony.hookedMethodCount,
            classFound = telephony.classFound,
            smsProviderHookedMethodCount = smsProvider.hookedMethodCount,
            smsProviderClassFound = smsProvider.classFound,
        )
    }

    private fun installClass(
        classLoader: ClassLoader,
        targetClass: String,
        stableIdPrefix: String,
        beforeSmsWrite: (WriteCall) -> Unit,
    ): ClassInstallResult {
        val providerClass = runCatching {
            HookHelpers.findClass(targetClass, classLoader)
        }.getOrNull() ?: return ClassInstallResult(hookedMethodCount = 0, classFound = false)

        var hookedMethodCount = 0
        METHOD_NAMES.forEach { methodName ->
            providerClass.declaredMethods
                .filter { it.name == methodName }
                .forEach { method ->
                    HookEnv.api.hookMethod(
                        method,
                        object : MethodHook() {
                            override val stableId: String = "$stableIdPrefix.$methodName"

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
                                        targetClassName = targetClass,
                                        contentValues = extractContentValues(methodName, param.args),
                                    ),
                                )
                            }
                        },
                    )
                    hookedMethodCount += 1
                }
        }
        return ClassInstallResult(hookedMethodCount = hookedMethodCount, classFound = true)
    }

    private fun extractContentValues(methodName: String, args: Array<Any?>): List<ContentValues> =
        when (methodName) {
            "insert", "update" -> listOfNotNull(args.getOrNull(1) as? ContentValues)
            "bulkInsert" -> (args.getOrNull(1) as? Array<*>)?.filterIsInstance<ContentValues>().orEmpty()
            else -> emptyList()
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
            message = "Diag sms provider %s: class=%s process=%s uri=%s uid=%d pid=%d pkgs=%s values=%s",
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

    /** Flattens one sms row into a plain map so the pure helpers stay JVM-testable. */
    fun contentValuesToMap(values: ContentValues): Map<String, Any?> =
        values.valueSet().associate { it.key to it.value }

    /** Inbox (or type-less) rows only; outbox/sent/draft writes must never be blocked. */
    fun isInboundType(type: Any?): Boolean =
        type == null || ((type as? Number)?.toInt() == SMS_MESSAGE_TYPE_INBOX)

    /** Pure inbound-row extraction over plain maps; see [contentValuesToMap]. */
    fun extractInboundDrafts(rows: List<Map<String, Any?>>): List<SmsProviderDraft> =
        rows.filter { isInboundType(it[KEY_TYPE]) }.map { row ->
            SmsProviderDraft(
                address = row[KEY_ADDRESS] as? String,
                body = row[KEY_BODY] as? String,
                date = (row[KEY_DATE] as? Number)?.toLong(),
                type = (row[KEY_TYPE] as? Number)?.toInt(),
            )
        }

    /** Result substituted into the hooked call when the host decides to block. */
    fun blockingResultFor(methodName: String): Any? = when (methodName) {
        "insert" -> null
        "bulkInsert" -> 0
        else -> null
    }

    private val METHOD_NAMES = listOf("insert", "bulkInsert", "update")
    private val SMS_AUTHORITIES = setOf("sms", "mms-sms", TARGET_PACKAGE)
}

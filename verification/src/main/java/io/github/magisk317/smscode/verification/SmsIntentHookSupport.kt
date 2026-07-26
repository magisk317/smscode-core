package io.github.magisk317.smscode.verification

import android.content.Intent
import io.github.magisk317.xposed.logging.MagiskOtel
import android.provider.Telephony
import kotlin.math.abs

object SmsIntentHookSupport {
    private const val EVENT_ID_EXTRA = "event_id"
    private const val DISPATCH_HANDLED_EXTRA_PREFIX = "xsms_dispatch_handled:"
    private const val DEFAULT_HANDLER_KEY = "default"
    private const val RAW_TABLE_DELETE_WHERE_FIELD = "mDeleteWhere"
    private const val RAW_TABLE_DELETE_WHERE_ARGS_FIELD = "mDeleteWhereArgs"

    /**
     * Check if the intent action is an SMS action.
     *
     * Android 17 (API 37) Impact:
     * - OTP messages (WebOTP format) are delayed by 3 hours for non-expected receivers
     * - This affects SMS_RECEIVED_ACTION broadcasts
     * - Xposed hooks in the SMS app process will receive the delayed broadcast
     * - Use SmsContentObserver as a fallback for OTP messages
     */
    fun isSmsAction(action: String?): Boolean {
        return action == Telephony.Sms.Intents.SMS_DELIVER_ACTION ||
            action == Telephony.Sms.Intents.SMS_RECEIVED_ACTION
    }

    fun ensureEventId(intent: Intent): String {
        val existing = intent.getStringExtra(EVENT_ID_EXTRA).orEmpty().trim()
        if (existing.isNotEmpty()) {
            return existing
        }
        val generated = "sms_${System.currentTimeMillis().toString(36)}_${abs(intent.hashCode()).toString(36)}"
        intent.putExtra(EVENT_ID_EXTRA, generated)
        return generated
    }

    fun markDispatchHandled(
        intent: Intent,
        action: String?,
        handlerKey: String = DEFAULT_HANDLER_KEY,
    ): Boolean {
        if (action.isNullOrBlank()) return false
        val normalizedHandlerKey = handlerKey.trim().ifBlank { DEFAULT_HANDLER_KEY }
        val key = "$DISPATCH_HANDLED_EXTRA_PREFIX$normalizedHandlerKey:$action"
        if (intent.getBooleanExtra(key, false)) {
            MagiskOtel.event(
                name = "sms.dispatch",
                attributes = mapOf(
                    "result" to "skip",
                    "duration_ms" to "0",
                    "process" to "hook",
                    "stage" to "handled_mark",
                    "reason" to "already_handled",
                    "source" to normalizedHandlerKey,
                ),
                statusOk = true,
            )
            return true
        }
        intent.putExtra(key, true)
        return false
    }

    fun getPduCount(
        intent: Intent,
        messageExtractor: (Intent) -> Array<*>? = { Telephony.Sms.Intents.getMessagesFromIntent(it) },
        onFailure: (Throwable) -> Unit = {},
    ): Int {
        return try {
            messageExtractor(intent)?.size ?: -1
        } catch (error: IllegalArgumentException) {
            handlePduCountFailure(error, onFailure)
        } catch (error: IllegalStateException) {
            handlePduCountFailure(error, onFailure)
        } catch (error: ClassCastException) {
            handlePduCountFailure(error, onFailure)
        }
    }

    private fun handlePduCountFailure(
        error: RuntimeException,
        onFailure: (Throwable) -> Unit,
    ): Int {
        MagiskOtel.event(
            name = "sms.dispatch",
            attributes = mapOf(
                "result" to "error",
                "duration_ms" to "0",
                "process" to "hook",
                "stage" to "pdu_count",
                "reason" to "extract_failed",
                "error_class" to error.javaClass.simpleName,
            ),
            statusOk = false,
        )
        onFailure(error)
        return -1
    }

    fun extractIntentAction(args: Array<Any?>?): String? {
        return findIntent(args)?.action
    }

    fun extractOrBuildSmsIntent(
        args: Array<Any?>?,
        fallbackAction: String,
        intentFactory: (String) -> Intent = ::Intent,
    ): Intent? {
        findIntent(args)?.let { return it }
        val pduList = extractPdus(args) ?: return null
        val format = extractFormat(args)
        return intentFactory(fallbackAction).apply {
            putExtra("pdus", pduList.toTypedArray())
            if (!format.isNullOrBlank()) {
                putExtra("format", format)
            }
        }
    }

    fun findRawTableReceiver(args: Array<Any?>?): Any? {
        return findArgWithFields(
            args,
            RAW_TABLE_DELETE_WHERE_FIELD,
            RAW_TABLE_DELETE_WHERE_ARGS_FIELD,
        )
    }

    fun defaultResultForType(type: Class<*>?): Any? {
        return when (type) {
            null, Void.TYPE, Void::class.java -> null
            Boolean::class.javaPrimitiveType, Boolean::class.javaObjectType -> false
            Int::class.javaPrimitiveType, Int::class.javaObjectType -> 0
            Long::class.javaPrimitiveType, Long::class.javaObjectType -> 0L
            Float::class.javaPrimitiveType, Float::class.javaObjectType -> 0f
            Double::class.javaPrimitiveType, Double::class.javaObjectType -> 0.0
            Short::class.javaPrimitiveType, Short::class.javaObjectType -> 0.toShort()
            Byte::class.javaPrimitiveType, Byte::class.javaObjectType -> 0.toByte()
            Char::class.javaPrimitiveType, Char::class.javaObjectType -> 0.toChar()
            else -> null
        }
    }

    private fun findArgWithFields(args: Array<Any?>?, vararg fieldNames: String): Any? {
        if (args == null || fieldNames.isEmpty()) return null
        return args.firstOrNull { candidate ->
            candidate != null && fieldNames.all { hasField(candidate, it) }
        }
    }

    private fun hasField(instance: Any, fieldName: String): Boolean {
        var current: Class<*>? = instance.javaClass
        while (current != null) {
            if (current.declaredFields.any { it.name == fieldName }) {
                return true
            }
            current = current.superclass
        }
        return false
    }

    private fun findIntent(args: Array<Any?>?): Intent? {
        if (args == null) return null
        return args.firstOrNull { it is Intent } as? Intent
    }

    private fun extractPdus(args: Array<Any?>?): List<ByteArray>? {
        if (args == null) return null
        return args.firstNotNullOfOrNull { arg ->
            val array = arg as? Array<*> ?: return@firstNotNullOfOrNull null
            val pdus = array.mapNotNull { it as? ByteArray }
            if (pdus.isEmpty() || pdus.size != array.size) null else pdus
        }
    }

    private fun extractFormat(args: Array<Any?>?): String? {
        if (args == null) return null
        return args.firstNotNullOfOrNull { arg ->
            val text = arg as? String ?: return@firstNotNullOfOrNull null
            text.takeIf { isSmsPduFormat(it) }
        }
    }

    private fun isSmsPduFormat(value: String): Boolean {
        return value.equals("3gpp", ignoreCase = true) ||
            value.equals("3gpp2", ignoreCase = true)
    }
}

package io.github.magisk317.smscode.xposed.hook.vendor

import android.content.Context
import io.github.magisk317.xposed.BaseHook
import io.github.magisk317.xposed.LoadParam
import io.github.magisk317.xposed.hook
import io.github.magisk317.xposed.logging.MagiskOtel
import io.github.magisk317.xposed.logging.XLog
import kotlinx.coroutines.runBlocking

/**
 * MIUI/HyperOS adaptation of the mark-as-read pipeline (issue XposedSmsCode#273), shared by
 * the SMS verification-code hosts (XposedSmsCode, xinyi-relay) via smscode-core.
 *
 * MIUI SMS (`com.android.mms`) derives its unread badge from a bugle-derived Room database
 * (`com.android.mms.storage.bugle.*`, unobfuscated because Room loads DAO impl classes
 * reflectively), so the telephony-side `read/seen` write from the primary path never clears
 * the badge, and MIUI notifications carry no mark-as-read semantic action. Capture evidence:
 * device_dumps devices/xiaomi_pudding sms-focus (jadx/com.android.mms).
 *
 * This hook installs into the already-scoped `com.android.mms` process and, after the SMS app
 * ingests a conversation, calls the app's own DAO write paths when the conversation snippet
 * parses as a verification code:
 * - `ConversationDao_Impl.actualMarkRead(List<Long>)` clears `unread_count` (the badge query
 *   reads `WHERE unread_count > 0`);
 * - `BugleDatabase.message().markRead(threadId, seen)` mirrors what the app itself does when
 *   the user opens a thread.
 *
 * Names come from the app's own Room layer and survive R8; any rename simply disables the
 * hook (every reflection step is runCatching-guarded) while the primary DB write and the
 * notification-side backup keep working. No new scope packages are introduced.
 */
class MiuiBugleReadStateHook(private val host: MiuiBugleReadStateHost) : BaseHook() {

    override fun deferUntilPackageReady(): Boolean = true

    override fun onLoadPackage(param: LoadParam) {
        if (param.packageName != MIUI_SMS_PACKAGE) return
        installInto(param.classLoader)
    }

    private fun installInto(classLoader: ClassLoader) {
        val daoClass = runCatching { Class.forName(DAO_IMPL_CLASS, false, classLoader) }
            .getOrElse {
                XLog.d(message = "MiuiBugleReadStateHook: %s not found, adaptation disabled", DAO_IMPL_CLASS)
                emitEvent(result = "install_skip", statusOk = true, reason = "dao_class_missing")
                return
            }
        val entityClass = runCatching { Class.forName(ENTITY_CLASS, false, classLoader) }.getOrNull()
        if (entityClass == null) {
            emitEvent(result = "install_skip", statusOk = true, reason = "entity_class_missing")
            return
        }
        var installed = 0
        for (methodName in INGEST_METHODS) {
            val methods = daoClass.declaredMethods.filter {
                it.name == methodName &&
                    it.parameterTypes.size == 1 &&
                    it.parameterTypes[0].isArray &&
                    it.parameterTypes[0].componentType == entityClass
            }
            for (method in methods) {
                runCatching {
                    method.hook {
                        doAfter {
                            runCatching { onConversationIngest(host, this, methodName) }.onFailure { error ->
                                XLog.w(message = "MiuiBugleReadStateHook: ingest handling failed", error)
                            }
                        }
                    }
                }.onFailure { error ->
                    XLog.w(message = "MiuiBugleReadStateHook: hook %s failed", methodName, error)
                }
                installed++
            }
        }
        XLog.i(message = "MiuiBugleReadStateHook: installed=%d", installed)
        emitEvent(
            result = if (installed > 0) "install_ok" else "install_skip",
            statusOk = installed > 0,
            reason = "installed=$installed",
        )
    }

    private fun onConversationIngest(
        host: MiuiBugleReadStateHost,
        param: io.github.magisk317.xposed.MethodHookParam,
        methodName: String,
    ) {
        val context = currentAppContext()
        if (context == null) {
            XLog.d(message = "BugleRead telemetry: %s skip no-context", methodName)
            return
        }
        // The verification-code parser resolves bundled rule assets through the given
        // context. Inside a vendor SMS-app process those assets are the vendor's, so the
        // parse must run on a context bound to the module's own package.
        val parseContext = runCatching {
            context.createPackageContext(
                host.applicationId,
                Context.CONTEXT_INCLUDE_CODE or Context.CONTEXT_IGNORE_SECURITY,
            )
        }.getOrElse {
            XLog.w(message = "BugleRead: module package context unavailable, falling back", it)
            context
        }
        if (!shouldAct(host, context, methodName)) return
        val entities = param.args.firstOrNull() as? Array<*>
        if (entities == null) {
            XLog.d(message = "BugleRead telemetry: %s skip args-not-array", methodName)
            return
        }
        val dao = param.thisObject ?: return
        for (entity in entities) {
            val snippet = callString(entity, GET_SNIPPET)
            if (snippet.isNullOrBlank()) {
                XLog.d(
                    message = "BugleRead telemetry: %s skip snippet-blank id=%s unread=%s",
                    methodName,
                    callLong(entity, GET_ID) ?: -1L,
                    callLong(entity, GET_UNREAD_COUNT) ?: -1L,
                )
                continue
            }
            val parsed = runCatching { runBlocking { host.parseVerificationCode(parseContext, snippet) } }
            val code = parsed.getOrNull()
            if (parsed.isFailure) {
                XLog.w(
                    message = "BugleRead: parse threw snip=%s err=%s",
                    snippet.take(50),
                    parsed.exceptionOrNull()?.toString()?.take(60) ?: "null",
                )
            }
            if (code.isNullOrBlank()) {
                XLog.d(message = "BugleRead: miss snip=%s", snippet.take(60))
                continue
            }
            val unread = callLong(entity, GET_UNREAD_COUNT) ?: 0L
            if (unread <= 0L) {
                XLog.d(
                    message = "BugleRead telemetry: %s skip unread-zero id=%s",
                    methodName,
                    callLong(entity, GET_ID) ?: -1L,
                )
                continue
            }
            val conversationId = callLong(entity, GET_ID) ?: continue
            markConversationRead(dao, conversationId)
            val threadId = callLong(entity, GET_THREAD_ID) ?: 0L
            markBugleMessagesRead(context.classLoader, threadId)
            emitEvent(result = "mark_read", statusOk = true, reason = "thread=$threadId")
        }
    }

    private fun shouldAct(host: MiuiBugleReadStateHost, context: Context, methodName: String): Boolean {
        if (!runCatching { host.isModuleEnabled(context) }.getOrDefault(false)) {
            XLog.d(message = "BugleRead telemetry: %s gate module-disabled", methodName)
            return false
        }
        if (!runCatching { host.isMobileAutomationAllowed(context) }.getOrDefault(false)) {
            XLog.d(message = "BugleRead telemetry: %s gate entitlement-off", methodName)
            return false
        }
        if (!runCatching { host.isMarkAsReadEnabled(context) }.getOrDefault(false)) {
            XLog.d(message = "BugleRead telemetry: %s gate mark-as-read-off", methodName)
            return false
        }
        val suppressed = runCatching {
            host.shouldSuppressByRelay(context, "MiuiBugleReadStateHook")
        }.getOrDefault(true)
        if (suppressed) {
            XLog.d(message = "BugleRead telemetry: %s gate relay-conflict", methodName)
            return false
        }
        return true
    }

    private fun markConversationRead(dao: Any, conversationId: Long) {
        runCatching {
            val markRead = dao.javaClass.declaredMethods.firstOrNull {
                it.name == DAO_MARK_READ &&
                    it.parameterTypes.size == 1 &&
                    List::class.java.isAssignableFrom(it.parameterTypes[0])
            } ?: error("actualMarkRead not found")
            markRead.isAccessible = true
            markRead.invoke(dao, listOf(conversationId))
        }.onFailure { error ->
            XLog.w(
                message = "MiuiBugleReadStateHook: conversation markRead failed id=%s",
                conversationId,
                error,
            )
        }
    }

    private fun markBugleMessagesRead(classLoader: ClassLoader, threadId: Long) {
        if (threadId <= 0L) return
        runCatching {
            val dbClass = Class.forName(DATABASE_CLASS, true, classLoader)
            val db = dbClass.methods.firstOrNull { it.name == DB_GET && it.parameterTypes.isEmpty() }
                ?.invoke(null) ?: error("BugleDatabase.get() not found")
            val messageDao = db.javaClass.methods.firstOrNull { it.name == DB_MESSAGE && it.parameterTypes.isEmpty() }
                ?.invoke(db) ?: error("BugleDatabase.message() not found")
            val markRead = messageDao.javaClass.methods.firstOrNull {
                it.name == MESSAGE_MARK_READ &&
                    it.parameterTypes.size == 2 &&
                    it.parameterTypes[0] == Long::class.javaPrimitiveType &&
                    it.parameterTypes[1] == Int::class.javaPrimitiveType
            } ?: error("MessageDao.markRead(long,int) not found")
            markRead.invoke(messageDao, threadId, 1)
        }.onFailure { error ->
            XLog.w(
                message = "MiuiBugleReadStateHook: message markRead failed thread=%s",
                threadId,
                error,
            )
        }
    }

    private fun currentAppContext(): Context? = runCatching {
        val activityThread = Class.forName(ACTIVITY_THREAD_CLASS)
        activityThread.methods.firstOrNull { it.name == CURRENT_APPLICATION && it.parameterTypes.isEmpty() }
            ?.invoke(null) as? Context
    }.getOrNull()

    private fun callString(target: Any?, getter: String): String? = runCatching {
        target?.javaClass?.methods?.firstOrNull { it.name == getter && it.parameterTypes.isEmpty() }
            ?.invoke(target) as? String
    }.getOrNull()

    private fun callLong(target: Any?, getter: String): Long? = runCatching {
        (target?.javaClass?.methods?.firstOrNull { it.name == getter && it.parameterTypes.isEmpty() }
            ?.invoke(target) as? Number)?.toLong()
    }.getOrNull()

    private fun emitEvent(result: String, statusOk: Boolean, reason: String) {
        MagiskOtel.event(
            name = "sms.miui_read_state",
            attributes = mutableMapOf(
                "result" to result,
                "reason" to reason,
                "process" to "hook",
            ),
            statusOk = statusOk,
        )
    }

    private companion object {
        const val MIUI_SMS_PACKAGE = "com.android.mms"
        const val DAO_IMPL_CLASS = "com.android.mms.storage.bugle.ConversationDao_Impl"
        const val DATABASE_CLASS = "com.android.mms.storage.bugle.BugleDatabase"
        const val ENTITY_CLASS = "com.android.mms.storage.bugle.BugleConversation"
        const val DAO_MARK_READ = "actualMarkRead"
        const val MESSAGE_MARK_READ = "markRead"
        const val DB_GET = "get"
        const val DB_MESSAGE = "message"
        const val GET_SNIPPET = "getSnippet"
        const val GET_ID = "getId"
        const val GET_THREAD_ID = "getThreadId"
        const val GET_UNREAD_COUNT = "getUnreadCount"
        const val ACTIVITY_THREAD_CLASS = "android.app.ActivityThread"
        const val CURRENT_APPLICATION = "currentApplication"
        val INGEST_METHODS = arrayOf("actualInsert", "actualUpdate")
    }
}

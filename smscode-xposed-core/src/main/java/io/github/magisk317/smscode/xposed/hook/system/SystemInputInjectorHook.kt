@file:Suppress("TooGenericExceptionCaught")

package io.github.magisk317.smscode.xposed.hook.system

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.Process
import android.os.SystemClock
import android.view.InputEvent
import android.view.KeyCharacterMap
import android.widget.Toast
import io.github.magisk317.smscode.xposed.utils.XLog
import io.github.magisk317.smscode.xposed.runtime.CoreHookPolicyHolder
import io.github.magisk317.smscode.xposed.helper.XposedWrapper
import io.github.magisk317.smscode.xposed.hook.BaseHook
import io.github.magisk317.smscode.xposed.hookapi.HookBridge
import io.github.magisk317.smscode.xposed.hookapi.HookHelpers
import io.github.magisk317.smscode.xposed.hookapi.LoadParam
import io.github.magisk317.smscode.xposed.hookapi.MethodHook
import io.github.magisk317.smscode.xposed.hookapi.MethodHookParam
import io.github.magisk317.smscode.xposed.hookapi.ZygoteParam
import java.lang.reflect.Method

class SystemInputInjectorHook : BaseHook() {
    @Volatile
    private var receiverRegistered = false

    @Volatile
    private var registerAttempts = 0

    @Volatile
    private var inputHandler: Handler? = null

    @Volatile
    private var inputManagerGlobal: Any? = null

    @Volatile
    private var injectMethod: Method? = null

    @Volatile
    private var injectMethodParamCount: Int = 0

    @Volatile
    private var mainHandler: Handler? = null

    @Volatile
    private var amsSystemReadyHooked = false
    @Volatile
    private var suppressionLogged = false
    @Volatile
    private var sendingUidFieldsLogged = false
    @Volatile
    private var lastAutoInputCallerUid: Int = -1
    @Volatile
    private var lastAutoInputCallerUidAt: Long = 0L

    override fun hookInitZygote(): Boolean = true

    override fun initZygote(startupParam: ZygoteParam) {
        try {
            // Redmi K60 Ultra (Redmi 23078RKD5C) Android 16 feedback:
            // system_server starts very early, ActivityThread.systemMain might be missed.
            XposedWrapper.findAndHookMethod(
                "android.app.ActivityThread",
                null,
                "systemMain",
                object : MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        XLog.i("XSmsCode: ActivityThread.systemMain hook triggered")
                        val activityThreadClass = HookHelpers.findClass("android.app.ActivityThread", null)
                        val activityThread = HookHelpers.callStaticMethod(
                            activityThreadClass,
                            "currentActivityThread",
                        )
                        val systemContext = HookHelpers.callMethod(activityThread, "getSystemContext") as? Context
                        if (systemContext != null) {
                            scheduleRegister(systemContext)
                        } else {
                            HookBridge.log("XSmsCode: systemContext is null in ActivityThread.systemMain hook")
                        }
                    }
                },
            )
            XLog.w("SystemInputInjectorHook: hooked ActivityThread.systemMain in zygote")
            HookBridge.log("XSmsCode: hooked ActivityThread.systemMain in zygote")
        } catch (t: Throwable) {
            XLog.e("SystemInputInjectorHook: failed to hook ActivityThread.systemMain in zygote", t)
            HookBridge.log("XSmsCode: failed to hook ActivityThread.systemMain in zygote: ${t.message}")
        }
    }

    override fun hookOnLoadPackage(): Boolean = true

    override fun onLoadPackage(lpparam: LoadParam) {
        if (lpparam.packageName != "android") return

        // Fallback for Redmi K60 Ultra (Android 16): 
        // If systemMain was already executed, try immediate initialization or hook systemReady.
        XLog.i("XSmsCode: SystemInputInjectorHook loading for android package")

        hookBroadcastCallerUid(lpparam.classLoader)

        try {
            // Attempt 1: Check if already ready
            val activityThreadClass = HookHelpers.findClass("android.app.ActivityThread", lpparam.classLoader)
            val activityThread = HookHelpers.callStaticMethod(activityThreadClass, "currentActivityThread")
            if (activityThread != null) {
                val systemContext = HookHelpers.callMethod(activityThread, "getSystemContext") as? Context
                if (systemContext != null) {
                    if (CoreHookPolicyHolder.shouldSuppressSystemHooks(systemContext, "SystemInputInjectorHook#onLoadPackage")) {
                        logSuppressedOnce("onLoadPackage")
                        receiverRegistered = true
                        return
                    }
                    XLog.w("XSmsCode: System context available in onLoadPackage, registering receiver")
                    HookBridge.log("XSmsCode: System context available in onLoadPackage, registering receiver")
                    scheduleRegister(systemContext)
                    if (receiverRegistered) return
                }
            }
        } catch (t: Throwable) {
            XLog.w("Failed to get system context in onLoadPackage: ${t.message}")
        }

        hookAmsSystemReadyFallback(lpparam.classLoader)
    }

    private fun hookAmsSystemReadyFallback(classLoader: ClassLoader?) {
        if (amsSystemReadyHooked) return
        try {
            val amsClass = HookHelpers.findClass("com.android.server.am.ActivityManagerService", classLoader)
            val methods = amsClass.declaredMethods.filter { it.name == "systemReady" }
            if (methods.isEmpty()) {
                XLog.w("SystemInputInjectorHook: no ActivityManagerService.systemReady method found, skip fallback hook")
                return
            }
            methods.forEach { method ->
                HookBridge.hookMethod(
                    method,
                    object : MethodHook() {
                        override fun afterHookedMethod(param: MethodHookParam) {
                            if (receiverRegistered) return
                            XLog.i("XSmsCode: ActivityManagerService.systemReady hook triggered")
                            val context = resolveSystemContext(param.thisObject)
                            if (context != null) {
                                if (CoreHookPolicyHolder.shouldSuppressSystemHooks(context, "SystemInputInjectorHook#systemReady")) {
                                    logSuppressedOnce("systemReady")
                                    receiverRegistered = true
                                    return
                                }
                                scheduleRegister(context)
                            }
                        }
                    },
                )
            }
            amsSystemReadyHooked = true
            XLog.w("SystemInputInjectorHook: hooked ActivityManagerService.systemReady overloads as fallback")
        } catch (t: Throwable) {
            XLog.e("SystemInputInjectorHook: failed to hook AMS.systemReady overloads", t)
        }
    }

    private fun resolveSystemContext(systemService: Any?): Context? {
        if (systemService == null) return null
        return try {
            HookHelpers.getObjectField(systemService, "mContext") as? Context
        } catch (_: Throwable) {
            try {
                HookHelpers.getObjectField(systemService, "mSystemContext") as? Context
            } catch (_: Throwable) {
                null
            }
        }
    }

    private fun scheduleRegister(context: Context) {
        if (receiverRegistered) return
        getMainHandler().postDelayed(
            { registerReceiver(context) },
            DELAY_REGISTER,
        )
    }

    @Volatile
    private var broadcastHooked = false

    private fun hookBroadcastCallerUid(classLoader: ClassLoader?) {
        if (broadcastHooked) return
        try {
            val queueClass = HookHelpers.findClassIfExists(
                "com.android.server.am.BroadcastQueueImpl",
                classLoader,
            ) ?: HookHelpers.findClassIfExists(
                "com.android.server.am.BroadcastQueue",
                classLoader,
            ) ?: return

            val methods = queueClass.declaredMethods.filter { it.name == "enqueueBroadcastLocked" }
            if (methods.isEmpty()) return

            methods.forEach { method ->
                HookBridge.hookMethod(
                    method,
                    object : MethodHook() {
                        override fun afterHookedMethod(param: MethodHookParam) {
                            val record = param.args.firstOrNull() ?: return
                            val intent = extractBroadcastIntent(record) ?: return
                            val action = intent.action ?: return
                            if (action != resolveActionAutoInput() && action != resolveActionShowToast()) return
                            val (uid, pkg) = extractCallerInfo(record)
                            if (uid >= 0) {
                                lastAutoInputCallerUid = uid
                                lastAutoInputCallerUidAt = SystemClock.elapsedRealtime()
                            }
                            XLog.w(
                                "Diag broadcast enqueue: action=%s callerUid=%d callerPkg=%s record=%s",
                                action,
                                uid,
                                pkg ?: "unknown",
                                record.javaClass.name,
                            )
                        }
                    },
                )
            }
            broadcastHooked = true
            XLog.w("SystemInputInjectorHook: hooked BroadcastQueue enqueue for caller uid")
        } catch (t: Throwable) {
            XLog.w("SystemInputInjectorHook: failed to hook BroadcastQueue: %s", t.message ?: t.javaClass.simpleName)
        }
    }

    private fun extractBroadcastIntent(record: Any): Intent? {
        if (record is Intent) return record
        return runCatching { HookHelpers.getObjectField(record, "intent") as? Intent }.getOrNull()
            ?: runCatching { HookHelpers.callMethod(record, "getIntent") as? Intent }.getOrNull()
    }

    private fun extractCallerInfo(record: Any): Pair<Int, String?> {
        val uidFields = listOf("callerUid", "callingUid", "uid")
        for (field in uidFields) {
            runCatching { HookHelpers.getIntField(record, field) }.getOrNull()?.let { return it to null }
        }
        var callerPkg = runCatching { HookHelpers.getObjectField(record, "callerPackage") as? String }.getOrNull()
        val callerApp = runCatching { HookHelpers.getObjectField(record, "callerApp") }.getOrNull()
        if (callerApp != null) {
            runCatching { HookHelpers.getIntField(callerApp, "uid") }.getOrNull()?.let { uid ->
                return uid to callerPkg
            }
            runCatching { HookHelpers.getObjectField(callerApp, "info") }.getOrNull()?.let { info ->
                if (callerPkg == null) {
                    callerPkg = runCatching { HookHelpers.getObjectField(info, "packageName") as? String }.getOrNull()
                        ?: runCatching { HookHelpers.callMethod(info, "getPackageName") as? String }.getOrNull()
                }
                runCatching { HookHelpers.getIntField(info, "uid") }.getOrNull()?.let { uid ->
                    return uid to callerPkg
                }
            }
        }
        return -1 to callerPkg
    }

    @Suppress("TooGenericExceptionCaught")
    private fun registerReceiver(context: Context) {
        try {
            if (receiverRegistered) return
            if (CoreHookPolicyHolder.shouldSuppressSystemHooks(context, "SystemInputInjectorHook#registerReceiver")) {
                logSuppressedOnce("registerReceiver")
                receiverRegistered = true
                return
            }
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    val sendingUid = resolveSendingUid(this)
                    val appUid = context.applicationInfo.uid
                    if (sendingUid != -1 && sendingUid != Process.SYSTEM_UID && sendingUid != Process.PHONE_UID &&
                        sendingUid != appUid
                    ) {
                        XLog.w("SystemServer input request rejected from uid=%d", sendingUid)
                        return
                    }
                    if (intent.action == resolveActionShowToast()) {
                        val text = intent.getStringExtra("text")
                        val duration = intent.getIntExtra("duration", Toast.LENGTH_LONG)
                        XLog.w(
                            "Diag system toast request: uid=%d text_len=%d duration=%d",
                            sendingUid,
                            text?.length ?: 0,
                            duration,
                        )
                        if (text.isNullOrEmpty()) {
                            XLog.w("SystemServer toast request ignored: empty text")
                            return
                        }
                        showToast(context, text, duration)
                        return
                    }
                    val code = intent.getStringExtra("code")
                    val autoEnter = intent.getBooleanExtra("autoEnter", false)
                    val inputIntervalMs = intent.getLongExtra("inputIntervalMs", 0L).coerceAtLeast(0L)
                    val attemptId = intent.getLongExtra("attemptId", -1L).takeIf { it >= 0L }
                    XLog.w(
                        "Diag system receiver onReceive: uid=%d code_len=%d autoEnter=%s inputIntervalMs=%d",
                        sendingUid,
                        code?.length ?: 0,
                        autoEnter,
                        inputIntervalMs,
                    )
                    if (!code.isNullOrEmpty()) {
                        XLog.i(
                            "SystemServer received input request: %s, autoEnter: %s, inputIntervalMs: %d",
                            code,
                            autoEnter,
                            inputIntervalMs,
                        )
                        injectText(context, code, autoEnter, inputIntervalMs, attemptId)
                    } else {
                        XLog.w("SystemServer received input request with empty code")
                        sendAutoInputResult(context, attemptId, success = false, reason = "empty_code")
                    }
                }
            }
            val filter = IntentFilter(resolveActionAutoInput())
            filter.addAction(resolveActionShowToast())
            filter.priority = RECEIVER_PRIORITY_SYSTEM_FALLBACK
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
            } else {
                context.registerReceiver(receiver, filter)
            }
            receiverRegistered = true
            XLog.w("SystemInputInjectorReceiver registered")
            HookBridge.log("XSmsCode: SystemInputInjectorReceiver registered")
        } catch (t: Throwable) {
            registerAttempts += 1
            XLog.e("Failed to register receiver", t)
            HookBridge.log("XSmsCode: Failed to register receiver: ${t.message}")
            if (registerAttempts < MAX_REGISTER_ATTEMPTS) {
                scheduleRegister(context)
            } else {
                HookBridge.log("XSmsCode: registerReceiver give up after $registerAttempts attempts")
            }
        }
    }

    private fun showToast(context: Context, text: String, duration: Int) {
        getMainHandler().post {
            try {
                val toast = Toast.makeText(context, text, duration)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    toast.addCallback(
                        object : Toast.Callback() {
                            override fun onToastShown() {
                                XLog.w("Diag system toast shown: text_len=%d", text.length)
                            }

                            override fun onToastHidden() {
                                XLog.w("Diag system toast hidden: text_len=%d", text.length)
                            }
                        },
                    )
                }
                toast.show()
                XLog.w("SystemServer toast show invoked: text_len=%d duration=%d", text.length, duration)
            } catch (t: Throwable) {
                XLog.e("Failed to show toast from System Server", t)
            }
        }
    }

    private fun logSuppressedOnce(stage: String) {
        if (suppressionLogged) return
        synchronized(this) {
            if (suppressionLogged) return
            XLog.w("SystemInputInjectorHook suppressed: stage=%s", stage)
            suppressionLogged = true
        }
    }

    private fun resolveSendingUid(receiver: BroadcastReceiver): Int {
        val cached = lastAutoInputCallerUid
        if (cached >= 0) {
            val ageMs = SystemClock.elapsedRealtime() - lastAutoInputCallerUidAt
            if (ageMs in 0..CACHED_UID_TTL_MS) {
                return cached
            }
        }
        // Try system API if available
        val direct = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                HookHelpers.callMethod(receiver, "getSendingUid") as Int
            } else {
                null
            }
        } catch (t: Throwable) {
            XLog.w("Failed to get sendingUid: ${t.message}")
            null
        }
        if (direct != null) return direct

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            val pendingResult = runCatching { HookHelpers.callMethod(receiver, "getPendingResult") }.getOrNull()
                ?: runCatching { HookHelpers.getObjectField(receiver, "mPendingResult") }.getOrNull()
            if (pendingResult != null) {
                val candidates = listOf("mSentFromUid", "mSendingUid", "mSenderUid", "mCallingUid")
                for (field in candidates) {
                    runCatching { HookHelpers.getIntField(pendingResult, field) }.getOrNull()?.let { return it }
                }
                logPendingResultFieldsOnce(pendingResult)
            } else {
                logPendingResultMissingOnce()
            }
        }
        return -1
    }

    @Volatile
    private var pendingResultMissingLogged = false

    private fun logPendingResultMissingOnce() {
        if (pendingResultMissingLogged) return
        synchronized(this) {
            if (pendingResultMissingLogged) return
            pendingResultMissingLogged = true
        }
        XLog.w("Diag pendingResult is null; unable to resolve sendingUid")
    }

    private fun logPendingResultFieldsOnce(pendingResult: Any) {
        if (sendingUidFieldsLogged) return
        synchronized(this) {
            if (sendingUidFieldsLogged) return
            sendingUidFieldsLogged = true
        }
        val fields = generateSequence(pendingResult.javaClass) { it.superclass }
            .flatMap { it.declaredFields.asSequence() }
            .map { "${it.name}:${it.type.name}" }
            .distinct()
            .sorted()
            .joinToString(limit = 80, truncated = "...")
        XLog.w("Diag pendingResult fields: %s", fields)
    }

    private fun getInputHandler(): Handler {
        val cached = inputHandler
        if (cached != null) return cached
        return synchronized(this) {
            val existing = inputHandler
            if (existing != null) {
                existing
            } else {
                val thread = HandlerThread("xsmscode-input")
                thread.start()
                Handler(thread.looper).also { inputHandler = it }
            }
        }
    }

    private fun getMainHandler(): Handler {
        val cached = mainHandler
        if (cached != null) return cached
        return synchronized(this) {
            val existing = mainHandler
            if (existing != null) {
                existing
            } else {
                val mainLooper = Looper.getMainLooper()
                val handler = if (mainLooper != null) {
                    Handler(mainLooper)
                } else {
                    // Fallback for early zygote stage when main looper isn't ready yet.
                    getInputHandler()
                }
                mainHandler = handler
                handler
            }
        }
    }

    private fun getInputManagerGlobal(): Pair<Any, Method>? {
        val cachedManager = inputManagerGlobal
        val cachedMethod = injectMethod
        if (cachedManager != null && cachedMethod != null) return cachedManager to cachedMethod
        return synchronized(this) {
            val manager = inputManagerGlobal
            val method = injectMethod
            if (manager != null && method != null) {
                manager to method
            } else {
                try {
                    val classCandidates = buildList {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                            add("android.hardware.input.InputManagerGlobal")
                            add("android.hardware.input.InputManager")
                        } else {
                            add("android.hardware.input.InputManager")
                            add("android.hardware.input.InputManagerGlobal")
                        }
                    }
                    val errors = mutableListOf<String>()
                    for (className in classCandidates) {
                        try {
                            val inputManagerClass = HookHelpers.findClass(className, null)
                            val instance = HookHelpers.callStaticMethod(inputManagerClass, "getInstance") ?: continue
                            val inject = findInjectMethod(inputManagerClass)
                            inputManagerGlobal = instance
                            injectMethod = inject
                            injectMethodParamCount = inject.parameterTypes.size
                            XLog.i(
                                "Resolved input injector: class=%s, method=%s",
                                className,
                                inject.toGenericString(),
                            )
                            return@synchronized instance to inject
                        } catch (t: Throwable) {
                            errors += "$className -> ${t::class.java.simpleName}: ${t.message}"
                        }
                    }
                    throw NoSuchMethodError(
                        "No compatible injectInputEvent found. Details: ${errors.joinToString(" | ")}",
                    )
                } catch (t: Throwable) {
                    XLog.e("Failed to resolve InputManagerGlobal", t)
                    null
                }
            }
        }
    }

    private fun findInjectMethod(inputManagerClass: Class<*>): Method {
        val candidates = (inputManagerClass.declaredMethods + inputManagerClass.methods).distinctBy {
            "${it.name}#${it.parameterTypes.joinToString(",") { p -> p.name }}"
        }
        val preferred = candidates.firstOrNull { method ->
            if (method.name != "injectInputEvent") return@firstOrNull false
            val params = method.parameterTypes
            params.size == 2 && params[0] == InputEvent::class.java && params[1] == Int::class.javaPrimitiveType
        }
        if (preferred != null) {
            preferred.isAccessible = true
            return preferred
        }

        val fallback = candidates.firstOrNull { method ->
            if (method.name != "injectInputEvent") return@firstOrNull false
            val params = method.parameterTypes
            when (params.size) {
                2 -> InputEvent::class.java.isAssignableFrom(params[0]) &&
                    (params[1] == Int::class.javaPrimitiveType || params[1] == Int::class.java)
                3 -> InputEvent::class.java.isAssignableFrom(params[0]) &&
                    (params[1] == Int::class.javaPrimitiveType || params[1] == Int::class.java) &&
                    (params[2] == Int::class.javaPrimitiveType || params[2] == Int::class.java)
                else -> false
            }
        }
        if (fallback != null) {
            fallback.isAccessible = true
            return fallback
        }

        val signatureDump = candidates
            .filter { it.name == "injectInputEvent" }
            .joinToString("; ") { it.toGenericString() }
            .ifBlank { "none" }
        throw NoSuchMethodException("injectInputEvent signatures: $signatureDump")
    }

    private fun invokeInject(manager: Any, method: Method, event: InputEvent, mode: Int): Boolean {
        val result = when (injectMethodParamCount) {
            2 -> method.invoke(manager, event, mode)
            3 -> method.invoke(manager, event, mode, 0)
            else -> return false
        }
        return when (result) {
            is Boolean -> result
            is Number -> result.toInt() != 0
            else -> false
        }
    }

    private fun injectText(
        context: Context,
        text: String,
        autoEnter: Boolean = false,
        inputIntervalMs: Long = 0L,
        attemptId: Long? = null,
    ) {
        getInputHandler().post {
            var success = false
            var failReason: String? = null
            try {
                val managerPair = getInputManagerGlobal() ?: return@post
                val (manager, method) = managerPair
                val mode = 0 // InputManager.INJECT_INPUT_EVENT_MODE_ASYNC
                var injectedCount = 0
                val keyCharacterMap = KeyCharacterMap.load(KeyCharacterMap.VIRTUAL_KEYBOARD)
                text.forEachIndexed { index, char ->
                    val events = keyCharacterMap.getEvents(charArrayOf(char))
                    if (events == null) {
                        XLog.w("Failed to create key events for char: %s", char.toString())
                        return@forEachIndexed
                    }
                    for (event in events) {
                        val result = invokeInject(manager, method, event, mode)
                        if (result) injectedCount += 1
                    }
                    if (inputIntervalMs > 0L && index < text.lastIndex) {
                        Thread.sleep(inputIntervalMs)
                    }
                }
                XLog.w("Injected key characters from System Server, count=%d", injectedCount)

                if (autoEnter) {
                    val now = android.os.SystemClock.uptimeMillis()
                    val downEvent = android.view.KeyEvent(now, now, android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_ENTER, 0)
                    val upEvent = android.view.KeyEvent(now, now, android.view.KeyEvent.ACTION_UP, android.view.KeyEvent.KEYCODE_ENTER, 0)

                    val downResult = invokeInject(manager, method, downEvent, mode)
                    val upResult = invokeInject(manager, method, upEvent, mode)

                    if (downResult && upResult) {
                        XLog.w("Injected KEYCODE_ENTER from System Server")
                        success = injectedCount > 0
                    } else {
                        XLog.e("Failed to inject KEYCODE_ENTER: down=$downResult, up=$upResult")
                        failReason = "enter_failed"
                    }
                } else {
                    success = injectedCount > 0
                }
            } catch (t: Throwable) {
                XLog.e("Failed to inject text/enter from System Server", t)
                failReason = "inject_exception"
            } finally {
                sendAutoInputResult(context, attemptId, success, failReason)
            }
        }
    }

    private fun sendAutoInputResult(
        context: Context,
        attemptId: Long?,
        success: Boolean,
        reason: String?,
    ) {
        if (attemptId == null) return
        runCatching {
            val intent = Intent(resolveActionAutoInputResult())
            val appId = io.github.magisk317.smscode.xposed.runtime.CoreRuntime.access.applicationId
            if (appId.isNotBlank()) {
                intent.setPackage(appId)
            }
            intent.putExtra("attemptId", attemptId)
            intent.putExtra("success", success)
            if (!success && !reason.isNullOrBlank()) {
                intent.putExtra("reason", reason)
            }
            context.sendBroadcast(intent)
            XLog.w(
                "Diag auto input result broadcast: attemptId=%d success=%s reason=%s",
                attemptId,
                success,
                reason ?: "<none>",
            )
        }.onFailure { error ->
            XLog.w(
                "Send auto input result failed: %s",
                error.message ?: error.javaClass.simpleName,
            )
        }
    }

    companion object {
        private const val DELAY_REGISTER = 500L
        private const val MAX_REGISTER_ATTEMPTS = 10
        private const val CACHED_UID_TTL_MS = 10_000L
        private const val RECEIVER_PRIORITY_SYSTEM_FALLBACK = -1000

        @Suppress("unused")
        fun resolveActionAutoInput(): String = "${io.github.magisk317.smscode.xposed.runtime.CoreRuntime.access.actionNamespace}.ACTION_AUTO_INPUT"

        fun resolveActionShowToast(): String =
            "${io.github.magisk317.smscode.xposed.runtime.CoreRuntime.access.actionNamespace}.ACTION_SHOW_TOAST"

        fun resolveActionAutoInputResult(): String =
            "${io.github.magisk317.smscode.xposed.runtime.CoreRuntime.access.actionNamespace}.ACTION_AUTO_INPUT_RESULT"
    }
}

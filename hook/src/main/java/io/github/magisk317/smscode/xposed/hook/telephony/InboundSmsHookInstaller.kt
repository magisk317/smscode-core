package io.github.magisk317.smscode.xposed.hook.telephony

import android.content.BroadcastReceiver
import io.github.magisk317.xposed.logging.XLog
import io.github.magisk317.xposed.HookEnv
import io.github.magisk317.xposed.HookHelpers
import io.github.magisk317.xposed.MethodHook
import io.github.magisk317.xposed.MethodHookParam

/**
 * Shared reflection plumbing for the inbound SMS handler hooks.
 *
 * Which system classes and methods carry an SMS through the telephony process varies
 * across Android versions and OEM builds, so both hosts used to carry their own copy of
 * "find the class, enumerate the methods, hook them". Only the callbacks differ, so the
 * lookup and installation live here.
 *
 * Hosts stay in control of what happens inside a hook; this object only decides what
 * gets hooked.
 */
object InboundSmsHookInstaller {

    const val SMS_HANDLER_CLASS = "com.android.internal.telephony.InboundSmsHandler"
    const val SMS_DISPATCHERS_CONTROLLER_CLASS =
        "com.android.internal.telephony.SmsDispatchersController"

    /** Dispatcher methods that carry an SMS on some Android/OEM builds. */
    val SMS_DISPATCHER_METHODS = listOf(
        "dispatchSmsDeliveryIntent",
        "dispatchSmsDeliveryIntentToApp",
        "dispatchSmsDeliveryIntentToRegisteredReceivers",
    )

    /** Newer builds route through the controller, with an extra permission-checked variant. */
    val SMS_DISPATCHER_CONTROLLER_METHODS = SMS_DISPATCHER_METHODS + listOf(
        "dispatchSmsDeliveryIntentToAppWithPermission",
    )

    data class Result(
        val hookedMethodCount: Int,
        val classFound: Boolean,
        val methodNames: List<String> = emptyList(),
        val missingClassNames: List<String> = emptyList(),
    )

    /** Hooks every constructor of [SMS_HANDLER_CLASS]; the runtime is born there. */
    fun installConstructorHook(
        classLoader: ClassLoader,
        className: String = SMS_HANDLER_CLASS,
        afterHook: (MethodHookParam) -> Unit,
    ): Result {
        val clazz = findClass(className, classLoader) ?: return Result(0, classFound = false)
        HookEnv.api.hookAllConstructors(
            clazz,
            object : MethodHook() {
                override val stableId: String = "inbound_sms.constructor"
                override fun afterHookedMethod(param: MethodHookParam) = afterHook(param)
            },
        )
        return Result(hookedMethodCount = 1, classFound = true)
    }

    /**
     * Hooks `dispatchIntent` on [SMS_HANDLER_CLASS].
     *
     * The receiver argument index differs across builds, so it is resolved per method and
     * handed to [beforeDispatch]; getting this wrong means reading the wrong argument as
     * the BroadcastReceiver, which used to be a per-host bug.
     */
    fun installDispatchIntentHook(
        classLoader: ClassLoader,
        className: String = SMS_HANDLER_CLASS,
        methodName: String = DISPATCH_INTENT_METHOD,
        beforeDispatch: (param: MethodHookParam, receiverIndex: Int) -> Unit,
    ): Result {
        val clazz = findClass(className, classLoader) ?: return Result(0, classFound = false)
        val methods = clazz.declaredMethods.filter { it.name == methodName }
        if (methods.isEmpty()) {
            XLog.e(message = "Method %s for Class %s cannot found", methodName, className)
            return Result(0, classFound = true)
        }
        var count = 0
        methods.forEach { method ->
            val receiverIndex = method.receiverArgumentIndex()
            HookEnv.api.hookMethod(
                method,
                object : MethodHook() {
                    override val stableId: String = "inbound_sms.dispatch_intent"
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        beforeDispatch(param, receiverIndex)
                    }
                },
            )
            count += 1
        }
        return Result(hookedMethodCount = count, classFound = true, methodNames = listOf(methodName))
    }

    /**
     * Hooks the dispatcher chain, which some ROMs use instead of `dispatchIntent`.
     *
     * Both the handler and the controller are covered because the routing differs per
     * build; [beforeDispatch] receives the hooked class name so hosts can tell them apart.
     */
    fun installDispatcherChainHooks(
        classLoader: ClassLoader,
        beforeDispatch: (className: String, methodName: String, param: MethodHookParam) -> Unit,
    ): Result {
        var count = 0
        val missing = mutableListOf<String>()

        count += installDispatcherMethods(
            classLoader = classLoader,
            className = SMS_HANDLER_CLASS,
            methodNames = SMS_DISPATCHER_METHODS,
            missing = missing,
            beforeDispatch = beforeDispatch,
        )
        count += installDispatcherMethods(
            classLoader = classLoader,
            className = SMS_DISPATCHERS_CONTROLLER_CLASS,
            methodNames = SMS_DISPATCHER_CONTROLLER_METHODS,
            missing = missing,
            beforeDispatch = beforeDispatch,
        )
        return Result(
            hookedMethodCount = count,
            classFound = missing.isEmpty(),
            methodNames = SMS_DISPATCHER_METHODS + SMS_DISPATCHER_CONTROLLER_METHODS,
            missingClassNames = missing,
        )
    }

    private fun installDispatcherMethods(
        classLoader: ClassLoader,
        className: String,
        methodNames: List<String>,
        missing: MutableList<String>,
        beforeDispatch: (className: String, methodName: String, param: MethodHookParam) -> Unit,
    ): Int {
        val clazz = findClass(className, classLoader, missing) ?: return 0
        var count = 0
        methodNames.forEach { name ->
            clazz.declaredMethods
                .filter { it.name == name }
                .forEach { method ->
                    HookEnv.api.hookMethod(
                        method,
                        object : MethodHook() {
                            override val stableId: String = "inbound_sms.dispatcher.$className.$name"
                            override fun beforeHookedMethod(param: MethodHookParam) {
                                beforeDispatch(className, name, param)
                            }
                        },
                    )
                    count += 1
                }
        }
        return count
    }

    private fun findClass(
        className: String,
        classLoader: ClassLoader,
        missing: MutableList<String>? = null,
    ): Class<*>? = runCatching {
        HookHelpers.findClass(className, classLoader)
    }.getOrElse {
        missing?.add(className)
        null
    }

    const val DISPATCH_INTENT_METHOD = "dispatchIntent"
}

/**
 * Index of the first [BroadcastReceiver] parameter, or -1 when the method takes none.
 *
 * `dispatchIntent` has moved its parameters across Android versions, so the index is
 * resolved per method rather than assumed.
 */
internal fun java.lang.reflect.Method.receiverArgumentIndex(): Int {
    val types = parameterTypes
    for (index in types.indices) {
        if (BroadcastReceiver::class.java.isAssignableFrom(types[index])) return index
    }
    return -1
}

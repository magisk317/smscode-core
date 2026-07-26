package io.github.magisk317.smscode.xposed.hook.telephony

import android.content.Context
import android.content.Intent
import io.github.magisk317.xposed.HookEnv
import io.github.magisk317.xposed.HookHelpers
import io.github.magisk317.xposed.MethodHook
import io.github.magisk317.xposed.MethodHookParam

object MmsEntryPointHookInstaller {
    data class Result(
        val hookedMethodCount: Int,
        val missingClassNames: List<String>,
    )

    fun install(
        classLoader: ClassLoader,
        beforeIntent: (Context, Intent, String, MethodHookParam) -> Unit,
    ): Result {
        val missingClassNames = mutableListOf<String>()
        var hookedMethodCount = 0

        RECEIVER_CLASS_NAMES.forEach { className ->
            val receiverClass = findClass(className, classLoader, missingClassNames) ?: return@forEach
            val callback = object : MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val context = param.args.getOrNull(0) as? Context ?: return
                    val intent = param.args.getOrNull(1) as? Intent ?: return
                    beforeIntent(context, intent, className, param)
                }
            }
            val visited = linkedSetOf<Class<*>>()
            var current: Class<*>? = receiverClass
            while (current != null && current != Any::class.java && visited.add(current)) {
                current.declaredMethods
                    .filter { method ->
                        val types = method.parameterTypes
                        types.size == 2 &&
                            Context::class.java.isAssignableFrom(types[0]) &&
                            Intent::class.java.isAssignableFrom(types[1])
                    }
                    .forEach { method ->
                        HookEnv.api.hookMethod(method, callback)
                        hookedMethodCount += 1
                    }
                current = current.superclass
            }
        }

        SERVICE_CLASS_NAMES.forEach { className ->
            val serviceClass = findClass(className, classLoader, missingClassNames) ?: return@forEach
            serviceClass.declaredMethods
                .filter { method ->
                    method.name in SERVICE_METHOD_NAMES &&
                        method.parameterTypes.any(Intent::class.java::isAssignableFrom)
                }
                .forEach { method ->
                    HookEnv.api.hookMethod(
                        method,
                        object : MethodHook() {
                            override fun beforeHookedMethod(param: MethodHookParam) {
                                val context = param.thisObject as? Context ?: return
                                val intent = param.args.firstOrNull { it is Intent } as? Intent ?: return
                                beforeIntent(context, intent, "$className.${method.name}", param)
                            }
                        },
                    )
                    hookedMethodCount += 1
                }
        }

        return Result(hookedMethodCount, missingClassNames)
    }

    private fun findClass(
        className: String,
        classLoader: ClassLoader,
        missingClassNames: MutableList<String>,
    ): Class<*>? = runCatching {
        HookHelpers.findClass(className, classLoader)
    }.getOrElse {
        missingClassNames += className
        null
    }

    private val RECEIVER_CLASS_NAMES = listOf(
        "com.android.mms.transaction.PrivilegedSmsReceiver",
        "com.android.mms.transaction.SmsReceiver",
    )
    private val SERVICE_CLASS_NAMES = listOf(
        "com.android.mms.transaction.SmsReceiverService",
    )
    private val SERVICE_METHOD_NAMES = setOf("onStartCommand", "handleIntent", "onHandleIntent")
}

package io.github.magisk317.smscode.xposed.hookapi

import android.util.Log
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import io.github.magisk317.smscode.xposed.utils.XLog
import io.github.magisk317.smscode.xposed.utils.markInterruptedIfNeeded
import io.github.magisk317.smscode.xposed.utils.rethrowIfFatal
import java.lang.reflect.Executable
import java.lang.reflect.Member
import java.lang.reflect.Method
import java.lang.reflect.Modifier

abstract class BaseLibXposedHookApi(
    protected val module: XposedModule,
) : HookApi {
    override fun hookMethod(member: Member, callback: MethodHook): HookHandle? {
        if (member is Method && (Modifier.isAbstract(member.modifiers) || Modifier.isNative(member.modifiers))) {
            XLog.d("Skip hook unsupported method: %s#%s", member.declaringClass.name, member.name)
            return null
        }
        val executable = member as? Executable ?: return null
        val handle = hookExecutable(executable, callback, createHooker(member, callback))
        return LibHookHandle(handle)
    }

    protected open fun hookExecutable(
        executable: Executable,
        callback: MethodHook,
        hooker: XposedInterface.Hooker,
    ): XposedInterface.HookHandle {
        val hookId = buildHookId(executable, callback)
        return module.hook(executable).setId(hookId).intercept(hooker)
    }

    protected fun buildHookId(executable: Executable, callback: MethodHook): String {
        return "smscode:${callback.stableId ?: callback.javaClass.name}@${buildExecutableSignature(executable)}"
    }

    protected fun buildLegacyHookId(executable: Executable, callback: MethodHook): String {
        return "smscode:${callback.javaClass.name}@${buildExecutableSignature(executable)}"
    }

    protected fun buildExecutableSignature(executable: Executable): String {
        val params = executable.parameterTypes.joinToString(",") { it.name }
        return "${executable.declaringClass.name}#${executable.name}($params)"
    }

    private fun createHooker(member: Member, callback: MethodHook): XposedInterface.Hooker {
        return XposedInterface.Hooker { chain ->
            val args = chain.args.toTypedArray()
            val param = SimpleMethodHookParam(chain.executable, chain.thisObject, args)
            val beforeFailure = runCatching {
                callback.beforeHookedMethod(param)
            }.exceptionOrNull()
            if (beforeFailure != null) {
                beforeFailure.markInterruptedIfNeeded()
                beforeFailure.rethrowIfFatal()
                module.log(Log.WARN, LOG_TAG, "Hook before failed: ${member.name}", beforeFailure)
            }
            if (param.returnEarly) {
                val throwable = param.throwable
                if (throwable != null) throw throwable
                return@Hooker param.result
            }

            var result: Any? = null
            var throwable: Throwable? = null
            val proceedFailure = runCatching {
                result = chain.proceed(param.args)
            }.exceptionOrNull()
            if (proceedFailure != null) {
                proceedFailure.markInterruptedIfNeeded()
                proceedFailure.rethrowIfFatal()
                throwable = proceedFailure
            }
            param.returnEarly = false
            param.result = result
            param.throwable = throwable
            val afterFailure = runCatching {
                callback.afterHookedMethod(param)
            }.exceptionOrNull()
            if (afterFailure != null) {
                afterFailure.markInterruptedIfNeeded()
                afterFailure.rethrowIfFatal()
                module.log(Log.WARN, LOG_TAG, "Hook after failed: ${member.name}", afterFailure)
            }
            val finalThrowable = param.throwable
            if (finalThrowable != null) throw finalThrowable
            param.result
        }
    }

    override fun hookAllConstructors(clazz: Class<*>, callback: MethodHook): Set<HookHandle> {
        return clazz.declaredConstructors.mapNotNull { hookMethod(it, callback) }.toSet()
    }

    override fun hookAllMethods(clazz: Class<*>, methodName: String, callback: MethodHook): Set<HookHandle> {
        return clazz.declaredMethods
            .filter { it.name == methodName }
            .mapNotNull { hookMethod(it, callback) }
            .toSet()
    }

    override fun log(priority: Int, tag: String?, msg: String, tr: Throwable?) {
        if (tr != null) {
            module.log(priority, tag, msg, tr)
        } else {
            module.log(priority, tag, msg)
        }
    }

    override fun getApiVersion(): Int? = module.apiVersion

    override fun getFrameworkName(): String? = module.frameworkName

    override fun getFrameworkVersionCode(): Long? = module.frameworkVersionCode

    override fun getXposedBridgeVersion(): Int? = null

    private class LibHookHandle(private val handle: XposedInterface.HookHandle) : HookHandle {
        override fun unhook() {
            handle.unhook()
        }
    }

    private companion object {
        private const val LOG_TAG = "XSmsCode"
    }
}

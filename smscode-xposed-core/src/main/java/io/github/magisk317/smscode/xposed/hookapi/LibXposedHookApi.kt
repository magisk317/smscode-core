package io.github.magisk317.smscode.xposed.hookapi

import android.util.Log
import io.github.magisk317.smscode.xposed.utils.XLog
import io.github.magisk317.smscode.xposed.utils.markInterruptedIfNeeded
import io.github.magisk317.smscode.xposed.utils.rethrowIfFatal
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import java.lang.reflect.Executable
import java.lang.reflect.Member
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import io.github.magisk317.smscode.xposed.hookapi.MethodHookParam

class LibXposedHookApi(private val module: XposedModule) : HookApi {
    override fun hookMethod(member: Member, callback: MethodHook): HookHandle? {
        if (member is Method && (Modifier.isAbstract(member.modifiers) || Modifier.isNative(member.modifiers))) {
            XLog.d("Skip hook unsupported method: %s#%s", member.declaringClass.name, member.name)
            return null
        }
        val executable = member as? Executable ?: return null
        val handle = module.hook(executable).intercept { chain ->
            val args = chain.args.toTypedArray()
            val param = SimpleMethodHookParam(chain.executable, chain.thisObject, args)
            val beforeFailure = runCatching {
                callback.beforeHookedMethod(param)
            }.exceptionOrNull()
            if (beforeFailure != null) {
                beforeFailure.markInterruptedIfNeeded()
                beforeFailure.rethrowIfFatal()
                module.log(Log.WARN, "XSmsCode", "Hook before failed: ${member.name}", beforeFailure)
            }
            val early = param.returnEarly
            if (early) {
                val throwable = param.throwable
                if (throwable != null) throw throwable
                return@intercept param.result
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
                module.log(Log.WARN, "XSmsCode", "Hook after failed: ${member.name}", afterFailure)
            }
            val finalThrowable = param.throwable
            if (finalThrowable != null) throw finalThrowable
            return@intercept param.result
        }
        return LibHookHandle(handle)
    }

    override fun hookAllConstructors(clazz: Class<*>, callback: MethodHook): Set<HookHandle> {
        val handles = clazz.declaredConstructors.mapNotNull { hookMethod(it, callback) }
        return handles.toSet()
    }

    override fun hookAllMethods(clazz: Class<*>, methodName: String, callback: MethodHook): Set<HookHandle> {
        val handles = clazz.declaredMethods
            .filter { it.name == methodName }
            .mapNotNull { hookMethod(it, callback) }
        return handles.toSet()
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
}

package io.github.magisk317.smscode.core.hookapi

import android.util.Log
import io.github.magisk317.smscode.core.runtime.CoreRuntime
import java.lang.reflect.Member

object HookBridge {
    fun hookMethod(member: Member?, callback: MethodHook): HookHandle? {
        if (member == null) return null
        return HookEnv.api.hookMethod(member, callback)
    }

    fun hookAllConstructors(clazz: Class<*>, callback: MethodHook): Set<HookHandle> =
        HookEnv.api.hookAllConstructors(clazz, callback)

    fun hookAllMethods(clazz: Class<*>, methodName: String, callback: MethodHook): Set<HookHandle> =
        HookEnv.api.hookAllMethods(clazz, methodName, callback)

    fun log(message: String, tr: Throwable? = null) {
        val runtime = CoreRuntime.access
        if (!runtime.logToXposed) return
        HookEnv.api.log(Log.INFO, runtime.logTag, message, tr)
    }
}

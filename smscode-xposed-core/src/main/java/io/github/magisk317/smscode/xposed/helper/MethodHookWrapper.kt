package io.github.magisk317.smscode.xposed.helper

import io.github.magisk317.smscode.xposed.utils.XLog
import io.github.magisk317.smscode.xposed.hookapi.MethodHook
import io.github.magisk317.smscode.xposed.hookapi.MethodHookParam
import io.github.magisk317.smscode.xposed.utils.markInterruptedIfNeeded
import io.github.magisk317.smscode.xposed.utils.rethrowIfFatal

abstract class MethodHookWrapper : MethodHook() {
    @Throws(Throwable::class)
    override fun beforeHookedMethod(param: MethodHookParam) {
        val failure = runCatching {
            before(param)
        }.exceptionOrNull()
        if (failure != null) {
            failure.markInterruptedIfNeeded()
            failure.rethrowIfFatal()
            XLog.d("Error in hook %s", param.method.name, failure)
        }
    }

    @Throws(Throwable::class)
    protected open fun before(param: MethodHookParam) {
    }

    @Throws(Throwable::class)
    override fun afterHookedMethod(param: MethodHookParam) {
        val failure = runCatching {
            after(param)
        }.exceptionOrNull()
        if (failure != null) {
            failure.markInterruptedIfNeeded()
            failure.rethrowIfFatal()
            XLog.e("Error in hook %s", param.method.name, failure)
        }
    }

    @Throws(Throwable::class)
    protected open fun after(param: MethodHookParam) {
    }
}

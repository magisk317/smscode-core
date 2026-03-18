@file:Suppress("TooGenericExceptionCaught")

package io.github.magisk317.smscode.core.helper

import io.github.magisk317.smscode.core.utils.XLog
import io.github.magisk317.smscode.core.hookapi.MethodHook
import io.github.magisk317.smscode.core.hookapi.MethodHookParam

abstract class MethodHookWrapper : MethodHook() {
    @Throws(Throwable::class)
    override fun beforeHookedMethod(param: MethodHookParam) {
        try {
            before(param)
        } catch (t: Throwable) {
            XLog.d("Error in hook %s", param.method.name, t)
        }
    }

    @Throws(Throwable::class)
    protected open fun before(param: MethodHookParam) {
    }

    @Throws(Throwable::class)
    override fun afterHookedMethod(param: MethodHookParam) {
        try {
            after(param)
        } catch (t: Throwable) {
            XLog.e("Error in hook %s", param.method.name, t)
        }
    }

    @Throws(Throwable::class)
    protected open fun after(param: MethodHookParam) {
    }
}

package io.github.magisk317.smscode.xposed.helper

import io.github.magisk317.smscode.runtime.contract.logging.LogRoute
import io.github.magisk317.smscode.xposed.utils.XLog
import io.github.magisk317.smscode.xposed.hookapi.MethodHook
import io.github.magisk317.smscode.xposed.hookapi.MethodHookParam
import io.github.magisk317.smscode.xposed.utils.markInterruptedIfNeeded
import io.github.magisk317.smscode.xposed.utils.rethrowIfFatal

abstract class MethodHookWrapper(
    private val route: LogRoute? = null,
) : MethodHook() {
    @Throws(Throwable::class)
    override fun beforeHookedMethod(param: MethodHookParam) {
        val activeRoute = route
        if (activeRoute != null) {
            XLog.withRoute(activeRoute) {
                beforeHookedMethodRouted(param)
            }
        } else {
            beforeHookedMethodRouted(param)
        }
    }

    private fun beforeHookedMethodRouted(param: MethodHookParam) {
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
        val activeRoute = route
        if (activeRoute != null) {
            XLog.withRoute(activeRoute) {
                afterHookedMethodRouted(param)
            }
        } else {
            afterHookedMethodRouted(param)
        }
    }

    private fun afterHookedMethodRouted(param: MethodHookParam) {
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

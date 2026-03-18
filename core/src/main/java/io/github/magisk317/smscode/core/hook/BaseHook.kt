package io.github.magisk317.smscode.core.hook

import io.github.magisk317.smscode.core.hookapi.LoadParam
import io.github.magisk317.smscode.core.hookapi.ZygoteParam

open class BaseHook : IHook {

    @Throws(Throwable::class)
    override fun initZygote(startupParam: ZygoteParam) {
    }

    open fun hookInitZygote(): Boolean = false

    @Throws(Throwable::class)
    override fun onLoadPackage(lpparam: LoadParam) {
    }

    open fun hookOnLoadPackage(): Boolean = true
}

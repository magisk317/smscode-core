package io.github.magisk317.smscode.xposed.hook

import io.github.magisk317.smscode.xposed.hookapi.LoadParam
import io.github.magisk317.smscode.xposed.hookapi.ZygoteParam

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

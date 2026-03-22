package io.github.magisk317.smscode.xposed.hook

import io.github.magisk317.smscode.xposed.hookapi.LoadParam
import io.github.magisk317.smscode.xposed.hookapi.ZygoteParam

interface IHook {

    @Throws(Throwable::class)
    fun initZygote(startupParam: ZygoteParam)

    @Throws(Throwable::class)
    fun onLoadPackage(lpparam: LoadParam)
}

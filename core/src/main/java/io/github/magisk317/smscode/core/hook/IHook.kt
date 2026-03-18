package io.github.magisk317.smscode.core.hook

import io.github.magisk317.smscode.core.hookapi.LoadParam
import io.github.magisk317.smscode.core.hookapi.ZygoteParam

interface IHook {

    @Throws(Throwable::class)
    fun initZygote(startupParam: ZygoteParam)

    @Throws(Throwable::class)
    fun onLoadPackage(lpparam: LoadParam)
}

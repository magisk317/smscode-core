package io.github.magisk317.smscode.verification

import android.content.Context
import android.os.Bundle
import io.github.magisk317.smscode.xposed.utils.XLog
import java.util.concurrent.Callable

interface SmsCodeAction<T> {
    fun action(): T
}

abstract class SmsCodeCallableAction<M>(
    @JvmField protected val mPluginContext: Context,
    @JvmField protected val mPhoneContext: Context,
    @JvmField protected var mSmsMsg: M,
) : SmsCodeAction<Bundle?>,
    Callable<Bundle?> {

    @Suppress("TooGenericExceptionCaught")
    override fun call(): Bundle? = try {
        action()
    } catch (t: Throwable) {
        XLog.e("Error in CallableAction#call()", t)
        null
    }
}

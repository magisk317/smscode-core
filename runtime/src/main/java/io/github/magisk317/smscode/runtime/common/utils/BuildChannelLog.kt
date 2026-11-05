package io.github.magisk317.smscode.runtime.common.utils

import android.util.Log
import io.github.magisk317.smscode.runtime.common.BuildConfig
import io.github.magisk317.xposed.logging.DefaultLogSanitizer

/** Platform-log fallback for code that cannot safely recurse through XLog. */
internal object BuildChannelLog {
    const val INFO: Int = Log.INFO

    fun i(tag: String, message: String, throwable: Throwable? = null): Int =
        write(Log.INFO, tag, message, throwable)

    fun w(tag: String, message: String, throwable: Throwable? = null): Int =
        write(Log.WARN, tag, message, throwable)

    fun e(tag: String, message: String, throwable: Throwable? = null): Int =
        write(Log.ERROR, tag, message, throwable)

    private fun write(priority: Int, tag: String, message: String, throwable: Throwable?): Int {
        if (!BuildConfig.DEBUG && !BuildConfig.IS_CI_BUILD) return 0
        val result = Log.println(priority, tag, DefaultLogSanitizer.limitForLog(message))
        throwable?.stackTraceToString()?.lineSequence()?.forEach { line ->
            Log.println(priority, tag, DefaultLogSanitizer.limitForLog(line))
        }
        return result
    }
}

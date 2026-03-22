package io.github.magisk317.smscode.xposed.utils

import android.util.Log
import io.github.magisk317.smscode.xposed.runtime.CoreLogSinkHolder
import io.github.magisk317.smscode.xposed.runtime.CoreRuntime

object XLog {

    @Volatile
    private var sLogLevel = CoreRuntime.access.logLevel

    private fun log(priority: Int, message: String, vararg args: Any?) {
        if (priority < sLogLevel) return

        val runtime = CoreRuntime.access
        val logTag = runtime.logTag

        // Write to the default log tag
        val lastArg = args.lastOrNull()
        val logMessage = if (lastArg is Throwable) {
            message + '\n' + Log.getStackTraceString(lastArg)
        } else {
            if (args.isNotEmpty()) {
                try {
                    String.format(message, *args)
                } catch (ignored: Exception) {
                    message
                }
            } else {
                message
            }
        }
        Log.println(priority, logTag, logMessage)

        // Duplicate to the Xposed log if enabled
        if (runtime.logToXposed) {
            Log.println(priority, "LSPosed-Bridge", "$logTag: $logMessage")
        }

        CoreLogSinkHolder.append(priority, logTag, logMessage)
    }

    @JvmStatic
    fun v(message: String, vararg args: Any?) {
        log(Log.VERBOSE, message, *args)
    }

    @JvmStatic
    fun d(message: String, vararg args: Any?) {
        log(Log.DEBUG, message, *args)
    }

    @JvmStatic
    fun i(message: String, vararg args: Any?) {
        log(Log.INFO, message, *args)
    }

    @JvmStatic
    fun w(message: String, vararg args: Any?) {
        log(Log.WARN, message, *args)
    }

    @JvmStatic
    fun e(message: String, vararg args: Any?) {
        log(Log.ERROR, message, *args)
    }

    @JvmStatic
    fun setLogLevel(logLevel: Int) {
        sLogLevel = logLevel
    }

    @JvmStatic
    fun getLogLevel(): Int = sLogLevel
}

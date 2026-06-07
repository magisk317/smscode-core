package io.github.magisk317.smscode.xposed.utils

import android.util.Log
import io.github.magisk317.smscode.runtime.contract.logging.LogFormatter
import io.github.magisk317.smscode.runtime.contract.logging.LogRoute
import io.github.magisk317.smscode.xposed.runtime.CoreLogSinkHolder
import io.github.magisk317.smscode.xposed.runtime.CoreRuntime

object XLog {

    private val routeContext = ThreadLocal<String?>()

    @Volatile
    private var testSink: ((Int, String) -> Unit)? = null

    @Volatile
    private var sLogLevel = CoreRuntime.access.logLevel

    private fun log(
        priority: Int,
        route: String?,
        force: Boolean,
        sensitive: Boolean,
        message: String,
        vararg args: Any?,
    ) {
        if (priority < sLogLevel) return

        val activeTestSink = testSink
        if (activeTestSink != null) {
            activeTestSink(priority, formatMessageForTest(message, args))
            return
        }

        val runtime = CoreRuntime.access
        val logTag = runtime.logTag

        val logMessage = LogFormatter.formatArgs(message, args)
        runCatching { Log.println(priority, logTag, logMessage) }

        // Duplicate to the Xposed log if enabled
        if (runtime.logToXposed) {
            runCatching { Log.println(priority, "LSPosed-Bridge", "$logTag: $logMessage") }
        }

        CoreLogSinkHolder.append(
            priority = priority,
            tag = logTag,
            message = logMessage,
            force = force,
            route = route ?: routeContext.get(),
            sensitive = sensitive,
        )
    }

    private fun formatMessageForTest(message: String, args: Array<out Any?>): String {
        return LogFormatter.formatArgs(message, args)
    }

    @JvmStatic
    fun v(message: String, vararg args: Any?) {
        log(Log.VERBOSE, null, false, true, message, *args)
    }

    @JvmStatic
    fun d(message: String, vararg args: Any?) {
        log(Log.DEBUG, null, false, true, message, *args)
    }

    @JvmStatic
    fun i(message: String, vararg args: Any?) {
        log(Log.INFO, null, false, true, message, *args)
    }

    @JvmStatic
    fun w(message: String, vararg args: Any?) {
        log(Log.WARN, null, true, true, message, *args)
    }

    @JvmStatic
    fun e(message: String, vararg args: Any?) {
        log(Log.ERROR, null, true, true, message, *args)
    }

    @JvmStatic
    fun v(route: LogRoute, message: String, vararg args: Any?) {
        log(Log.VERBOSE, route.id, false, true, message, *args)
    }

    @JvmStatic
    fun d(route: LogRoute, message: String, vararg args: Any?) {
        log(Log.DEBUG, route.id, false, true, message, *args)
    }

    @JvmStatic
    fun i(route: LogRoute, message: String, vararg args: Any?) {
        log(Log.INFO, route.id, false, true, message, *args)
    }

    @JvmStatic
    fun w(route: LogRoute, message: String, vararg args: Any?) {
        log(Log.WARN, route.id, true, true, message, *args)
    }

    @JvmStatic
    fun e(route: LogRoute, message: String, vararg args: Any?) {
        log(Log.ERROR, route.id, true, true, message, *args)
    }

    @JvmStatic
    fun log(
        priority: Int,
        route: LogRoute,
        force: Boolean,
        sensitive: Boolean,
        message: String,
        vararg args: Any?,
    ) {
        log(priority, route.id, force, sensitive, message, *args)
    }

    @JvmStatic
    fun <T> withRoute(route: LogRoute, block: () -> T): T {
        val previous = routeContext.get()
        routeContext.set(route.id)
        return try {
            block()
        } finally {
            if (previous == null) {
                routeContext.remove()
            } else {
                routeContext.set(previous)
            }
        }
    }

    @JvmStatic
    fun setLogLevel(logLevel: Int) {
        sLogLevel = logLevel
    }

    @JvmStatic
    fun getLogLevel(): Int = sLogLevel

    @JvmStatic
    fun setTestSink(sink: ((Int, String) -> Unit)?) {
        testSink = sink
    }
}

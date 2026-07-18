package io.github.magisk317.smscode.verification

import android.util.Log
import io.github.magisk317.smscode.runtime.contract.logging.LogRoute
import io.github.magisk317.xposed.logging.LogSink
import io.github.magisk317.xposed.logging.XLog as ContractXLog

/**
 * Retained for backward-compatible sink installation by verification host code.
 */
interface VerificationLogSink {
    fun append(
        priority: Int,
        tag: String,
        message: String,
        force: Boolean = false,
        route: String? = null,
        sensitive: Boolean = true,
    )
}

object VerificationLogSinkHolder {
    @Volatile
    private var sink: VerificationLogSink? = null

    fun install(logSink: VerificationLogSink?) {
        sink = logSink
        if (logSink != null) {
            ContractXLog.install(LogSink { event ->
                logSink.append(
                    priority = event.priority,
                    tag = event.tag,
                    message = event.message,
                    force = event.force,
                    route = event.route,
                    sensitive = event.sensitive,
                )
            })
        } else {
            ContractXLog.install(null)
        }
    }

    fun append(
        priority: Int,
        tag: String,
        message: String,
        force: Boolean = false,
        route: String? = null,
        sensitive: Boolean = true,
    ) {
        sink?.append(priority, tag, message, force, route, sensitive)
    }
}

/**
 * Delegate to the unified XLog in magisk-xposed-kit.
 * Preserves `io.github.magisk317.smscode.verification.XLog` import path, and keeps the
 * [LogRoute] typed overloads by converting the route to its neutral string id.
 */
object XLog {
    @JvmStatic fun v(message: String, vararg args: Any?) = ContractXLog.v(message, *args)
    @JvmStatic fun d(message: String, vararg args: Any?) = ContractXLog.d(message, *args)
    @JvmStatic fun i(message: String, vararg args: Any?) = ContractXLog.i(message, *args)
    @JvmStatic fun w(message: String, vararg args: Any?) = ContractXLog.w(message, *args)
    @JvmStatic fun e(message: String, vararg args: Any?) = ContractXLog.e(message, *args)
    @JvmStatic fun v(route: LogRoute, message: String, vararg args: Any?) =
        ContractXLog.log(Log.VERBOSE, route.id, false, true, message, *args)
    @JvmStatic fun d(route: LogRoute, message: String, vararg args: Any?) =
        ContractXLog.log(Log.DEBUG, route.id, false, true, message, *args)
    @JvmStatic fun i(route: LogRoute, message: String, vararg args: Any?) =
        ContractXLog.log(Log.INFO, route.id, false, true, message, *args)
    @JvmStatic fun w(route: LogRoute, message: String, vararg args: Any?) =
        ContractXLog.log(Log.WARN, route.id, true, true, message, *args)
    @JvmStatic fun e(route: LogRoute, message: String, vararg args: Any?) =
        ContractXLog.log(Log.ERROR, route.id, true, true, message, *args)
    @JvmStatic fun log(
        priority: Int, route: LogRoute, force: Boolean, sensitive: Boolean, message: String, vararg args: Any?,
    ) = ContractXLog.log(priority, route.id, force, sensitive, message, *args)
    @JvmStatic fun <T> withRoute(route: LogRoute, block: () -> T): T = ContractXLog.withRoute(route.id, block)
    @JvmStatic fun setLogLevel(logLevel: Int) = ContractXLog.setLogLevel(logLevel)
    @JvmStatic fun getLogLevel(): Int = ContractXLog.getLogLevel()
    @JvmStatic fun setTestSink(sink: ((Int, String) -> Unit)?) = ContractXLog.setTestSink(sink)
    @JvmStatic fun configure(tag: String, logLevel: Int) = ContractXLog.configure(tag = tag, logLevel = logLevel)
}

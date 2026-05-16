package io.github.magisk317.smscode.xposed.runtime

interface CoreLogSink {
    fun append(
        priority: Int,
        tag: String,
        message: String,
        force: Boolean = false,
        route: String? = null,
        sensitive: Boolean = true,
    )
}

object CoreLogSinkHolder {
    @Volatile
    private var sink: CoreLogSink? = null

    fun install(logSink: CoreLogSink?) {
        sink = logSink
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

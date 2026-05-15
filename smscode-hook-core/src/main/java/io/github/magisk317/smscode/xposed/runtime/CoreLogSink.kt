package io.github.magisk317.smscode.xposed.runtime

interface CoreLogSink {
    fun append(priority: Int, tag: String, message: String)
}

object CoreLogSinkHolder {
    @Volatile
    private var sink: CoreLogSink? = null

    fun install(logSink: CoreLogSink?) {
        sink = logSink
    }

    fun append(priority: Int, tag: String, message: String) {
        sink?.append(priority, tag, message)
    }
}

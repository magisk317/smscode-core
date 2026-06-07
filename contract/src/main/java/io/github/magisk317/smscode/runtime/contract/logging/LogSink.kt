package io.github.magisk317.smscode.runtime.contract.logging

fun interface LogSink {
    fun append(event: LogEvent)
}

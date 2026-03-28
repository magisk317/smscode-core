package io.github.magisk317.smscode.xposed.utils

internal fun Throwable.markInterruptedIfNeeded() {
    if (this is InterruptedException) {
        Thread.currentThread().interrupt()
    }
}

internal fun Throwable.rethrowIfFatal() {
    when (this) {
        is VirtualMachineError, is ThreadDeath, is LinkageError -> throw this
    }
}

internal inline fun <T> runNonFatalCatching(block: () -> T): Result<T> {
    val result = runCatching(block)
    result.exceptionOrNull()?.let { throwable ->
        throwable.markInterruptedIfNeeded()
        throwable.rethrowIfFatal()
    }
    return result
}

internal inline fun <T> runNonFatalOrNull(block: () -> T): T? {
    return runNonFatalCatching(block).getOrNull()
}

internal fun resolveStaticIntFieldOrDefault(owner: Class<*>, fieldName: String, defaultValue: Int): Int {
    return runNonFatalOrNull { owner.getField(fieldName).getInt(null) } ?: defaultValue
}

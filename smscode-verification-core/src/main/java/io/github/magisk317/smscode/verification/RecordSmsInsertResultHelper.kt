package io.github.magisk317.smscode.verification

object RecordSmsInsertResultHelper {
    fun success(detail: String? = null): RecordSmsActionHelper.InsertResult {
        return RecordSmsActionHelper.InsertResult(success = true, detail = detail)
    }

    fun failure(error: String): RecordSmsActionHelper.InsertResult {
        return RecordSmsActionHelper.InsertResult(success = false, error = error)
    }

    fun fromThrowable(throwable: Throwable): RecordSmsActionHelper.InsertResult {
        return failure(throwable.message ?: throwable.javaClass.simpleName)
    }

    inline fun capture(
        block: () -> RecordSmsActionHelper.InsertResult,
    ): RecordSmsActionHelper.InsertResult {
        return try {
            block()
        } catch (throwable: Throwable) {
            fromThrowable(throwable)
        }
    }
}

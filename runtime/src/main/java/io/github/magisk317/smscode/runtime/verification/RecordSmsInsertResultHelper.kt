package io.github.magisk317.smscode.runtime.verification

import android.content.OperationApplicationException
import android.database.SQLException
import android.os.OperationCanceledException
import android.os.RemoteException

object RecordSmsInsertResultHelper {
    fun success(detail: String? = null): RecordSmsActionHelper.InsertResult {
        return RecordSmsActionHelper.InsertResult(success = true, detail = detail)
    }

    fun duplicate(detail: String? = null): RecordSmsActionHelper.InsertResult {
        return RecordSmsActionHelper.InsertResult(success = true, duplicate = true, detail = detail)
    }

    fun failure(error: String, errorClass: String? = null): RecordSmsActionHelper.InsertResult {
        return RecordSmsActionHelper.InsertResult(success = false, error = error, errorClass = errorClass)
    }

    fun fromThrowable(throwable: Throwable): RecordSmsActionHelper.InsertResult {
        return failure(
            error = throwable.message ?: throwable.javaClass.simpleName,
            errorClass = throwable.javaClass.simpleName,
        )
    }

    inline fun capture(
        block: () -> RecordSmsActionHelper.InsertResult,
    ): RecordSmsActionHelper.InsertResult {
        return try {
            block()
        } catch (throwable: SecurityException) {
            fromThrowable(throwable)
        } catch (throwable: IllegalArgumentException) {
            fromThrowable(throwable)
        } catch (throwable: IllegalStateException) {
            fromThrowable(throwable)
        } catch (throwable: SQLException) {
            fromThrowable(throwable)
        } catch (throwable: OperationApplicationException) {
            fromThrowable(throwable)
        } catch (throwable: OperationCanceledException) {
            fromThrowable(throwable)
        } catch (throwable: RemoteException) {
            fromThrowable(throwable)
        } catch (throwable: InterruptedException) {
            Thread.currentThread().interrupt()
            fromThrowable(throwable)
        }
    }
}

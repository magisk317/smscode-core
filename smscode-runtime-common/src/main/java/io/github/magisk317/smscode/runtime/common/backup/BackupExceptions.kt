package io.github.magisk317.smscode.runtime.common.backup

class BackupInvalidException(message: String? = "Backup invalid", cause: Throwable? = null) :
    Exception(
        message,
        cause,
    ) {
    constructor(cause: Throwable?) : this(null, cause)
}

class VersionInvalidException(message: String?) : Exception(message)

class VersionMissedException(message: String?) : Exception(message)

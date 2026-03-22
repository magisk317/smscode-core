package io.github.magisk317.smscode.xposed.runtime

import android.util.Log

interface CoreRuntimeAccess {
    val logTag: String
    val logLevel: Int
    val logToXposed: Boolean
    val debug: Boolean
    val applicationId: String
    val actionNamespace: String
}

object CoreRuntime {
    @Volatile
    private var accessRef: CoreRuntimeAccess? = null

    fun install(access: CoreRuntimeAccess) {
        accessRef = access
    }

    val access: CoreRuntimeAccess
        get() = accessRef ?: DefaultAccess

    private object DefaultAccess : CoreRuntimeAccess {
        override val logTag: String = "smscode-core"
        override val logLevel: Int = Log.INFO
        override val logToXposed: Boolean = false
        override val debug: Boolean = false
        override val applicationId: String = ""
        override val actionNamespace: String = "io.github.magisk317.smscode"
    }
}

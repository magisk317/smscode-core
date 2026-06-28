package io.github.magisk317.smscode.xposed.prefs

interface CorePrefsAccess {
    fun getBoolean(key: String, defaultValue: Boolean): Boolean
    fun getString(key: String, defaultValue: String): String
    fun getInt(key: String, defaultValue: Int): Int
}

object CorePrefs {
    @Volatile
    private var accessRef: CorePrefsAccess? = null

    fun install(access: CorePrefsAccess) {
        accessRef = access
    }

    internal fun resetForTest() {
        accessRef = null
    }

    fun getBoolean(key: String, defaultValue: Boolean): Boolean =
        accessRef?.getBoolean(key, defaultValue) ?: defaultValue

    fun getString(key: String, defaultValue: String): String =
        accessRef?.getString(key, defaultValue) ?: defaultValue

    fun getInt(key: String, defaultValue: Int): Int =
        accessRef?.getInt(key, defaultValue) ?: defaultValue
}

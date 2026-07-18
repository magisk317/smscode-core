package io.github.magisk317.smscode.runtime.common.prefs

import android.content.SharedPreferences
import io.github.magisk317.smscode.runtime.contract.prefs.PrefRead
import io.github.magisk317.smscode.runtime.contract.prefs.PrefsSource

/**
 * 把一个 `() -> SharedPreferences?` 供给器适配为三态 [PrefsSource]。
 *
 * 供给器返回 null 或读取抛异常 → [PrefRead.Unavailable]（源不可用，例如跨进程模块进程冻结、
 * 无本地上下文）；源可达但不含该 key → [PrefRead.Miss]；否则 → [PrefRead.Hit]。
 *
 * 覆盖两个历史实现的共同读取路径：
 * - 远程：包装 LibXposed `getRemotePreferences(group)` 得到的跨进程 SharedPreferences。
 * - 本地：包装 hook 进程的 `getSharedPreferences(name, MODE_PRIVATE)` 兜底。
 *
 * @param sourceName 来源标签，写入 [PrefRead.Hit.source]，供诊断与业务回退判断。
 * @param provider 偏好供给器；每次读取都会调用，便于反映跨进程可用性的实时变化。
 * @param onError 读取异常回调（仅诊断，不影响返回三态）。
 */
class SharedPrefsSource(
    override val sourceName: String,
    private val provider: () -> SharedPreferences?,
    private val onError: (String, Throwable) -> Unit = { _, _ -> },
) : PrefsSource {

    override fun readBoolean(key: String, defaultValue: Boolean): PrefRead<Boolean> {
        val prefs = prefsOrNull() ?: return PrefRead.Unavailable
        return readSafely("prefs boolean '$key' failed") {
            if (!prefs.contains(key)) {
                PrefRead.Miss
            } else {
                PrefRead.Hit(prefs.getBoolean(key, defaultValue), sourceName)
            }
        }
    }

    override fun readString(key: String, defaultValue: String): PrefRead<String> {
        val prefs = prefsOrNull() ?: return PrefRead.Unavailable
        return readSafely("prefs string '$key' failed") {
            if (!prefs.contains(key)) {
                PrefRead.Miss
            } else {
                PrefRead.Hit(prefs.getString(key, defaultValue) ?: defaultValue, sourceName)
            }
        }
    }

    override fun readInt(key: String, defaultValue: Int): PrefRead<Int> {
        val prefs = prefsOrNull() ?: return PrefRead.Unavailable
        return readSafely("prefs int '$key' failed") {
            if (!prefs.contains(key)) {
                PrefRead.Miss
            } else {
                val value = when (val any = prefs.all[key]) {
                    is Int -> any
                    is Long -> any.toInt()
                    is String -> any.toIntOrNull() ?: defaultValue
                    else -> prefs.getInt(key, defaultValue)
                }
                PrefRead.Hit(value, sourceName)
            }
        }
    }

    private fun prefsOrNull(): SharedPreferences? = readSafely("prefs provider failed", fallback = null) {
        provider.invoke()
    }

    @Suppress("TooGenericExceptionCaught")
    private inline fun <T> readSafely(
        failureMessage: String,
        fallback: T,
        block: () -> T,
    ): T = try {
        block()
    } catch (error: Exception) {
        // Preferences may be backed by Binder or disk; both expose failures as implementation-specific exceptions.
        onError(failureMessage, error)
        fallback
    }

    private inline fun <T> readSafely(
        failureMessage: String,
        block: () -> PrefRead<T>,
    ): PrefRead<T> = readSafely(failureMessage, PrefRead.Unavailable, block)
}

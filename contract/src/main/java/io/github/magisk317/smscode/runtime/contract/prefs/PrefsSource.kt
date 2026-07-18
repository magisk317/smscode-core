package io.github.magisk317.smscode.runtime.contract.prefs

/**
 * 单次偏好读取的三态结果，用于把「确认缺失」与「源不可用/冻结」区分开。
 *
 * - [Hit]：命中，携带值与来源标签。
 * - [Miss]：源可达但确认该 key 不存在（可安全负缓存默认值）。
 * - [Unavailable]：源不可用（跨进程冻结、无上下文、读异常等），不应用默认值毒化缓存。
 */
sealed class PrefRead<out T> {
    data class Hit<T>(val value: T, val source: String) : PrefRead<T>()
    object Miss : PrefRead<Nothing>()
    object Unavailable : PrefRead<Nothing>()
}

/** 解析后的偏好读取结果：值 + 来源标签（供诊断与业务回退判断）。 */
data class PrefReadResult<T>(val value: T, val source: String)

/**
 * 一个可按 key 读取偏好的源。多个源组成解析链（远程优先、本地兜底）。
 *
 * 注意：不接收 `Context` 参数——真实实现都持有各自的 provider / hookContext，
 * 传入的 context 在历史实现中从未被使用，故共享契约不再携带它。
 */
interface PrefsSource {
    val sourceName: String

    fun readBoolean(key: String, defaultValue: Boolean): PrefRead<Boolean>

    fun readString(key: String, defaultValue: String): PrefRead<String>

    fun readInt(key: String, defaultValue: Int): PrefRead<Int>
}

/** 偏好来源标签常量。 */
object PrefSources {
    /** 解析链耗尽（全部 Miss 或全部 Unavailable）时的终态来源标签。 */
    const val SOURCE_DEFAULT = "default"
}

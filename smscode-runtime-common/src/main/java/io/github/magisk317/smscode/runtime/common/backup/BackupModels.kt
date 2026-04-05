package io.github.magisk317.smscode.runtime.common.backup

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

object BackupConst {
    const val BACKUP_VERSION = 2

    const val KEY_VERSION = "version"
    const val KEY_SCHEMA_VERSION = "schema_version"
    const val KEY_APP_VERSION = "app_version"
    const val KEY_TIMESTAMP = "timestamp"

    const val KEY_RULES = "rules"
    const val KEY_PREFERENCES = "preferences"
    const val KEY_RECORDS = "records"

    const val KEY_COMPANY = "company"
    const val KEY_CODE_KEYWORD = "code_keyword"
    const val KEY_CODE_REGEX = "code_regex"
}

data class BackupImportResult(
    val result: ImportResult,
    val rules: List<BackupRule> = emptyList(),
    val preferences: Map<String, String?>? = null,
    val records: List<BackupSmsRecord>? = null,
    val warning: ImportWarning? = null,
)

data class BackupParseResult(
    val schemaVersion: Int,
    val appVersion: String,
    val rules: List<BackupRule>,
    val preferences: Map<String, String?>?,
    val records: List<BackupSmsRecord>?,
)

@Serializable
data class BackupPayload(
    @SerialName(BackupConst.KEY_VERSION)
    val version: Int = BackupConst.BACKUP_VERSION,
    @SerialName(BackupConst.KEY_SCHEMA_VERSION)
    val schemaVersion: Int = BackupConst.BACKUP_VERSION,
    @SerialName(BackupConst.KEY_APP_VERSION)
    val appVersion: String = "",
    @SerialName(BackupConst.KEY_RULES)
    val rules: List<BackupRule> = emptyList(),
    @SerialName(BackupConst.KEY_PREFERENCES)
    val preferences: Map<String, String?>? = null,
    @SerialName(BackupConst.KEY_RECORDS)
    val records: List<BackupSmsRecord>? = null,
)

@Serializable
data class BackupRule(
    val company: String? = null,
    val codeKeyword: String = "",
    val codeRegex: String = "",
)

@Serializable
data class BackupSmsRecord(
    @SerialName("sender")
    val sender: String? = null,
    @SerialName("body")
    val body: String? = null,
    @SerialName("date")
    val date: Long = 0,
    @SerialName("company")
    val company: String? = null,
    @SerialName("code")
    val smsCode: String? = null,
    @SerialName("packageName")
    val packageName: String? = null,
    @SerialName("msgType")
    val msgType: Int = 0,
    @SerialName("callType")
    val callType: Int = 0,
    @SerialName("forwardStatus")
    val forwardStatus: Int = 0,
    @SerialName("forwardTarget")
    val forwardTarget: String? = null,
    @SerialName("forwardMessage")
    val forwardMessage: String? = null,
    @SerialName("forwardTime")
    val forwardTime: Long = 0L,
)

enum class ExportResult {
    SUCCESS,
    FAILED,
}

enum class ImportResult {
    SUCCESS,
    VERSION_MISSED,
    VERSION_UNKNOWN,
    VERSION_TOO_NEW,
    VERSION_TOO_OLD,
    BACKUP_INVALID,
    READ_FAILED,
}

enum class ImportWarning {
    APP_VERSION_MISMATCH,
}

package io.github.magisk317.smscode.runtime.contract.backup

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class BackupPayload(
    @SerialName("version")
    val version: Int = 2,
    @SerialName("schema_version")
    val schemaVersion: Int = 2,
    @SerialName("app_version")
    val appVersion: String = "",
    @SerialName("rules")
    val rules: List<BackupRule> = emptyList(),
    @SerialName("preferences")
    val preferences: Map<String, String?>? = null,
    @SerialName("records")
    val records: List<BackupSmsRecord>? = null,
)

@Serializable
data class BackupRule(
    val company: String? = null,
    val codeKeyword: String = "",
    val codeRegex: String = "",
    val senderRegex: String? = null,
    val packageNameHint: String? = null,
    val priority: Int = 0,
)

@Serializable
data class BackupSmsRecord(
    @SerialName("sender")
    val sender: String? = null,
    @SerialName("body")
    val body: String? = null,
    @SerialName("date")
    val date: Long = 0,
    @SerialName("processedTime")
    val processedTime: Long = 0L,
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

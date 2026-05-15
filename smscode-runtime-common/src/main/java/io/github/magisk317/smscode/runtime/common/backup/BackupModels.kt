package io.github.magisk317.smscode.runtime.common.backup

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
typealias BackupImportResult = io.github.magisk317.smscode.runtime.contract.backup.BackupImportResult
typealias BackupParseResult = io.github.magisk317.smscode.runtime.contract.backup.BackupParseResult
typealias BackupPayload = io.github.magisk317.smscode.runtime.contract.backup.BackupPayload
typealias BackupRule = io.github.magisk317.smscode.runtime.contract.backup.BackupRule
typealias BackupSmsRecord = io.github.magisk317.smscode.runtime.contract.backup.BackupSmsRecord
typealias ExportResult = io.github.magisk317.smscode.runtime.contract.backup.ExportResult
typealias ImportResult = io.github.magisk317.smscode.runtime.contract.backup.ImportResult
typealias ImportWarning = io.github.magisk317.smscode.runtime.contract.backup.ImportWarning

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
    const val KEY_SENDER_REGEX = "sender_regex"
    const val KEY_PACKAGE_NAME_HINT = "package_name_hint"
    const val KEY_PRIORITY = "priority"
}

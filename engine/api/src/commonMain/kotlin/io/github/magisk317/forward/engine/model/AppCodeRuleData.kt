package io.github.magisk317.forward.engine.model

interface AppCodeRuleData {
    val id: Long
    val company: String?
    val codeKeyword: String
    val codeRegex: String
}

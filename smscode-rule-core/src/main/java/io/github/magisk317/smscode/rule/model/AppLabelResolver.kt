package io.github.magisk317.smscode.rule.model

fun interface AppLabelResolver {
    fun findPackageNameByLabel(label: String): String?
}

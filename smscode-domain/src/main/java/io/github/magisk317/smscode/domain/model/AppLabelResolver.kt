package io.github.magisk317.smscode.domain.model

fun interface AppLabelResolver {
    fun findPackageNameByLabel(label: String): String?
}

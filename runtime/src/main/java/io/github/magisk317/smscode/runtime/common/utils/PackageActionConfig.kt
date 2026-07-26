package io.github.magisk317.smscode.runtime.common.utils

/**
 * Product-specific inputs for [PackageActions] / browser helpers.
 *
 * Shared code must not depend on parent [BuildConfig] or [R]; parents bind constants and string
 * resource ids here and keep a thin facade for existing call sites.
 */
data class PackageActionConfig(
    val applicationId: String,
    val githubLatestReleaseUrl: String,
    val browserMissingMessageResId: Int,
)

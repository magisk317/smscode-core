package io.github.magisk317.smscode.runtime.common.utils

object FrameworkCompatibilityMonitor {

    private val officialLsposedKeywords = listOf(
        "lsposed",
        "zygisk_lsposed",
        "riru_lsposed",
    )

    private val incompatibleNameKeywords = listOf(
        "vector",
        "jingmatrix",
    )

    enum class FrameworkIssueType {
        KNOWN_INCOMPATIBLE_FRAMEWORK,
        HOOKER_ANNOTATION_INCOMPATIBLE,
    }

    data class FrameworkIssue(
        val issueType: FrameworkIssueType,
        val message: String,
        val detectedAt: Long,
        val frameworkInfo: FrameworkInfo? = null,
    )

    private const val ANNOTATION_ERROR_TEXT = "Hooker should be annotated with @XposedHooker"

    fun detectIssue(
        frameworkInfo: FrameworkInfo?,
        latestLogMessage: String?,
        detectedAt: Long,
    ): FrameworkIssue? {
        if (frameworkInfo != null && isKnownIncompatibleFramework(frameworkInfo)) {
            return FrameworkIssue(
                issueType = FrameworkIssueType.KNOWN_INCOMPATIBLE_FRAMEWORK,
                message = frameworkInfo.displayLabel,
                detectedAt = detectedAt,
                frameworkInfo = frameworkInfo,
            )
        }
        if (!latestLogMessage.isNullOrBlank() && latestLogMessage.contains(ANNOTATION_ERROR_TEXT)) {
            return FrameworkIssue(
                issueType = FrameworkIssueType.HOOKER_ANNOTATION_INCOMPATIBLE,
                message = latestLogMessage,
                detectedAt = detectedAt,
                frameworkInfo = frameworkInfo,
            )
        }
        return null
    }

    private fun isKnownIncompatibleFramework(frameworkInfo: FrameworkInfo): Boolean {
        val name = frameworkInfo.name.lowercase()
        val moduleId = frameworkInfo.moduleId?.lowercase().orEmpty()
        val author = frameworkInfo.author?.lowercase().orEmpty()
        val officialLsposedDetected = listOf(name, moduleId).any { candidate ->
            officialLsposedKeywords.any(candidate::contains)
        }
        if (officialLsposedDetected) {
            return false
        }
        return listOf(name, moduleId).any { candidate ->
            incompatibleNameKeywords.any(candidate::contains)
        } || author.contains("jingmatrix")
    }
}

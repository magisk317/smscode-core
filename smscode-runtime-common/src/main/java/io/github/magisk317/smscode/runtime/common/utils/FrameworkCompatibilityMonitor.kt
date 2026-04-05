package io.github.magisk317.smscode.runtime.common.utils

object FrameworkCompatibilityMonitor {

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
        val tokens = listOfNotNull(
            frameworkInfo.name,
            frameworkInfo.moduleId,
            frameworkInfo.author,
        ).joinToString(" ").lowercase()
        return tokens.contains("jingmatrix") ||
            tokens.contains("zygisk_vector") ||
            tokens.contains(" vector")
    }
}

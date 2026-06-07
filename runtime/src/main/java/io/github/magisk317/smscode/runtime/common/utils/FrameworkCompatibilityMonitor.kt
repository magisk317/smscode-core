package io.github.magisk317.smscode.runtime.common.utils

object FrameworkCompatibilityMonitor {

    enum class FrameworkIssueType {
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
}

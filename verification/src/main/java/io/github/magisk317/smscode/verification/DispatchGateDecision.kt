package io.github.magisk317.smscode.verification

enum class DispatchGateReason {
    ALLOW,
    MODULE_DISABLED,
    CONFLICT_SUPPRESSED,
}

data class DispatchGateDecision(
    val reason: DispatchGateReason,
) {
    val blocked: Boolean = reason != DispatchGateReason.ALLOW

    companion object {
        fun allow(): DispatchGateDecision = DispatchGateDecision(DispatchGateReason.ALLOW)
        fun moduleDisabled(): DispatchGateDecision = DispatchGateDecision(DispatchGateReason.MODULE_DISABLED)
        fun conflictSuppressed(): DispatchGateDecision = DispatchGateDecision(DispatchGateReason.CONFLICT_SUPPRESSED)
    }
}

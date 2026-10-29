package io.github.magisk317.forward.contract.constant

/** Stable wire vocabulary for call classification across hook and product sides. */
object ForwardCallType {
    const val UNKNOWN = 0
    const val INCOMING = 1
    const val OUTGOING = 2
    const val MISSED = 3
    const val VOICEMAIL = 4
    const val REJECTED = 5
    const val BLOCKED = 6
}

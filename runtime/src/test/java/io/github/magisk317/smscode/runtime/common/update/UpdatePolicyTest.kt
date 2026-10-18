package io.github.magisk317.smscode.runtime.common.update

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class UpdatePolicyTest {

    @Test
    fun resolveAvailableTarget_prefersPlayWhenAvailable() {
        assertEquals(UpdatePolicy.StartupTarget.PLAY, UpdatePolicy.resolveAvailableTarget(playStoreAvailable = true))
        assertEquals(UpdatePolicy.StartupTarget.GITHUB, UpdatePolicy.resolveAvailableTarget(playStoreAvailable = false))
    }
}

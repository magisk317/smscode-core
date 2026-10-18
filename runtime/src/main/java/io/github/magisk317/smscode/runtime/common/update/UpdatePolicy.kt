package io.github.magisk317.smscode.runtime.common.update

object UpdatePolicy {

    enum class StartupTarget {
        PLAY,
        GITHUB,
    }

    fun resolveAvailableTarget(playStoreAvailable: Boolean): StartupTarget =
        if (playStoreAvailable) StartupTarget.PLAY else StartupTarget.GITHUB
}

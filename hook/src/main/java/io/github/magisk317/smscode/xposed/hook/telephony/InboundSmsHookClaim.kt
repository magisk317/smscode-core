package io.github.magisk317.smscode.xposed.hook.telephony

import android.os.Process

/**
 * Cross-classloader claim for "initialise this hook once per process".
 *
 * libxposed gives every process its own classloader, so a static set only deduplicates
 * within one process. When several processes load the telephony package (com.android.phone
 * plus vendor variants), each would install the hooks independently, which shows up as
 * duplicated handling.
 *
 * System properties are visible across classloaders inside a process, so a short-lived
 * property works as the claim. The window keeps a re-loaded module from re-claiming
 * immediately.
 */
class InboundSmsHookClaim(
    private val windowMs: Long = DEFAULT_CLAIM_WINDOW_MS,
    private val clock: () -> Long = System::currentTimeMillis,
    private val propertyReader: (String) -> String? = System::getProperty,
    private val propertyWriter: (String, String) -> Unit = { key, value -> System.setProperty(key, value) },
    private val propertyClearer: (String) -> Unit = System::clearProperty,
    private val pid: Int = Process.myPid(),
) {
    private val installedKeys = mutableSetOf<String>()
    private val claimedPropertyKeys = mutableSetOf<String>()

    /**
     * Claims [key] for this process.
     *
     * Returns null when the claim succeeded, or the age in milliseconds of the existing
     * claim when it is still inside the window.
     */
    fun claim(key: String, windowMs: Long = this.windowMs): Long? = synchronized(lock) {
        val now = clock()
        val last = propertyReader(key)?.toLongOrNull()
        if (last != null && now - last <= windowMs) {
            return now - last
        }
        propertyWriter(key, now.toString())
        claimedPropertyKeys += key
        null
    }

    /**
     * Builds a process-scoped claim key. The pid is included so sibling processes do not
     * block each other; the window is what protects against a classloader re-initialising
     * inside the same process.
     */
    fun key(prefix: String, packageName: String, processName: String): String = buildString {
        append(prefix)
        append('|')
        append(packageName)
        append('|')
        append(processName.ifBlank { packageName })
        append("|pid:")
        append(pid)
    }

    /**
     * In-process guard, for hooks that must not be installed twice in the same classloader.
     *
     * Returns true the first time it sees [key].
     */
    fun markInstalled(key: String): Boolean = synchronized(lock) { installedKeys.add(key) }

    /** Releases this classloader's install state before libxposed swaps it during hot reload. */
    fun reset() = synchronized(lock) {
        claimedPropertyKeys.forEach(propertyClearer)
        claimedPropertyKeys.clear()
        installedKeys.clear()
    }

    /**
     * Two-tier install protocol shared by every telephony hook entry: a cross-process
     * window claim guards concurrent process initialisation, and a separate in-process
     * install mark guards duplicate loads inside one classloader.
     */
    fun claimInstall(
        packageName: String,
        processName: String,
        windowMs: Long = DEFAULT_CLAIM_WINDOW_MS,
        initPrefix: String = "hook_init",
        installPrefix: String = "hook_install",
    ): HookInstallClaim {
        val initKey = key(initPrefix, packageName, processName)
        claim(initKey, windowMs)?.let { return HookInstallClaim.AlreadyClaimed(it) }
        val installKey = key(installPrefix, packageName, processName)
        if (!markInstalled(installKey)) return HookInstallClaim.AlreadyInstalled
        return HookInstallClaim.Proceed(installKey)
    }

    companion object {
        val lock = Any()
        const val DEFAULT_CLAIM_WINDOW_MS = 5 * 60 * 1000L
    }
}

/** Outcome of [InboundSmsHookClaim.claimInstall]. */
sealed interface HookInstallClaim {
    /** Another process claimed initialisation inside the window; do not install. */
    data class AlreadyClaimed(val ageMs: Long) : HookInstallClaim

    /** This classloader already installed the hook; do not install again. */
    data object AlreadyInstalled : HookInstallClaim

    /** Claim acquired; installation may proceed. */
    data class Proceed(val installKey: String) : HookInstallClaim
}

package io.github.magisk317.forward.capture

import android.content.Context
import android.content.Intent
import io.github.magisk317.forward.contract.ForwardBroadcastContract
import io.github.magisk317.xposed.capability.runtime.CoreRuntime
import io.github.magisk317.xposed.logging.XLog
import io.github.magisk317.xposed.runNonFatalCatching

/** The module endpoint a captured notification should be forwarded to. */
data class ForwardDispatchEndpoint(
    val packageName: String,
    val receiverClassName: String,
    val recoveryServiceClassName: String? = null,
    val relaunchActivityClassName: String? = null,
)

/**
 * Resolves the module package that should receive forwarded notifications.
 *
 * Candidates come from the broadcast receivers registered for the forward
 * action, the runtime-reported application id and the consumer-provided
 * fallback list. The first installed candidate wins and is cached.
 */
class ForwardEndpointResolver(
    private val fallbackCandidatePackages: List<String>,
    private val receiverClassName: String,
    private val recoveryServiceClassName: String?,
    private val relaunchActivityClassName: String?,
) {

    @Volatile
    private var cachedEndpoint: ForwardDispatchEndpoint? = null

    fun resolve(systemContext: Context): ForwardDispatchEndpoint? {
        cachedEndpoint?.let { return it }
        val resolved = withClearedCallingIdentity(REASON_RESOLVE) {
            resolveEndpoint(systemContext)
        } ?: return null
        cachedEndpoint = resolved
        XLog.d(message = TAG + ": cache endpoint package=%s", resolved.packageName)
        return resolved
    }

    private fun resolveEndpoint(systemContext: Context): ForwardDispatchEndpoint? {
        val candidates = LinkedHashSet<String>()
        runNonFatalCatching {
            val intent = Intent(ForwardBroadcastContract.forwardAction(CoreRuntime.access.actionNamespace))
            CompatPackageManager.queryBroadcastReceivers(systemContext.packageManager, intent)
                .forEach { resolveInfo ->
                    resolveInfo.activityInfo?.packageName?.takeIf { it.isNotBlank() }?.let { candidates.add(it) }
                }
        }.onFailure { t ->
            XLog.w(message = TAG + ": queryBroadcastReceivers failed: %s", t.message ?: t.javaClass.simpleName)
        }
        CoreRuntime.access.applicationId.takeIf { it.isNotBlank() }?.let { candidates.add(it) }
        candidates.addAll(fallbackCandidatePackages)

        for (packageName in candidates) {
            runNonFatalCatching {
                if (isPackageInstalled(systemContext, packageName)) {
                    return ForwardDispatchEndpoint(
                        packageName = packageName,
                        receiverClassName = receiverClassName,
                        recoveryServiceClassName = recoveryServiceClassName,
                        relaunchActivityClassName = relaunchActivityClassName,
                    )
                }
                XLog.w(message = TAG + ": candidate %s rejected. installed=%s", packageName, false)
            }.onFailure { t ->
                XLog.w(
                    message = TAG + ": candidate %s resolve failed: %s",
                    packageName,
                    t.message ?: t.javaClass.simpleName,
                )
            }
        }
        return null
    }

    private fun isPackageInstalled(systemContext: Context, packageName: String): Boolean {
        return runNonFatalCatching {
            CompatPackageManager.getPackageInfo(systemContext.packageManager, packageName)
            true
        }.getOrDefault(false)
    }

    private companion object {
        const val TAG = "ForwardEndpointResolver"
        const val REASON_RESOLVE = "resolveEndpoint"
    }
}


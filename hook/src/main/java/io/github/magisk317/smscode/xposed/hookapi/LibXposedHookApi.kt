package io.github.magisk317.smscode.xposed.hookapi

import android.util.Log
import androidx.annotation.Keep
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import java.lang.reflect.Executable

@Keep
class LibXposedHookApi(module: XposedModule) : BaseLibXposedHookApi(module) {
    private val pendingOldHooksById = linkedMapOf<String, ArrayDeque<XposedInterface.HookHandle>>()
    private val pendingOldHooksByExecutable = linkedMapOf<String, ArrayDeque<XposedInterface.HookHandle>>()
    private val pendingOldHooks = linkedSetOf<XposedInterface.HookHandle>()
    private var replacingOldHooks = false

    fun beginHotReload(oldHandles: List<XposedInterface.HookHandle>) {
        replacingOldHooks = true
        pendingOldHooksById.clear()
        pendingOldHooksByExecutable.clear()
        pendingOldHooks.clear()
        oldHandles.forEach { handle ->
            val id = handle.id
            pendingOldHooks += handle
            if (id.isNullOrBlank()) {
                return@forEach
            } else {
                pendingOldHooksById.getOrPut(id) { ArrayDeque() }.addLast(handle)
                parseExecutableSignature(id)?.let { signature ->
                    pendingOldHooksByExecutable.getOrPut(signature) { ArrayDeque() }.addLast(handle)
                }
            }
        }
    }

    fun finishHotReload(): Int {
        val staleHandles = pendingOldHooks.toList()
        staleHandles.forEach { handle ->
            runCatching { handle.unhook() }
                .onFailure { module.log(Log.WARN, LOG_TAG, "Failed to unhook stale hot reload handle", it) }
        }
        val removed = staleHandles.size
        pendingOldHooksById.clear()
        pendingOldHooksByExecutable.clear()
        pendingOldHooks.clear()
        replacingOldHooks = false
        return removed
    }

    override fun hookExecutable(
        executable: Executable,
        callback: MethodHook,
        hooker: XposedInterface.Hooker,
    ): XposedInterface.HookHandle {
        val hookId = buildHookId(executable, callback)
        val legacyHookId = buildLegacyHookId(executable, callback)
        val executableSignature = buildExecutableSignature(executable)
        return sequenceOf(hookId, legacyHookId)
            .distinct()
            .firstNotNullOfOrNull { id -> takePendingHook(pendingOldHooksById[id]) }
            ?.replaceHook(hooker)
            ?: takeUniquePendingHookByExecutable(executableSignature)
                ?.also {
                    module.log(
                        Log.INFO,
                        LOG_TAG,
                        "Replacing hot reload hook by executable signature: $executableSignature",
                    )
                }
                ?.replaceHook(hooker)
            ?: super.hookExecutable(executable, callback, hooker)
    }

    private fun takePendingHook(queue: ArrayDeque<XposedInterface.HookHandle>?): XposedInterface.HookHandle? {
        if (!replacingOldHooks || queue == null) return null
        while (queue.isNotEmpty()) {
            val handle = queue.removeFirst()
            if (pendingOldHooks.remove(handle)) return handle
        }
        return null
    }

    private fun takeUniquePendingHookByExecutable(executableSignature: String): XposedInterface.HookHandle? {
        if (!replacingOldHooks) return null
        val queue = pendingOldHooksByExecutable[executableSignature] ?: return null
        var candidate: XposedInterface.HookHandle? = null
        var liveCount = 0
        queue.forEach { handle ->
            if (handle in pendingOldHooks) {
                liveCount += 1
                candidate = handle
                if (liveCount > 1) return null
            }
        }
        val handle = candidate ?: return null
        if (!queue.remove(handle)) return null
        return if (pendingOldHooks.remove(handle)) handle else null
    }

    private fun parseExecutableSignature(hookId: String): String? {
        if (!hookId.startsWith("smscode:")) return null
        val signature = hookId.substringAfter('@', missingDelimiterValue = "")
        return signature.ifBlank { null }
    }

    private companion object {
        private const val LOG_TAG = "XSmsCode"
    }
}

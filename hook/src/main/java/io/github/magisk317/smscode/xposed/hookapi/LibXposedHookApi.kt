package io.github.magisk317.smscode.xposed.hookapi

import android.util.Log
import androidx.annotation.Keep
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import java.lang.reflect.Executable

@Keep
class LibXposedHookApi(module: XposedModule) : BaseLibXposedHookApi(module) {
    private val pendingOldHooksById = linkedMapOf<String, ArrayDeque<XposedInterface.HookHandle>>()
    private val pendingOldHooksWithoutId = mutableListOf<XposedInterface.HookHandle>()
    private var replacingOldHooks = false

    fun beginHotReload(oldHandles: List<XposedInterface.HookHandle>) {
        replacingOldHooks = true
        pendingOldHooksById.clear()
        pendingOldHooksWithoutId.clear()
        oldHandles.forEach { handle ->
            val id = handle.id
            if (id.isNullOrBlank()) {
                pendingOldHooksWithoutId += handle
            } else {
                pendingOldHooksById.getOrPut(id) { ArrayDeque() }.addLast(handle)
            }
        }
    }

    fun finishHotReload(): Int {
        val staleHandles = pendingOldHooksById.values.flatten() + pendingOldHooksWithoutId
        staleHandles.forEach { handle ->
            runCatching { handle.unhook() }
                .onFailure { module.log(Log.WARN, LOG_TAG, "Failed to unhook stale hot reload handle", it) }
        }
        val removed = staleHandles.size
        pendingOldHooksById.clear()
        pendingOldHooksWithoutId.clear()
        replacingOldHooks = false
        return removed
    }

    override fun hookExecutable(
        executable: Executable,
        callback: MethodHook,
        hooker: XposedInterface.Hooker,
    ): XposedInterface.HookHandle {
        val hookId = buildHookId(executable, callback)
        return pendingOldHooksById[hookId]?.removeFirstOrNull()?.takeIf { replacingOldHooks }?.replaceHook(hooker)
            ?: super.hookExecutable(executable, callback, hooker)
    }

    private companion object {
        private const val LOG_TAG = "XSmsCode"
    }
}

package io.github.magisk317.smscode.xposed.hook.me

import io.github.magisk317.smscode.xposed.utils.ModuleUtils
import io.github.magisk317.smscode.xposed.utils.XLog
import io.github.magisk317.xposed.BaseHook
import io.github.magisk317.xposed.HookEnv
import io.github.magisk317.xposed.HookHelpers
import io.github.magisk317.xposed.LoadParam
import io.github.magisk317.xposed.MethodHook
import io.github.magisk317.xposed.MethodHookParam

open class ModuleUtilsHook(
    private val targetPackage: String,
    private val moduleVersion: Int,
) : BaseHook() {

    @Throws(Throwable::class)
    override fun onLoadPackage(param: LoadParam) {
        if (targetPackage == param.packageName) {
            try {
                XLog.i("Hooking current Xposed module status...")
                hookModuleUtils(param)
            } catch (e: ReflectiveOperationException) {
                XLog.e("Failed to hook current Xposed module status.")
            } catch (e: IllegalArgumentException) {
                XLog.e("Failed to hook current Xposed module status.")
            } catch (e: IllegalStateException) {
                XLog.e("Failed to hook current Xposed module status.")
            } catch (e: SecurityException) {
                XLog.e("Failed to hook current Xposed module status.")
            } catch (e: UnsupportedOperationException) {
                XLog.e("Failed to hook current Xposed module status.")
            }
        }
    }

    @Throws(Throwable::class)
    private fun hookModuleUtils(lpparam: LoadParam) {
        val className = ModuleUtils::class.java.name
        val clazz = HookHelpers.findClass(className, lpparam.classLoader)
        val method = HookHelpers.findMethodExactIfExists(clazz, "getModuleVersion")
        method?.let {
            HookEnv.api.hookMethod(it, object : MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    param.result = moduleVersion
                }
            })
        }
    }
}

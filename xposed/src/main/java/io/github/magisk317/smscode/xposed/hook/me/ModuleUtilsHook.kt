package io.github.magisk317.smscode.xposed.hook.me

import io.github.magisk317.smscode.xposed.helper.XposedWrapper
import io.github.magisk317.smscode.xposed.hook.BaseHook
import io.github.magisk317.smscode.xposed.hookapi.LoadParam
import io.github.magisk317.smscode.xposed.hookapi.MethodHook
import io.github.magisk317.smscode.xposed.hookapi.MethodHookParam
import io.github.magisk317.smscode.xposed.utils.ModuleUtils
import io.github.magisk317.smscode.xposed.utils.XLog

open class ModuleUtilsHook(
    private val targetPackage: String,
    private val moduleVersion: Int,
) : BaseHook() {

    @Throws(Throwable::class)
    override fun onLoadPackage(lpparam: LoadParam) {
        if (targetPackage == lpparam.packageName) {
            try {
                XLog.i("Hooking current Xposed module status...")
                hookModuleUtils(lpparam)
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
        XposedWrapper.findAndHookMethod(
            className,
            lpparam.classLoader,
            "getModuleVersion",
            object : MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    param.result = moduleVersion
                }
            },
        )
    }
}

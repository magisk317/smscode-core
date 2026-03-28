package io.github.magisk317.smscode.xposed.hook.permission

import android.os.Build
import androidx.annotation.RequiresApi
import io.github.magisk317.smscode.xposed.constant.PermConst.PACKAGE_PERMISSIONS
import io.github.magisk317.smscode.xposed.utils.XLog
import io.github.magisk317.smscode.xposed.helper.MethodHookWrapper
import io.github.magisk317.smscode.xposed.helper.XposedWrapper
import io.github.magisk317.smscode.xposed.hook.BaseSubHook
import io.github.magisk317.smscode.xposed.hookapi.MethodHook
import io.github.magisk317.smscode.xposed.hookapi.HookBridge
import io.github.magisk317.smscode.xposed.hookapi.HookHelpers
import java.lang.reflect.Method
import io.github.magisk317.smscode.xposed.hookapi.MethodHookParam

/**
 * Android 4.4 ~ Android 8.1 (API 19 - 27)<br/>
 * Hook com.android.server.pm.PackageManagerService
 */
class PackageManagerServiceHook(classLoader: ClassLoader) : BaseSubHook(classLoader) {

    @RequiresApi(Build.VERSION_CODES.KITKAT)
    override fun startHook() {
        try {
            hookGrantPermissionsLPw()
        } catch (e: ReflectiveOperationException) {
            XLog.e("Failed to hook PackageManagerService", e)
        } catch (e: IllegalArgumentException) {
            XLog.e("Failed to hook PackageManagerService", e)
        } catch (e: IllegalStateException) {
            XLog.e("Failed to hook PackageManagerService", e)
        } catch (e: SecurityException) {
            XLog.e("Failed to hook PackageManagerService", e)
        } catch (e: UnsupportedOperationException) {
            XLog.e("Failed to hook PackageManagerService", e)
        }
    }

    private fun hookGrantPermissionsLPw() {
        val pmsClass = XposedWrapper.findClass(CLASS_PACKAGE_MANAGER_SERVICE, mClassLoader)
        val method: Method = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                // Android 5.0 +
                XLog.d("Hooking grantPermissionsLPw() for Android 21+")
                HookHelpers.findMethodExact(
                    pmsClass,
                    "grantPermissionsLPw",
                    /* PackageParser.Package pkg */
                    CLASS_PACKAGE_PARSER_PACKAGE,
                    /* boolean replace           */
                    Boolean::class.javaPrimitiveType,
                    /* String packageOfInterest  */
                    String::class.java,
                )
            } else {
                // Android 4.4 +
                XLog.d("Hooking grantPermissionsLPw() for Android 19+")
                HookHelpers.findMethodExact(
                    pmsClass,
                    "grantPermissionsLPw",
                    /* PackageParser.Package pkg */
                    CLASS_PACKAGE_PARSER_PACKAGE,
                    /* boolean replace           */
                    Boolean::class.javaPrimitiveType,
                )
            }
        } catch (e: ReflectiveOperationException) {
            PermissionDebugProbe.logFailure("PackageManagerServiceHook grantPermissionsLPw", e)
            PermissionDebugProbe.dumpClass("PackageManagerServiceHook target", pmsClass)
            throw e
        } catch (e: IllegalArgumentException) {
            PermissionDebugProbe.logFailure("PackageManagerServiceHook grantPermissionsLPw", e)
            PermissionDebugProbe.dumpClass("PackageManagerServiceHook target", pmsClass)
            throw e
        } catch (e: IllegalStateException) {
            PermissionDebugProbe.logFailure("PackageManagerServiceHook grantPermissionsLPw", e)
            PermissionDebugProbe.dumpClass("PackageManagerServiceHook target", pmsClass)
            throw e
        } catch (e: SecurityException) {
            PermissionDebugProbe.logFailure("PackageManagerServiceHook grantPermissionsLPw", e)
            PermissionDebugProbe.dumpClass("PackageManagerServiceHook target", pmsClass)
            throw e
        } catch (e: UnsupportedOperationException) {
            PermissionDebugProbe.logFailure("PackageManagerServiceHook grantPermissionsLPw", e)
            PermissionDebugProbe.dumpClass("PackageManagerServiceHook target", pmsClass)
            throw e
        }

        HookBridge.hookMethod(
            method,
            object : MethodHookWrapper() {
                override fun after(param: MethodHookParam) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        grantPermissionsLPwSinceM(param)
                    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                        grantPermissionsLPwSinceK(param)
                    }
                }
            },
        )
    }

    companion object {
        private const val CLASS_PACKAGE_MANAGER_SERVICE = "com.android.server.pm.PackageManagerService"
        private const val CLASS_PACKAGE_PARSER_PACKAGE = "android.content.pm.PackageParser.Package"

        private fun grantPermissionsLPwSinceM(param: MethodHookParam) {
            // API 23 (Android 6.0)
            val pkg = param.args[0]
            val packageNameInPkg = HookHelpers.getObjectField(pkg, "packageName") as String

            for (packageName in PACKAGE_PERMISSIONS.keys) {
                if (packageName == packageNameInPkg) {
                    XLog.d("PackageName: %s", packageName)
                    val extras = HookHelpers.getObjectField(pkg, "mExtras")
                    val permissionsState = HookHelpers.callMethod(extras, "getPermissionsState")
                    val requestedPermissions = HookHelpers.getObjectField(pkg, "requestedPermissions") as? List<*>
                    val settings = HookHelpers.getObjectField(param.thisObject, "mSettings")
                    val permissions = HookHelpers.getObjectField(settings, "mPermissions")

                    val permissionsToGrant = PACKAGE_PERMISSIONS[packageName] ?: continue
                    for (permissionToGrant in permissionsToGrant) {
                        if (requestedPermissions?.contains(permissionToGrant) != true) {
                            val granted = HookHelpers.callMethod(
                                permissionsState,
                                "hasInstallPermission",
                                permissionToGrant,
                            ) as Boolean
                            if (!granted) {
                                val bpToGrant = HookHelpers.callMethod(permissions, "get", permissionToGrant)
                                val result = HookHelpers.callMethod(
                                    permissionsState,
                                    "grantInstallPermission",
                                    bpToGrant,
                                ) as Int
                                XLog.d("Add $bpToGrant; result = $result")
                            } else {
                                XLog.d("Already have $permissionToGrant permission")
                            }
                        }
                    }
                }
            }
        }

        private fun grantPermissionsLPwSinceK(param: MethodHookParam) {
            // API 19 (Android 4.4)
            val pkg = param.args[0]
            val pms = param.thisObject ?: return
            val packageNameInPkg = HookHelpers.getObjectField(pkg, "packageName") as String

            for (packageName in PACKAGE_PERMISSIONS.keys) {
                if (packageName == packageNameInPkg) {
                    XLog.d("PackageName: %s", packageName)
                    val extra = HookHelpers.getObjectField(pkg, "mExtras")
                    val grantedPermissions = HookHelpers.getObjectField(
                        extra,
                        "grantedPermissions",
                    )
                    val settings = HookHelpers.getObjectField(pms, "mSettings")
                    val permissions = HookHelpers.getObjectField(settings, "mPermissions")

                    val permissionsToGrant = PACKAGE_PERMISSIONS[packageName] ?: continue
                    for (permissionToGrant in permissionsToGrant) {
                        val granted = (grantedPermissions as? Collection<*>)?.contains(permissionToGrant) == true
                        if (!granted) {
                            val bpToGrant = HookHelpers.callMethod(permissions, "get", permissionToGrant)
                            HookHelpers.callMethod(grantedPermissions, "add", permissionToGrant)

                            val gpGids = HookHelpers.getObjectField(extra, "gids") as IntArray
                            val bpGids = HookHelpers.getObjectField(bpToGrant, "gids") as IntArray
                            HookHelpers.callStaticMethod(pms.javaClass, "appendInts", gpGids, bpGids)

                            XLog.d("Add $bpToGrant")
                        } else {
                            XLog.d("Already have $permissionToGrant permission")
                        }
                    }
                }
            }
        }
    }
}

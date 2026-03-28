package io.github.magisk317.smscode.xposed.helper

import io.github.magisk317.smscode.xposed.utils.XLog
import io.github.magisk317.smscode.xposed.hookapi.HookBridge
import io.github.magisk317.smscode.xposed.hookapi.HookHandle
import io.github.magisk317.smscode.xposed.hookapi.HookHelpers
import io.github.magisk317.smscode.xposed.hookapi.MethodHook
import java.lang.reflect.Member
import java.lang.reflect.Method
import java.lang.reflect.Modifier

/**
 * Xposed Wrapper Utils
 */
object XposedWrapper {
    fun findClass(className: String, classLoader: ClassLoader?): Class<*>? = try {
        HookHelpers.findClass(className, classLoader)
    } catch (_: ClassNotFoundException) {
        XLog.e("Class not found: %s", className)
        null
    } catch (_: NoClassDefFoundError) {
        XLog.e("Class not found: %s", className)
        null
    }

    fun findAndHookMethod(
        className: String,
        classLoader: ClassLoader?,
        methodName: String,
        vararg parameterTypesAndCallback: Any,
    ): HookHandle? = try {
        val clazz = HookHelpers.findClass(className, classLoader)
        findAndHookMethod(clazz, methodName, *parameterTypesAndCallback)
    } catch (t: ClassNotFoundException) {
        XLog.e("Error in hook %s#%s", className, methodName, t)
        null
    } catch (t: NoClassDefFoundError) {
        XLog.e("Error in hook %s#%s", className, methodName, t)
        null
    } catch (t: IllegalArgumentException) {
        XLog.e("Error in hook %s#%s", className, methodName, t)
        null
    } catch (t: IllegalStateException) {
        XLog.e("Error in hook %s#%s", className, methodName, t)
        null
    } catch (t: SecurityException) {
        XLog.e("Error in hook %s#%s", className, methodName, t)
        null
    } catch (t: UnsupportedOperationException) {
        XLog.e("Error in hook %s#%s", className, methodName, t)
        null
    }

    fun findAndHookMethod(clazz: Class<*>, methodName: String, vararg parameterTypesAndCallback: Any): HookHandle? {
        return try {
            if (parameterTypesAndCallback.isEmpty()) {
                null
            } else {
                val callback = parameterTypesAndCallback.lastOrNull() as? MethodHook
                if (callback == null) {
                    null
                } else {
                    val paramTypes = parameterTypesAndCallback.dropLast(1).toTypedArray()
                    val method = HookHelpers.findMethodExactIfExists(clazz, methodName, *paramTypes)
                    if (method == null) null else hookMethod(method, callback)
                }
            }
        } catch (t: IllegalArgumentException) {
            XLog.e("Error in hook %s#%s", clazz.name, methodName, t)
            null
        } catch (t: IllegalStateException) {
            XLog.e("Error in hook %s#%s", clazz.name, methodName, t)
            null
        } catch (t: SecurityException) {
            XLog.e("Error in hook %s#%s", clazz.name, methodName, t)
            null
        } catch (t: UnsupportedOperationException) {
            XLog.e("Error in hook %s#%s", clazz.name, methodName, t)
            null
        } catch (t: NoClassDefFoundError) {
            XLog.e("Error in hook %s#%s", clazz.name, methodName, t)
            null
        }
    }

    fun hookMethod(hookMethod: Member, callback: MethodHook): HookHandle? {
        return try {
            if (hookMethod is Method && (Modifier.isAbstract(hookMethod.modifiers) || Modifier.isNative(hookMethod.modifiers))) {
                XLog.d("Skip hook unsupported method: %s#%s", hookMethod.declaringClass.name, hookMethod.name)
                null
            } else {
                HookBridge.hookMethod(hookMethod, callback)
            }
        } catch (t: IllegalArgumentException) {
            XLog.e("Error in hookMethod: %s", hookMethod.name, t)
            null
        } catch (t: IllegalStateException) {
            XLog.e("Error in hookMethod: %s", hookMethod.name, t)
            null
        } catch (t: SecurityException) {
            XLog.e("Error in hookMethod: %s", hookMethod.name, t)
            null
        } catch (t: UnsupportedOperationException) {
            XLog.e("Error in hookMethod: %s", hookMethod.name, t)
            null
        }
    }

    fun hookAllConstructors(hookClass: Class<*>, callback: MethodHook): Set<HookHandle>? = try {
        HookBridge.hookAllConstructors(hookClass, callback)
    } catch (t: IllegalArgumentException) {
        XLog.e("Error in hookAllConstructors: %s", hookClass.name, t)
        null
    } catch (t: IllegalStateException) {
        XLog.e("Error in hookAllConstructors: %s", hookClass.name, t)
        null
    } catch (t: SecurityException) {
        XLog.e("Error in hookAllConstructors: %s", hookClass.name, t)
        null
    } catch (t: UnsupportedOperationException) {
        XLog.e("Error in hookAllConstructors: %s", hookClass.name, t)
        null
    }

    fun hookAllMethods(hookClass: Class<*>, methodName: String, callback: MethodHook): Set<HookHandle>? = try {
        HookBridge.hookAllMethods(hookClass, methodName, callback)
    } catch (t: IllegalArgumentException) {
        XLog.e("Error in hookAllMethods: %s", hookClass.name, t)
        null
    } catch (t: IllegalStateException) {
        XLog.e("Error in hookAllMethods: %s", hookClass.name, t)
        null
    } catch (t: SecurityException) {
        XLog.e("Error in hookAllMethods: %s", hookClass.name, t)
        null
    } catch (t: UnsupportedOperationException) {
        XLog.e("Error in hookAllMethods: %s", hookClass.name, t)
        null
    }
}

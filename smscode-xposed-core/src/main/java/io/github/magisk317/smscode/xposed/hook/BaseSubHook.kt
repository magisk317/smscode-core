package io.github.magisk317.smscode.xposed.hook

abstract class BaseSubHook(@JvmField protected val mClassLoader: ClassLoader) {
    abstract fun startHook()
}

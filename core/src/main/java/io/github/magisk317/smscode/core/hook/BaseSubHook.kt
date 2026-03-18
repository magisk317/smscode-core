package io.github.magisk317.smscode.core.hook

abstract class BaseSubHook(@JvmField protected val mClassLoader: ClassLoader) {
    abstract fun startHook()
}

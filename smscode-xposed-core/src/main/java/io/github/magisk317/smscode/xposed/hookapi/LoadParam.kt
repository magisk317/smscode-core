package io.github.magisk317.smscode.xposed.hookapi

class LoadParam(
    val packageName: String,
    val processName: String,
    val classLoader: ClassLoader,
)

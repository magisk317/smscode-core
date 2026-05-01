package io.github.magisk317.smscode.xposed.hookapi

class LoadParam(
    val packageName: String,
    val processName: String,
    // Early system callbacks may not expose a package class loader yet.
    val classLoader: ClassLoader?,
)

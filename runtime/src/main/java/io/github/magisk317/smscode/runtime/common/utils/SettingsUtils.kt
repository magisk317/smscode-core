package io.github.magisk317.smscode.runtime.common.utils

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.core.net.toUri

/**
 * Utility for android.provider.Settings
 */
object SettingsUtils {

    private fun getSecureString(context: Context, key: String): String? =
        Settings.Secure.getString(context.contentResolver, key)

    /**
     * Get system default SMS app package
     * @return package name of default sms app
     */
    @JvmStatic
    fun getDefaultSmsAppPackage(context: Context): String? {
        val key = "sms_default_application"
        return getSecureString(context, key)
    }

    /**
     * Request ignore battery optimization
     */
    @SuppressLint("BatteryLife")
    @JvmStatic
    fun requestIgnoreBatteryOptimization(context: Context) {
        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
        intent.data = "package:${context.packageName}".toUri()
        context.startActivity(intent)
    }

    /**
     * Go to ignore battery optimization settings.
     */
    @JvmStatic
    fun gotoIgnoreBatteryOptimizationSettings(context: Context) {
        val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
        context.startActivity(intent)
    }
}

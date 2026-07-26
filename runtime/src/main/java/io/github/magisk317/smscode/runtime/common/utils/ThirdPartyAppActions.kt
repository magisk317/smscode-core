package io.github.magisk317.smscode.runtime.common.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import io.github.magisk317.smscode.runtime.common.packageenv.PackageEnvCore
import io.github.magisk317.xposed.logging.MagiskOtel

/**
 * 配置驱动的第三方 App 动作原语（纯 Android 能力）。
 *
 * 只承载「按包名启动 App」「ACTION_VIEW 打开 URI」「跳应用商店详情页 + Web 回退」这类
 * 与产品无关的系统交互；包名、URL、文案等产品常量由调用方（父项目）持有并传入。
 */
object ThirdPartyAppActions {

    private const val PLAY_STORE_PACKAGE_NAME = "com.android.vending"

    /**
     * 按包名启动目标 App。调用方应先自行完成安装/启用状态检查。
     *
     * @return 成功拉起返回 true；目标无启动入口或启动失败返回 false。
     */
    @JvmStatic
    fun launchPackage(context: Context, packageName: String): Boolean {
        val startedAt = System.nanoTime()
        val ok = runCatching {
            val intent = context.packageManager.getLaunchIntentForPackage(packageName) ?: return@runCatching false
            context.startActivity(intent)
            true
        }.getOrDefault(false)
        val durationMs = ((System.nanoTime() - startedAt) / 1_000_000L).coerceAtLeast(0L)
        MagiskOtel.event(
            name = "app.lifecycle",
            attributes = mapOf(
                "result" to if (ok) "ok" else "error",
                "duration_ms" to durationMs.toString(),
                "process" to "app",
                "stage" to "launch_package",
                "reason" to if (ok) "launched" else "launch_failed",
                "target_package" to packageName,
            ),
            statusOk = ok,
        )
        return ok
    }

    /**
     * 用 ACTION_VIEW 打开一个 URI（网页、scheme、二维码收款页等）。
     *
     * @param newTask 是否附加 FLAG_ACTIVITY_NEW_TASK；从非 Activity 上下文启动时需要。
     * @return 成功打开返回 true；无可处理组件或打开失败返回 false。
     */
    @JvmStatic
    fun openViewUri(context: Context, uri: String, newTask: Boolean = true): Boolean {
        val startedAt = System.nanoTime()
        val ok = runCatching {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uri)).apply {
                if (newTask) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        }.getOrDefault(false)
        val durationMs = ((System.nanoTime() - startedAt) / 1_000_000L).coerceAtLeast(0L)
        MagiskOtel.event(
            name = "app.lifecycle",
            attributes = mapOf(
                "result" to if (ok) "ok" else "error",
                "duration_ms" to durationMs.toString(),
                "process" to "app",
                "stage" to "open_view_uri",
                "reason" to if (ok) "opened" else "open_failed",
            ),
            statusOk = ok,
        )
        return ok
    }

    /**
     * 判断设备是否安装并启用了 Google Play 商店。
     */
    @JvmStatic
    fun isMarketAvailable(context: Context): Boolean =
        PackageEnvCore.isPackageEnabled(context, PLAY_STORE_PACKAGE_NAME)

    /**
     * 打开指定应用在 Play 商店的详情页；商店不可用或跳转失败时回退到 Play 网页。
     *
     * @param applicationId 目标应用包名，由调用方从各自 BuildConfig 传入。
     */
    @JvmStatic
    fun openMarketDetails(context: Context, applicationId: String) {
        val marketIntent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("market://details?id=$applicationId"),
        ).apply {
            setPackage(PLAY_STORE_PACKAGE_NAME)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(marketIntent)
        } catch (ignored: Exception) {
            val webIntent = Intent(
                Intent.ACTION_VIEW,
                Uri.parse("https://play.google.com/store/apps/details?id=$applicationId"),
            ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
            context.startActivity(webIntent)
        }
    }
}

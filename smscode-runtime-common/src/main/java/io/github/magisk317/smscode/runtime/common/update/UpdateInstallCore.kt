package io.github.magisk317.smscode.runtime.common.update

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext

data class UpdateArtifactConfig(
    val apkFilePrefix: String,
)

object UpgradeDownloader {

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    data class Progress(
        val bytesRead: Long,
        val totalBytes: Long,
        val percent: Float,
    )

    suspend fun download(
        context: Context,
        versionCode: Long,
        asset: UpgradeApkAsset,
        artifactConfig: UpdateArtifactConfig,
        onProgress: (Progress) -> Unit = {},
    ): File = withContext(Dispatchers.IO) {
        val updatesDir = File(context.cacheDir, "updates").apply { mkdirs() }
        val target = File(updatesDir, buildTargetFileName(versionCode, asset, artifactConfig))
        val temp = File(target.parentFile, "${target.name}.part")
        if (temp.exists()) temp.delete()

        try {
            val request = Request.Builder()
                .url(asset.downloadUrl)
                .get()
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("HTTP ${response.code}")
                }
                val body = response.body
                val expectedSize = if (asset.fileSize > 0L) asset.fileSize else body.contentLength()
                FileOutputStream(temp).use { output ->
                    body.byteStream().use { input ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        var totalRead = 0L
                        while (true) {
                            coroutineContext.ensureActive()
                            val count = input.read(buffer)
                            if (count < 0) break
                            output.write(buffer, 0, count)
                            totalRead += count
                            val percent = if (expectedSize > 0L) {
                                (totalRead.toFloat() / expectedSize.toFloat()).coerceIn(0f, 1f)
                            } else {
                                0f
                            }
                            onProgress(Progress(totalRead, expectedSize, percent))
                        }
                        output.flush()
                        if (asset.fileSize > 0L && totalRead != asset.fileSize) {
                            throw IOException("size_mismatch expected=${asset.fileSize} actual=$totalRead")
                        }
                    }
                }
            }
            if (target.exists()) target.delete()
            if (!temp.renameTo(target)) {
                throw IOException("rename_failed")
            }
            cleanupOldPackages(updatesDir, keep = target.name)
            target
        } finally {
            if (temp.exists()) temp.delete()
        }
    }

    internal fun buildTargetFileName(
        versionCode: Long,
        asset: UpgradeApkAsset,
        artifactConfig: UpdateArtifactConfig,
    ): String {
        val abiLabel = asset.abi.ifBlank { "universal" }.replace("[^A-Za-z0-9._-]".toRegex(), "_")
        return "${artifactConfig.apkFilePrefix}-v${versionCode}-${abiLabel}.apk"
    }

    private fun cleanupOldPackages(dir: File, keep: String) {
        dir.listFiles()
            ?.filter { it.name.endsWith(".apk") && it.name != keep }
            ?.forEach { runCatching { it.delete() } }
    }
}

object ApkSecurityVerifier {

    data class VerificationResult(
        val success: Boolean,
        val reason: String? = null,
    )

    fun verifyDownloadedApk(
        context: Context,
        apkFile: File,
        expectedSha256: String,
        expectedSigningCertSha256: String,
    ): VerificationResult {
        if (!apkFile.exists()) return VerificationResult(false, "apk_not_found")
        if (expectedSha256.isBlank()) return VerificationResult(false, "missing_sha256")
        if (expectedSigningCertSha256.isBlank()) return VerificationResult(false, "missing_signing_cert")

        val actualSha = computeSha256(apkFile)
        if (!isDigestMatch(actualSha, expectedSha256)) {
            return VerificationResult(false, "sha256_mismatch")
        }

        val archiveInfo = getArchivePackageInfo(context.packageManager, apkFile) ?: return VerificationResult(
            false,
            "archive_parse_failed",
        )
        if (archiveInfo.packageName != context.packageName) {
            return VerificationResult(false, "package_name_mismatch")
        }

        val currentSignatures = getInstalledSigningCertDigests(context.packageManager, context.packageName)
        val archiveSignatures = getArchiveSigningCertDigests(context.packageManager, apkFile)
        if (currentSignatures.isEmpty() || archiveSignatures.isEmpty()) {
            return VerificationResult(false, "signing_info_missing")
        }
        if (!isSigningCertExpected(archiveSignatures, expectedSigningCertSha256)) {
            return VerificationResult(false, "expected_signing_cert_mismatch")
        }
        if (currentSignatures.intersect(archiveSignatures).isEmpty()) {
            return VerificationResult(false, "installed_signing_cert_mismatch")
        }
        return VerificationResult(true)
    }

    fun computeSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().toHex()
    }

    fun isDigestMatch(actual: String, expected: String): Boolean =
        normalizeDigest(actual) == normalizeDigest(expected)

    fun isSigningCertExpected(certs: Set<String>, expected: String): Boolean {
        val normalizedExpected = normalizeDigest(expected)
        return certs.any { normalizeDigest(it) == normalizedExpected }
    }

    private fun normalizeDigest(raw: String): String =
        raw.lowercase().replace("[^0-9a-f]".toRegex(), "")

    private fun ByteArray.toHex(): String =
        joinToString(separator = "") { byte -> "%02x".format(byte) }

    private fun getInstalledSigningCertDigests(
        pm: PackageManager,
        packageName: String,
    ): Set<String> {
        val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getPackageInfo(
                packageName,
                PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong()),
            )
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES)
        }
        return extractSigningDigests(packageInfo)
    }

    private fun getArchiveSigningCertDigests(pm: PackageManager, apkFile: File): Set<String> {
        val packageInfo = getArchivePackageInfo(pm, apkFile) ?: return emptySet()
        return extractSigningDigests(packageInfo)
    }

    private fun getArchivePackageInfo(pm: PackageManager, apkFile: File): PackageInfo? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getPackageArchiveInfo(
                apkFile.absolutePath,
                PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong()),
            )
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageArchiveInfo(apkFile.absolutePath, PackageManager.GET_SIGNING_CERTIFICATES)
        }
    }

    private fun extractSigningDigests(packageInfo: PackageInfo): Set<String> {
        val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val signingInfo = packageInfo.signingInfo ?: return emptySet()
            if (signingInfo.hasMultipleSigners()) {
                signingInfo.apkContentsSigners
            } else {
                signingInfo.signingCertificateHistory
            }
        } else {
            @Suppress("DEPRECATION")
            packageInfo.signatures
        }
        return signatures
            ?.mapNotNull { signature ->
                runCatching {
                    MessageDigest.getInstance("SHA-256")
                        .digest(signature.toByteArray())
                        .toHex()
                }.getOrNull()
            }
            ?.toSet()
            ?: emptySet()
    }
}

object UpgradeInstaller {

    fun canRequestPackageInstalls(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }

    fun buildUnknownSourceSettingsIntent(context: Context): Intent {
        return Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}"),
        ).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    fun installApk(context: Context, apkFile: File): Result<Unit> = runCatching {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.files",
            apkFile,
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            setDataAndType(uri, "application/vnd.android.package-archive")
        }
        context.startActivity(intent)
        Unit
    }
}

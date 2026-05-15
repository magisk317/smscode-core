package io.github.magisk317.smscode.runtime.common.rules

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

interface SmsCodeRuleCatalogSource {
    suspend fun readIndex(): String
    suspend fun readRule(path: String): String
}

interface WritableSmsCodeRuleCatalogSource : SmsCodeRuleCatalogSource {
    suspend fun writeCatalog(indexText: String, ruleTexts: Map<String, String>)
}

class AssetSmsCodeRuleCatalogSource(
    context: Context,
    private val assetRoot: String = DEFAULT_SMS_CODE_RULES_ASSET_ROOT,
) : SmsCodeRuleCatalogSource {
    private val appContext = context.applicationContext ?: context

    override suspend fun readIndex(): String =
        readAssetOrNull(SMS_CODE_RULES_INDEX_PATH) ?: readAsset(SMS_CODE_RULES_BUNDLED_INDEX_PATH)

    override suspend fun readRule(path: String): String = readAsset(path)

    private suspend fun readAsset(path: String): String =
        readAssetOrNull(path) ?: throw FileNotFoundException("$assetRoot/$path")

    private suspend fun readAssetOrNull(path: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            appContext.assets.open("$assetRoot/$path").bufferedReader(Charsets.UTF_8).use { it.readText() }
        }.getOrNull()
    }
}

class FileSmsCodeRuleCatalogSource(
    private val rootDir: File,
) : WritableSmsCodeRuleCatalogSource {
    override suspend fun readIndex(): String = readFile(SMS_CODE_RULES_INDEX_PATH)

    override suspend fun readRule(path: String): String = readFile(path)

    override suspend fun writeCatalog(indexText: String, ruleTexts: Map<String, String>) = withContext(Dispatchers.IO) {
        val tempDir = File(rootDir.parentFile ?: rootDir, "${rootDir.name}.tmp")
        if (tempDir.exists()) tempDir.deleteRecursively()
        writeFile(tempDir, SMS_CODE_RULES_INDEX_PATH, indexText)
        for ((path, text) in ruleTexts) {
            writeFile(tempDir, path, text)
        }
        if (rootDir.exists()) rootDir.deleteRecursively()
        if (!tempDir.renameTo(rootDir)) {
            throw IOException("Unable to replace rule cache directory: ${rootDir.absolutePath}")
        }
    }

    private suspend fun readFile(path: String): String = withContext(Dispatchers.IO) {
        val file = File(rootDir, path)
        if (!file.isFile) throw FileNotFoundException(file.absolutePath)
        file.readText(Charsets.UTF_8)
    }

    private fun writeFile(root: File, path: String, text: String) {
        val file = File(root, path)
        file.parentFile?.mkdirs()
        file.writeText(text, Charsets.UTF_8)
    }
}

class RemoteSmsCodeRuleCatalogSource(
    private val remoteSource: SmsCodeRuleRemoteSource = SmsCodeRuleRemoteSource(),
    private val userAgent: String = "SmsCodeRules/1",
    private val requester: suspend (String) -> String = { url -> fetchText(url, userAgent) },
) : SmsCodeRuleCatalogSource {
    override suspend fun readIndex(): String = requester("${remoteSource.rawBaseUrl}/$SMS_CODE_RULES_INDEX_PATH")

    override suspend fun readRule(path: String): String = requester("${remoteSource.rawBaseUrl}/${encodePath(path)}")

    companion object {
        private suspend fun fetchText(urlString: String, userAgent: String): String = withContext(Dispatchers.IO) {
            val connection = (URL(urlString).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 10_000
                readTimeout = 10_000
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", userAgent)
            }
            try {
                val code = connection.responseCode
                val stream = if (code in 200..299) connection.inputStream else connection.errorStream
                val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
                if (code !in 200..299) {
                    throw IOException("HTTP $code ${connection.responseMessage}: $body")
                }
                body
            } finally {
                connection.disconnect()
            }
        }

        private fun encodePath(path: String): String =
            path.split('/').joinToString("/") { segment ->
                URLEncoder.encode(segment, StandardCharsets.UTF_8.toString()).replace("+", "%20")
            }
    }
}

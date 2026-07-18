package io.github.magisk317.smscode.runtime.common.store

import android.content.Context
import io.github.magisk317.smscode.runtime.common.utils.JsonUtils
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

class JsonEntityFileStore<K>(
    private val fileResolver: (Context, K) -> File,
    private val prepareFileForCommit: (File) -> Unit = {},
    private val logger: Logger = Logger { _, _ -> },
    private val maxFileBytes: Long = DEFAULT_MAX_FILE_BYTES,
) {
    fun interface Logger {
        fun error(message: String, throwable: Throwable?)
    }

    fun getStoreFile(context: Context, key: K): File = fileResolver(context, key)

    fun <T : Any> storeEntities(
        context: Context,
        key: K,
        entities: List<T>,
        entityClass: Class<T>,
    ): Boolean = storeEntities(getStoreFile(context, key), entities, entityClass)

    fun <T : Any> storeEntities(
        storeFile: File,
        entities: List<T>,
        entityClass: Class<T>,
    ): Boolean {
        var temporaryFile: File? = null
        return runCatching {
            val json = if (entities.isEmpty()) "[]" else JsonUtils.listToJson(entities, entityClass)
            if (json.isBlank()) {
                logger.error("Entity store serialization returned empty JSON: ${storeFile.name}", null)
                return false
            }
            val content = json.toByteArray(StandardCharsets.UTF_8)
            if (content.size > maxFileBytes) {
                logger.error("Entity store payload exceeds limit: ${storeFile.name}", null)
                return false
            }

            val directory = storeFile.parentFile
                ?: throw IllegalArgumentException("Entity store file has no parent: ${storeFile.name}")
            if (!directory.exists() && !directory.mkdirs()) {
                throw IllegalStateException("Unable to create entity store directory")
            }
            val temp = File.createTempFile(".${storeFile.name}.", ".tmp", directory)
            temporaryFile = temp
            FileOutputStream(temp).use { output ->
                output.write(content)
                output.fd.sync()
            }
            prepareFileForCommit(temp)
            moveReplacing(temp, storeFile)
            temporaryFile = null
            true
        }.getOrElse { error ->
            logger.error("Entity store write failed: ${storeFile.name}", error)
            false
        }.also {
            temporaryFile?.delete()
        }
    }

    fun <T : Any> storeEntity(
        context: Context,
        key: K,
        entity: T,
        entityClass: Class<T>,
    ): Boolean = storeEntities(context, key, listOf(entity), entityClass)

    fun <T : Any> loadEntities(
        context: Context,
        key: K,
        entityClass: Class<T>,
    ): List<T> = loadEntities(getStoreFile(context, key), entityClass)

    fun <T : Any> loadEntities(storeFile: File, entityClass: Class<T>): List<T> {
        if (!storeFile.isFile || storeFile.length() == 0L) return emptyList()
        if (storeFile.length() > maxFileBytes) {
            logger.error("Entity store file exceeds limit: ${storeFile.name}", null)
            return emptyList()
        }
        return runCatching {
            storeFile.bufferedReader(StandardCharsets.UTF_8).use { reader ->
                JsonUtils.listFromJson(reader, entityClass)
            }
        }.getOrElse { error ->
            logger.error("Entity store read failed: ${storeFile.name}", error)
            emptyList()
        }
    }

    fun <T : Any> loadEntity(context: Context, key: K, entityClass: Class<T>): T? =
        loadEntities(context, key, entityClass).firstOrNull()

    private fun moveReplacing(source: File, destination: File) {
        try {
            Files.move(
                source.toPath(),
                destination.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(source.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private companion object {
        const val DEFAULT_MAX_FILE_BYTES = 8 * 1024 * 1024L
    }
}

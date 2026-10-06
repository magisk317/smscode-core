package io.github.magisk317.smscode.runtime.common.utils

import io.github.magisk317.smscode.runtime.common.serialization.JsonConfig
import java.io.Reader
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.serializer

/**
 * Json utils using Kotlin Serialization.
 *
 * The stable core API takes an explicit [KSerializer]: generic classes inject one at
 * construction time (reified is unusable inside generic code), while concrete call
 * sites use the Class-based overloads or the reified inline conveniences, which only
 * resolve the serializer and delegate here. Nothing in this object is inline except
 * the conveniences, so behaviour lives in exactly one place.
 */
object JsonUtils {

    val json = JsonConfig.json

    // ---- KSerializer core API (non-inline; the anchor for generic classes) ----

    @JvmStatic
    fun <T : Any> toJson(value: T, serializer: KSerializer<T>): String =
        json.encodeToString(serializer, value)

    @JvmStatic
    fun <T : Any> toJson(value: T, serializer: KSerializer<T>, writer: Appendable) {
        writer.append(toJson(value, serializer))
    }

    @JvmStatic
    fun <T : Any> fromJson(jsonString: String?, serializer: KSerializer<T>): T? {
        if (jsonString.isNullOrEmpty()) return null
        return json.decodeFromString(serializer, jsonString)
    }

    @JvmStatic
    fun <T : Any> fromJson(reader: Reader?, serializer: KSerializer<T>): T? {
        if (reader == null) return null
        return fromJson(reader.readText(), serializer)
    }

    @JvmStatic
    fun <T : Any> listToJson(list: List<T>, serializer: KSerializer<T>): String =
        json.encodeToString(ListSerializer(serializer), list)

    @JvmStatic
    fun <T : Any> listFromJson(jsonString: String?, serializer: KSerializer<T>): List<T> {
        if (jsonString.isNullOrEmpty()) return emptyList()
        return json.decodeFromString(ListSerializer(serializer), jsonString)
    }

    // ---- Class-based list overloads (kept for JsonEntityFileStore; the JVM Class
    // overload of serializer() resolves by runtime class, so this stays correct) ----

    @JvmStatic
    fun <T : Any> listToJson(list: List<T>, entityClass: Class<T>): String {
        val entitySerializer = serializer(entityClass)
        val checked = list.map { entityClass.cast(it) as Any }
        return json.encodeToString(ListSerializer(entitySerializer), checked)
    }

    @JvmStatic
    fun <T : Any> listFromJson(jsonString: String, entityClass: Class<T>): List<T> {
        val entitySerializer = serializer(entityClass)
        val decoded = json.decodeFromString(ListSerializer(entitySerializer), jsonString)
        return decoded.map { entityClass.cast(it)!! }
    }

    @JvmStatic
    fun <T : Any> listFromJson(reader: Reader?, entityClass: Class<T>): List<T> {
        if (reader == null) return emptyList()
        return listFromJson(reader.readText(), entityClass)
    }

    // ---- Reified conveniences (concrete-type call sites; delegate to kotlinx) ----

    @JvmStatic
    inline fun <reified T> toJson(value: T): String = json.encodeToString(value)

    @JvmStatic
    inline fun <reified T> toJson(value: T, writer: Appendable) {
        writer.append(json.encodeToString(value))
    }

    @JvmStatic
    inline fun <reified T> entityFromJson(jsonString: String?): T? {
        if (jsonString.isNullOrEmpty()) return null
        return json.decodeFromString<T>(jsonString)
    }

    @JvmStatic
    inline fun <reified T> entityFromJson(reader: Reader?): T? {
        if (reader == null) return null
        return json.decodeFromString<T>(reader.readText())
    }

    @JvmStatic
    inline fun <reified T> listFromJson(jsonString: String?): List<T> {
        if (jsonString.isNullOrEmpty()) return emptyList()
        return json.decodeFromString<List<T>>(jsonString)
    }

    @JvmStatic
    inline fun <reified T> listFromJson(reader: Reader?): List<T> {
        if (reader == null) return emptyList()
        return listFromJson(reader.readText())
    }
}

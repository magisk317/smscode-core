package io.github.magisk317.smscode.db.ext

import androidx.room.TypeConverter
import io.github.magisk317.smscode.db.entity.Sender

class ConvertersSenderList {

    @TypeConverter
    fun stringToObject(value: String): List<Sender> {
        // 跨线程直接查询 DB 会引起死锁和架构问题，且轻量化版本不再立刻连表加载 Sender 对象，
        // 真正需要时可在 Repository/ViewModel 层面组合。详见 xinyi ConvertersSenderList 原实现。
        return emptyList()
    }

    @TypeConverter
    fun objectToString(list: List<Sender>): String {
        return list.joinToString(",") { it.id.toString() }
    }
}

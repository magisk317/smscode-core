package io.github.magisk317.forward.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * The forward-domain database. Owns everything the forwarding pipeline
 * records: events, sender dispatch logs and (in upcoming schema revisions)
 * sender configuration and route/filter rules.
 */
@Database(
    entities = [
        ForwardEventEntity::class,
        SenderDispatchLogEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class ForwardDatabase : RoomDatabase() {

    abstract fun forwardEventDao(): ForwardEventDao

    abstract fun senderDispatchLogDao(): SenderDispatchLogDao

    companion object {
        const val NAME = "forward.db"

        fun build(context: Context, name: String = NAME): ForwardDatabase =
            Room.databaseBuilder(context.applicationContext, ForwardDatabase::class.java, name)
                .build()

        fun buildInMemory(context: Context): ForwardDatabase =
            Room.inMemoryDatabaseBuilder(context.applicationContext, ForwardDatabase::class.java)
                .allowMainThreadQueries()
                .build()
    }
}

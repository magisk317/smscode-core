package io.github.magisk317.forward.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

/**
 * The forward-domain database. Owns everything the forwarding pipeline
 * records: events, sender dispatch logs and (in upcoming schema revisions)
 * sender configuration and route/filter rules.
 */
@TypeConverters(ForwardDateConverter::class)
@Database(
    entities = [
        ForwardEventEntity::class,
        SenderDispatchLogEntity::class,
        ForwardSenderEntity::class,
        ForwardRouteRuleEntity::class,
        ForwardFilterRuleEntity::class,
        ForwardScheduledTaskEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class ForwardDatabase : RoomDatabase() {

    abstract fun forwardEventDao(): ForwardEventDao

    abstract fun senderDispatchLogDao(): SenderDispatchLogDao

    abstract fun forwardScheduledTaskDao(): ForwardScheduledTaskDao

    abstract fun forwardFilterRuleDao(): ForwardFilterRuleDao

    abstract fun forwardRouteRuleDao(): ForwardRouteRuleDao

    abstract fun forwardSenderDao(): ForwardSenderDao

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

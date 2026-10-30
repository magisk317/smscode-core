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
        ForwardAppInfoEntity::class,
        ForwardRuleEntity::class,
        ForwardMetaEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class ForwardDatabase : RoomDatabase() {

    abstract fun forwardEventDao(): ForwardEventDao

    abstract fun senderDispatchLogDao(): SenderDispatchLogDao

    abstract fun forwardScheduledTaskDao(): ForwardScheduledTaskDao

    abstract fun forwardFilterRuleDao(): ForwardFilterRuleDao

    abstract fun forwardRouteRuleDao(): ForwardRouteRuleDao

    abstract fun forwardSenderDao(): ForwardSenderDao

    abstract fun forwardAppInfoDao(): ForwardAppInfoDao

    abstract fun forwardRuleDao(): ForwardRuleDao

    abstract fun forwardMetaDao(): ForwardMetaDao

    companion object {
        const val NAME = "forward.db"

        private val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS forward_app_info (" +
                        "package_name TEXT NOT NULL PRIMARY KEY, label TEXT, blocked INTEGER NOT NULL, " +
                        "forwarding INTEGER NOT NULL, forwarding_configured INTEGER NOT NULL, " +
                        "notify_template TEXT NOT NULL)",
                )
                db.execSQL(
                    "ALTER TABLE forward_scheduled_task ADD COLUMN created_at INTEGER NOT NULL DEFAULT 0",
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS forward_meta (" +
                        "\"key\" TEXT NOT NULL, \"value\" TEXT NOT NULL, PRIMARY KEY(\"key\"))",
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS forward_rule (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "type TEXT NOT NULL DEFAULT 'sms', " +
                        "filed TEXT NOT NULL DEFAULT 'transpond_all', " +
                        "\"check\" TEXT NOT NULL DEFAULT 'is', " +
                        "value TEXT NOT NULL DEFAULT '', " +
                        "sender_id INTEGER NOT NULL DEFAULT 0, " +
                        "sms_template TEXT NOT NULL DEFAULT '', " +
                        "regex_replace TEXT NOT NULL DEFAULT '', " +
                        "sim_slot TEXT NOT NULL DEFAULT 'ALL', " +
                        "status INTEGER NOT NULL DEFAULT 1, " +
                        "time INTEGER NOT NULL, " +
                        "sender_list_json TEXT NOT NULL DEFAULT '[]', " +
                        "sender_logic TEXT NOT NULL DEFAULT 'ALL', " +
                        "silent_period_start INTEGER NOT NULL DEFAULT 0, " +
                        "silent_period_end INTEGER NOT NULL DEFAULT 0, " +
                        "silent_day_of_week TEXT NOT NULL DEFAULT '', " +
                        "title TEXT NOT NULL DEFAULT '')",
                )
            }
        }

        fun build(context: Context, name: String = NAME): ForwardDatabase =
            Room.databaseBuilder(context.applicationContext, ForwardDatabase::class.java, name)
                .addMigrations(MIGRATION_1_2)
                .build()

        fun buildInMemory(context: Context): ForwardDatabase =
            Room.inMemoryDatabaseBuilder(context.applicationContext, ForwardDatabase::class.java)
                .allowMainThreadQueries()
                .build()
    }
}

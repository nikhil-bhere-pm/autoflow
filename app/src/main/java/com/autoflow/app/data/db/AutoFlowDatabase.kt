package com.autoflow.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.autoflow.app.data.db.dao.CustomCategoryDao
import com.autoflow.app.data.db.dao.GoalDao
import com.autoflow.app.data.db.dao.MerchantCategoryDao
import com.autoflow.app.data.db.dao.RefundMatchDao
import com.autoflow.app.data.db.dao.TransactionDao
import com.autoflow.app.data.db.entity.CustomCategoryEntity
import com.autoflow.app.data.db.entity.GoalEntity
import com.autoflow.app.data.db.entity.GoalEntryEntity
import com.autoflow.app.data.db.entity.MerchantCategoryEntity
import com.autoflow.app.data.db.entity.RefundMatchEntity
import com.autoflow.app.data.db.entity.TransactionEntity

@Database(
    entities = [
        TransactionEntity::class,
        RefundMatchEntity::class,
        GoalEntity::class,
        GoalEntryEntity::class,
        MerchantCategoryEntity::class,
        CustomCategoryEntity::class
    ],
    version = 5,
    exportSchema = true
)
@TypeConverters(AutoFlowTypeConverters::class)
abstract class AutoFlowDatabase : RoomDatabase() {

    abstract fun transactionDao(): TransactionDao
    abstract fun refundMatchDao(): RefundMatchDao
    abstract fun goalDao(): GoalDao
    abstract fun merchantCategoryDao(): MerchantCategoryDao
    abstract fun customCategoryDao(): CustomCategoryDao

    companion object {
        @Volatile
        private var INSTANCE: AutoFlowDatabase? = null

        const val DATABASE_NAME = "autoflow.db"

        val MIGRATION_1_2_SQL: List<String> = listOf(
            """
            CREATE TABLE IF NOT EXISTS `merchant_categories` (
                `merchantKey` TEXT NOT NULL,
                `category` TEXT NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                PRIMARY KEY(`merchantKey`)
            )
            """.trimIndent(),
            """
            ALTER TABLE `transactions` ADD COLUMN `categorySource` TEXT NOT NULL DEFAULT 'AUTO'
            """.trimIndent()
        )

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                MIGRATION_1_2_SQL.forEach { db.execSQL(it) }
            }
        }

        val MIGRATION_2_3_SQL: List<String> = listOf(
            "ALTER TABLE `transactions` ADD COLUMN `payment_mode` TEXT NOT NULL DEFAULT 'UNKNOWN'",
            "ALTER TABLE `transactions` ADD COLUMN `note` TEXT",
            "UPDATE `transactions` SET `payment_mode` = 'CASH' WHERE `source` = 'MANUAL_CASH'"
        )

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                MIGRATION_2_3_SQL.forEach { db.execSQL(it) }
            }
        }

        val MIGRATION_3_4_SQL: List<String> = listOf(
            """
            CREATE TABLE IF NOT EXISTS `custom_categories` (
                `name_key` TEXT NOT NULL,
                `name` TEXT NOT NULL,
                `icon_key` TEXT NOT NULL,
                `color_key` TEXT NOT NULL,
                `created_at` INTEGER NOT NULL,
                PRIMARY KEY(`name_key`)
            )
            """.trimIndent()
        )

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                MIGRATION_3_4_SQL.forEach { db.execSQL(it) }
            }
        }

        val MIGRATION_4_5_SQL: List<String> = listOf(
            "DROP TABLE IF EXISTS `goal_contributions`",
            "DROP TABLE IF EXISTS `goals`",
            """
            CREATE TABLE IF NOT EXISTS `goals` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `name` TEXT NOT NULL,
                `name_key` TEXT NOT NULL,
                `kind` TEXT NOT NULL,
                `target_paise` INTEGER NOT NULL,
                `created_at` INTEGER NOT NULL,
                `updated_at` INTEGER NOT NULL
            )
            """.trimIndent(),
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_goals_name_key` ON `goals` (`name_key`)",
            """
            CREATE TABLE IF NOT EXISTS `goal_entries` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `goal_id` INTEGER NOT NULL,
                `type` TEXT NOT NULL,
                `amount_paise` INTEGER NOT NULL,
                `note` TEXT,
                `occurred_at` INTEGER NOT NULL,
                `linked_transaction_id` INTEGER,
                `is_deleted` INTEGER NOT NULL DEFAULT 0,
                `created_at` INTEGER NOT NULL,
                `updated_at` INTEGER NOT NULL,
                FOREIGN KEY(`goal_id`) REFERENCES `goals`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent(),
            "CREATE INDEX IF NOT EXISTS `index_goal_entries_goal_id` ON `goal_entries` (`goal_id`)",
            "CREATE INDEX IF NOT EXISTS `index_goal_entries_linked_transaction_id` ON `goal_entries` (`linked_transaction_id`)"
        )

        fun executeMigration4To5(execSql: (String) -> Unit, queryCount: (String) -> Int) {
            val goalsCount = queryCount("goals")
            if (goalsCount > 0) {
                throw IllegalStateException("Migration 4->5 aborted: 'goals' table contains $goalsCount row(s). Refusing to drop non-empty table.")
            }
            val contribCount = queryCount("goal_contributions")
            if (contribCount > 0) {
                throw IllegalStateException("Migration 4->5 aborted: 'goal_contributions' table contains $contribCount row(s). Refusing to drop non-empty table.")
            }
            MIGRATION_4_5_SQL.forEach { execSql(it) }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                executeMigration4To5(
                    execSql = { db.execSQL(it) },
                    queryCount = { tableName ->
                        var count = 0
                        val cursor = db.query("SELECT COUNT(*) FROM `$tableName`")
                        if (cursor.moveToFirst()) {
                            count = cursor.getInt(0)
                        }
                        cursor.close()
                        count
                    }
                )
            }
        }

        fun getDatabase(context: Context): AutoFlowDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AutoFlowDatabase::class.java,
                    DATABASE_NAME
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

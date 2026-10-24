package com.autoflow.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.autoflow.app.data.db.dao.GoalContributionDao
import com.autoflow.app.data.db.dao.GoalDao
import com.autoflow.app.data.db.dao.MerchantCategoryDao
import com.autoflow.app.data.db.dao.RefundMatchDao
import com.autoflow.app.data.db.dao.TransactionDao
import com.autoflow.app.data.db.entity.GoalContributionEntity
import com.autoflow.app.data.db.entity.GoalEntity
import com.autoflow.app.data.db.entity.MerchantCategoryEntity
import com.autoflow.app.data.db.entity.RefundMatchEntity
import com.autoflow.app.data.db.entity.TransactionEntity

@Database(
    entities = [
        TransactionEntity::class,
        RefundMatchEntity::class,
        GoalEntity::class,
        GoalContributionEntity::class,
        MerchantCategoryEntity::class
    ],
    version = 2,
    exportSchema = false
)
@TypeConverters(AutoFlowTypeConverters::class)
abstract class AutoFlowDatabase : RoomDatabase() {

    abstract fun transactionDao(): TransactionDao
    abstract fun refundMatchDao(): RefundMatchDao
    abstract fun goalDao(): GoalDao
    abstract fun goalContributionDao(): GoalContributionDao
    abstract fun merchantCategoryDao(): MerchantCategoryDao

    companion object {
        @Volatile
        private var INSTANCE: AutoFlowDatabase? = null

        const val DATABASE_NAME = "autoflow.db"

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `merchant_categories` (
                        `merchantKey` TEXT NOT NULL,
                        `category` TEXT NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`merchantKey`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    ALTER TABLE `transactions` ADD COLUMN `categorySource` TEXT NOT NULL DEFAULT 'AUTO'
                    """.trimIndent()
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
                    .addMigrations(MIGRATION_1_2)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

package com.autoflow.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.autoflow.app.data.db.dao.GoalContributionDao
import com.autoflow.app.data.db.dao.GoalDao
import com.autoflow.app.data.db.dao.RefundMatchDao
import com.autoflow.app.data.db.dao.TransactionDao
import com.autoflow.app.data.db.entity.GoalContributionEntity
import com.autoflow.app.data.db.entity.GoalEntity
import com.autoflow.app.data.db.entity.RefundMatchEntity
import com.autoflow.app.data.db.entity.TransactionEntity

@Database(
    entities = [
        TransactionEntity::class,
        RefundMatchEntity::class,
        GoalEntity::class,
        GoalContributionEntity::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(AutoFlowTypeConverters::class)
abstract class AutoFlowDatabase : RoomDatabase() {

    abstract fun transactionDao(): TransactionDao
    abstract fun refundMatchDao(): RefundMatchDao
    abstract fun goalDao(): GoalDao
    abstract fun goalContributionDao(): GoalContributionDao

    companion object {
        @Volatile
        private var INSTANCE: AutoFlowDatabase? = null

        const val DATABASE_NAME = "autoflow.db"

        fun getDatabase(context: Context): AutoFlowDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AutoFlowDatabase::class.java,
                    DATABASE_NAME
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}

package com.autoflow.app

import android.app.Application
import androidx.room.withTransaction
import com.autoflow.app.data.db.AutoFlowDatabase
import com.autoflow.app.data.repository.TransactionRepository

class AutoFlowApplication : Application() {

    val database: AutoFlowDatabase by lazy {
        AutoFlowDatabase.getDatabase(this)
    }

    val transactionRepository: TransactionRepository by lazy {
        TransactionRepository(
            transactionDao = database.transactionDao(),
            merchantCategoryDao = database.merchantCategoryDao(),
            runInTransaction = { block -> database.withTransaction { block() } }
        )
    }

    val ingestionPipeline: com.autoflow.app.ingestion.IngestionPipeline by lazy {
        com.autoflow.app.ingestion.DefaultIngestionPipeline(database)
    }

    override fun onCreate() {
        super.onCreate()
    }
}

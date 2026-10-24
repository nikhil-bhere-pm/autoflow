package com.autoflow.app

import android.app.Application
import com.autoflow.app.data.db.AutoFlowDatabase
import com.autoflow.app.data.repository.TransactionRepository

class AutoFlowApplication : Application() {

    val database: AutoFlowDatabase by lazy {
        AutoFlowDatabase.getDatabase(this)
    }

    val transactionRepository: TransactionRepository by lazy {
        TransactionRepository(database.transactionDao())
    }

    val ingestionPipeline: com.autoflow.app.ingestion.IngestionPipeline by lazy {
        com.autoflow.app.ingestion.DefaultIngestionPipeline(database)
    }

    override fun onCreate() {
        super.onCreate()
    }
}

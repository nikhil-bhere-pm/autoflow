package com.autoflow.app.ingestion

import com.autoflow.app.data.db.entity.TransactionEntity

/**
 * Clean architectural contract for future transaction ingestion channels
 * (SMS BroadcastReceiver, SMS historical scanner, Manual quick-entry).
 *
 * Specific implementations and parsing engines are deferred to later phases.
 */
interface IngestionPipeline {
    /**
     * Ingests a pre-parsed or manual financial entry into the processing workflow.
     */
    suspend fun ingest(transaction: TransactionEntity): IngestionResult
}

sealed interface IngestionResult {
    data class Success(val transactionId: Long) : IngestionResult
    data class DuplicateIgnored(val existingHash: String) : IngestionResult
    data class Error(val message: String) : IngestionResult
}

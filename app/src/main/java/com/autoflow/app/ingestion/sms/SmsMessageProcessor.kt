package com.autoflow.app.ingestion.sms

import android.util.Log
import com.autoflow.app.data.adapter.AutoFlowParserAdapter
import com.autoflow.app.ingestion.IngestionPipeline
import com.autoflow.app.ingestion.IngestionResult

sealed interface ProcessSmsResult {
    data class Success(val transactionId: Long) : ProcessSmsResult
    data class Ignored(val reason: String) : ProcessSmsResult
    data class Duplicate(val existingHash: String) : ProcessSmsResult
    data class Error(val message: String) : ProcessSmsResult
}

/**
 * Encapsulates the business logic of taking raw SMS parts, passing them to the
 * in-memory parser adapter, and forwarding valid expense/refund entries to the IngestionPipeline.
 *
 * Guarantees:
 * 1. Zero raw SMS retention: raw SMS text is not stored, cached, or written to disk.
 * 2. Privacy-first logging: raw message bodies, OTPs, and sensitive data are NEVER logged.
 * 3. Crash resilience: malformed or unrecognized messages are dropped cleanly without exceptions.
 * 4. No unrecognized archive: messages that are not expenses/refunds are discarded immediately.
 */
class SmsMessageProcessor(
    private val ingestionPipeline: IngestionPipeline,
    private val parserAdapter: AutoFlowParserAdapter = AutoFlowParserAdapter
) {

    companion object {
        private const val TAG = "AutoFlow_SmsProcessor"
    }

    suspend fun processMessage(
        sender: String,
        body: String,
        timestamp: Long
    ): ProcessSmsResult {
        val trimmedBody = body.trim()
        if (trimmedBody.isEmpty()) {
            return ProcessSmsResult.Ignored("Empty message body")
        }

        // 1. In-memory parse & domain adaptation
        // The raw body is discarded as soon as parsing completes.
        val entity = try {
            parserAdapter.parseAndAdapt(trimmedBody, sender, timestamp)
        } catch (e: Exception) {
            // Log only the exception class; never log message content
            Log.w(TAG, "Parser error encountered: ${e.javaClass.simpleName}")
            return ProcessSmsResult.Error("Parser error: ${e.javaClass.simpleName}")
        }

        if (entity == null) {
            // Not a financial expense or refund (e.g. OTP, promotional, salary/income, balance check)
            // Discard immediately without persisting to any unrecognized archive
            return ProcessSmsResult.Ignored("Irrelevant or non-expense message")
        }

        // 2. Ingest through IngestionPipeline (deduplication, soft-delete protection, refund capping)
        return try {
            when (val result = ingestionPipeline.ingest(entity)) {
                is IngestionResult.Success -> {
                    // Safe log: record type, amount in paise, review status. NO SMS body or full accounts.
                    Log.i(TAG, "Successfully ingested transaction: type=${entity.recordType} amountPaise=${entity.amountPaise} status=${entity.reviewStatus}")
                    ProcessSmsResult.Success(result.transactionId)
                }
                is IngestionResult.DuplicateIgnored -> {
                    Log.d(TAG, "Duplicate SMS ignored: ${result.reason}")
                    ProcessSmsResult.Duplicate(result.existingHash)
                }
                is IngestionResult.SoftDeletedIgnored -> {
                    Log.d(TAG, "Soft-deleted transaction ignored during SMS rescan")
                    ProcessSmsResult.Ignored("Soft-deleted transaction")
                }
                is IngestionResult.Error -> {
                    Log.w(TAG, "Ingestion error: ${result.message}")
                    ProcessSmsResult.Error(result.message)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error during pipeline ingestion: ${e.javaClass.simpleName}")
            ProcessSmsResult.Error("Pipeline error: ${e.javaClass.simpleName}")
        }
    }
}

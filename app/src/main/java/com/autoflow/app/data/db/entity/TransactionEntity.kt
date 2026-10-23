package com.autoflow.app.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.autoflow.app.data.model.RecordType
import com.autoflow.app.data.model.ReviewStatus
import com.autoflow.app.data.model.TransactionSource

@Entity(
    tableName = "transactions",
    indices = [
        Index(value = ["transaction_hash"], unique = true, name = "idx_tx_hash"),
        Index(value = ["date_time"], name = "idx_tx_date"),
        Index(value = ["review_status"], name = "idx_tx_review"),
        Index(value = ["reference"], name = "idx_tx_ref")
    ]
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,

    @ColumnInfo(name = "amount_paise")
    val amountPaise: Long,

    @ColumnInfo(name = "record_type")
    val recordType: RecordType,

    @ColumnInfo(name = "date_time")
    val dateTime: Long,

    @ColumnInfo(name = "merchant_name")
    val merchantName: String,

    @ColumnInfo(name = "category")
    val category: String = "Others",

    @ColumnInfo(name = "account_number")
    val accountNumber: String? = null,

    @ColumnInfo(name = "bank_name")
    val bankName: String? = null,

    @ColumnInfo(name = "reference")
    val reference: String? = null,

    @ColumnInfo(name = "transaction_hash")
    val transactionHash: String,

    @ColumnInfo(name = "source")
    val source: TransactionSource,

    @ColumnInfo(name = "review_status")
    val reviewStatus: ReviewStatus,

    @ColumnInfo(name = "uncertainty_bits")
    val uncertaintyBits: Int = 0,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "is_deleted")
    val isDeleted: Int = 0
) {
    init {
        require(amountPaise > 0) { "Transaction amount in paise must be strictly positive: $amountPaise" }
        require(transactionHash.isNotBlank()) { "Transaction hash cannot be blank" }
    }
}

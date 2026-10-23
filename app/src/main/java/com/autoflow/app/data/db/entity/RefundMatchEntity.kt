package com.autoflow.app.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.autoflow.app.data.model.MatchStatus

@Entity(
    tableName = "refund_matches",
    foreignKeys = [
        ForeignKey(
            entity = TransactionEntity::class,
            parentColumns = ["id"],
            childColumns = ["original_expense_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = TransactionEntity::class,
            parentColumns = ["id"],
            childColumns = ["refund_transaction_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(
            value = ["original_expense_id", "refund_transaction_id"],
            unique = true,
            name = "idx_refund_link"
        ),
        Index(value = ["original_expense_id"], name = "idx_refund_orig_expense"),
        Index(value = ["refund_transaction_id"], name = "idx_refund_tx")
    ]
)
data class RefundMatchEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,

    @ColumnInfo(name = "original_expense_id")
    val originalExpenseId: Long,

    @ColumnInfo(name = "refund_transaction_id")
    val refundTransactionId: Long,

    @ColumnInfo(name = "allocated_amount_paise")
    val allocatedAmountPaise: Long,

    @ColumnInfo(name = "match_status")
    val matchStatus: MatchStatus,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis()
) {
    init {
        require(allocatedAmountPaise > 0) {
            "Allocated refund amount in paise must be strictly positive: $allocatedAmountPaise"
        }
        require(originalExpenseId != refundTransactionId) {
            "A transaction cannot be refunded against itself"
        }
    }
}

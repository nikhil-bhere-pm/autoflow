package com.autoflow.app.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "goal_entries",
    foreignKeys = [
        ForeignKey(
            entity = GoalEntity::class,
            parentColumns = ["id"],
            childColumns = ["goal_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["goal_id"]),
        Index(value = ["linked_transaction_id"])
    ]
)
data class GoalEntryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,

    @ColumnInfo(name = "goal_id")
    val goalId: Long,

    @ColumnInfo(name = "type")
    val type: String,

    @ColumnInfo(name = "amount_paise")
    val amountPaise: Long,

    @ColumnInfo(name = "note")
    val note: String? = null,

    @ColumnInfo(name = "occurred_at")
    val occurredAt: Long,

    @ColumnInfo(name = "linked_transaction_id")
    val linkedTransactionId: Long? = null,

    @ColumnInfo(name = "is_deleted", defaultValue = "0")
    val isDeleted: Int = 0,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis()
) {
    init {
        require(amountPaise > 0) { "Entry amount in paise must be strictly positive: $amountPaise" }
        require(type == "SET_ASIDE" || type == "AVOIDED" || type == "USED") { "Invalid entry type: $type" }
    }
}

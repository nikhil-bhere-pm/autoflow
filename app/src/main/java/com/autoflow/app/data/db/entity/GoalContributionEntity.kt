package com.autoflow.app.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.autoflow.app.data.model.SavingsType

@Entity(
    tableName = "goal_contributions",
    foreignKeys = [
        ForeignKey(
            entity = GoalEntity::class,
            parentColumns = ["id"],
            childColumns = ["goal_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["goal_id"], name = "idx_contrib_goal_id"),
        Index(value = ["savings_type"], name = "idx_contrib_savings_type"),
        Index(value = ["date"], name = "idx_contrib_date")
    ]
)
data class GoalContributionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,

    @ColumnInfo(name = "goal_id")
    val goalId: Long,

    @ColumnInfo(name = "savings_type")
    val savingsType: SavingsType,

    @ColumnInfo(name = "amount_paise")
    val amountPaise: Long,

    @ColumnInfo(name = "date")
    val date: Long,

    @ColumnInfo(name = "note")
    val note: String? = null,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis()
) {
    init {
        require(amountPaise > 0) {
            "Contribution amount in paise must be strictly positive: $amountPaise"
        }
    }
}

package com.autoflow.app.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "goals")
data class GoalEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "target_amount_paise")
    val targetAmountPaise: Long,

    @ColumnInfo(name = "target_date")
    val targetDate: Long,

    @ColumnInfo(name = "color_hex")
    val colorHex: String = "#6200EE",

    @ColumnInfo(name = "is_archived")
    val isArchived: Int = 0,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis()
) {
    init {
        require(name.isNotBlank()) { "Goal name cannot be blank" }
        require(targetAmountPaise > 0) {
            "Goal target amount in paise must be strictly positive: $targetAmountPaise"
        }
    }
}

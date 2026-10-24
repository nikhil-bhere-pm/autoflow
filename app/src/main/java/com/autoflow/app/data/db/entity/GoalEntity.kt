package com.autoflow.app.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "goals",
    indices = [
        Index(value = ["name_key"], unique = true)
    ]
)
data class GoalEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "name_key")
    val nameKey: String,

    @ColumnInfo(name = "target_paise")
    val targetPaise: Long,

    @ColumnInfo(name = "deadline_at")
    val deadlineAt: Long? = null,

    @ColumnInfo(name = "is_deleted", defaultValue = "0")
    val isDeleted: Int = 0,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis()
) {
    init {
        require(name.isNotBlank()) { "Goal name cannot be blank" }
        require(name.trim().length <= 30) { "Goal name cannot exceed 30 characters" }
        require(targetPaise > 0) { "Goal target in paise must be strictly positive: $targetPaise" }
    }
}

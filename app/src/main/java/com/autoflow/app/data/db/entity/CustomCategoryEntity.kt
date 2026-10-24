package com.autoflow.app.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "custom_categories")
data class CustomCategoryEntity(
    @PrimaryKey
    @ColumnInfo(name = "name_key")
    val nameKey: String,

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "icon_key")
    val iconKey: String,

    @ColumnInfo(name = "color_key")
    val colorKey: String,

    @ColumnInfo(name = "created_at")
    val createdAt: Long
)

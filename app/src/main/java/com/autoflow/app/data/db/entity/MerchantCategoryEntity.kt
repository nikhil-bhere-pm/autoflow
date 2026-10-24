package com.autoflow.app.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "merchant_categories")
data class MerchantCategoryEntity(
    @PrimaryKey
    @ColumnInfo(name = "merchantKey")
    val merchantKey: String,

    @ColumnInfo(name = "category")
    val category: String,

    @ColumnInfo(name = "updatedAt")
    val updatedAt: Long
)

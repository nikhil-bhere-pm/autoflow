package com.autoflow.app.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.autoflow.app.data.db.entity.MerchantCategoryEntity

@Dao
interface MerchantCategoryDao {
    @Query("SELECT * FROM merchant_categories WHERE merchantKey = :key LIMIT 1")
    suspend fun getByKey(key: String): MerchantCategoryEntity?

    @Query("SELECT category FROM merchant_categories WHERE merchantKey = :key LIMIT 1")
    suspend fun getCategory(key: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: MerchantCategoryEntity)

    @Query("SELECT * FROM merchant_categories")
    suspend fun getAll(): List<MerchantCategoryEntity>

    @Query("UPDATE merchant_categories SET category = :newCategory WHERE category = :oldCategory")
    suspend fun updateCategory(oldCategory: String, newCategory: String): Int = 0

    @Query("DELETE FROM merchant_categories WHERE category = :category")
    suspend fun deleteByCategory(category: String): Int = 0

    @Query("DELETE FROM merchant_categories WHERE merchantKey = :key")
    suspend fun deleteByKey(key: String): Int = 0
}

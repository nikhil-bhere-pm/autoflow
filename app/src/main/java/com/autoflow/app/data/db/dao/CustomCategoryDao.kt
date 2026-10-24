package com.autoflow.app.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.autoflow.app.data.db.entity.CustomCategoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CustomCategoryDao {

    @Query("SELECT * FROM custom_categories ORDER BY created_at ASC")
    fun getAll(): Flow<List<CustomCategoryEntity>>

    @Query("SELECT * FROM custom_categories ORDER BY created_at ASC")
    suspend fun getAllSync(): List<CustomCategoryEntity>

    @Query("SELECT * FROM custom_categories WHERE name_key = :nameKey LIMIT 1")
    suspend fun getByNameKey(nameKey: String): CustomCategoryEntity?

    @Query("SELECT COUNT(*) FROM custom_categories")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: CustomCategoryEntity)

    @Update
    suspend fun update(entity: CustomCategoryEntity)

    @Query("""
        UPDATE custom_categories 
        SET name_key = :newKey, name = :newName, icon_key = :newIcon, color_key = :newColor 
        WHERE name_key = :oldKey
    """)
    suspend fun updateCategoryValues(
        oldKey: String,
        newKey: String,
        newName: String,
        newIcon: String,
        newColor: String
    ): Int

    @Query("DELETE FROM custom_categories WHERE name_key = :nameKey")
    suspend fun deleteByKey(nameKey: String): Int
}

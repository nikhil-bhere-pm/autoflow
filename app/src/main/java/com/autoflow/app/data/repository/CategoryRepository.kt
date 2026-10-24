package com.autoflow.app.data.repository

import com.autoflow.app.data.db.dao.CustomCategoryDao
import com.autoflow.app.data.db.dao.MerchantCategoryDao
import com.autoflow.app.data.db.dao.TransactionDao
import com.autoflow.app.data.db.entity.CustomCategoryEntity
import com.autoflow.app.domain.category.ALLOWED_CATEGORY_COLORS
import com.autoflow.app.domain.category.ALLOWED_CATEGORY_ICONS
import com.autoflow.app.domain.category.BUILT_IN_CATEGORIES
import com.autoflow.app.domain.category.CategoryItem
import com.autoflow.app.domain.category.CategoryOperationResult
import com.autoflow.app.domain.category.cleanCategoryName
import com.autoflow.app.domain.category.isReservedCategoryName
import com.autoflow.app.domain.category.isValidCategoryNameCharacters
import com.autoflow.app.domain.category.normalizeCategoryKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class CategoryRepository(
    private val customCategoryDao: CustomCategoryDao,
    private val transactionDao: TransactionDao,
    private val merchantCategoryDao: MerchantCategoryDao? = null,
    private val runInTransaction: suspend (suspend () -> Unit) -> Unit = { block -> block() }
) {

    fun getAllCategories(): Flow<List<CategoryItem>> {
        return customCategoryDao.getAll().map { customList ->
            val builtIns = BUILT_IN_CATEGORIES.map { name ->
                CategoryItem(
                    name = name,
                    isBuiltIn = true,
                    iconKey = null,
                    colorKey = null
                )
            }
            val customs = customList.map { entity ->
                CategoryItem(
                    name = entity.name,
                    isBuiltIn = false,
                    iconKey = entity.iconKey,
                    colorKey = entity.colorKey
                )
            }
            builtIns + customs
        }
    }

    suspend fun getAllCategoriesSync(): List<CategoryItem> {
        val builtIns = BUILT_IN_CATEGORIES.map { name ->
            CategoryItem(
                name = name,
                isBuiltIn = true,
                iconKey = null,
                colorKey = null
            )
        }
        val customs = customCategoryDao.getAllSync().map { entity ->
            CategoryItem(
                name = entity.name,
                isBuiltIn = false,
                iconKey = entity.iconKey,
                colorKey = entity.colorKey
            )
        }
        return builtIns + customs
    }

    suspend fun addCategory(
        name: String,
        iconKey: String,
        colorKey: String,
        now: Long = System.currentTimeMillis()
    ): CategoryOperationResult {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) {
            return CategoryOperationResult.NameBlank
        }

        val cleaned = cleanCategoryName(name)
        if (cleaned.length > 20) {
            return CategoryOperationResult.NameTooLong
        }

        if (!isValidCategoryNameCharacters(cleaned)) {
            return CategoryOperationResult.NameInvalidCharacters
        }

        val key = normalizeCategoryKey(cleaned)
        if (isReservedCategoryName(key)) {
            return CategoryOperationResult.NameNotUnique
        }


        if (iconKey !in ALLOWED_CATEGORY_ICONS) {
            return CategoryOperationResult.InvalidIcon
        }

        if (colorKey !in ALLOWED_CATEGORY_COLORS) {
            return CategoryOperationResult.InvalidColor
        }

        if (customCategoryDao.count() >= 30) {
            return CategoryOperationResult.TooManyCategories
        }

        if (customCategoryDao.getByNameKey(key) != null) {
            return CategoryOperationResult.NameNotUnique
        }

        val entity = CustomCategoryEntity(
            nameKey = key,
            name = cleaned,
            iconKey = iconKey,
            colorKey = colorKey,
            createdAt = now
        )

        try {
            customCategoryDao.insert(entity)
        } catch (e: Exception) {
            if (isConstraintViolation(e)) {
                return CategoryOperationResult.NameNotUnique
            }
            throw e
        }

        return CategoryOperationResult.Success(
            CategoryItem(
                name = cleaned,
                isBuiltIn = false,
                iconKey = iconKey,
                colorKey = colorKey
            )
        )
    }

    suspend fun updateCategory(
        currentName: String,
        newName: String,
        iconKey: String,
        colorKey: String
    ): CategoryOperationResult {
        val currentKey = normalizeCategoryKey(currentName)
        if (isReservedCategoryName(currentKey)) {
            return CategoryOperationResult.BuiltInCategoryReadOnly
        }

        val existing = customCategoryDao.getByNameKey(currentKey)
            ?: return CategoryOperationResult.CategoryNotFound

        val trimmedNew = newName.trim()
        if (trimmedNew.isEmpty()) {
            return CategoryOperationResult.NameBlank
        }

        val cleanedNew = cleanCategoryName(newName)
        if (cleanedNew.length > 20) {
            return CategoryOperationResult.NameTooLong
        }

        if (!isValidCategoryNameCharacters(cleanedNew)) {
            return CategoryOperationResult.NameInvalidCharacters
        }

        if (iconKey !in ALLOWED_CATEGORY_ICONS) {
            return CategoryOperationResult.InvalidIcon
        }

        if (colorKey !in ALLOWED_CATEGORY_COLORS) {
            return CategoryOperationResult.InvalidColor
        }

        val newKey = normalizeCategoryKey(cleanedNew)
        if (newKey != currentKey) {
            if (isReservedCategoryName(newKey)) {
                return CategoryOperationResult.NameNotUnique
            }
            if (customCategoryDao.getByNameKey(newKey) != null) {
                return CategoryOperationResult.NameNotUnique
            }
        }

        val oldName = existing.name
        runInTransaction {
            customCategoryDao.updateCategoryValues(
                oldKey = currentKey,
                newKey = newKey,
                newName = cleanedNew,
                newIcon = iconKey,
                newColor = colorKey
            )

            if (cleanedNew != oldName || currentKey != newKey) {
                transactionDao.updateCategory(oldCategory = oldName, newCategory = cleanedNew)
                if (currentName != oldName) {
                    transactionDao.updateCategory(oldCategory = currentName, newCategory = cleanedNew)
                }

                merchantCategoryDao?.updateCategory(oldCategory = oldName, newCategory = cleanedNew)
                if (currentName != oldName) {
                    merchantCategoryDao?.updateCategory(oldCategory = currentName, newCategory = cleanedNew)
                }
            }
        }

        return CategoryOperationResult.Success(
            CategoryItem(
                name = cleanedNew,
                isBuiltIn = false,
                iconKey = iconKey,
                colorKey = colorKey
            )
        )
    }

    suspend fun countTransactionsInCategory(name: String): Int {
        val key = normalizeCategoryKey(name)
        val existing = customCategoryDao.getByNameKey(key)
        val targetName = existing?.name ?: name
        return transactionDao.countActiveTransactionsInCategory(targetName)
    }

    suspend fun deleteCategory(
        name: String,
        now: Long = System.currentTimeMillis()
    ): CategoryOperationResult {
        val key = normalizeCategoryKey(name)
        if (isReservedCategoryName(key)) {
            return CategoryOperationResult.BuiltInCategoryReadOnly
        }

        val existing = customCategoryDao.getByNameKey(key)
            ?: return CategoryOperationResult.CategoryNotFound

        val targetName = existing.name
        runInTransaction {
            transactionDao.reassignCategoryToOthers(category = targetName, now = now)
            if (name != targetName) {
                transactionDao.reassignCategoryToOthers(category = name, now = now)
            }

            merchantCategoryDao?.deleteByCategory(category = targetName)
            if (name != targetName) {
                merchantCategoryDao?.deleteByCategory(category = name)
            }

            customCategoryDao.deleteByKey(key)
        }

        return CategoryOperationResult.Success()
    }

    private fun isConstraintViolation(e: Exception): Boolean {
        val msg = e.message ?: ""
        val cls = e.javaClass.simpleName
        return cls.contains("Constraint", ignoreCase = true) ||
                msg.contains("constraint", ignoreCase = true) ||
                msg.contains("unique", ignoreCase = true) ||
                msg.contains("primary key", ignoreCase = true)
    }
}

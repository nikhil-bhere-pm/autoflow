package com.autoflow.app.presentation.ui.components

import com.autoflow.app.domain.category.CategoryItem
import com.autoflow.app.domain.category.normalizeCategoryKey

object CategoryThemeRegistry {
    private val categoryMap = mutableMapOf<String, CategoryItem>()

    fun updateCategories(categories: List<CategoryItem>) {
        synchronized(categoryMap) {
            categoryMap.clear()
            for (cat in categories) {
                categoryMap[normalizeCategoryKey(cat.name)] = cat
            }
        }
    }

    fun getCategoryItem(name: String?): CategoryItem? {
        if (name == null) return null
        return synchronized(categoryMap) {
            categoryMap[normalizeCategoryKey(name)]
        }
    }

    fun getColorKey(name: String?): String? = getCategoryItem(name)?.colorKey

    fun getIconKey(name: String?): String? = getCategoryItem(name)?.iconKey
}

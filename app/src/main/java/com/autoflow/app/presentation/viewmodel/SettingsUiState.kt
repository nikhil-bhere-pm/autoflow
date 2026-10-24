package com.autoflow.app.presentation.viewmodel

import com.autoflow.app.domain.category.CategoryItem
import com.autoflow.app.domain.category.CategoryOperationResult
import com.autoflow.app.presentation.theme.AppThemeMode
import com.autoflow.app.presentation.theme.ThemePreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class InMemoryThemePreferences(
    initial: AppThemeMode = AppThemeMode.SYSTEM
) : ThemePreferences {
    private val _themeMode = MutableStateFlow(initial)
    override val themeMode: StateFlow<AppThemeMode> = _themeMode.asStateFlow()

    override fun setThemeMode(mode: AppThemeMode) {
        _themeMode.value = mode
    }
}

fun CategoryOperationResult.toUserMessage(): String? = when (this) {
    is CategoryOperationResult.Success -> null
    is CategoryOperationResult.NameBlank -> "Name cannot be empty"
    is CategoryOperationResult.NameTooLong -> "Name must be 20 characters or less"
    is CategoryOperationResult.NameInvalidCharacters ->
        "Name contains invalid characters. Use letters, numbers, spaces, and & - ' only"
    is CategoryOperationResult.NameNotUnique -> "A category with this name already exists"
    is CategoryOperationResult.InvalidIcon -> "Please select a valid icon"
    is CategoryOperationResult.InvalidColor -> "Please select a valid color"
    is CategoryOperationResult.TooManyCategories ->
        "Limit reached. You can only create up to 30 custom categories"
    is CategoryOperationResult.BuiltInCategoryReadOnly ->
        "Built-in categories cannot be edited or deleted"
    is CategoryOperationResult.CategoryNotFound -> "Category not found"
}

data class CustomCategoryRowUiModel(
    val category: CategoryItem,
    val transactionCount: Int
)

package com.autoflow.app.domain.category

import java.util.Locale

data class CategoryItem(
    val name: String,
    val isBuiltIn: Boolean,
    val iconKey: String? = null,
    val colorKey: String? = null
) {
    val isCustom: Boolean get() = !isBuiltIn
}

sealed interface CategoryOperationResult {
    data class Success(val category: CategoryItem? = null) : CategoryOperationResult
    data object NameBlank : CategoryOperationResult
    data object NameTooLong : CategoryOperationResult
    data object NameInvalidCharacters : CategoryOperationResult
    data object NameNotUnique : CategoryOperationResult
    data object InvalidIcon : CategoryOperationResult
    data object InvalidColor : CategoryOperationResult
    data object TooManyCategories : CategoryOperationResult
    data object BuiltInCategoryReadOnly : CategoryOperationResult
    data object CategoryNotFound : CategoryOperationResult
}

typealias CategoryResult = CategoryOperationResult

val ALLOWED_CATEGORY_ICONS: Set<String> = setOf(
    "PAW", "HOME", "GIFT", "BOOK", "COFFEE", "TOOL", "LEAF", "MUSIC", "PHONE", "WALLET", "BAG", "CALENDAR"
)

val ALLOWED_CATEGORY_COLORS: Set<String> = setOf(
    "ORANGE", "GREEN", "BLUE", "PINK", "GOLD", "PURPLE", "TEAL", "RED"
)

val BUILT_IN_CATEGORIES: List<String> = listOf(
    "Food",
    "Groceries",
    "Transport",
    "Shopping",
    "Bills",
    "Entertainment",
    "Health",
    "Travel",
    "Others"
)

val RESERVED_CATEGORY_NAMES: List<String> = listOf(
    "Food",
    "Groceries",
    "Transport",
    "Shopping",
    "Bills",
    "Entertainment",
    "Health",
    "Travel",
    "Others",
    "Unknown",
    "Refund",
    "Cash",
    "Not counted"
)

fun normalizeCategoryKey(name: String): String {
    return name.trim()
        .replace(Regex("""\s+"""), " ")
        .lowercase(Locale.ROOT)
}

fun cleanCategoryName(name: String): String {
    return name.trim()
        .replace(Regex("""\s+"""), " ")
}

fun isValidCategoryNameCharacters(name: String): Boolean {
    return name.all { c ->
        c.isLetter() || c.isDigit() || c == ' ' || c == '&' || c == '-' || c == '\''
    }
}

fun isReservedCategoryName(key: String): Boolean {
    val normalizedKey = normalizeCategoryKey(key)
    return RESERVED_CATEGORY_NAMES.any { normalizeCategoryKey(it) == normalizedKey }
}

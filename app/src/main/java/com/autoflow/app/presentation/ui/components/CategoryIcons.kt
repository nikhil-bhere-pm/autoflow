package com.autoflow.app.presentation.ui.components

import androidx.annotation.DrawableRes
import com.autoflow.app.R

object AFCategoryIcons {
    @DrawableRes
    fun getDrawableRes(category: String?): Int = when (category?.trim()?.lowercase()) {
        "food" -> R.drawable.ic_cat_food
        "groceries" -> R.drawable.ic_cat_groceries
        "transport" -> R.drawable.ic_cat_transport
        "shopping" -> R.drawable.ic_cat_shopping
        "bills" -> R.drawable.ic_cat_bills
        "entertainment" -> R.drawable.ic_cat_entertainment
        "health" -> R.drawable.ic_cat_health
        "travel" -> R.drawable.ic_cat_travel
        "others" -> R.drawable.ic_cat_others
        else -> R.drawable.ic_cat_unknown
    }
}

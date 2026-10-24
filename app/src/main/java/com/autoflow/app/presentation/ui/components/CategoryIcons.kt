package com.autoflow.app.presentation.ui.components

import androidx.annotation.DrawableRes
import com.autoflow.app.R

object AFCategoryIcons {
    @DrawableRes
    fun getDrawableRes(category: String?): Int {
        val norm = category?.trim()?.lowercase() ?: return R.drawable.ic_cat_unknown
        return when (norm) {
            "food" -> R.drawable.ic_cat_food
            "groceries" -> R.drawable.ic_cat_groceries
            "transport" -> R.drawable.ic_cat_transport
            "shopping" -> R.drawable.ic_cat_shopping
            "bills" -> R.drawable.ic_cat_bills
            "entertainment" -> R.drawable.ic_cat_entertainment
            "health" -> R.drawable.ic_cat_health
            "travel" -> R.drawable.ic_cat_travel
            "others" -> R.drawable.ic_cat_others
            else -> {
                val iconKey = CategoryThemeRegistry.getIconKey(norm)
                if (iconKey != null) {
                    getDrawableResForIconKey(iconKey)
                } else {
                    R.drawable.ic_cat_unknown
                }
            }
        }
    }

    @DrawableRes
    fun getDrawableResForIconKey(iconKey: String?): Int = when (iconKey?.trim()?.uppercase()) {
        "PAW" -> R.drawable.ic_cat_paw
        "HOME" -> R.drawable.ic_cat_home
        "GIFT" -> R.drawable.ic_cat_gift
        "BOOK" -> R.drawable.ic_cat_book
        "COFFEE" -> R.drawable.ic_cat_coffee
        "TOOL" -> R.drawable.ic_cat_tool
        "LEAF" -> R.drawable.ic_cat_leaf
        "MUSIC" -> R.drawable.ic_cat_music
        "PHONE" -> R.drawable.ic_cat_phone
        "WALLET" -> R.drawable.ic_cat_wallet
        "BAG" -> R.drawable.ic_cat_bag
        "CALENDAR" -> R.drawable.ic_cat_calendar
        else -> R.drawable.ic_cat_unknown
    }
}


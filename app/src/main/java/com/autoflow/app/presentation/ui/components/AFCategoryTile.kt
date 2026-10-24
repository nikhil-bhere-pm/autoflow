package com.autoflow.app.presentation.ui.components

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.autoflow.app.presentation.theme.AppRadius
import com.autoflow.app.presentation.theme.AppSpacing
import com.autoflow.app.presentation.theme.AutoFlowTheme

@Composable
fun AFCategoryTile(
    category: String,
    modifier: Modifier = Modifier,
    size: Dp = AppSpacing.minTapTarget,
    iconSize: Dp = 24.dp,
    onClick: (() -> Unit)? = null
) {
    val colors = AutoFlowTheme.colors
    val iconColor = colors.forCategory(category)
    val tileBg = colors.categoryTileBackground(category)
    val shape = RoundedCornerShape(AppRadius.md)

    val clickableModifier = if (onClick != null) {
        Modifier.clickable(onClick = onClick)
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .size(size)
            .defaultMinSize(minWidth = AppSpacing.minTapTarget, minHeight = AppSpacing.minTapTarget)
            .clip(shape)
            .background(tileBg)
            .then(clickableModifier),
        contentAlignment = Alignment.Center
    ) {
        val iconRes = AFCategoryIcons.getDrawableRes(category)
        Icon(
            painter = painterResource(id = iconRes),
            contentDescription = category,
            tint = iconColor,
            modifier = Modifier.size(iconSize)
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Preview(name = "Light - AFCategoryTile")
@Composable
private fun AFCategoryTileLightPreview() {
    AutoFlowTheme(darkTheme = false) {
        Surface(color = AutoFlowTheme.colors.bg) {
            FlowRow(
                modifier = Modifier.padding(AppSpacing.md),
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)
            ) {
                val categories = listOf("Food", "Groceries", "Transport", "Shopping", "Bills", "Entertainment", "Health", "Travel", "Others", "Unknown")
                for (cat in categories) {
                    AFCategoryTile(category = cat, onClick = {})
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Preview(name = "Dark - AFCategoryTile", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun AFCategoryTileDarkPreview() {
    AutoFlowTheme(darkTheme = true) {
        Surface(color = AutoFlowTheme.colors.bg) {
            FlowRow(
                modifier = Modifier.padding(AppSpacing.md),
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)
            ) {
                val categories = listOf("Food", "Groceries", "Transport", "Shopping", "Bills", "Entertainment", "Health", "Travel", "Others", "Unknown")
                for (cat in categories) {
                    AFCategoryTile(category = cat, onClick = {})
                }
            }
        }
    }
}

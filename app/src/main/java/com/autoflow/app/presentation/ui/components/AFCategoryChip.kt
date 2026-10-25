package com.autoflow.app.presentation.ui.components

import android.content.res.Configuration
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.autoflow.app.presentation.theme.AppRadius
import com.autoflow.app.presentation.theme.AppSpacing
import com.autoflow.app.presentation.theme.AutoFlowTheme

object AFChipTokens {
    val visualHeight = AppSpacing.chipVisualHeight
    val minTouchTarget = AppSpacing.chipMinTouchTarget
    val horizontalPadding = AppSpacing.chipHorizontalPadding
    val gap = AppSpacing.chipGap
    val fontSize = 13.sp
    val lineHeight = 18.sp
}

@Composable
fun AFCategoryChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: (@Composable () -> Unit)? = null
) {
    val colors = AutoFlowTheme.colors

    val containerColor = if (selected) colors.brand else colors.chip
    val contentColor = if (selected) colors.buttonText else colors.ink
    val border = if (selected) null else BorderStroke(1.dp, colors.line)
    val shape = RoundedCornerShape(AppRadius.full)

    Box(
        modifier = modifier
            .defaultMinSize(minHeight = AFChipTokens.minTouchTarget, minWidth = AFChipTokens.minTouchTarget)
            .clip(shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = shape,
            color = containerColor,
            contentColor = contentColor,
            border = border,
            modifier = Modifier.defaultMinSize(minHeight = AFChipTokens.visualHeight)
        ) {
            Row(
                modifier = Modifier
                    .defaultMinSize(minHeight = AFChipTokens.visualHeight)
                    .padding(horizontal = AFChipTokens.horizontalPadding, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                if (icon != null) {
                    icon()
                    Spacer(modifier = Modifier.width(AppSpacing.xs))
                }
                Text(
                    text = text,
                    style = AutoFlowTheme.typography.labelLarge.copy(
                        fontSize = AFChipTokens.fontSize,
                        lineHeight = AFChipTokens.lineHeight
                    )
                )
            }
        }
    }
}

@Preview(name = "Light - AFCategoryChip")
@Composable
private fun AFCategoryChipLightPreview() {
    AutoFlowTheme(darkTheme = false) {
        Surface(color = AutoFlowTheme.colors.bg) {
            Column(
                modifier = Modifier.padding(AppSpacing.md),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)
            ) {
                AFCategoryChip(text = "Food", selected = true, onClick = {})
                AFCategoryChip(text = "Groceries", selected = false, onClick = {})
                AFCategoryChip(text = "Transport", selected = false, onClick = {})
            }
        }
    }
}

@Preview(name = "Dark - AFCategoryChip", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun AFCategoryChipDarkPreview() {
    AutoFlowTheme(darkTheme = true) {
        Surface(color = AutoFlowTheme.colors.bg) {
            Column(
                modifier = Modifier.padding(AppSpacing.md),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)
            ) {
                AFCategoryChip(text = "Food", selected = true, onClick = {})
                AFCategoryChip(text = "Groceries", selected = false, onClick = {})
                AFCategoryChip(text = "Transport", selected = false, onClick = {})
            }
        }
    }
}

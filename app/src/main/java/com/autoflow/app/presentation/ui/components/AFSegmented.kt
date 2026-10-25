package com.autoflow.app.presentation.ui.components

import android.content.res.Configuration
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.autoflow.app.presentation.theme.AppRadius
import com.autoflow.app.presentation.theme.AppSpacing
import com.autoflow.app.presentation.theme.AutoFlowTheme

@Composable
fun AFSegmented(
    items: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = AutoFlowTheme.colors
    val outerShape = RoundedCornerShape(AppRadius.md)
    val itemShape = RoundedCornerShape(AppRadius.sm)

    Surface(
        shape = outerShape,
        color = colors.chip,
        border = BorderStroke(1.dp, colors.line),
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = AFChipTokens.minTouchTarget)
    ) {
        Row(
            modifier = Modifier
                .padding(2.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEachIndexed { index, title ->
                val isSelected = index == selectedIndex
                val containerColor = if (isSelected) colors.sheet else colors.chip
                val textColor = if (isSelected) colors.ink else colors.muted
                val border = if (isSelected) BorderStroke(1.dp, colors.line) else null

                Surface(
                    shape = itemShape,
                    color = containerColor,
                    border = border,
                    modifier = Modifier
                        .weight(1f)
                        .defaultMinSize(minHeight = AFChipTokens.visualHeight)
                        .clip(itemShape)
                        .clickable { onSelect(index) }
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .defaultMinSize(minHeight = AFChipTokens.visualHeight)
                            .padding(horizontal = AFChipTokens.horizontalPadding, vertical = 2.dp)
                    ) {
                        Text(
                            text = title,
                            style = AutoFlowTheme.typography.labelLarge.copy(
                                fontSize = AFChipTokens.fontSize,
                                lineHeight = AFChipTokens.lineHeight
                            ),
                            color = textColor
                        )
                    }
                }
            }
        }
    }
}

@Preview(name = "Light - AFSegmented")
@Composable
private fun AFSegmentedLightPreview() {
    AutoFlowTheme(darkTheme = false) {
        Surface(color = AutoFlowTheme.colors.bg) {
            Column(modifier = Modifier.padding(AppSpacing.md)) {
                AFSegmented(
                    items = listOf("Daily", "Weekly", "Monthly"),
                    selectedIndex = 0,
                    onSelect = {}
                )
            }
        }
    }
}

@Preview(name = "Dark - AFSegmented", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun AFSegmentedDarkPreview() {
    AutoFlowTheme(darkTheme = true) {
        Surface(color = AutoFlowTheme.colors.bg) {
            Column(modifier = Modifier.padding(AppSpacing.md)) {
                AFSegmented(
                    items = listOf("Daily", "Weekly", "Monthly"),
                    selectedIndex = 1,
                    onSelect = {}
                )
            }
        }
    }
}

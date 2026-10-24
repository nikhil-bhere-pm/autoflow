package com.autoflow.app.presentation.ui.components

import android.content.res.Configuration
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.autoflow.app.presentation.theme.AppSpacing
import com.autoflow.app.presentation.theme.AutoFlowTheme

data class AFBottomNavItem(
    val title: String,
    val icon: @Composable (selected: Boolean) -> Unit
)

@Composable
fun AFBottomNav(
    items: List<AFBottomNavItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    centerButton: (@Composable () -> Unit)? = null,
    windowInsets: WindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal)
) {
    val colors = AutoFlowTheme.colors

    Surface(
        color = colors.sheet,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(windowInsets)
        ) {
            HorizontalDivider(thickness = 1.dp, color = colors.line)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 64.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (centerButton == null) {
                    items.forEachIndexed { index, item ->
                        val isSelected = index == selectedIndex
                        val itemColor = if (isSelected) colors.brand else colors.muted

                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .defaultMinSize(minHeight = 56.dp)
                                .clickable { onSelect(index) }
                                .padding(vertical = AppSpacing.xs),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            item.icon(isSelected)
                            Text(
                                text = item.title,
                                style = AutoFlowTheme.typography.labelSmall,
                                color = itemColor,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                    }
                } else {
                    if (items.size == 1) {
                        // Single tab on left, centerButton in middle, spacer on right for balance
                        val item = items[0]
                        val isSelected = selectedIndex == 0
                        val itemColor = if (isSelected) colors.brand else colors.muted

                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .defaultMinSize(minHeight = 56.dp)
                                .clickable { onSelect(0) }
                                .padding(vertical = AppSpacing.xs),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            item.icon(isSelected)
                            Text(
                                text = item.title,
                                style = AutoFlowTheme.typography.labelSmall,
                                color = itemColor,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }

                        Box(
                            modifier = Modifier.weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            centerButton()
                        }

                        Spacer(modifier = Modifier.weight(1f))
                    } else {
                        val half = items.size / 2
                        val leftItems = items.take(half)
                        val rightItems = items.drop(half)

                        leftItems.forEachIndexed { idx, item ->
                            val isSelected = idx == selectedIndex
                            val itemColor = if (isSelected) colors.brand else colors.muted
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .defaultMinSize(minHeight = 56.dp)
                                    .clickable { onSelect(idx) }
                                    .padding(vertical = AppSpacing.xs),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                item.icon(isSelected)
                                Text(
                                    text = item.title,
                                    style = AutoFlowTheme.typography.labelSmall,
                                    color = itemColor,
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                            }
                        }

                        Box(
                            modifier = Modifier.weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            centerButton()
                        }

                        rightItems.forEachIndexed { idx, item ->
                            val actualIdx = half + idx
                            val isSelected = actualIdx == selectedIndex
                            val itemColor = if (isSelected) colors.brand else colors.muted
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .defaultMinSize(minHeight = 56.dp)
                                    .clickable { onSelect(actualIdx) }
                                    .padding(vertical = AppSpacing.xs),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                item.icon(isSelected)
                                Text(
                                    text = item.title,
                                    style = AutoFlowTheme.typography.labelSmall,
                                    color = itemColor,
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Preview(name = "Light - AFBottomNav")
@Composable
private fun AFBottomNavLightPreview() {
    AutoFlowTheme(darkTheme = false) {
        val items = listOf(
            AFBottomNavItem("Home") { sel -> Text("⌂", color = if (sel) AutoFlowTheme.colors.brand else AutoFlowTheme.colors.muted) },
            AFBottomNavItem("Stats") { sel -> Text("📊", color = if (sel) AutoFlowTheme.colors.brand else AutoFlowTheme.colors.muted) },
            AFBottomNavItem("Settings") { sel -> Text("⚙", color = if (sel) AutoFlowTheme.colors.brand else AutoFlowTheme.colors.muted) }
        )
        AFBottomNav(items = items, selectedIndex = 0, onSelect = {})
    }
}

@Preview(name = "Dark - AFBottomNav", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun AFBottomNavDarkPreview() {
    AutoFlowTheme(darkTheme = true) {
        val items = listOf(
            AFBottomNavItem("Home") { sel -> Text("⌂", color = if (sel) AutoFlowTheme.colors.brand else AutoFlowTheme.colors.muted) },
            AFBottomNavItem("Stats") { sel -> Text("📊", color = if (sel) AutoFlowTheme.colors.brand else AutoFlowTheme.colors.muted) },
            AFBottomNavItem("Settings") { sel -> Text("⚙", color = if (sel) AutoFlowTheme.colors.brand else AutoFlowTheme.colors.muted) }
        )
        AFBottomNav(items = items, selectedIndex = 1, onSelect = {})
    }
}

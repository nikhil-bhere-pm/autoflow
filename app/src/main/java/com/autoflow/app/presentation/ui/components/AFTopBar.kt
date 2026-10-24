package com.autoflow.app.presentation.ui.components

import android.content.res.Configuration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.autoflow.app.presentation.theme.AppSpacing
import com.autoflow.app.presentation.theme.AutoFlowTheme

@Composable
fun AFTopBar(
    title: String,
    modifier: Modifier = Modifier,
    navigationIcon: (@Composable () -> Unit)? = null,
    actions: (@Composable RowScope.() -> Unit)? = null,
    showDivider: Boolean = true,
    windowInsets: WindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
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
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 56.dp)
                    .padding(horizontal = AppSpacing.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (navigationIcon != null) {
                    Box(
                        modifier = Modifier.defaultMinSize(minWidth = AppSpacing.minTapTarget, minHeight = AppSpacing.minTapTarget),
                        contentAlignment = Alignment.Center
                    ) {
                        navigationIcon()
                    }
                    Spacer(modifier = Modifier.width(AppSpacing.xs))
                } else {
                    Spacer(modifier = Modifier.width(AppSpacing.sm))
                }

                Text(
                    text = title,
                    style = AutoFlowTheme.typography.titleLarge,
                    color = colors.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                if (actions != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        actions()
                    }
                }
            }

            if (showDivider) {
                HorizontalDivider(thickness = 1.dp, color = colors.line)
            }
        }
    }
}

@Preview(name = "Light - AFTopBar")
@Composable
private fun AFTopBarLightPreview() {
    AutoFlowTheme(darkTheme = false) {
        AFTopBar(
            title = "Transactions",
            navigationIcon = { Text("←", style = AutoFlowTheme.typography.titleLarge, color = AutoFlowTheme.colors.ink) },
            actions = {
                Box(
                    modifier = Modifier.defaultMinSize(minWidth = AppSpacing.minTapTarget, minHeight = AppSpacing.minTapTarget),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Filter", style = AutoFlowTheme.typography.labelLarge, color = AutoFlowTheme.colors.brand)
                }
            }
        )
    }
}

@Preview(name = "Dark - AFTopBar", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun AFTopBarDarkPreview() {
    AutoFlowTheme(darkTheme = true) {
        AFTopBar(
            title = "Transactions",
            navigationIcon = { Text("←", style = AutoFlowTheme.typography.titleLarge, color = AutoFlowTheme.colors.ink) },
            actions = {
                Box(
                    modifier = Modifier.defaultMinSize(minWidth = AppSpacing.minTapTarget, minHeight = AppSpacing.minTapTarget),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Filter", style = AutoFlowTheme.typography.labelLarge, color = AutoFlowTheme.colors.brand)
                }
            }
        )
    }
}

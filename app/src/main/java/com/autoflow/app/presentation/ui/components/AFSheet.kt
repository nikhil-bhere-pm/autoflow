package com.autoflow.app.presentation.ui.components

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import com.autoflow.app.presentation.theme.AppRadius
import com.autoflow.app.presentation.theme.AppSpacing
import com.autoflow.app.presentation.theme.AutoFlowTheme

@Composable
fun AFSheet(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = AutoFlowTheme.colors
    val sheetShape = RoundedCornerShape(
        topStart = AppRadius.xl,
        topEnd = AppRadius.xl,
        bottomStart = 0.dp,
        bottomEnd = 0.dp
    )

    Surface(
        shape = sheetShape,
        color = colors.sheet,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = AppSpacing.lg)
        ) {
            // Drag handle
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = AppSpacing.sm),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .width(36.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(AppRadius.full))
                        .background(colors.line)
                )
            }

            content()
        }
    }
}

@Preview(name = "Light - AFSheet")
@Composable
private fun AFSheetLightPreview() {
    AutoFlowTheme(darkTheme = false) {
        Surface(color = AutoFlowTheme.colors.bg) {
            AFSheet(onDismiss = {}) {
                Column(modifier = Modifier.padding(AppSpacing.md)) {
                    Text(
                        text = "Sheet Title",
                        style = AutoFlowTheme.typography.titleLarge,
                        color = AutoFlowTheme.colors.ink
                    )
                    Spacer(modifier = Modifier.height(AppSpacing.sm))
                    Text(
                        text = "This is a sheet content example with standard padding and styling.",
                        style = AutoFlowTheme.typography.bodyMedium,
                        color = AutoFlowTheme.colors.muted
                    )
                    Spacer(modifier = Modifier.height(AppSpacing.md))
                    AFButton(text = "Confirm", onClick = {}, modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
}

@Preview(name = "Dark - AFSheet", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun AFSheetDarkPreview() {
    AutoFlowTheme(darkTheme = true) {
        Surface(color = AutoFlowTheme.colors.bg) {
            AFSheet(onDismiss = {}) {
                Column(modifier = Modifier.padding(AppSpacing.md)) {
                    Text(
                        text = "Sheet Title",
                        style = AutoFlowTheme.typography.titleLarge,
                        color = AutoFlowTheme.colors.ink
                    )
                    Spacer(modifier = Modifier.height(AppSpacing.sm))
                    Text(
                        text = "This is a sheet content example with standard padding and styling.",
                        style = AutoFlowTheme.typography.bodyMedium,
                        color = AutoFlowTheme.colors.muted
                    )
                    Spacer(modifier = Modifier.height(AppSpacing.md))
                    AFButton(text = "Confirm", onClick = {}, modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
}

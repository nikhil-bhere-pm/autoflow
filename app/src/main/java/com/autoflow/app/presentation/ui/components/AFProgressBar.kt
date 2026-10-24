package com.autoflow.app.presentation.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.autoflow.app.presentation.theme.AppRadius
import com.autoflow.app.presentation.theme.AutoFlowTheme

@Composable
fun AFProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    height: Dp = 8.dp
) {
    val colors = AutoFlowTheme.colors
    val clampedProgress = progress.coerceIn(0f, 1f)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(AppRadius.full))
            .background(colors.track)
    ) {
        if (clampedProgress > 0f) {
            val barColor = colors.bar
            Box(
                modifier = Modifier
                    .fillMaxWidth(clampedProgress)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(AppRadius.full))
                    .background(barColor)
            )
        }
    }
}

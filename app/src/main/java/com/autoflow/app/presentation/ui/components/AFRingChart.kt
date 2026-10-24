package com.autoflow.app.presentation.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.autoflow.app.presentation.theme.AppSpacing
import com.autoflow.app.presentation.theme.AutoFlowTheme
import com.autoflow.app.presentation.util.CurrencyFormatter
import com.autoflow.app.presentation.viewmodel.CategorySpend

@Composable
fun AFRingChart(
    categories: List<CategorySpend>,
    totalSpendPaise: Long,
    topCategory: CategorySpend?,
    modifier: Modifier = Modifier,
    chartSize: Dp = 190.dp,
    strokeWidth: Dp = 20.dp
) {
    val colors = AutoFlowTheme.colors
    val accessibilityLabel = if (topCategory != null) {
        "Category spend ring chart. Top category: ${topCategory.category}, ${topCategory.percent}% of total."
    } else {
        "Category spend ring chart."
    }

    Box(
        modifier = modifier
            .size(chartSize)
            .semantics { contentDescription = accessibilityLabel },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(chartSize)) {
            val strokePx = strokeWidth.toPx()
            val diameter = size.minDimension - strokePx
            val radius = diameter / 2f
            val centerOffset = androidx.compose.ui.geometry.Offset(size.width / 2f, size.height / 2f)

            // Draw base track
            drawCircle(
                color = colors.track,
                radius = radius,
                center = centerOffset,
                style = Stroke(width = strokePx)
            )

            if (totalSpendPaise > 0L) {
                var currentAngle = -90f // Start from top
                val totalFloat = totalSpendPaise.toFloat()

                for (cat in categories) {
                    val sweepAngle = (cat.totalPaise.toFloat() / totalFloat) * 360f
                    if (sweepAngle <= 0f) continue

                    val sliceColor = colors.forCategory(cat.category)

                    // Draw arc slice
                    drawArc(
                        color = sliceColor,
                        startAngle = currentAngle,
                        sweepAngle = sweepAngle,
                        useCenter = false,
                        topLeft = androidx.compose.ui.geometry.Offset(
                            centerOffset.x - radius,
                            centerOffset.y - radius
                        ),
                        size = androidx.compose.ui.geometry.Size(diameter, diameter),
                        style = Stroke(width = strokePx, cap = StrokeCap.Butt)
                    )

                    currentAngle += sweepAngle
                }
            }
        }

        // Center content: Top category name, amount, and % of total
        if (topCategory != null) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = topCategory.category,
                    style = AutoFlowTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = colors.ink,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(AppSpacing.xxs))
                Text(
                    text = CurrencyFormatter.formatPaiseToInr(topCategory.totalPaise),
                    style = AutoFlowTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = colors.ink,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(AppSpacing.xxs))
                Text(
                    text = "${topCategory.percent}% of total",
                    style = AutoFlowTheme.typography.labelSmall,
                    color = colors.muted,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

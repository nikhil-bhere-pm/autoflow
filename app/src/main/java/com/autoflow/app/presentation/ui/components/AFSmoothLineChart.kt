package com.autoflow.app.presentation.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.autoflow.app.domain.stats.MonthPoint
import com.autoflow.app.presentation.theme.AutoFlowTheme
import com.autoflow.app.presentation.viewmodel.PeriodFilter
import java.time.YearMonth
import java.util.Locale

@Composable
fun AFSmoothLineChart(
    monthlySeries: List<MonthPoint>,
    periodFilter: PeriodFilter,
    accessibilityDescription: String,
    onSelectMonth: (YearMonth) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = AutoFlowTheme.colors

    val selectedMonth = when (periodFilter) {
        is PeriodFilter.SpecificMonth -> periodFilter.month
        is PeriodFilter.Predefined -> {
            val nonFuture = monthlySeries.filter { !it.isFuture }
            nonFuture.lastOrNull()?.month ?: YearMonth.now()
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = accessibilityDescription }
    ) {
        // Chart drawing area: height 88.dp
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(88.dp)
        ) {
            val canvasWidth = size.width
            val canvasHeight = size.height

            val dotRadiusPx = 7.dp.toPx()
            val ringWidthPx = 3.dp.toPx()
            val strokeWidthPx = 2.75.dp.toPx()

            // Padding so the dot and stroke are never clipped at the edges
            val topPadding = dotRadiusPx + ringWidthPx + strokeWidthPx
            val bottomPadding = dotRadiusPx + ringWidthPx + strokeWidthPx

            val geometry = computeChartGeometry(
                monthlySeries = monthlySeries,
                selectedMonth = selectedMonth,
                width = canvasWidth,
                height = canvasHeight,
                paddingTop = topPadding,
                paddingBottom = bottomPadding
            )

            if (geometry.nonFuturePoints.isNotEmpty()) {
                val linePath = Path()
                val firstPt = geometry.nonFuturePoints.first()
                linePath.moveTo(firstPt.x, firstPt.y)

                for (seg in geometry.segments) {
                    linePath.cubicTo(
                        seg.control1X, seg.control1Y,
                        seg.control2X, seg.control2Y,
                        seg.end.x, seg.end.y
                    )
                }

                // If at least 2 points exist, draw gradient fill under line
                if (geometry.nonFuturePoints.size >= 2) {
                    val lastPt = geometry.nonFuturePoints.last()
                    val fillPath = Path()
                    fillPath.addPath(linePath)
                    fillPath.lineTo(lastPt.x, canvasHeight)
                    fillPath.lineTo(firstPt.x, canvasHeight)
                    fillPath.close()

                    val highestY = geometry.nonFuturePoints.minOf { it.y }
                    val gradientBrush = Brush.verticalGradient(
                        colors = listOf(
                            colors.bar.copy(alpha = 0.28f),
                            colors.bar.copy(alpha = 0.0f)
                        ),
                        startY = highestY,
                        endY = canvasHeight
                    )

                    drawPath(
                        path = fillPath,
                        brush = gradientBrush
                    )

                    // Draw smooth curved line with 2.75dp stroke, round caps, brand color
                    drawPath(
                        path = linePath,
                        color = colors.brand,
                        style = Stroke(
                            width = strokeWidthPx,
                            cap = StrokeCap.Round
                        )
                    )
                }

                // Dotted muted line continuing at current level across future months, ending before right edge
                if (monthlySeries.any { it.isFuture }) {
                    val lastPt = geometry.nonFuturePoints.lastOrNull()
                    if (lastPt != null) {
                        val endX = canvasWidth - 16.dp.toPx()
                        if (endX > lastPt.x) {
                            val dottedPath = Path().apply {
                                moveTo(lastPt.x, lastPt.y)
                                lineTo(endX, lastPt.y)
                            }
                            drawPath(
                                path = dottedPath,
                                color = colors.muted.copy(alpha = 0.45f),
                                style = Stroke(
                                    width = 1.75.dp.toPx(),
                                    cap = StrokeCap.Round,
                                    pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(
                                        floatArrayOf(6.dp.toPx(), 6.dp.toPx()),
                                        0f
                                    )
                                )
                            )
                        }
                    }
                }

                // Dot on selected month: filled with sheet color, brand-color ring ~3dp, radius ~7dp
                geometry.selectedDot?.let { dot ->
                    val center = Offset(dot.x, dot.y)
                    drawCircle(
                        color = colors.sheet,
                        radius = dotRadiusPx,
                        center = center
                    )
                    drawCircle(
                        color = colors.brand,
                        radius = dotRadiusPx,
                        center = center,
                        style = Stroke(width = ringWidthPx)
                    )
                }
            }
        }

        // Month labels row under the chart, five equal columns, each 36dp high
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            monthlySeries.forEach { point ->
                val isSelected = when (periodFilter) {
                    is PeriodFilter.SpecificMonth -> periodFilter.month == point.month
                    is PeriodFilter.Predefined -> point.month == selectedMonth
                }

                val monthAbbr = point.month.month.name.take(3).lowercase(Locale.ROOT)
                    .replaceFirstChar { it.uppercase(Locale.ROOT) }

                val labelColor = when {
                    point.isFuture -> colors.muted.copy(alpha = 0.38f)
                    isSelected -> colors.ink
                    else -> colors.muted
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .height(36.dp)
                        .clickable(
                            enabled = !point.isFuture,
                            onClick = { onSelectMonth(point.month) }
                        ),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = monthAbbr,
                        style = AutoFlowTheme.typography.labelMedium,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = labelColor
                    )
                }
            }
        }
    }
}

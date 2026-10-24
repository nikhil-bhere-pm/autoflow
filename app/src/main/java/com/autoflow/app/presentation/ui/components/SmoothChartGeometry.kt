package com.autoflow.app.presentation.ui.components

import com.autoflow.app.domain.stats.MonthPoint
import java.time.YearMonth

data class ChartPoint(
    val index: Int,
    val month: YearMonth,
    val x: Float,
    val y: Float,
    val isFuture: Boolean,
    val totalPaise: Long
)

data class CubicBezierSegment(
    val start: ChartPoint,
    val control1X: Float,
    val control1Y: Float,
    val control2X: Float,
    val control2Y: Float,
    val end: ChartPoint
)

data class SmoothChartGeometry(
    val allPoints: List<ChartPoint>,
    val nonFuturePoints: List<ChartPoint>,
    val segments: List<CubicBezierSegment>,
    val selectedDot: ChartPoint?
)

/**
 * Pure geometry function computing:
 * - 5 column-centered points
 * - Catmull-Rom spline converted to cubic Bezier segments
 * - Scaling with dot-safe padding
 * - Flat line handling for equal/zero data
 * - Future month truncation (the line terminates at the latest non-future month)
 */
fun computeChartGeometry(
    monthlySeries: List<MonthPoint>,
    selectedMonth: YearMonth,
    width: Float,
    height: Float,
    paddingTop: Float = 16f,
    paddingBottom: Float = 16f
): SmoothChartGeometry {
    if (monthlySeries.isEmpty()) {
        return SmoothChartGeometry(emptyList(), emptyList(), emptyList(), null)
    }

    val nonFutureSeries = monthlySeries.filter { !it.isFuture }
    val isFlat = nonFutureSeries.isEmpty() || nonFutureSeries.all { it.totalPaise == nonFutureSeries[0].totalPaise }
    val midY = (paddingTop + (height - paddingBottom)) / 2f
    val usableHeight = (height - paddingBottom) - paddingTop

    val minPaise = if (nonFutureSeries.isNotEmpty()) nonFutureSeries.minOf { it.totalPaise } else 0L
    val maxPaise = if (nonFutureSeries.isNotEmpty()) nonFutureSeries.maxOf { it.totalPaise } else 0L

    val colWidth = if (monthlySeries.isNotEmpty()) width / monthlySeries.size.toFloat() else width

    val allPoints = monthlySeries.mapIndexed { index, item ->
        val x = (index + 0.5f) * colWidth
        val y = if (isFlat || item.isFuture) {
            if (isFlat) midY else (height - paddingBottom)
        } else {
            val frac = (item.totalPaise - minPaise).toFloat() / (maxPaise - minPaise).toFloat()
            (height - paddingBottom) - (frac * usableHeight)
        }
        ChartPoint(
            index = index,
            month = item.month,
            x = x,
            y = y,
            isFuture = item.isFuture,
            totalPaise = item.totalPaise
        )
    }

    val nonFuturePoints = allPoints.filter { !it.isFuture }

    val segments = mutableListOf<CubicBezierSegment>()
    if (nonFuturePoints.size >= 2) {
        for (i in 0 until nonFuturePoints.size - 1) {
            val p0 = nonFuturePoints[i]
            val p1 = nonFuturePoints[i + 1]

            val pPrevX = if (i > 0) nonFuturePoints[i - 1].x else (2 * p0.x - p1.x)
            val pPrevY = if (i > 0) nonFuturePoints[i - 1].y else (2 * p0.y - p1.y)

            val pNextX = if (i + 2 < nonFuturePoints.size) nonFuturePoints[i + 2].x else (2 * p1.x - p0.x)
            val pNextY = if (i + 2 < nonFuturePoints.size) nonFuturePoints[i + 2].y else (2 * p1.y - p0.y)

            val c1x = p0.x + (p1.x - pPrevX) / 6f
            val c1y = (p0.y + (p1.y - pPrevY) / 6f).coerceIn(paddingTop, height - paddingBottom)

            val c2x = p1.x - (pNextX - p0.x) / 6f
            val c2y = (p1.y - (pNextY - p0.y) / 6f).coerceIn(paddingTop, height - paddingBottom)

            segments.add(
                CubicBezierSegment(
                    start = p0,
                    control1X = c1x,
                    control1Y = c1y,
                    control2X = c2x,
                    control2Y = c2y,
                    end = p1
                )
            )
        }
    }

    val selectedDot = nonFuturePoints.find { it.month == selectedMonth } ?: nonFuturePoints.lastOrNull()

    return SmoothChartGeometry(
        allPoints = allPoints,
        nonFuturePoints = nonFuturePoints,
        segments = segments,
        selectedDot = selectedDot
    )
}

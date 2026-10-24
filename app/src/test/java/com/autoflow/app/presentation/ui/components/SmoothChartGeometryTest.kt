package com.autoflow.app.presentation.ui.components

import com.autoflow.app.domain.stats.MonthPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.YearMonth

class SmoothChartGeometryTest {

    private val width = 500f
    private val height = 200f
    private val paddingTop = 16f
    private val paddingBottom = 16f

    @Test
    fun testPointsLandInOrderAndInsideBounds() {
        val series = listOf(
            MonthPoint(YearMonth.of(2026, 6), 10000L, false),
            MonthPoint(YearMonth.of(2026, 7), 50000L, false),
            MonthPoint(YearMonth.of(2026, 8), 30000L, false),
            MonthPoint(YearMonth.of(2026, 9), 80000L, false),
            MonthPoint(YearMonth.of(2026, 10), 20000L, false)
        )

        val geometry = computeChartGeometry(
            monthlySeries = series,
            selectedMonth = YearMonth.of(2026, 8),
            width = width,
            height = height,
            paddingTop = paddingTop,
            paddingBottom = paddingBottom
        )

        assertEquals(5, geometry.allPoints.size)

        // Points must land strictly in order from left to right
        for (i in 0 until 4) {
            assertTrue(
                "X coordinates must be strictly increasing",
                geometry.allPoints[i].x < geometry.allPoints[i + 1].x
            )
        }

        // Points must land strictly inside the bounds [0, width] and [paddingTop, height - paddingBottom]
        for (pt in geometry.allPoints) {
            assertTrue("Point X out of bounds", pt.x in 0f..width)
            assertTrue("Point Y out of bounds", pt.y in paddingTop..(height - paddingBottom))
        }

        // Segments must exist between all adjacent non-future points
        assertEquals(4, geometry.segments.size)
    }

    @Test
    fun testFlatDataGivesFlatLine() {
        val series = listOf(
            MonthPoint(YearMonth.of(2026, 6), 0L, false),
            MonthPoint(YearMonth.of(2026, 7), 0L, false),
            MonthPoint(YearMonth.of(2026, 8), 0L, false),
            MonthPoint(YearMonth.of(2026, 9), 0L, false),
            MonthPoint(YearMonth.of(2026, 10), 0L, false)
        )

        val geometry = computeChartGeometry(
            monthlySeries = series,
            selectedMonth = YearMonth.of(2026, 8),
            width = width,
            height = height,
            paddingTop = paddingTop,
            paddingBottom = paddingBottom
        )

        val expectedMidY = (paddingTop + (height - paddingBottom)) / 2f
        assertEquals(100f, expectedMidY, 0.001f)

        for (pt in geometry.allPoints) {
            assertEquals("Y must be in middle for flat data", expectedMidY, pt.y, 0.001f)
        }

        for (seg in geometry.segments) {
            assertEquals(expectedMidY, seg.start.y, 0.001f)
            assertEquals(expectedMidY, seg.control1Y, 0.001f)
            assertEquals(expectedMidY, seg.control2Y, 0.001f)
            assertEquals(expectedMidY, seg.end.y, 0.001f)
        }
    }

    @Test
    fun testLineStopsBeforeFutureMonths() {
        // Oct 2026 is current. Nov 2026 and Dec 2026 are future.
        val series = listOf(
            MonthPoint(YearMonth.of(2026, 8), 20000L, false),
            MonthPoint(YearMonth.of(2026, 9), 50000L, false),
            MonthPoint(YearMonth.of(2026, 10), 30000L, false),
            MonthPoint(YearMonth.of(2026, 11), 0L, true),
            MonthPoint(YearMonth.of(2026, 12), 0L, true)
        )

        val geometry = computeChartGeometry(
            monthlySeries = series,
            selectedMonth = YearMonth.of(2026, 10),
            width = width,
            height = height,
            paddingTop = paddingTop,
            paddingBottom = paddingBottom
        )

        // Line must only contain non-future months (3 points)
        assertEquals(3, geometry.nonFuturePoints.size)
        // 3 points -> 2 segments
        assertEquals(2, geometry.segments.size)

        // Last segment must end at the last non-future month (Oct 2026, index 2)
        val lastSegment = geometry.segments.last()
        assertEquals(YearMonth.of(2026, 10), lastSegment.end.month)
        assertEquals(2, lastSegment.end.index)
    }

    @Test
    fun testHighestMonthIsNotClipped() {
        val series = listOf(
            MonthPoint(YearMonth.of(2026, 8), 10000L, false),
            MonthPoint(YearMonth.of(2026, 9), 20000L, false),
            MonthPoint(YearMonth.of(2026, 10), 90000L, false), // Highest
            MonthPoint(YearMonth.of(2026, 11), 0L, true),
            MonthPoint(YearMonth.of(2026, 12), 0L, true)
        )

        val geometry = computeChartGeometry(
            monthlySeries = series,
            selectedMonth = YearMonth.of(2026, 10),
            width = width,
            height = height,
            paddingTop = paddingTop,
            paddingBottom = paddingBottom
        )

        val highestPoint = geometry.allPoints[2]
        assertEquals(paddingTop, highestPoint.y, 0.001f)

        // Dot radius is 7dp, ring is 3dp; total extent above center is 10dp.
        // With paddingTop = 16f, highestPoint.y - 10f = 6f > 0, so the dot is never clipped.
        val dotExtentTop = highestPoint.y - 10f
        assertTrue("Highest dot must have clearance from top of canvas", dotExtentTop >= 0f)
    }

    @Test
    fun testSelectedDotPositions() {
        val series = listOf(
            MonthPoint(YearMonth.of(2026, 8), 20000L, false),
            MonthPoint(YearMonth.of(2026, 9), 50000L, false),
            MonthPoint(YearMonth.of(2026, 10), 30000L, false),
            MonthPoint(YearMonth.of(2026, 11), 0L, true),
            MonthPoint(YearMonth.of(2026, 12), 0L, true)
        )

        val geometry = computeChartGeometry(
            monthlySeries = series,
            selectedMonth = YearMonth.of(2026, 9),
            width = width,
            height = height,
            paddingTop = paddingTop,
            paddingBottom = paddingBottom
        )

        assertNotNull(geometry.selectedDot)
        assertEquals(YearMonth.of(2026, 9), geometry.selectedDot?.month)
        assertEquals(1, geometry.selectedDot?.index)
    }
}

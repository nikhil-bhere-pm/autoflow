package com.autoflow.app.presentation.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.round

class ThemeContrastTest {

    data class ContrastResult(
        val pair: String,
        val theme: String,
        val ratio: Double,
        val passesTextThreshold: Boolean
    )

    private fun channelToLinear(channel: Double): Double {
        return if (channel <= 0.04045) {
            channel / 12.92
        } else {
            ((channel + 0.055) / 1.055).pow(2.4)
        }
    }

    private fun relativeLuminance(hex: String): Double {
        val clean = hex.removePrefix("#")
        val r = clean.substring(0, 2).toInt(16) / 255.0
        val g = clean.substring(2, 4).toInt(16) / 255.0
        val b = clean.substring(4, 6).toInt(16) / 255.0
        return 0.2126 * channelToLinear(r) + 0.7152 * channelToLinear(g) + 0.0722 * channelToLinear(b)
    }

    private fun contrastRatio(hex1: String, hex2: String): Double {
        val l1 = relativeLuminance(hex1)
        val l2 = relativeLuminance(hex2)
        val lighter = max(l1, l2)
        val darker = min(l1, l2)
        val raw = (lighter + 0.05) / (darker + 0.05)
        return round(raw * 100.0) / 100.0
    }

    private fun blendHex(fgHex: String, bgHex: String, alpha: Double = 0.11): String {
        val cleanFg = fgHex.removePrefix("#")
        val cleanBg = bgHex.removePrefix("#")
        val r1 = cleanFg.substring(0, 2).toInt(16)
        val g1 = cleanFg.substring(2, 4).toInt(16)
        val b1 = cleanFg.substring(4, 6).toInt(16)
        val r2 = cleanBg.substring(0, 2).toInt(16)
        val g2 = cleanBg.substring(2, 4).toInt(16)
        val b2 = cleanBg.substring(4, 6).toInt(16)
        val r = (r1 * alpha + r2 * (1 - alpha)).toInt().coerceIn(0, 255)
        val g = (g1 * alpha + g2 * (1 - alpha)).toInt().coerceIn(0, 255)
        val b = (b1 * alpha + b2 * (1 - alpha)).toInt().coerceIn(0, 255)
        return String.format("#%02X%02X%02X", r, g, b)
    }

    @Test
    fun testWcagContrastRatios_andReport() {
        val results = mutableListOf<ContrastResult>()

        // LIGHT THEME
        val lightBg = "#F9FAFB"
        val lightSheet = "#FFFFFF"
        val lightInk = "#0F172A"
        val lightMuted = "#64748B"
        val lightButton = "#047857"
        val lightButtonText = "#FFFFFF"
        val lightTint = "#D1FAE5"
        val lightOnTint = "#065F46"
        val lightAmberBg = "#FEF3C7"
        val lightAmberFg = "#78350F"
        val lightRed = "#B91C1C"

        val lightCategories = listOf(
            "Food" to "#B83A0B",
            "Groceries" to "#047857",
            "Transport" to "#1D4ED8",
            "Shopping" to "#BE185D",
            "Bills" to "#8F5B05",
            "Entertainment" to "#6D28D9",
            "Health" to "#B91C1C",
            "Travel" to "#0F766E",
            "Others" to "#475569",
            "Unknown" to "#475569"
        )

        // DARK THEME
        val darkBg = "#0B1412"
        val darkSheet = "#12201C"
        val darkInk = "#F1F5F9"
        val darkMuted = "#94A3B8"
        val darkButton = "#34D399"
        val darkButtonText = "#052E22"
        val darkTint = "#0F3D2E"
        val darkOnTint = "#A7F3D0"
        val darkAmberBg = "#3A2E0A"
        val darkAmberFg = "#FDE68A"
        val darkRed = "#FCA5A5"

        val darkCategories = listOf(
            "Food" to "#FDBA74",
            "Groceries" to "#6EE7B7",
            "Transport" to "#93C5FD",
            "Shopping" to "#F9A8D4",
            "Bills" to "#FDE047",
            "Entertainment" to "#C4B5FD",
            "Health" to "#FCA5A5",
            "Travel" to "#5EEAD4",
            "Others" to "#CBD5E1",
            "Unknown" to "#CBD5E1"
        )

        fun checkPair(theme: String, name: String, fg: String, bg: String) {
            val ratio = contrastRatio(fg, bg)
            val pass = ratio >= 4.5
            results.add(ContrastResult(name, theme, ratio, pass))
        }

        // Test Light Theme Core Pairs
        checkPair("Light", "ink on bg", lightInk, lightBg)
        checkPair("Light", "ink on sheet", lightInk, lightSheet)
        checkPair("Light", "muted on bg", lightMuted, lightBg)
        checkPair("Light", "muted on sheet", lightMuted, lightSheet)
        checkPair("Light", "buttonText on button", lightButtonText, lightButton)
        checkPair("Light", "onTint on tint", lightOnTint, lightTint)
        checkPair("Light", "amberFg on amberBg", lightAmberFg, lightAmberBg)
        checkPair("Light", "red on sheet", lightRed, lightSheet)

        // Test Light Categories over 11% tile on sheet
        for ((cat, hex) in lightCategories) {
            val tileBg = blendHex(hex, lightSheet, 0.11)
            checkPair("Light", "$cat on 11% tile", hex, tileBg)
        }

        // Test Dark Theme Core Pairs
        checkPair("Dark", "ink on bg", darkInk, darkBg)
        checkPair("Dark", "ink on sheet", darkInk, darkSheet)
        checkPair("Dark", "muted on bg", darkMuted, darkBg)
        checkPair("Dark", "muted on sheet", darkMuted, darkSheet)
        checkPair("Dark", "buttonText on button", darkButtonText, darkButton)
        checkPair("Dark", "onTint on tint", darkOnTint, darkTint)
        checkPair("Dark", "amberFg on amberBg", darkAmberFg, darkAmberBg)
        checkPair("Dark", "red on sheet", darkRed, darkSheet)

        // Test Dark Categories over 11% tile on sheet
        for ((cat, hex) in darkCategories) {
            val tileBg = blendHex(hex, darkSheet, 0.11)
            checkPair("Dark", "$cat on 11% tile", hex, tileBg)
        }

        println("=========================================================================")
        println(String.format("%-28s | %-6s | %-8s | %-6s", "Pair", "Theme", "Ratio", "Status"))
        println("-------------------------------------------------------------------------")
        for (r in results) {
            val status = if (r.passesTextThreshold) "PASS" else "FAIL"
            println(String.format("%-28s | %-6s | %-8.2f | %-6s", r.pair, r.theme, r.ratio, status))
        }
        println("=========================================================================")

        // Assert ALL 36 pairs pass the 4.5:1 threshold with no exceptions allowed
        assertEquals(36, results.size)
        for (r in results) {
            assertTrue("Expected ${r.pair} in ${r.theme} to pass >= 4.5, was ${r.ratio}", r.passesTextThreshold)
        }
    }
}

package com.autoflow.app.presentation.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CurrencyFormatterTest {

    @Test
    fun testFormatPaiseToInr_requiredPromptExamples() {
        // 13000 paise -> ₹130.00
        assertEquals("₹130.00", CurrencyFormatter.formatPaiseToInr(13000L))

        // 12345600 paise -> ₹1,23,456.00
        assertEquals("₹1,23,456.00", CurrencyFormatter.formatPaiseToInr(12345600L))
    }

    @Test
    fun testFormatPaiseToInr_zeroAndSubRupee() {
        assertEquals("₹0.00", CurrencyFormatter.formatPaiseToInr(0L))
        assertEquals("₹0.50", CurrencyFormatter.formatPaiseToInr(50L))
        assertEquals("₹0.05", CurrencyFormatter.formatPaiseToInr(5L))
    }

    @Test
    fun testFormatPaiseToInr_largeIndianNumberingGrouping() {
        // ₹1,000.00 (100000 paise)
        assertEquals("₹1,000.00", CurrencyFormatter.formatPaiseToInr(100000L))

        // ₹10,000.00 (1000000 paise)
        assertEquals("₹10,000.00", CurrencyFormatter.formatPaiseToInr(1000000L))

        // ₹1,00,000.00 (1 lakh rupees = 10000000 paise)
        assertEquals("₹1,00,000.00", CurrencyFormatter.formatPaiseToInr(10000000L))

        // ₹10,00,000.00 (10 lakh rupees)
        assertEquals("₹10,00,000.00", CurrencyFormatter.formatPaiseToInr(100000000L))

        // ₹1,00,00,000.00 (1 crore rupees)
        assertEquals("₹1,00,00,000.00", CurrencyFormatter.formatPaiseToInr(1000000000L))
    }

    @Test
    fun testParseRupeesToPaise_validInputs() {
        assertEquals(13000L, CurrencyFormatter.parseRupeesToPaise("130"))
        assertEquals(13050L, CurrencyFormatter.parseRupeesToPaise("130.5"))
        assertEquals(13050L, CurrencyFormatter.parseRupeesToPaise("130.50"))
        assertEquals(12345600L, CurrencyFormatter.parseRupeesToPaise("1,23,456.00"))
        assertEquals(50L, CurrencyFormatter.parseRupeesToPaise("0.50"))
    }

    @Test
    fun testParseRupeesToPaise_invalidAndZeroInputs() {
        assertNull(CurrencyFormatter.parseRupeesToPaise(""))
        assertNull(CurrencyFormatter.parseRupeesToPaise("   "))
        assertNull(CurrencyFormatter.parseRupeesToPaise("0"))
        assertNull(CurrencyFormatter.parseRupeesToPaise("-50"))
        assertNull(CurrencyFormatter.parseRupeesToPaise("abc"))
        assertNull(CurrencyFormatter.parseRupeesToPaise("10.999")) // more than 2 decimals
    }
}

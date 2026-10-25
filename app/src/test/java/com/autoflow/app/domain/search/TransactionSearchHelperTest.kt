package com.autoflow.app.domain.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TransactionSearchHelperTest {

    @Test
    fun testParseRupeesToPaise_validInputs() {
        assertEquals(32000L, TransactionSearchHelper.parseRupeesToPaise("320"))
        assertEquals(32000L, TransactionSearchHelper.parseRupeesToPaise("320.00"))
        assertEquals(32050L, TransactionSearchHelper.parseRupeesToPaise("320.5"))
        assertEquals(32050L, TransactionSearchHelper.parseRupeesToPaise("320.50"))
        assertEquals(1575L, TransactionSearchHelper.parseRupeesToPaise("15.75"))
        assertEquals(100L, TransactionSearchHelper.parseRupeesToPaise("1.00"))
        assertEquals(0L, TransactionSearchHelper.parseRupeesToPaise("0"))
        assertEquals(0L, TransactionSearchHelper.parseRupeesToPaise("0.00"))
        assertEquals(32000L, TransactionSearchHelper.parseRupeesToPaise("₹320"))
        assertEquals(32000L, TransactionSearchHelper.parseRupeesToPaise("₹320.00"))
        assertEquals(50000L, TransactionSearchHelper.parseRupeesToPaise("Rs. 500"))
        assertEquals(50000L, TransactionSearchHelper.parseRupeesToPaise("rs 500"))
        assertEquals(32000L, TransactionSearchHelper.parseRupeesToPaise(" 320. "))
    }

    @Test
    fun testParseRupeesToPaise_invalidInputs() {
        assertNull(TransactionSearchHelper.parseRupeesToPaise(""))
        assertNull(TransactionSearchHelper.parseRupeesToPaise("   "))
        assertNull(TransactionSearchHelper.parseRupeesToPaise("abc"))
        assertNull(TransactionSearchHelper.parseRupeesToPaise("-50"))
        assertNull(TransactionSearchHelper.parseRupeesToPaise("10000000001")) // exceeds 10 crore rupees
    }

    @Test
    fun testEscapeLike() {
        assertEquals("normal text", TransactionSearchHelper.escapeLike("normal text"))
        assertEquals("50\\% discount", TransactionSearchHelper.escapeLike("50% discount"))
        assertEquals("item\\_name", TransactionSearchHelper.escapeLike("item_name"))
        assertEquals("path\\\\to\\\\file", TransactionSearchHelper.escapeLike("path\\to\\file"))
        assertEquals("100\\\\\\%\\_done", TransactionSearchHelper.escapeLike("100\\%_done"))
    }

    @Test
    fun testExtractDigitsPattern() {
        assertEquals("%320%", TransactionSearchHelper.extractDigitsPattern("320"))
        assertEquals("%32000%", TransactionSearchHelper.extractDigitsPattern("320.00"))
        assertEquals("%50%", TransactionSearchHelper.extractDigitsPattern("₹50"))
        assertNull(TransactionSearchHelper.extractDigitsPattern("swiggy"))
        assertNull(TransactionSearchHelper.extractDigitsPattern("50%"))
        assertNull(TransactionSearchHelper.extractDigitsPattern("Tea_"))
    }
}

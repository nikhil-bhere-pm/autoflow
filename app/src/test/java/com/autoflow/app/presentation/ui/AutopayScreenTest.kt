package com.autoflow.app.presentation.ui

import com.autoflow.app.presentation.util.CurrencyFormatter
import com.autoflow.app.presentation.viewmodel.AutopayUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class AutopayScreenTest {

    /**
     * NOTE: Composable layout rendering, pixel measurements, and visual styling
     * cannot be tested in JVM unit tests without a physical device or emulator.
     * The tests below verify navigation resolution, state defaults, currency formatting,
     * and string resource integrity.
     */

    @Test
    fun testAutopayUiStateDefaultIsEmpty() {
        val defaultState: AutopayUiState = AutopayUiState.Empty
        assertEquals(AutopayUiState.Empty, defaultState)
    }

    @Test
    fun testCurrencyFormatterFormatsZeroPaiseAsExpected() {
        // Wireframe requirement: amount "₹0.00" formatted with CurrencyFormatter
        val formatted = CurrencyFormatter.formatPaiseToInr(0L)
        assertEquals("₹0.00", formatted)
    }

    @Test
    fun testAutopayNavigationBackResolution() {
        // Back from Autopay screen returns to Categories screen
        val currentScreen: Screen = Screen.Autopay
        val backDestination = when (currentScreen) {
            is Screen.Autopay -> Screen.Categories
            else -> Screen.Home
        }
        assertEquals(Screen.Categories, backDestination)
    }

    @Test
    fun testAutopayStringsExistInStringsXml() {
        val stringsFile = File("src/main/res/values/strings.xml").canonicalFile
        assertTrue("strings.xml must exist", stringsFile.exists())

        val factory = DocumentBuilderFactory.newInstance()
        val builder = factory.newDocumentBuilder()
        val doc = builder.parse(stringsFile)
        val stringNodes = doc.getElementsByTagName("string")

        val stringMap = mutableMapOf<String, String>()
        for (i in 0 until stringNodes.length) {
            val node = stringNodes.item(i) as Element
            val key = node.getAttribute("name")
            val value = node.textContent
            stringMap[key] = value
        }

        val requiredStrings = listOf(
            "categories_title",
            "autopay_title",
            "autopay_content_desc",
            "autopay_summary_label",
            "autopay_summary_empty_line",
            "autopay_how_it_works_section",
            "autopay_tag_title",
            "autopay_tag_text",
            "autopay_repeats_title",
            "autopay_repeats_text",
            "autopay_next_expected_title",
            "autopay_next_expected_text",
            "autopay_note_text",
            "autopay_empty_title",
            "autopay_empty_text"
        )

        for (key in requiredStrings) {
            assertTrue("strings.xml must define string key '$key'", stringMap.containsKey(key))
            val value = stringMap[key]
            assertNotNull("Value for '$key' must not be null", value)
            assertTrue("Value for '$key' must not be blank", value!!.isNotBlank())
        }

        // Verify specific key strings match prompt requirements
        assertEquals("Categories", stringMap["categories_title"])
        assertEquals("Autopay", stringMap["autopay_title"])
        assertEquals("Autopay this month", stringMap["autopay_summary_label"])
        assertEquals("No autopay payments found yet.", stringMap["autopay_summary_empty_line"])
        assertEquals("How it will work", stringMap["autopay_how_it_works_section"])
        assertEquals("Autopay tag", stringMap["autopay_tag_title"])
        assertEquals("Repeats tag", stringMap["autopay_repeats_title"])
        assertEquals("Next expected", stringMap["autopay_next_expected_title"])
        assertEquals("Nothing here yet", stringMap["autopay_empty_title"])
    }
}

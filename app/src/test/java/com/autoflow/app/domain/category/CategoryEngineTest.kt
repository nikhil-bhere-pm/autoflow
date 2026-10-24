package com.autoflow.app.domain.category

import com.autoflow.app.data.adapter.AutoFlowParserAdapter
import com.autoflow.app.data.model.RecordType
import com.autoflow.app.data.model.ReviewStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CategoryEngineTest {

    private val baseTimestamp = 1700000000000L

    @Test
    fun testExpectedFinalCategories() {
        val expected = mapOf(
            "SWIGGY INST" to "Groceries",
            "SWIGGY" to "Food",
            "ZOMATO" to "Food",
            "UBER" to "Transport",
            "OLA" to "Transport",
            "RAPIDO" to "Transport",
            "AMAZON" to "Shopping",
            "FLIPKART" to "Shopping",
            "MYNTRA" to "Shopping",
            "BIGBASKET" to "Groceries",
            "BLINKIT" to "Groceries",
            "ZEPTO" to "Groceries",
            "DMART" to "Groceries",
            "RELIANCE RETAIL" to "Others",
            "IRCTC" to "Travel",
            "MAKEMYTRIP" to "Travel",
            "NETFLIX" to "Entertainment",
            "SPOTIFY" to "Entertainment",
            "BOOKMYSHOW" to "Entertainment",
            "JIO" to "Bills",
            "AIRTEL" to "Bills",
            "APOLLO PHARMACY" to "Health",
            "PHARMEASY" to "Health",
            "1MG" to "Health",
            "UNKNOWN MERCHANT" to "Others"
        )

        for ((merchant, expectedCategory) in expected) {
            val res = CategoryEngine.resolveCategory(merchant)
            assertEquals("Category mismatch for merchant '$merchant'", expectedCategory, res.category)
            assertFalse("Merchant '$merchant' should not be marked excluded", res.isExcluded)
        }
    }

    @Test
    fun testWholeWordSafety_doesNotMatchSubstrings() {
        // "SOLANKI STORES" contains "ola" (transport), must NOT become Transport
        val solankiRes = CategoryEngine.resolveCategory("SOLANKI STORES")
        assertEquals("SOLANKI STORES must not be Transport", "Others", solankiRes.category)

        // Tricky 1: "CHOLAMANDALAM FINANCE" contains "ola" (transport), must NOT become Transport
        val cholaRes = CategoryEngine.resolveCategory("CHOLAMANDALAM FINANCE")
        assertEquals("CHOLAMANDALAM FINANCE must not be Transport", "Others", cholaRes.category)

        // Tricky 2: "GASPAR STORES" contains "gas" (bills), must NOT become Bills
        val gasparRes = CategoryEngine.resolveCategory("GASPAR STORES")
        assertEquals("GASPAR STORES must not be Bills", "Others", gasparRes.category)

        // Tricky 3: "METROPOLIS HEALTHCARE" contains "metro" (transport) and "healthcare" (health).
        // Whole-word matching must safely skip "metro" and categorize as Health.
        val metropolisRes = CategoryEngine.resolveCategory("METROPOLIS HEALTHCARE")
        assertEquals("METROPOLIS HEALTHCARE should match Health", "Health", metropolisRes.category)
    }

    @Test
    fun testPriority_userRuleBeatsAliasBeatsKeyword() {
        // 1. User rule beats alias:
        // Normally "SWIGGY INST" resolves to "Groceries" via alias.
        // If user rule specifies "Food", it must resolve to "Food".
        val userRuleRes = CategoryEngine.resolveCategory("SWIGGY INST", userCategory = "Food")
        assertEquals("User rule must take precedence over built-in alias", "Food", userRuleRes.category)

        // 2. Built-in alias beats keyword:
        // "SWIGGY INST" matches alias "Groceries" and keyword "swiggy" (Food).
        // Alias must take precedence.
        val aliasRes = CategoryEngine.resolveCategory("SWIGGY INST")
        assertEquals("Built-in alias must take precedence over keyword mapper", "Groceries", aliasRes.category)

        // "IRCTC" matches alias "Travel" and keyword "TRANSPORT".
        val irctcRes = CategoryEngine.resolveCategory("IRCTC")
        assertEquals("IRCTC alias must take precedence as Travel", "Travel", irctcRes.category)

        // 3. Keyword beats Others:
        val kwRes = CategoryEngine.resolveCategory("ZOMATO")
        assertEquals("Keyword mapper must resolve ZOMATO to Food", "Food", kwRes.category)

        // 4. Unknown merchant falls back to Others:
        val fallbackRes = CategoryEngine.resolveCategory("NONEXISTENT ENTITY 12345")
        assertEquals("Unrecognized merchant must fall back to Others", "Others", fallbackRes.category)
    }

    @Test
    fun testNormalizationEquality() {
        val variations = listOf(
            "SWIGGY   INST.",
            "swiggy inst",
            "Swiggy Inst",
            "  swiggy    inst  ",
            "SWIGGY-INST"
        )
        for (v in variations) {
            val res = CategoryEngine.resolveCategory(v)
            assertEquals("Normalized variation '$v' must resolve to Groceries", "Groceries", res.category)
        }
    }

    @Test
    fun testJunkMerchantsNeverStored() {
        assertFalse(CategoryEngine.shouldStoreRule("Unknown Merchant"))
        assertFalse(CategoryEngine.shouldStoreRule("unknown"))
        assertFalse(CategoryEngine.shouldStoreRule("UPI"))
        assertFalse(CategoryEngine.shouldStoreRule("pos"))
        assertFalse(CategoryEngine.shouldStoreRule("card payment"))
        assertFalse(CategoryEngine.shouldStoreRule("atm"))
        assertFalse(CategoryEngine.shouldStoreRule("bank"))
        assertFalse(CategoryEngine.shouldStoreRule(""))
        assertFalse(CategoryEngine.shouldStoreRule("   "))
        assertFalse(CategoryEngine.shouldStoreRule(null))

        // Real merchants return true
        assertTrue(CategoryEngine.shouldStoreRule("ZOMATO"))
        assertTrue(CategoryEngine.shouldStoreRule("SWIGGY INST"))
        assertTrue(CategoryEngine.shouldStoreRule("STARBUCKS"))
    }

    @Test
    fun testCreditCardPayment_markedOthersAndExcluded() {
        val msg = "Rs 15,000.00 debited from A/c XX1234 on 24-10-26 towards Credit Card Payment."
        val entity = AutoFlowParserAdapter.parseAndAdapt(msg, "VM-HDFCBK", baseTimestamp)

        assertNotNull("Credit card payment debit should be parsed", entity)
        assertEquals(RecordType.EXPENSE, entity!!.recordType)
        assertEquals("Others", entity.category)
        assertEquals(ReviewStatus.EXCLUDED, entity.reviewStatus)
    }
}

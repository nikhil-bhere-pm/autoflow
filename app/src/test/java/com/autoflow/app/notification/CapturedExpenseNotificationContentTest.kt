package com.autoflow.app.notification

import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.autoflow.app.data.model.ReviewStatus
import com.autoflow.app.data.model.TransactionSource
import com.autoflow.app.domain.category.BUILT_IN_CATEGORIES
import com.autoflow.app.domain.model.FinancialTransaction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CapturedExpenseNotificationContentTest {

    private fun createTx(
        id: Long = 101L,
        amountPaise: Long = 32000L,
        merchant: String = "Swiggy",
        category: String = "Food",
        reviewStatus: ReviewStatus = ReviewStatus.CONFIRMED,
        isDeleted: Boolean = false
    ): FinancialTransaction {
        return FinancialTransaction(
            id = id,
            amountPaise = amountPaise,
            recordType = com.autoflow.app.data.model.RecordType.EXPENSE,
            dateTime = 1700000000000L,
            merchantName = merchant,
            category = category,
            categorySource = "AUTO",
            paymentMode = "UPI",
            note = null,
            accountNumber = "1234",
            bankName = "HDFC",
            reference = "REF001",
            transactionHash = "hash_$id",
            source = TransactionSource.SMS_AUTO,
            reviewStatus = reviewStatus,
            uncertaintyReasons = emptySet(),
            createdAt = 1700000000000L,
            updatedAt = 1700000000000L,
            isDeleted = isDeleted
        )
    }

    @Test
    fun testState1_needsCategory_unknownMerchant() {
        val tx = createTx(
            id = 42L,
            amountPaise = 32000L,
            merchant = "",
            category = "Others",
            reviewStatus = ReviewStatus.NEEDS_REVIEW
        )

        val content = CapturedExpenseNotificationBuilder.buildNeedsCategoryContent(tx, BUILT_IN_CATEGORIES)

        assertEquals("₹320.00 spent at Unknown Merchant", content.title)
        assertEquals("Pick a category so it is counted correctly", content.text)
        assertEquals(CapturedExpenseNotificationBuilder.CHANNEL_ID, content.channelId)
        assertNull(content.timeoutMs)

        // 3 buttons: 2 categories + "More…"
        assertEquals(3, content.actions.size)
        assertEquals("More…", content.actions[2].title)
        assertEquals(CapturedExpenseNotificationBuilder.ACTION_CATEGORY_POPUP, content.actions[2].actionType)
        assertTrue(content.actions[2].isActivity)
        assertFalse(content.actions[0].isActivity)
        assertFalse(content.actions[1].isActivity)

        // Lock screen public version has zero private info
        assertEquals("AutoFlow", content.publicTitle)
        assertEquals("New expense captured", content.publicText)
        assertFalse(content.publicText.contains("320"))
        assertFalse(content.publicText.contains("Unknown"))
    }

    @Test
    fun testState1_needsCategory_inReview() {
        val tx = createTx(
            id = 43L,
            amountPaise = 150000L,
            merchant = "Apollo Pharmacy",
            category = "Others",
            reviewStatus = ReviewStatus.NEEDS_REVIEW
        )

        val content = CapturedExpenseNotificationBuilder.buildInitialContent(tx, BUILT_IN_CATEGORIES)

        assertEquals("₹1,500.00 spent at Apollo Pharmacy", content.title)
        assertEquals("Pick a category so it is counted correctly", content.text)
        assertEquals(3, content.actions.size)
        // Suggestions should pick Health
        assertEquals("Health", content.actions[0].title)
        assertEquals("More…", content.actions[2].title)
    }

    @Test
    fun testState2_merchantAlreadyKnown() {
        val tx = createTx(
            id = 44L,
            amountPaise = 13000L,
            merchant = "Chai Point",
            category = "Food",
            reviewStatus = ReviewStatus.CONFIRMED
        )

        val content = CapturedExpenseNotificationBuilder.buildInitialContent(tx, BUILT_IN_CATEGORIES)

        assertEquals("₹130.00 spent at Chai Point", content.title)
        assertEquals("Food · counted in today's total", content.text)
        assertNull(content.timeoutMs)

        // 2 buttons: "Change" and "Not an expense"
        assertEquals(2, content.actions.size)
        assertEquals("Change", content.actions[0].title)
        assertEquals(CapturedExpenseNotificationBuilder.ACTION_CATEGORY_POPUP, content.actions[0].actionType)
        assertTrue(content.actions[0].isActivity)
        assertEquals("Not an expense", content.actions[1].title)
        assertFalse(content.actions[1].isActivity)

        // Lock screen
        assertEquals("New expense captured", content.publicText)
    }

    @Test
    fun testState3_afterTappingCategory() {
        val content = CapturedExpenseNotificationBuilder.buildCategorySavedContent(
            txId = 42L,
            amountPaise = 32000L,
            category = "Food"
        )

        assertEquals("Saved: ₹320.00 · Food", content.title)
        assertEquals("Future spends at this merchant will use Food", content.text)
        assertEquals(15000L, content.timeoutMs)

        // 1 button: "Undo"
        assertEquals(1, content.actions.size)
        assertEquals("Undo", content.actions[0].title)
        assertEquals(CapturedExpenseNotificationBuilder.ACTION_UNDO_CATEGORY, content.actions[0].actionType)
        assertFalse(content.actions[0].isActivity)
    }

    @Test
    fun testState4_afterTappingNotAnExpense() {
        val content = CapturedExpenseNotificationBuilder.buildNotExpenseContent(
            txId = 44L,
            amountPaise = 13000L,
            merchantName = "Chai Point"
        )

        assertEquals("Marked as not an expense", content.title)
        assertEquals("₹130.00 at Chai Point not counted", content.text)
        assertEquals(15000L, content.timeoutMs)

        // 1 button: "Undo"
        assertEquals(1, content.actions.size)
        assertEquals("Undo", content.actions[0].title)
        assertEquals(CapturedExpenseNotificationBuilder.ACTION_UNDO_NOT_EXPENSE, content.actions[0].actionType)
        assertFalse(content.actions[0].isActivity)
    }

    @Test
    fun testNotificationIdDeterministic() {
        val id1 = CapturedExpenseNotificationBuilder.getNotificationId(10L)
        val id2 = CapturedExpenseNotificationBuilder.getNotificationId(10L)
        val id3 = CapturedExpenseNotificationBuilder.getNotificationId(11L)

        assertEquals(id1, id2)
        assertTrue(id1 != id3)
    }

    @Test
    fun testActionRequestCode_uniqueAndDeterministic() {
        val txId = 42L
        val codeChange = CapturedExpenseNotificationHelper.getActionRequestCode(txId, "CHANGE")
        val codeNotExpense = CapturedExpenseNotificationHelper.getActionRequestCode(txId, "NOT_EXPENSE")
        val codeMore = CapturedExpenseNotificationHelper.getActionRequestCode(txId, "MORE")
        val codeCatFood = CapturedExpenseNotificationHelper.getActionRequestCode(txId, "CAT_Food")
        val codeCatTravel = CapturedExpenseNotificationHelper.getActionRequestCode(txId, "CAT_Travel")
        val otherTxCodeChange = CapturedExpenseNotificationHelper.getActionRequestCode(43L, "CHANGE")

        // Deterministic
        assertEquals(codeChange, CapturedExpenseNotificationHelper.getActionRequestCode(txId, "CHANGE"))

        // Distinct across different actions on same tx
        val set = setOf(codeChange, codeNotExpense, codeMore, codeCatFood, codeCatTravel, otherTxCodeChange)
        assertEquals(6, set.size)
    }

    @Test
    fun testRuleA_parserCategorisedFood_noRule_showsKnownState() {
        val tx = createTx(
            id = 51L,
            amountPaise = 28000L,
            merchant = "Swiggy",
            category = "Food",
            reviewStatus = ReviewStatus.CONFIRMED
        )

        assertFalse(CapturedExpenseNotificationBuilder.needsCategory(tx, hasSavedRule = false))
        val content = CapturedExpenseNotificationBuilder.buildInitialContent(tx, BUILT_IN_CATEGORIES, hasSavedRule = false)

        assertEquals("₹280.00 spent at Swiggy", content.title)
        assertEquals("Food · counted in today's total", content.text)
        assertEquals(2, content.actions.size)
        assertEquals("Change", content.actions[0].title)
        assertEquals("Not an expense", content.actions[1].title)
    }

    @Test
    fun testRuleB_othersCategory_noRule_showsNeedsCategory() {
        val tx = createTx(
            id = 52L,
            amountPaise = 15000L,
            merchant = "Local Shop",
            category = "Others",
            reviewStatus = ReviewStatus.CONFIRMED
        )

        assertTrue(CapturedExpenseNotificationBuilder.needsCategory(tx, hasSavedRule = false))
        val content = CapturedExpenseNotificationBuilder.buildInitialContent(tx, BUILT_IN_CATEGORIES, hasSavedRule = false)

        assertEquals("₹150.00 spent at Local Shop", content.title)
        assertEquals("Pick a category so it is counted correctly", content.text)
        assertEquals(3, content.actions.size)
        assertEquals("More…", content.actions[2].title)
    }

    @Test
    fun testRuleC_reviewStatusNeedsReview_noRule_showsNeedsCategory() {
        val tx = createTx(
            id = 53L,
            amountPaise = 42000L,
            merchant = "Swiggy",
            category = "Food",
            reviewStatus = ReviewStatus.NEEDS_REVIEW
        )

        assertTrue(CapturedExpenseNotificationBuilder.needsCategory(tx, hasSavedRule = false))
        val content = CapturedExpenseNotificationBuilder.buildInitialContent(tx, BUILT_IN_CATEGORIES, hasSavedRule = false)

        assertEquals("₹420.00 spent at Swiggy", content.title)
        assertEquals("Pick a category so it is counted correctly", content.text)
        assertEquals(3, content.actions.size)
        assertEquals("More…", content.actions[2].title)
    }

    @Test
    fun testRuleD_savedRuleWithOthersCategory_showsKnownState() {
        val tx = createTx(
            id = 54L,
            amountPaise = 50000L,
            merchant = "General Store",
            category = "Others",
            reviewStatus = ReviewStatus.CONFIRMED
        )

        assertFalse(CapturedExpenseNotificationBuilder.needsCategory(tx, hasSavedRule = true))
        val content = CapturedExpenseNotificationBuilder.buildInitialContent(tx, BUILT_IN_CATEGORIES, hasSavedRule = true)

        assertEquals("₹500.00 spent at General Store", content.title)
        assertEquals("Others · counted in today's total", content.text)
        assertEquals(2, content.actions.size)
        assertEquals("Change", content.actions[0].title)
        assertEquals("Not an expense", content.actions[1].title)
    }

    @Test
    fun testRuleE_savedRuleInReview_showsNeedsCategory() {
        val tx = createTx(
            id = 55L,
            amountPaise = 50000L,
            merchant = "General Store",
            category = "Others",
            reviewStatus = ReviewStatus.NEEDS_REVIEW
        )

        assertTrue(CapturedExpenseNotificationBuilder.needsCategory(tx, hasSavedRule = true))
        val content = CapturedExpenseNotificationBuilder.buildInitialContent(tx, BUILT_IN_CATEGORIES, hasSavedRule = true)

        assertEquals("Pick a category so it is counted correctly", content.text)
        assertEquals(3, content.actions.size)
        assertEquals("More…", content.actions[2].title)
    }

    @Test
    fun testCompactChipTokens() {
        assertEquals(36.dp, com.autoflow.app.presentation.ui.components.AFChipTokens.visualHeight)
        assertEquals(40.dp, com.autoflow.app.presentation.ui.components.AFChipTokens.minTouchTarget)
        assertEquals(12.dp, com.autoflow.app.presentation.ui.components.AFChipTokens.horizontalPadding)
        assertEquals(6.dp, com.autoflow.app.presentation.ui.components.AFChipTokens.gap)
        assertEquals(13.sp, com.autoflow.app.presentation.ui.components.AFChipTokens.fontSize)
    }
}

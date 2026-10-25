package com.autoflow.app.notification

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
}

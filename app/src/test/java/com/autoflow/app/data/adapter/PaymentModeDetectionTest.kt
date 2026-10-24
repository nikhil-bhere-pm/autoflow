package com.autoflow.app.data.adapter

import com.autoflow.app.data.model.PaymentMode
import org.junit.Assert.assertEquals
import org.junit.Test

class PaymentModeDetectionTest {

    private val adapter = AutoFlowParserAdapter

    data class ModeTestCase(
        val id: String,
        val message: String,
        val expectedMode: String,
        val description: String
    )

    private val testCases = listOf(
        ModeTestCase(
            id = "PM-01",
            message = "Spent INR 130 using Axis Bank Credit Card no. XX7997 on 24-10-26 09:59:42 IST at SWIGGY INST.",
            expectedMode = PaymentMode.CREDIT,
            description = "Credit card keyword"
        ),
        ModeTestCase(
            id = "PM-02",
            message = "Txn of INR 500.00 on HDFC Bank CC ending 1234 on 24-10-26.",
            expectedMode = PaymentMode.CREDIT,
            description = "CC prefix with space"
        ),
        ModeTestCase(
            id = "PM-03",
            message = "INR 1,200.00 spent on your Card ending XX1234 (Credit Card) at AMAZON.",
            expectedMode = PaymentMode.CREDIT,
            description = "Card ending followed by credit"
        ),
        ModeTestCase(
            id = "PM-04",
            message = "Your Card no. XX4321 credit card debited for INR 250.",
            expectedMode = PaymentMode.CREDIT,
            description = "Card no followed by credit"
        ),
        ModeTestCase(
            id = "PM-05",
            message = "Rs 450.00 debited from A/c XX1234 on 24-10-26 at ZOMATO. Paid by UPI.",
            expectedMode = PaymentMode.UPI,
            description = "Paid by UPI"
        ),
        ModeTestCase(
            id = "PM-06",
            message = "Rs.500.00 debited from A/c XX1234 on 24-10-26 and credited to VPA swiggy@upi.",
            expectedMode = PaymentMode.UPI,
            description = "VPA and @upi handle"
        ),
        ModeTestCase(
            id = "PM-07",
            message = "Sent Rs.250.00 from HDFC Bank A/c XX1234 to SWIGGY on 24-10-26 via UPI.",
            expectedMode = PaymentMode.UPI,
            description = "via UPI"
        ),
        ModeTestCase(
            id = "PM-08",
            message = "Paid Rs 150 to merchant@okaxis on 24-10-26. Ref: 12345.",
            expectedMode = PaymentMode.UPI,
            description = "@okaxis handle"
        ),
        ModeTestCase(
            id = "PM-09",
            message = "Rs 1,200.00 debited from A/c XX1234 on 24-10-26 at AMAZON. Paid by HDFC Bank Debit Card.",
            expectedMode = PaymentMode.BANK,
            description = "Debit Card keyword counts as BANK"
        ),
        ModeTestCase(
            id = "PM-10",
            message = "Rs 10,000.00 debited from A/c XX1234 by NEFT to ACME on 24-10-26.",
            expectedMode = PaymentMode.BANK,
            description = "NEFT transfer"
        ),
        ModeTestCase(
            id = "PM-11",
            message = "Rs 2,500.00 debited from A/c XX1234 by IMPS on 24-10-26.",
            expectedMode = PaymentMode.BANK,
            description = "IMPS transfer"
        ),
        ModeTestCase(
            id = "PM-12",
            message = "Rs 200,000.00 debited from A/c XX1234 by RTGS on 24-10-26.",
            expectedMode = PaymentMode.BANK,
            description = "RTGS transfer"
        ),
        ModeTestCase(
            id = "PM-13",
            message = "Rs 5,000.00 withdrawn at ATM from A/c XX1234 on 24-10-26.",
            expectedMode = PaymentMode.BANK,
            description = "ATM withdrawal"
        ),
        ModeTestCase(
            id = "PM-14",
            message = "Rs 450.00 debited from A/c XX1234 at ZOMATO on 24-10-26.",
            expectedMode = PaymentMode.BANK,
            description = "debited from A/c without UPI/card"
        ),
        ModeTestCase(
            id = "PM-15",
            message = "Paid Rs 500 at Local Store on 24-10-26.",
            expectedMode = PaymentMode.UNKNOWN,
            description = "No payment mode signals -> UNKNOWN"
        ),
        ModeTestCase(
            id = "PM-16",
            message = "Rs 1,200.00 debited from A/c XX1234 at AMAZON. Paid by HDFC Bank Debit Card. For support email cardservices@hdfcbank.net.",
            expectedMode = PaymentMode.BANK,
            description = "Debit card ending with support email -> BANK (not UPI)"
        ),
        ModeTestCase(
            id = "PM-17",
            message = "Spent INR 130 using Axis Bank Credit Card no. XX7997 on 24-10-26 at SWIGGY INST. Queries: email cardservices@axisbank.com.",
            expectedMode = PaymentMode.CREDIT,
            description = "Credit card ending with support email -> CREDIT (not UPI)"
        ),
        ModeTestCase(
            id = "PM-18",
            message = "Paid Rs 150 to x@okaxis on 24-10-26. Ref: 12345.",
            expectedMode = PaymentMode.UPI,
            description = "Real UPI handle x@okaxis without dots -> UPI"
        ),
        ModeTestCase(
            id = "PM-19",
            message = "Sent Rs 250 to y@ybl on 24-10-26. UPI Ref: 67890.",
            expectedMode = PaymentMode.UPI,
            description = "Real UPI handle y@ybl without dots -> UPI"
        ),
        ModeTestCase(
            id = "PM-20",
            message = "Paid Rs 400 to swiggy@upi on 24-10-26. Txn: 998877.",
            expectedMode = PaymentMode.UPI,
            description = "Real UPI handle swiggy@upi without dots -> UPI"
        )
    )

    @Test
    fun testAllPaymentModeTestCases() {
        for (case in testCases) {
            val detected = adapter.detectPaymentMode(case.message)
            assertEquals(
                "Test case ${case.id} (${case.description}) failed for message: '${case.message}'",
                case.expectedMode,
                detected
            )
        }
    }
}

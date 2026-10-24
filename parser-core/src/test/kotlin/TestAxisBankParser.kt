package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.test.ExpectedTransaction
import com.pennywiseai.parser.core.test.ParserTestCase
import com.pennywiseai.parser.core.test.ParserTestUtils
import org.junit.jupiter.api.*
import java.math.BigDecimal

class AxisBankParserTest {
    @TestFactory
    fun `test Axis Bank Parser comprehensive test suite`(): List<DynamicTest> {
        val parser = AxisBankParser()

        ParserTestUtils.printTestHeader(
            parserName = "Axis Bank",
            bankName = parser.getBankName(),
            currency = parser.getCurrency()
        )

        val testCases = listOf(
            // Credit card "Spent" transactions
            ParserTestCase(
                name = "Credit Card Spent - Swiggy",
                message = """Spent INR 131
Axis Bank Card no. XX0818
05-10-25 09:43:27 IST
Swiggy Limi
Avl Limit: INR 217162.72
Not you? SMS BLOCK 0818 to 919951860002""",
                sender = "AX-AXISBK-S",
                expected = ExpectedTransaction(
                    amount = BigDecimal("131"),
                    currency = "INR",
                    type = com.pennywiseai.parser.core.TransactionType.CREDIT,
                    merchant = "Swiggy",
                    accountLast4 = "0818",
                    isFromCard = true,
                    creditLimit = BigDecimal("217162.72")
                )
            ),

            ParserTestCase(
                name = "Credit Card Spent - Amazon",
                message = """Spent INR 1299.00
Axis Bank Card no. XX5678
12-10-25 14:30:15 IST
Amazon Pay
Avl Limit: INR 50000.00
Not you? SMS BLOCK 5678 to 919951860002""",
                sender = "AX-AXISBK-S",
                expected = ExpectedTransaction(
                    amount = BigDecimal("1299.00"),
                    currency = "INR",
                    type = com.pennywiseai.parser.core.TransactionType.CREDIT,
                    merchant = "Amazon",
                    accountLast4 = "5678",
                    isFromCard = true,
                    creditLimit = BigDecimal("50000.00")
                )
            ),

            ParserTestCase(
                name = "Credit Card Spent - Avenue Supermarts (format 2)",
                message = """Spent
Card no. XX7441
INR 562
01-09-25 12:04:18
AVENUE SUPE
Avl Lmt INR 5120.87
SMS BLOCK 7441 to 919951860002, if not you - Axis Bank""",
                sender = "CP-AXISBK-S",
                expected = ExpectedTransaction(
                    amount = BigDecimal("562"),
                    currency = "INR",
                    type = com.pennywiseai.parser.core.TransactionType.CREDIT,
                    merchant = "AVENUE",  // cleanMerchantName converts ALL_CAPS -> Proper Case, but single words stay uppercase
                    accountLast4 = "7441",
                    isFromCard = true,
                    creditLimit = BigDecimal("5120.87")
                )
            ),

            ParserTestCase(
                name = "Credit Card Spent - Blinkit (Format 1 with IST)",
                message = """Spent INR 174
Axis Bank Card no. XX7441
13-09-25 21:35:56 IST
Blinkit
Avl Limit: INR 6652.78
Not you? SMS BLOCK 7441 to 919951860002""",
                sender = "JX-AXISBK-S",
                expected = ExpectedTransaction(
                    amount = BigDecimal("174"),
                    currency = "INR",
                    type = com.pennywiseai.parser.core.TransactionType.CREDIT,
                    merchant = "Blinkit",
                    accountLast4 = "7441",
                    isFromCard = true,
                    creditLimit = BigDecimal("6652.78")
                )
            ),

            ParserTestCase(
                name = "Credit Card Spent - Blinkit (Format 2 without IST)",
                message = """Spent
Card no. XX7441
INR 207
01-09-25 14:10:35
Blinkit
Avl Lmt INR 4632.87
SMS BLOCK 7441 to 919951860002, if not you - Axis Bank""",
                sender = "AX-AXISBK-S",
                expected = ExpectedTransaction(
                    amount = BigDecimal("207"),
                    currency = "INR",
                    type = com.pennywiseai.parser.core.TransactionType.CREDIT,
                    merchant = "Blinkit",
                    accountLast4 = "7441",
                    isFromCard = true,
                    creditLimit = BigDecimal("4632.87")
                )
            ),

            ParserTestCase(
                name = "Credit Card Spent - BPCL Petrol",
                message = """Spent INR 500
Axis Bank Card no. XX6018
22-09-25 09:03:41 IST
BPCL ARUNAA
Avl Limit: INR 17131.47
Not you? SMS BLOCK 6018 to 919951860002""",
                sender = "CP-AXISBK-S",
                expected = ExpectedTransaction(
                    amount = BigDecimal("500"),
                    currency = "INR",
                    type = com.pennywiseai.parser.core.TransactionType.CREDIT,
                    merchant = "BPCL ARUNAA",
                    accountLast4 = "6018",
                    isFromCard = true,
                    creditLimit = BigDecimal("17131.47")
                )
            ),

            ParserTestCase(
                name = "Credit Card Spent - JSK Fuel Station",
                message = """Spent INR 500
Axis Bank Card no. XX6018
13-09-25 13:08:07 IST
JSK FUEL ST
Avl Limit: INR 6826.78
Not you? SMS BLOCK 6018 to 919951860002""",
                sender = "JX-AXISBK-S",
                expected = ExpectedTransaction(
                    amount = BigDecimal("500"),
                    currency = "INR",
                    type = com.pennywiseai.parser.core.TransactionType.CREDIT,
                    merchant = "JSK FUEL ST",
                    accountLast4 = "6018",
                    isFromCard = true,
                    creditLimit = BigDecimal("6826.78")
                )
            ),

            // ATM withdrawals
            ParserTestCase(
                name = "ATM Withdrawal - Axis Bank Location",
                message = "INR 2000.00 debited from A/c no. XX589034 on AXIS BANK L 04-11-2025 16:06:39 IST. Avl bal: INR 98919.81. Not you? SMS BLOCKCARD XX0192 to +919951860002 - Axis Bank",
                sender = "JD-AXISBK-S",
                expected = ExpectedTransaction(
                    amount = BigDecimal("2000.00"),
                    currency = "INR",
                    type = com.pennywiseai.parser.core.TransactionType.EXPENSE,
                    merchant = "ATM",
                    accountLast4 = "9034",
                    balance = BigDecimal("98919.81")
                )
            ),

            // Debit card transactions - Issue #120
            ParserTestCase(
                name = "Debit Card - BURGRILL",
                message = "INR 209.00 debited from A/c no. XXxxxxy on BURGRILL 04-12-2025 13:13:27 IST. Avl bal: INR xxxxxxx. Not you? SMS BLOCKCARD XX0023 to +919951860002 - Axis Bank",
                sender = "JD-AXISBK-S",
                expected = ExpectedTransaction(
                    amount = BigDecimal("209.00"),
                    currency = "INR",
                    type = com.pennywiseai.parser.core.TransactionType.EXPENSE,
                    merchant = "BURGRILL",
                    accountLast4 = null
                )
            ),

            ParserTestCase(
                name = "Debit Card - Restaurant",
                message = "INR 1028.00 debited from A/c no. XXxxxxy on RESTAURANT XY 02-12-2025 20:38:23 IST. Avl bal: INR xxxxxxx. Not you? SMS BLOCKCARD XX0023 to +919951860002 - Axis Bank",
                sender = "JD-AXISBK-S",
                expected = ExpectedTransaction(
                    amount = BigDecimal("1028.00"),
                    currency = "INR",
                    type = com.pennywiseai.parser.core.TransactionType.EXPENSE,
                    merchant = "RESTAURANT XY",
                    accountLast4 = null
                )
            ),

            ParserTestCase(
                name = "Debit Card - Numeric Account Pattern",
                message = "INR 500.00 debited from A/c no. XX312225 on MERCHANT ABC 02-12-2025 20:38:23 IST. Avl bal: INR 10000.00. Not you? SMS BLOCKCARD XX0023 to +919951860002 - Axis Bank",
                sender = "JD-AXISBK-S",
                expected = ExpectedTransaction(
                    amount = BigDecimal("500.00"),
                    currency = "INR",
                    type = com.pennywiseai.parser.core.TransactionType.EXPENSE,
                    merchant = "MERCHANT ABC",
                    accountLast4 = "2225",
                    balance = BigDecimal("10000.00")
                )
            ),

            // Regression: Merchant following 'to' with period or balance text
            ParserTestCase(
                name = "Debit SMS - Merchant to BLINKIT with period and balance",
                message = "INR 130.00 debited from A/c no. XX1234 to BLINKIT. Bal INR 4870.00. Not you? SMS BLOCK 1234 to 919951860002 - Axis Bank",
                sender = "AX-AXISBK-S",
                expected = ExpectedTransaction(
                    amount = BigDecimal("130.00"),
                    currency = "INR",
                    type = com.pennywiseai.parser.core.TransactionType.EXPENSE,
                    merchant = "BLINKIT",
                    accountLast4 = "1234",
                    balance = BigDecimal("4870.00")
                )
            ),

            ParserTestCase(
                name = "Debit SMS - Merchant to ZOMATO with on date",
                message = "INR 350.00 debited from A/c no. XX1234 to ZOMATO on 24-10-2026. Avl bal: INR 4520.00. Not you? SMS BLOCK 1234 to 919951860002 - Axis Bank",
                sender = "AX-AXISBK-S",
                expected = ExpectedTransaction(
                    amount = BigDecimal("350.00"),
                    currency = "INR",
                    type = com.pennywiseai.parser.core.TransactionType.EXPENSE,
                    merchant = "ZOMATO",
                    accountLast4 = "1234",
                    balance = BigDecimal("4520.00")
                )
            ),

            ParserTestCase(
                name = "Debit SMS - Merchant to SWIGGY via UPI",
                message = "INR 220.00 debited from A/c no. XX1234 to SWIGGY via UPI. Bal INR 4300.00. Not you? SMS BLOCK 1234 to 919951860002 - Axis Bank",
                sender = "AX-AXISBK-S",
                expected = ExpectedTransaction(
                    amount = BigDecimal("220.00"),
                    currency = "INR",
                    type = com.pennywiseai.parser.core.TransactionType.EXPENSE,
                    merchant = "SWIGGY",
                    accountLast4 = "1234",
                    balance = BigDecimal("4300.00")
                )
            ),

            // Regression: VPA-based merchant extraction
            ParserTestCase(
                name = "UPI VPA - to VPA swiggy@axisbank",
                message = "INR 250.00 debited from A/c no. XX1234 to VPA swiggy@axisbank on 24-10-2026. Bal INR 4050.00. Not you? SMS BLOCK 1234 to 919951860002 - Axis Bank",
                sender = "AX-AXISBK-S",
                expected = ExpectedTransaction(
                    amount = BigDecimal("250.00"),
                    currency = "INR",
                    type = com.pennywiseai.parser.core.TransactionType.EXPENSE,
                    merchant = "swiggy",
                    accountLast4 = "1234",
                    balance = BigDecimal("4050.00")
                )
            ),

            ParserTestCase(
                name = "UPI VPA - handle with dot and uppercase handle",
                message = "INR 150.00 debited from A/c no. XX1234 to UBER.INDIA@axisbank on 24-10-2026. Bal INR 3900.00. Not you? SMS BLOCK 1234 to 919951860002 - Axis Bank",
                sender = "AX-AXISBK-S",
                expected = ExpectedTransaction(
                    amount = BigDecimal("150.00"),
                    currency = "INR",
                    type = com.pennywiseai.parser.core.TransactionType.EXPENSE,
                    merchant = "UBER INDIA",
                    accountLast4 = "1234",
                    balance = BigDecimal("3900.00")
                )
            ),

            // Regression: Supported UPI slash variants
            ParserTestCase(
                name = "UPI Slash - DR variant with ref and merchant and bank",
                message = "INR 350.00 debited from A/c no. XX1234 via UPI/DR/429812345678/ZOMATO/UTIB. Bal INR 3550.00. Not you? SMS BLOCK 1234 to 919951860002 - Axis Bank",
                sender = "AX-AXISBK-S",
                expected = ExpectedTransaction(
                    amount = BigDecimal("350.00"),
                    currency = "INR",
                    type = com.pennywiseai.parser.core.TransactionType.EXPENSE,
                    merchant = "ZOMATO",
                    reference = "429812345678",
                    accountLast4 = "1234",
                    balance = BigDecimal("3550.00")
                )
            ),

            ParserTestCase(
                name = "UPI Slash - P2M variant with ref and merchant",
                message = "INR 499.00 debited from A/c no. XX1234 via UPI/P2M/429812345679/BOOKMYSHOW. Bal INR 3051.00. Not you? SMS BLOCK 1234 to 919951860002 - Axis Bank",
                sender = "AX-AXISBK-S",
                expected = ExpectedTransaction(
                    amount = BigDecimal("499.00"),
                    currency = "INR",
                    type = com.pennywiseai.parser.core.TransactionType.EXPENSE,
                    merchant = "BOOKMYSHOW",
                    reference = "429812345679",
                    accountLast4 = "1234",
                    balance = BigDecimal("3051.00")
                )
            ),

            ParserTestCase(
                name = "UPI Slash - ref and merchant without DR/P2M prefix",
                message = "INR 120.00 debited from A/c no. XX1234 via UPI/429812345680/ZEPTO/UTIB. Bal INR 2931.00. Not you? SMS BLOCK 1234 to 919951860002 - Axis Bank",
                sender = "AX-AXISBK-S",
                expected = ExpectedTransaction(
                    amount = BigDecimal("120.00"),
                    currency = "INR",
                    type = com.pennywiseai.parser.core.TransactionType.EXPENSE,
                    merchant = "ZEPTO",
                    reference = "429812345680",
                    accountLast4 = "1234",
                    balance = BigDecimal("2931.00")
                )
            ),

            // Regression: Missing or ambiguous merchant
            ParserTestCase(
                name = "Missing Merchant - Generic debit without payee",
                message = "INR 500.00 debited from A/c no. XX1234 on 24-10-2026. Avl Bal INR 2431.00. Not you? Call 18004195959 - Axis Bank",
                sender = "AX-AXISBK-S",
                expected = ExpectedTransaction(
                    amount = BigDecimal("500.00"),
                    currency = "INR",
                    type = com.pennywiseai.parser.core.TransactionType.EXPENSE,
                    accountLast4 = "1234",
                    balance = BigDecimal("2431.00"),
                    expectNullMerchant = true
                )
            ),

            ParserTestCase(
                name = "Ambiguous Merchant - P2P Account transfer",
                message = "INR 1000.00 debited from A/c no. XX1234 to A/c XX5678 on 24-10-2026. Bal INR 1431.00. Not you? SMS BLOCK 1234 to 919951860002 - Axis Bank",
                sender = "AX-AXISBK-S",
                expected = ExpectedTransaction(
                    amount = BigDecimal("1000.00"),
                    currency = "INR",
                    type = com.pennywiseai.parser.core.TransactionType.EXPENSE,
                    accountLast4 = "1234",
                    balance = BigDecimal("1431.00"),
                    expectNullMerchant = true
                )
            ),

            // Regression: Generic labels, bank codes, account identifiers, references, and trailing boilerplate
            ParserTestCase(
                name = "Non-Merchant - Bank name Axis Bank in to clause",
                message = "INR 600.00 debited from A/c no. XX1234 to AXIS BANK on 24-10-2026. Bal INR 831.00. Not you? SMS BLOCK 1234 to 919951860002 - Axis Bank",
                sender = "AX-AXISBK-S",
                expected = ExpectedTransaction(
                    amount = BigDecimal("600.00"),
                    currency = "INR",
                    type = com.pennywiseai.parser.core.TransactionType.EXPENSE,
                    accountLast4 = "1234",
                    balance = BigDecimal("831.00"),
                    expectNullMerchant = true
                )
            ),

            ParserTestCase(
                name = "Non-Merchant - Bank IFSC code UTIB in UPI slash without merchant",
                message = "INR 200.00 debited from A/c no. XX1234 via UPI/DR/429812345681/UTIB. Bal INR 631.00. Not you? SMS BLOCK 1234 to 919951860002 - Axis Bank",
                sender = "AX-AXISBK-S",
                expected = ExpectedTransaction(
                    amount = BigDecimal("200.00"),
                    currency = "INR",
                    type = com.pennywiseai.parser.core.TransactionType.EXPENSE,
                    reference = "429812345681",
                    accountLast4 = "1234",
                    balance = BigDecimal("631.00"),
                    expectNullMerchant = true
                )
            ),

            ParserTestCase(
                name = "Non-Merchant - Generated QR code in VPA",
                message = "INR 80.00 debited from A/c no. XX1234 to VPA paytmqr28100505@paytm. Bal INR 551.00. Not you? SMS BLOCK 1234 to 919951860002 - Axis Bank",
                sender = "AX-AXISBK-S",
                expected = ExpectedTransaction(
                    amount = BigDecimal("80.00"),
                    currency = "INR",
                    type = com.pennywiseai.parser.core.TransactionType.EXPENSE,
                    accountLast4 = "1234",
                    balance = BigDecimal("551.00"),
                    expectNullMerchant = true
                )
            ),

            // Credit card "Spent ... at <merchant>" layout tests
            ParserTestCase(
                name = "Credit Card Spent at - Swiggy Inst multiline",
                message = "Spent INR 130 \nusing Axis Bank Credit Card no. XX1234 on 24-10-26 09:59:42 IST at \nSWIGGY INST. \nAvl limit: INR 40000.4. Not you? SMS BLOCK 1234 to +919951860002.",
                sender = "AX-AXISBK-S",
                expected = ExpectedTransaction(
                    amount = BigDecimal("130"),
                    currency = "INR",
                    type = com.pennywiseai.parser.core.TransactionType.CREDIT,
                    merchant = "SWIGGY INST",
                    accountLast4 = "1234",
                    isFromCard = true,
                    creditLimit = BigDecimal("40000.4")
                )
            ),
            ParserTestCase(
                name = "Credit Card Spent at - Swiggy Inst single-space",
                message = "Spent INR 130 using Axis Bank Credit Card no. XX1234 on 24-10-26 09:59:42 IST at SWIGGY INST. Avl limit: INR 40000.4. Not you? SMS BLOCK 1234 to +919951860002.",
                sender = "AX-AXISBK-S",
                expected = ExpectedTransaction(
                    amount = BigDecimal("130"),
                    currency = "INR",
                    type = com.pennywiseai.parser.core.TransactionType.CREDIT,
                    merchant = "SWIGGY INST",
                    accountLast4 = "1234",
                    isFromCard = true,
                    creditLimit = BigDecimal("40000.4")
                )
            ),
            ParserTestCase(
                name = "Credit Card Spent at - Swiggy Inst title Avl Limit",
                message = "Spent INR 130 using Axis Bank Credit Card no. XX1234 on 24-10-26 09:59:42 IST at SWIGGY INST. Avl Limit: INR 40000.4. Not you? SMS BLOCK 1234 to +919951860002.",
                sender = "AX-AXISBK-S",
                expected = ExpectedTransaction(
                    amount = BigDecimal("130"),
                    currency = "INR",
                    type = com.pennywiseai.parser.core.TransactionType.CREDIT,
                    merchant = "SWIGGY INST",
                    accountLast4 = "1234",
                    isFromCard = true,
                    creditLimit = BigDecimal("40000.4")
                )
            ),
            ParserTestCase(
                name = "Credit Card Spent at - Swiggy Inst Avl Lmt",
                message = "Spent INR 130 using Axis Bank Credit Card no. XX1234 on 24-10-26 09:59:42 IST at SWIGGY INST. Avl Lmt: INR 40000.4. Not you? SMS BLOCK 1234 to +919951860002.",
                sender = "AX-AXISBK-S",
                expected = ExpectedTransaction(
                    amount = BigDecimal("130"),
                    currency = "INR",
                    type = com.pennywiseai.parser.core.TransactionType.CREDIT,
                    merchant = "SWIGGY INST",
                    accountLast4 = "1234",
                    isFromCard = true,
                    creditLimit = BigDecimal("40000.4")
                )
            ),
            ParserTestCase(
                name = "Credit Card Spent at - Swiggy Inst no trailing period",
                message = "Spent INR 130 \nusing Axis Bank Credit Card no. XX1234 on 24-10-26 09:59:42 IST at \nSWIGGY INST \nAvl limit: INR 40000.4. Not you? SMS BLOCK 1234 to +919951860002.",
                sender = "AX-AXISBK-S",
                expected = ExpectedTransaction(
                    amount = BigDecimal("130"),
                    currency = "INR",
                    type = com.pennywiseai.parser.core.TransactionType.CREDIT,
                    merchant = "SWIGGY INST",
                    accountLast4 = "1234",
                    isFromCard = true,
                    creditLimit = BigDecimal("40000.4")
                )
            ),
            ParserTestCase(
                name = "Credit Card Spent at - merchant with apostrophe",
                message = "Spent INR 130 using Axis Bank Credit Card no. XX1234 on 24-10-26 09:59:42 IST at MCDONALD'S. Avl limit: INR 40000.4. Not you? SMS BLOCK 1234 to +919951860002.",
                sender = "AX-AXISBK-S",
                expected = ExpectedTransaction(
                    amount = BigDecimal("130"),
                    currency = "INR",
                    type = com.pennywiseai.parser.core.TransactionType.CREDIT,
                    merchant = "MCDONALD'S",
                    accountLast4 = "1234",
                    isFromCard = true,
                    creditLimit = BigDecimal("40000.4")
                )
            ),
            ParserTestCase(
                name = "Credit Card Spent at - merchant with period",
                message = "Spent INR 130 using Axis Bank Credit Card no. XX1234 on 24-10-26 09:59:42 IST at AMAZON.IN. Avl limit: INR 40000.4. Not you? SMS BLOCK 1234 to +919951860002.",
                sender = "AX-AXISBK-S",
                expected = ExpectedTransaction(
                    amount = BigDecimal("130"),
                    currency = "INR",
                    type = com.pennywiseai.parser.core.TransactionType.CREDIT,
                    merchant = "AMAZON.IN",
                    accountLast4 = "1234",
                    isFromCard = true,
                    creditLimit = BigDecimal("40000.4")
                )
            ),
            ParserTestCase(
                name = "Credit Card Spent at - merchant with asterisk",
                message = "Spent INR 130 using Axis Bank Credit Card no. XX1234 on 24-10-26 09:59:42 IST at STAR*MERCHANT. Avl limit: INR 40000.4. Not you? SMS BLOCK 1234 to +919951860002.",
                sender = "AX-AXISBK-S",
                expected = ExpectedTransaction(
                    amount = BigDecimal("130"),
                    currency = "INR",
                    type = com.pennywiseai.parser.core.TransactionType.CREDIT,
                    merchant = "STAR*MERCHANT",
                    accountLast4 = "1234",
                    isFromCard = true,
                    creditLimit = BigDecimal("40000.4")
                )
            ),
            ParserTestCase(
                name = "Credit Card Spent at - candidate is AXIS BANK",
                message = "Spent INR 130 using Axis Bank Credit Card no. XX1234 on 24-10-26 09:59:42 IST at AXIS BANK. Avl limit: INR 40000.4. Not you? SMS BLOCK 1234 to +919951860002.",
                sender = "AX-AXISBK-S",
                expected = ExpectedTransaction(
                    amount = BigDecimal("130"),
                    currency = "INR",
                    type = com.pennywiseai.parser.core.TransactionType.CREDIT,
                    accountLast4 = "1234",
                    isFromCard = true,
                    creditLimit = BigDecimal("40000.4"),
                    expectNullMerchant = true
                )
            ),
            ParserTestCase(
                name = "Credit Card Spent at - candidate is time",
                message = "Spent INR 130 using Axis Bank Credit Card no. XX1234 on 24-10-26 09:59:42 IST at 09:59:42 IST. Avl limit: INR 40000.4. Not you? SMS BLOCK 1234 to +919951860002.",
                sender = "AX-AXISBK-S",
                expected = ExpectedTransaction(
                    amount = BigDecimal("130"),
                    currency = "INR",
                    type = com.pennywiseai.parser.core.TransactionType.CREDIT,
                    accountLast4 = "1234",
                    isFromCard = true,
                    creditLimit = BigDecimal("40000.4"),
                    expectNullMerchant = true
                )
            ),
            ParserTestCase(
                name = "Credit Card Spent at - candidate is empty dot",
                message = "Spent INR 130 using Axis Bank Credit Card no. XX1234 on 24-10-26 09:59:42 IST at . Avl limit: INR 40000.4. Not you? SMS BLOCK 1234 to +919951860002.",
                sender = "AX-AXISBK-S",
                expected = ExpectedTransaction(
                    amount = BigDecimal("130"),
                    currency = "INR",
                    type = com.pennywiseai.parser.core.TransactionType.CREDIT,
                    accountLast4 = "1234",
                    isFromCard = true,
                    creditLimit = BigDecimal("40000.4"),
                    expectNullMerchant = true
                )
            ),

            ParserTestCase(
                name = "Debit with UPI Ref suffix",
                message = "INR 130.00 debited from A/c no. XX1234 to BLINKIT. Bal INR 421.00. UPI Ref: 429812345682. Not you? SMS BLOCK 1234 to 919951860002 - Axis Bank",
                sender = "AX-AXISBK-S",
                expected = ExpectedTransaction(
                    amount = BigDecimal("130.00"),
                    currency = "INR",
                    type = com.pennywiseai.parser.core.TransactionType.EXPENSE,
                    merchant = "BLINKIT",
                    reference = "429812345682",
                    accountLast4 = "1234",
                    balance = BigDecimal("421.00")
                )
            )
        )

        val handleCases: List<Pair<String, Boolean>> = listOf(
            "AX-AXISBK-S" to true,
            "JD-AXISBK-S" to true,
            "CP-AXISBK-S" to true,
            "JX-AXISBK-S" to true,
            "AX-AXISBANK-S" to true,
            "AX-AXIS-S" to true,
            "AXISBK" to true,
            "AXISBANK" to true,
            "AXIS" to true,
            "HDFC" to false,
            "SBI" to false,
            "" to false
        )

        val result =
            return ParserTestUtils.runTestSuite(parser, testCases, handleCases, "Axis Bank Parser Tests")

    }
}

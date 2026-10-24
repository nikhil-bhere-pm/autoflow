package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.TransactionType
import java.math.BigDecimal

/**
 * Parser for Axis Bank SMS messages
 */
class AxisBankParser : BaseIndianBankParser() {

    override fun getBankName() = "Axis Bank"

    override fun canHandle(sender: String): Boolean {
        val normalizedSender = sender.uppercase()
        return normalizedSender.contains("AXIS BANK") ||
                normalizedSender.contains("AXISBANK") ||
                normalizedSender.contains("AXISBK") ||
                normalizedSender.contains("AXISB") ||
                // DLT patterns for transactions (-S suffix)
                normalizedSender.matches(Regex("^[A-Z]{2}-AXISBK-S$")) ||
                normalizedSender.matches(Regex("^[A-Z]{2}-AXISBANK-S$")) ||
                normalizedSender.matches(Regex("^[A-Z]{2}-AXIS-S$")) ||
                // Legacy patterns
                normalizedSender.matches(Regex("^[A-Z]{2}-AXISBK$")) ||
                normalizedSender.matches(Regex("^[A-Z]{2}-AXIS$")) ||
                // Direct sender IDs
                normalizedSender == "AXISBK" ||
                normalizedSender == "AXISBANK" ||
                normalizedSender == "AXIS"
    }

    /**
     * Foreign-currency card spend, in both Axis layouts (#888):
     * "Spent USD 100 Axis Bank Card no. XX0000 …" and the older
     * "Spent Card no. XX0000 USD 10 …". Group 1 = ISO code, 2 = amount.
     */
    private val foreignSpendPattern = Regex(
        """\bSpent\s+(?:Card\s+no\.\s*\S+\s+)?([A-Z]{3})\s+((?:[0-9,]+(?:\.\d{1,2})?|\.\d{1,2}))\b"""
    )

    private fun foreignSpend(message: String): MatchResult? =
        foreignSpendPattern.find(message)?.takeIf { it.groupValues[1] != "INR" }

    override fun transactionCurrency(message: String): String =
        foreignSpend(message)?.groupValues?.get(1) ?: getCurrency()

    // The card itself (balance, "Avl Limit: INR …") stays in INR.
    override fun accountCurrency(message: String): String? =
        if (foreignSpend(message) != null) getCurrency() else null

    override fun extractAmount(message: String): BigDecimal? {
        foreignSpend(message)?.let { match ->
            return match.groupValues[2].replace(",", "").toBigDecimalOrNull()
        }

        val inrDebitPattern = Regex(
            """INR\s+([0-9,]+(?:\.\d{2})?)\s+debited""",
            RegexOption.IGNORE_CASE
        )
        inrDebitPattern.find(message)?.let { match ->
            val amount = match.groupValues[1].replace(",", "")
            return try {
                BigDecimal(amount)
            } catch (e: NumberFormatException) {
                null
            }
        }

        val inrCreditPattern = Regex(
            """INR\s+([0-9,]+(?:\.\d{2})?)\s+credited""",
            RegexOption.IGNORE_CASE
        )
        inrCreditPattern.find(message)?.let { match ->
            val amount = match.groupValues[1].replace(",", "")
            return try {
                BigDecimal(amount)
            } catch (e: NumberFormatException) {
                null
            }
        }

        val paymentPattern = Regex(
            """Payment\s+of\s+INR\s+([0-9,]+(?:\.\d{2})?)""",
            RegexOption.IGNORE_CASE
        )
        paymentPattern.find(message)?.let { match ->
            val amount = match.groupValues[1].replace(",", "")
            return try {
                BigDecimal(amount)
            } catch (e: NumberFormatException) {
                null
            }
        }

        return super.extractAmount(message)
    }

    private fun isKnownNonMerchant(candidate: String): Boolean {
        val upper = candidate.uppercase().trim()
        if (upper.isEmpty()) return true

        val nonMerchantWords = setOf(
            "AXIS", "AXIS BANK", "AXISBANK", "AXISBK", "UTIB",
            "ATM", "CASH WITHDRAWAL", "WITHDRAWAL",
            "TRANSFER", "PAYMENT", "SENT", "DEBIT", "DEBITED", "CREDIT", "CREDITED",
            "UPI", "UPI REF", "REF", "REFERENCE", "IMPS", "NEFT", "RTGS", "NACH", "ECS",
            "BAL", "BALANCE", "AVL BAL", "AVL LIMIT", "AVL LMT", "LIMIT",
            "NOT YOU", "BLOCK", "BLOCKCARD", "SMS BLOCK",
            "INFO", "INR", "RS"
        )
        if (upper in nonMerchantWords) return true

        // Bank names / codes
        if (upper.endsWith(" BANK") || upper.endsWith(" BK")) return true

        // Account patterns: "A/C XX...", "ACCOUNT ...", "AC ..."
        if (upper.startsWith("A/C") || upper.startsWith("ACCOUNT") || upper.startsWith("AC ") || upper.startsWith("A/C NO")) return true
        if (upper.matches(Regex("""^(?:XX|\*{2,}|X+)?\d+$"""))) return true

        // QR code identifiers: "PAYTMQR...", "BHARATPE...", etc.
        if (upper.startsWith("PAYTMQR") || upper.startsWith("BHARATPE") || upper.matches(Regex("""^[A-Z0-9]+QR.*"""))) return true

        // Prefixed transfer / payment clauses
        if (upper.startsWith("TRANSFER TO ") || upper.startsWith("PAYMENT TO ") || upper.startsWith("PAID TO ") || upper.startsWith("SENT TO ")) return true

        // Reference numbers (e.g. standalone 6+ digit numbers)
        if (upper.matches(Regex("""^\d{6,}$"""))) return true
        if (upper.startsWith("REF ") || upper.startsWith("REF.") || upper.startsWith("TXN ") || upper.startsWith("UPI REF")) return true

        return false
    }

    private fun isDateOrTime(candidate: String): Boolean {
        val trimmed = candidate.trim()
        if (trimmed.matches(Regex("""^\d{1,4}[/-]\d{1,2}[/-]\d{1,4}$"""))) return true
        if (trimmed.matches(Regex("""^\d{1,2}[:.]\d{2}(?:[:.]\d{2})?(?:\s*(?:AM|PM|IST))?$""", RegexOption.IGNORE_CASE))) return true
        if (trimmed.matches(Regex("""^\d{1,2}[/-][A-Za-z]{3}[/-]\d{2,4}$""", RegexOption.IGNORE_CASE))) return true
        if (trimmed.matches(Regex("""^\d{1,2}[:.]\d{2}(?:[:.]\d{2})?\s+IST$""", RegexOption.IGNORE_CASE))) return true
        return false
    }

    override fun extractMerchant(message: String, sender: String): String? {
        // ATM withdrawal detection
        // Pattern: "debited from A/c no. XX589034 on AXIS BANK L" or similar
        val lowerMessage = message.lowercase()
        if (lowerMessage.contains("debited from a/c no.") &&
            lowerMessage.contains(" on axis bank")) {
            return "ATM"
        }

        // Also check for explicit ATM mentions
        if ((lowerMessage.contains("atm") || lowerMessage.contains("cash withdrawal")) &&
            lowerMessage.contains("debited")) {
            return "ATM"
        }

        // Debit card transaction pattern (Issue #120)
        // Pattern: "debited from A/c no. XXxxxxy on BURGRILL 04-12-2025 13:13:27 IST"
        // Extract merchant name between "on" and the date pattern
        val debitCardPattern = Regex(
            """debited from A/c no\. [^\s]+ on ([^0-9]+?)(?:\s+\d{2}-\d{2}-\d{2,4})""",
            RegexOption.IGNORE_CASE
        )
        debitCardPattern.find(message)?.let { match ->
            val merchant = cleanMerchantName(match.groupValues[1].trim())
            if (isValidMerchantName(merchant) && !isKnownNonMerchant(merchant)) {
                return merchant
            }
        }

        // Credit card "Spent" transactions with merchant on separate line
        // Format 1: "Spent INR 131\nAxis Bank Card no. XX0818\n05-10-25 09:43:27 IST\nSwiggy Limi\nAvl Limit:"
        // Format 2: "Spent\nCard no. XX7441\nINR 562\n01-09-25 12:04:18\nAVENUE SUPE\nAvl Lmt"
        val spentPatternWithIST = Regex(
            """Spent[\s\S]*?IST\s*\n\s*([^\n]+?)(?:\s*\n|\s*Avl Limit:|\s*Avl Lmt|\s*Not you?)""",
            RegexOption.IGNORE_CASE
        )
        spentPatternWithIST.find(message)?.let { match ->
            var merchant = match.groupValues[1].trim()

            // Clean up truncated merchant names by removing common truncation patterns
            merchant = merchant.replace(Regex("""\s+Limi$"""), "")  // "Swiggy Limi" -> "Swiggy"
            merchant = merchant.replace(Regex("""\s+Pay$"""), "")   // "Amazon Pay" -> "Amazon"
            merchant = merchant.replace(Regex("""\s+SUPE$"""), "")  // "AVENUE SUPE" -> "AVENUE"

            merchant = cleanMerchantName(merchant)
            if (isValidMerchantName(merchant) && !isKnownNonMerchant(merchant)) {
                return merchant
            }
        }

        // Alternative pattern without IST (for formats that use different time formats)
        val spentPatternWithTime = Regex(
            """Spent[\s\S]*?\d{2}-\d{2}-\d{2}\s+\d{2}:\d{2}:\d{2}\s*\n\s*([^\n]+?)(?:\s*\n|\s*Avl Limit:|\s*Avl Lmt|\s*Not you?)""",
            RegexOption.IGNORE_CASE
        )
        spentPatternWithTime.find(message)?.let { match ->
            var merchant = match.groupValues[1].trim()

            // Clean up truncated merchant names
            merchant = merchant.replace(Regex("""\s+Limi$"""), "")
            merchant = merchant.replace(Regex("""\s+Pay$"""), "")
            merchant = merchant.replace(Regex("""\s+SUPE$"""), "")

            merchant = cleanMerchantName(merchant)
            if (isValidMerchantName(merchant) && !isKnownNonMerchant(merchant)) {
                return merchant
            }
        }

        // Credit card "Spent ... at <merchant>" layout (both multi-line and single-line variants)
        val spentAtPattern = Regex(
            """(?:Spent[\s\S]*?\bat|Credit\s+Card[\s\S]*?\bat)\s+([A-Za-z0-9\s&._'*\/()#+\-]+?)(?:\s*\.\s*(?:Avl|Limit|Lmt|Not\s+you|$)|(?<=[A-Za-z0-9])\.(?:\s+|$)|(?:\s+(?:Avl\s+limit|Avl\s+lmt|Not\s+you)\b)|\n|$)""",
            RegexOption.IGNORE_CASE
        )
        spentAtPattern.find(message)?.let { match ->
            val rawCandidate = match.groupValues[1].trim()
            if (!rawCandidate.contains(":") && !isDateOrTime(rawCandidate)) {
                val candidate = cleanMerchantName(rawCandidate)
                if (isValidMerchantName(candidate) && !isKnownNonMerchant(candidate) && !isDateOrTime(candidate)) {
                    return candidate
                }
            }
        }

        // VPA-based extraction: "to VPA swiggy@axisbank", "to swiggy@axisbank", "towards VPA swiggy@upi"
        val vpaPattern = Regex(
            """(?:to|towards)\s+(?:VPA\s+)?([A-Za-z0-9._-]+)@[A-Za-z0-9._-]+""",
            RegexOption.IGNORE_CASE
        )
        vpaPattern.find(message)?.let { match ->
            val rawHandle = match.groupValues[1].trim()
            if (!isKnownNonMerchant(rawHandle)) {
                val cleanedHandle = cleanMerchantName(
                    rawHandle.replace('.', ' ').replace('_', ' ').replace('-', ' ').trim()
                )
                if (isValidMerchantName(cleanedHandle) && !isKnownNonMerchant(cleanedHandle)) {
                    return cleanedHandle
                }
            }
        }

        // NPCI UPI slash patterns:
        // Examples:
        // UPI/DR/429812345678/ZOMATO/UTIB
        // UPI/P2M/429812345678/SWIGGY/UTIB
        // UPI/429812345678/BLINKIT/UTIB
        val upiSlashPattern = Regex(
            """UPI/(?:(?:DR|CR|P2M|P2A)/)?(?:\d+/)?([^/\n]+?)(?:/[A-Za-z0-9]+|\s*\.|\s+Bal|\s+Avl|\s+Not\s+you|$)""",
            RegexOption.IGNORE_CASE
        )
        upiSlashPattern.find(message)?.let { match ->
            val candidate = cleanMerchantName(match.groupValues[1].trim())
            if (isValidMerchantName(candidate) && !isKnownNonMerchant(candidate)) {
                return candidate
            }
        }

        // Bounded "to/towards" pattern:
        // Examples:
        // "debited ... to BLINKIT. Bal INR 4870.00."
        // "debited ... to ZOMATO on 24-10-2026."
        // "debited ... to SWIGGY via UPI."
        val toPattern = Regex(
            """(?:to|towards)\s+([A-Za-z0-9\s&._-]+?)(?:\s+via\s+UPI|\s+on\s+\d|\s*\.\s*(?:Bal|Avl|Not|Ref|UPI|$)|(?<=[A-Za-z0-9])\.(?:\s+|$)|(?:\s+(?:Bal|Avl|UPI\s+Ref|Ref|Not\s+you)\b)|$)""",
            RegexOption.IGNORE_CASE
        )
        toPattern.find(message)?.let { match ->
            val merchant = cleanMerchantName(match.groupValues[1].trim())
            if (isValidMerchantName(merchant) && !isKnownNonMerchant(merchant)) {
                return merchant
            }
        }

        val infoPattern = Regex(
            """Info\s*[-–]\s*([^.\n]+?)(?:\.\s*Chk|\s*$)""",
            RegexOption.IGNORE_CASE
        )
        infoPattern.find(message)?.let { match ->
            val info = match.groupValues[1].trim()
            return when {
                info.contains("SALARY", ignoreCase = true) -> "Salary"
                else -> {
                    val candidate = cleanMerchantName(info)
                    if (isValidMerchantName(candidate) && !isKnownNonMerchant(candidate)) candidate else null
                }
            }
        }

        // Fall back to base class patterns, but strictly reject non-merchants
        val fallback = super.extractMerchant(message, sender)
        if (fallback != null && !isKnownNonMerchant(fallback)) {
            return fallback
        }

        return null
    }

    override fun extractAccountLast4(message: String): String? {
        super.extractAccountLast4(message)?.let { return it }
        // Pattern 1: "A/c no. XXNNNN" or "A/c no. XXxxxxy"
        val acNoPattern = Regex(
            """A/c\s+no\.\s+([X*xX\d]+)""",
            RegexOption.IGNORE_CASE
        )
        acNoPattern.find(message)?.let { match ->
            return extractLast4Digits(match.groupValues[1])
        }

        // Pattern 2: "Card no. XXNNNN"
        val cardNoPattern = Regex(
            """Card\s+no\.\s+([X*\d]+)""",
            RegexOption.IGNORE_CASE
        )
        cardNoPattern.find(message)?.let { match ->
            return extractLast4Digits(match.groupValues[1])
        }

        // Pattern 3: "Credit Card XXNNNN"
        val creditCardPattern = Regex(
            """Credit\s+Card\s+([X*\d]+)""",
            RegexOption.IGNORE_CASE
        )
        creditCardPattern.find(message)?.let { match ->
            return extractLast4Digits(match.groupValues[1])
        }

        return null
    }

    override fun extractReference(message: String): String? {
        // Pattern 1: UPI slash format: UPI/DR/<ref>, UPI/P2M/<ref>, UPI/<ref>
        val upiSlashRefPattern = Regex(
            """UPI/(?:(?:DR|CR|P2M|P2A)/)?([0-9]{9,12})""",
            RegexOption.IGNORE_CASE
        )
        upiSlashRefPattern.find(message)?.let { match ->
            return match.groupValues[1]
        }

        // Pattern 2: Explicit UPI Ref No: "UPI Ref: 429812345678", "UPI Ref No 429812345678"
        val upiRefPattern = Regex(
            """UPI\s+Ref(?:\s+No\.?)?[:\s]+([0-9]{9,12})""",
            RegexOption.IGNORE_CASE
        )
        upiRefPattern.find(message)?.let { match ->
            return match.groupValues[1]
        }

        val legacyUpiRefPattern = Regex(
            """UPI/[^/]+/([0-9]+)""",
            RegexOption.IGNORE_CASE
        )
        legacyUpiRefPattern.find(message)?.let { match ->
            return match.groupValues[1]
        }

        return super.extractReference(message)
    }

    override fun isTransactionMessage(message: String): Boolean {
        val lowerMessage = message.lowercase()

        // Skip Axis-specific payment confirmation messages (payment TO card, not spending)
        if (lowerMessage.contains("payment") &&
            lowerMessage.contains("has been received") &&
            lowerMessage.contains("towards your axis bank")
        ) {
            return false
        }

        // Base class handles common payment reminders and other non-transaction messages
        return super.isTransactionMessage(message)
    }

    override fun extractTransactionType(message: String): TransactionType? {
        val lowerMessage = message.lowercase()

        // Credit card transactions: if message contains "Avl Limit" or "Avl Lmt", it's a credit card
        if (lowerMessage.contains("avl limit") || lowerMessage.contains("avl lmt")) {
            return TransactionType.CREDIT
        }

        // Explicit credit card mention
        if ((lowerMessage.contains("credit card") || lowerMessage.contains(" cc ")) &&
            (lowerMessage.contains("debited") || lowerMessage.contains("spent"))
        ) {
            return TransactionType.CREDIT
        }

        // Fall back to base class for standard checks
        return super.extractTransactionType(message)
    }

    override fun extractAvailableLimit(message: String): BigDecimal? {
        // Axis Bank specific patterns using "INR" instead of "Rs"
        val axisCreditLimitPatterns = listOf(
            // "Avl Limit: INR 217162.72"
            Regex("""Avl\s+Limit:?\s*INR\s+([0-9,]+(?:\.\d{1,2})?)""", RegexOption.IGNORE_CASE),
            // "Avl Lmt INR 4632.87"
            Regex("""Avl\s+Lmt\s+INR\s+([0-9,]+(?:\.\d{1,2})?)""", RegexOption.IGNORE_CASE),
            // "Available limit INR 111,111.89"
            Regex("""Available\s+limit:?\s*INR\s+([0-9,]+(?:\.\d{1,2})?)""", RegexOption.IGNORE_CASE)
        )

        for (pattern in axisCreditLimitPatterns) {
            pattern.find(message)?.let { match ->
                val limitStr = match.groupValues[1].replace(",", "")
                return try {
                    BigDecimal(limitStr)
                } catch (e: NumberFormatException) {
                    null
                }
            }
        }

        // Fall back to base class patterns (for Rs-based formats)
        return super.extractAvailableLimit(message)
    }
}
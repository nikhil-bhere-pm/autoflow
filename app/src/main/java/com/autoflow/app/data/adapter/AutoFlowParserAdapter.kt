package com.autoflow.app.data.adapter

import com.autoflow.app.data.db.entity.TransactionEntity
import com.autoflow.app.data.model.RecordType
import com.autoflow.app.data.model.ReviewStatus
import com.autoflow.app.data.model.TransactionSource
import com.autoflow.app.data.model.UncertaintyReason
import com.pennywiseai.parser.core.ParsedTransaction
import com.pennywiseai.parser.core.TransactionType
import com.pennywiseai.parser.core.bank.BankParserFactory
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Adapter bridging PennyWise's parser-core to AutoFlow's domain and storage invariants.
 *
 * Enforces:
 * 1. Integer paise representation (Long) with zero float rounding.
 * 2. Strict expense-only scope: refunds take precedence, ordinary salary/income ignored.
 * 3. Dropping of balance inquiries and non-expense account transfers.
 * 4. Deterministic uncertainty bitmask generation.
 * 5. Zero raw SMS body retention (raw text is discarded immediately after in-memory parsing).
 */
object AutoFlowParserAdapter {

    private val REFUND_KEYWORDS = listOf(
        "refund",
        "refunded",
        "reversal",
        "reversed",
        "cashback",
        "returned",
        "chargeback",
        "money back",
        "credited back"
    )

    private val GENERIC_MERCHANT_PLACEHOLDERS = setOf(
        "unknown",
        "unknown merchant",
        "pos",
        "upi",
        "upi payment",
        "card payment",
        "merchant",
        "debit",
        "credit"
    )

    // Compiled regexes for narrowed guard evaluation
    private val otpPhraseRegex = Regex(
        """\b(?:is\s+(?:the\s+|your\s+)?(?:otp|one[- ]time[- ]password)|(?:otp|one[- ]time[- ]password)\s+is|(?:otp|one[- ]time[- ]password)\s+for\b[^.]*?\bis|verification code)\b"""
    )
    private val otpTokenRegex = Regex("""\b(?:otp|one[- ]time[- ]password)\b""")
    private val otpWarningContextRegex = Regex(
        """(?:never|do not|don't)\s+(?:share|disclose|reveal)\s+(?:your\s+)?(?:otp|one[- ]time[- ]password)|(?:otp|one[- ]time[- ]password),?\s*(?:cvv|pin)"""
    )
    private val candidateDigitRegex = Regex("""(?<!\w)\d{4,8}(?!\w)""")

    private val creditLimitUpdateRegex = Regex(
        """\bcredit limit\b.{0,35}?\b(?:is now|increased to|enhanced to|revised to|has been (?:increased|enhanced|revised))\b"""
    )

    private val failedDebitRegex = Regex(
        """\b(?:has failed|failed|declined|unsuccessful|cancelled|canceled|voided)\b"""
    )
    private val promisePhraseRegex = Regex(
        """\b(?:will\s+(?:be|get)\s+(?:reversed|refunded|credited|initiated|processed)|refund\s+(?:(?:has\s+been|is)\s+)?initiated|if\s+debited|refund,?\s*if\s+applicable|will\s+(?:reflect|credit|reverse)|within\s+\d+(?:-\d+)?\s*(?:working|business)?\s*days)\b"""
    )
    private val completedRefundActionRegex = Regex(
        """\b(?:refunded|reversed|chargeback|returned|credited\s+back)\b""",
        RegexOption.IGNORE_CASE
    )
    private val completedRefundWordRegex = Regex(
        """\b(?:refunded|refund\s+of|reversed|reversal|chargeback|returned|credited\s+back)\b""",
        RegexOption.IGNORE_CASE
    )

    private val xxDigitsRegex = Regex("""xx\d+""", RegexOption.IGNORE_CASE)
    private val junkMerchantPrefixes = listOf("your", "a/c", "account", "card")

    private val completedSpendStatementRegex = Regex(
        """\b(?:debited|debit\s+alert|spent|paid|sent(?:\s+to)?|transferred\s+to\s+(?!(?:your\b|a/c\b|account\b|bank\s+account\b))|withdrawn|purchase)\b"""
    )
    private val uncompletedDebitPhrasesRegex = Regex(
        """\b(?:if\s+debited|will\s+be\s+debited|scheduled\s+to\s+be\s+debited|not\s+debited|no\s+amount\s+has\s+been\s+debited)\b"""
    )

    private val fallbackMerchantRegex = Regex(
        """\b(?:at|from|with|transferred\s+to|paid\s+to)\s+([A-Za-z0-9 &]{2,30}?)(?=[.]|\s+on\b|\s+ref\b|\s+at\b|\s+for\b|\s+credited\b|\s+debited\b|\s+reversed\b|\s+refunded\b|\s+to\b|;|$)""",
        RegexOption.IGNORE_CASE
    )

    private fun hasCompletedSpend(lower: String): Boolean {
        val textWithoutUncompleted = uncompletedDebitPhrasesRegex.replace(lower, " ")
        return completedSpendStatementRegex.containsMatchIn(textWithoutUncompleted)
    }

    private fun isPromiseMessage(lower: String): Boolean {
        if (!promisePhraseRegex.containsMatchIn(lower)) return false
        val textWithoutPromises = promisePhraseRegex.replace(lower, " ")
        val hasCompletedRefund = completedRefundActionRegex.containsMatchIn(textWithoutPromises)
        if (hasCompletedRefund) return false

        // Failed/declined/cancelled gate must NOT be bypassed by hasCompletedSpend
        val isFailed = failedDebitRegex.containsMatchIn(lower) ||
            lower.contains("no amount has been debited") ||
            lower.contains("not debited") ||
            lower.contains("could not be processed") ||
            lower.contains("was declined")
        if (isFailed) return true

        // Legitimate purchases with promise-style footers (cashback, reward points, refund if applicable) must not be dropped
        if (hasCompletedSpend(lower)) return false

        return true
    }

    /**
     * Conservative phrase-based guard rejecting non-transaction messages
     * (OTP codes, promised refunds/reversals, failed/declined debits without completed refund, reminders/mandates, promos, card bills, and payment received on card).
     */
    fun isNonTransactionMessage(message: String): Boolean {
        val lower = message.lowercase()

        // 1. OTP / Verification / Authentication
        // Block only when an OTP code is present (4-8 digits within ~30 chars of OTP) or explicit OTP phrases.
        // Do NOT block on bare "OTP" or fraud warnings ("do not share" / "never share" / "never disclose").
        if (hasOtpCode(lower)) return true

        // 2. Global Promise Guard
        // Messages promising a future refund/reversal without a completed refund word must be ignored.
        if (isPromiseMessage(lower)) return true

        // 3. Failed / Declined / Cancelled / Negated debits
        // Block failed/declined debits unless they report a completed refund/reversal.
        val isFailedOrDeclined = failedDebitRegex.containsMatchIn(lower) ||
            lower.contains("no amount has been debited") ||
            lower.contains("not debited") ||
            lower.contains("could not be processed") ||
            lower.contains("was declined")

        if (isFailedOrDeclined) {
            val textWithoutPromises = promisePhraseRegex.replace(lower, " ")
            val hasCompletedRefund = completedRefundWordRegex.containsMatchIn(textWithoutPromises)
            if (!hasCompletedRefund) {
                return true
            }
        }

        // 4. Credit limit / card limit updates
        // Block only when "credit limit" is followed within ~20-35 characters by "is now", "increased to",
        // "enhanced to", "revised to", or "has been increased/enhanced/revised". Never block "Avl limit" or "Available credit limit".
        if (creditLimitUpdateRegex.containsMatchIn(lower)) return true

        // 4. Period / monthly spend summary
        if (lower.contains("you have spent") ||
            Regex("""\bspent\b.*?\bthis month\b""").containsMatchIn(lower) ||
            lower.contains("monthly spend") ||
            lower.contains("spend summary") ||
            lower.contains("total spends")
        ) return true

        // 5. Upcoming debits / mandates / reminders
        if (lower.contains("will be debited") ||
            lower.contains("will be deducted") ||
            lower.contains("scheduled to be debited") ||
            lower.contains("mandate scheduled")
        ) return true
        if (lower.startsWith("reminder:") || lower.startsWith("reminder -") ||
            lower.contains("payment reminder") || lower.contains("mandate reminder")
        ) return true

        // 6. Promos / offers / discounts
        if (lower.contains("t&c apply") ||
            lower.contains("terms and conditions apply") ||
            lower.contains("off!") ||
            lower.contains("cashback up to") ||
            lower.contains("win up to") ||
            lower.contains("avail discount")
        ) return true

        // 7. Card bill / statements / amount due
        if (lower.contains("total amount due") ||
            lower.contains("minimum amount due") ||
            lower.contains("minimum due") ||
            lower.contains("min due") ||
            lower.contains("bill generated") ||
            lower.contains("statement generated") ||
            lower.contains("pay by")
        ) return true

        // 8. Payment received towards credit card debt
        if (Regex("""\breceived towards (?:your )?(?:axis bank |hdfc |icici |sbi )?(?:credit )?card\b""").containsMatchIn(lower) ||
            lower.contains("payment received towards")
        ) return true

        return false
    }

    private fun hasOtpCode(lower: String): Boolean {
        if (otpPhraseRegex.containsMatchIn(lower)) return true

        for (match in otpTokenRegex.findAll(lower)) {
            val start = (match.range.first - 35).coerceAtLeast(0)
            val end = (match.range.last + 36).coerceAtMost(lower.length)
            val window = lower.substring(start, end)

            // If OTP appears in a warning clause (e.g. "never share OTP" / "never disclose your One Time Password"), skip unless an OTP code indicator exists
            if (otpWarningContextRegex.containsMatchIn(window)) {
                continue
            }

            for (dm in candidateDigitRegex.findAll(window)) {
                val numIdx = dm.range.first
                val prefix = window.substring((numIdx - 10).coerceAtLeast(0), numIdx).trimEnd()
                if (prefix.endsWith("rs") || prefix.endsWith("rs.") || prefix.endsWith("inr") ||
                    prefix.endsWith("bal") || prefix.endsWith("bal:") || prefix.endsWith("bal.") ||
                    prefix.endsWith("a/c") || prefix.endsWith("card") || prefix.endsWith("no.") ||
                    prefix.endsWith("block") || prefix.endsWith("call")
                ) {
                    continue
                }
                return true
            }
        }
        return false
    }

    private val advisoryWarningSanitizationRegex = Regex(
        """(?i)\b(?:never|do\s+not|don't)\s+(?:share|disclose|reveal)\b[^.]*?\b(?:otp|one[- ]time[- ]password|cvv|pin)\b[^.]*?(?=[.]|$)"""
    )

    /**
     * Parses an incoming SMS message in-memory and adapts it directly to an AutoFlow [TransactionEntity].
     *
     * Note: The [smsBody] parameter is dereferenced upon method completion and is NOT stored
     * in the returned entity or written to any logs.
     */
    fun parseAndAdapt(
        smsBody: String,
        sender: String,
        timestamp: Long
    ): TransactionEntity? {
        if (isNonTransactionMessage(smsBody)) {
            return null
        }
        val sanitizedBody = advisoryWarningSanitizationRegex.replace(smsBody, " ")
        val parsed = BankParserFactory.parse(sanitizedBody, sender, timestamp) ?: return null
        return adapt(parsed, smsBody, timestamp)
    }

    /**
     * Adapts an already parsed [ParsedTransaction] from parser-core to AutoFlow's model.
     */
    fun adapt(
        parsed: ParsedTransaction,
        smsBodySnippet: String,
        fallbackTimestamp: Long
    ): TransactionEntity? {
        if (isNonTransactionMessage(smsBodySnippet)) {
            return null
        }
        // 1. Drop balance inquiries and investments immediately
        if (parsed.type == TransactionType.BALANCE_UPDATE || parsed.type == TransactionType.INVESTMENT) {
            return null
        }

        // 2. Disambiguate transaction type: Refund vs Expense vs Ignored Income
        val isRefund = isRefundSignal(parsed, smsBodySnippet)

        val recordType = when {
            isRefund -> RecordType.REFUND
            parsed.type == TransactionType.EXPENSE || parsed.type == TransactionType.CREDIT -> {
                if (hasCompletedSpend(smsBodySnippet.lowercase())) {
                    RecordType.EXPENSE
                } else {
                    return null
                }
            }
            parsed.type == TransactionType.TRANSFER -> {
                // If it's an internal transfer between known accounts, ignore it.
                if (parsed.fromAccount != null && parsed.toAccount != null && parsed.fromAccount == parsed.toAccount) {
                    return null
                }
                // Outbound payments/transfers are expenses; inbound are ignored unless marked refund
                if (hasCompletedSpend(smsBodySnippet.lowercase())) {
                    RecordType.EXPENSE
                } else {
                    return null
                }
            }
            // Ordinary income (salary, external deposits) without refund keywords is ignored unless outbound spend occurred
            parsed.type == TransactionType.INCOME -> {
                if (hasCompletedSpend(smsBodySnippet.lowercase())) {
                    RecordType.EXPENSE
                } else {
                    return null
                }
            }
            else -> return null
        }

        // 3. Convert BigDecimal to Integer Paise (Long)
        val amountPaise = try {
            (parsed.amount * BigDecimal(100)).setScale(0, RoundingMode.HALF_UP).toLong()
        } catch (e: Exception) {
            return null
        }
        if (amountPaise <= 0L) return null

        // 4. Clean merchant name and evaluate uncertainty
        val cleanedMerchant = cleanMerchant(parsed.merchant, smsBodySnippet)
        val uncertaintyReasons = mutableSetOf<UncertaintyReason>()

        if (cleanedMerchant == "Unknown Merchant" || GENERIC_MERCHANT_PLACEHOLDERS.contains(cleanedMerchant.lowercase())) {
            uncertaintyReasons.add(UncertaintyReason.UNKNOWN_MERCHANT)
        }

        // Check for ambiguous amount (e.g. credit limit ambiguity in parser)
        if (parsed.creditLimit != null && parsed.creditLimit == parsed.amount) {
            uncertaintyReasons.add(UncertaintyReason.AMBIGUOUS_AMOUNT)
        }

        // Unmatched refund invariant: New refunds start as UNMATCHED_REFUND until reconciled
        if (recordType == RecordType.REFUND) {
            uncertaintyReasons.add(UncertaintyReason.UNMATCHED_REFUND)
        }

        val reviewStatus = if (uncertaintyReasons.isEmpty()) {
            ReviewStatus.CONFIRMED
        } else {
            ReviewStatus.NEEDS_REVIEW
        }

        // 5. Compute immutable transaction hash for Tier-1 deduplication
        val txHash = parsed.transactionHash ?: parsed.generateTransactionId()

        // 6. Construct TransactionEntity (raw SMS text is NOT stored)
        return TransactionEntity(
            amountPaise = amountPaise,
            recordType = recordType,
            dateTime = if (parsed.timestamp > 0) parsed.timestamp else fallbackTimestamp,
            merchantName = cleanedMerchant,
            category = "Others",
            accountNumber = parsed.accountLast4,
            bankName = parsed.bankName.ifBlank { null },
            reference = parsed.reference,
            transactionHash = txHash,
            source = TransactionSource.SMS_AUTO,
            reviewStatus = reviewStatus,
            uncertaintyBits = UncertaintyReason.toBitmask(uncertaintyReasons)
        )
    }

    private val accountFragmentRegex = Regex("""(?:\*+|xx)\d+|\b(?:a/c|account)\b""", RegexOption.IGNORE_CASE)

    private fun isRefundSignal(parsed: ParsedTransaction, message: String): Boolean {
        val lowerMessage = message.lowercase()
        val hasRefundKeyword = REFUND_KEYWORDS.any { lowerMessage.contains(it) }
        if (!hasRefundKeyword) {
            return false
        }

        val textWithoutPromises = promisePhraseRegex.replace(lowerMessage, " ")
        val hasCompletedRefund = completedRefundWordRegex.containsMatchIn(textWithoutPromises)
        if (hasCompletedRefund) {
            return true
        }

        if (hasCompletedSpend(lowerMessage)) {
            return false
        }

        return true
    }

    private val JUNK_MERCHANT_NAMES = setOf(
        "upi",
        "neft",
        "imps",
        "rtgs",
        "atm",
        "pos",
        "card",
        "bank",
        "unknown",
        "unknown merchant",
        "merchant",
        "debit",
        "credit",
        "vpa",
        "upi payment",
        "card payment"
    )

    private val trailingViaRegex = Regex("""\s+via(?:\s+.*)?$""", RegexOption.IGNORE_CASE)

    private val vpaRegex = Regex("""\b([A-Za-z0-9.\-_]+)@[A-Za-z0-9.\-_]+\b""")

    private val ignoredVpaPrefixes = setOf(
        "support", "care", "help", "contact", "customercare", "helpdesk", "grievance", "nodal"
    )

    private fun extractVpaMerchant(smsBody: String): String? {
        for (match in vpaRegex.findAll(smsBody)) {
            val handle = match.groupValues[1]
            if (handle.length in 3..20 &&
                handle.all { it.isLetter() || it == '.' || it == '-' } &&
                handle.any { it.isLetter() } &&
                !ignoredVpaPrefixes.contains(handle.lowercase())
            ) {
                return handle.uppercase()
            }
        }
        return null
    }

    private fun isJunkOrPlaceholderMerchant(merchant: String?): Boolean {
        if (merchant.isNullOrBlank()) return true
        val lower = merchant.trim().lowercase()
        if (JUNK_MERCHANT_NAMES.contains(lower)) return true
        if (GENERIC_MERCHANT_PLACEHOLDERS.contains(lower)) return true
        if (isJunkMerchant(merchant)) return true
        return false
    }

    private fun isJunkMerchant(merchant: String): Boolean {
        val lower = merchant.trim().lowercase()
        if (xxDigitsRegex.containsMatchIn(lower)) return true
        if (junkMerchantPrefixes.any { lower.startsWith(it) }) return true
        if (lower.contains(" for ") || lower.contains(" from ")) return true
        if (accountFragmentRegex.containsMatchIn(lower)) return true
        return false
    }

    private fun extractFallbackMerchant(smsBody: String): String? {
        for (match in fallbackMerchantRegex.findAll(smsBody)) {
            val candidate = match.groupValues[1].trim()
            if (candidate.length in 2..30 &&
                !isJunkMerchant(candidate) &&
                candidate.all { it.isLetterOrDigit() || it == '&' || it == ' ' }
            ) {
                return candidate
            }
        }
        return null
    }

    private fun cleanMerchant(merchant: String?, smsBody: String): String {
        // Strip trailing "via <anything>"
        val rawCleaned = merchant?.replace(trailingViaRegex, "")?.trim()
        if (!isJunkOrPlaceholderMerchant(rawCleaned)) {
            return rawCleaned!!
        }

        // Try fallback regex
        val fallback = extractFallbackMerchant(smsBody)?.replace(trailingViaRegex, "")?.trim()
        if (!isJunkOrPlaceholderMerchant(fallback)) {
            return fallback!!
        }

        // Try VPA extraction
        val vpa = extractVpaMerchant(smsBody)
        if (vpa != null) {
            return vpa
        }

        return "Unknown Merchant"
    }
}

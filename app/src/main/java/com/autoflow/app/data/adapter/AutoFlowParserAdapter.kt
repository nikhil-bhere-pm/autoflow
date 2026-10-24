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
        "money back"
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
        """\b(?:will\s+(?:be|get)\s+(?:reversed|refunded|credited|initiated|processed)|refund\s+(?:(?:has\s+been|is)\s+)?initiated|if\s+debited|will\s+(?:reflect|credit|reverse)|within\s+\d+(?:-\d+)?\s*(?:working|business)?\s*days)\b"""
    )
    private val completedRefundWordRegex = Regex(
        """\b(?:refunded|reversed|credited|returned|chargeback)\b"""
    )

    private val xxDigitsRegex = Regex("""xx\d+""", RegexOption.IGNORE_CASE)
    private val junkMerchantPrefixes = listOf("your", "a/c", "account", "card")

    private val fallbackMerchantRegex = Regex(
        """\b(?:at|from)\s+([A-Za-z0-9 &]{2,30}?)(?=[.]|\s+on\b|\s+ref\b|\s+at\b|$)""",
        RegexOption.IGNORE_CASE
    )

    private fun isPromiseMessage(lower: String): Boolean {
        if (!promisePhraseRegex.containsMatchIn(lower)) return false
        val textWithoutPromises = promisePhraseRegex.replace(lower, " ")
        val hasCompletedWord = completedRefundWordRegex.containsMatchIn(textWithoutPromises)
        return !hasCompletedWord
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
        val parsed = BankParserFactory.parse(smsBody, sender, timestamp) ?: return null
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
            parsed.type == TransactionType.EXPENSE || parsed.type == TransactionType.CREDIT -> RecordType.EXPENSE
            parsed.type == TransactionType.TRANSFER -> {
                // If it's an internal transfer between known accounts, ignore it.
                if (parsed.fromAccount != null && parsed.toAccount != null && parsed.fromAccount == parsed.toAccount) {
                    return null
                }
                // Outbound payments/transfers are expenses; inbound are ignored unless marked refund
                RecordType.EXPENSE
            }
            // Ordinary income (salary, external deposits) without refund keywords is ignored
            parsed.type == TransactionType.INCOME -> return null
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

    private fun isRefundSignal(parsed: ParsedTransaction, message: String): Boolean {
        val lowerMessage = message.lowercase()
        return REFUND_KEYWORDS.any { lowerMessage.contains(it) }
    }

    private fun isJunkMerchant(merchant: String): Boolean {
        val lower = merchant.trim().lowercase()
        if (xxDigitsRegex.containsMatchIn(lower)) return true
        if (junkMerchantPrefixes.any { lower.startsWith(it) }) return true
        if (lower.contains(" for ")) return true
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
        if (!merchant.isNullOrBlank() && !isJunkMerchant(merchant)) {
            return merchant.trim()
        }
        val fallback = extractFallbackMerchant(smsBody)
        return fallback ?: "Unknown Merchant"
    }
}

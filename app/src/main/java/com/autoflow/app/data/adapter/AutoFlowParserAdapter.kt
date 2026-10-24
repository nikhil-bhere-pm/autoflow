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

    /**
     * Conservative phrase-based guard rejecting non-transaction messages
     * (OTP, failed/declined debits, reminders/mandates, promos, card bills, and payment received on card).
     */
    fun isNonTransactionMessage(message: String): Boolean {
        val lower = message.lowercase()

        // 1. OTP / Verification / Authentication
        if (Regex("""\b(?:otp|one[- ]time[- ]password|verification code)\b""").containsMatchIn(lower)) return true
        if (lower.contains("do not share") || lower.contains("never share")) return true

        // 2. Failed / Declined / Cancelled / Negated debits
        if (Regex("""\b(?:has failed|failed|declined|unsuccessful|cancelled|canceled|voided)\b""").containsMatchIn(lower)) return true
        if (lower.contains("no amount has been debited") ||
            lower.contains("not debited") ||
            lower.contains("could not be processed") ||
            lower.contains("was declined")
        ) return true

        // 3. Credit limit / card limit updates
        if (lower.contains("credit limit") && (
                lower.contains("is now") ||
                lower.contains("increased") ||
                lower.contains("enhanced") ||
                lower.contains("good news") ||
                lower.contains("has been")
            )) return true

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
        if (lower.startsWith("reminder:") || lower.contains(" reminder:")) return true

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
        val cleanedMerchant = cleanMerchant(parsed.merchant)
        val uncertaintyReasons = mutableSetOf<UncertaintyReason>()

        if (cleanedMerchant == null || GENERIC_MERCHANT_PLACEHOLDERS.contains(cleanedMerchant.lowercase())) {
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
            merchantName = cleanedMerchant ?: "Unknown Merchant",
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

    private fun cleanMerchant(merchant: String?): String? {
        if (merchant.isNullOrBlank()) return null
        val trimmed = merchant.trim()
        return trimmed.ifBlank { null }
    }
}

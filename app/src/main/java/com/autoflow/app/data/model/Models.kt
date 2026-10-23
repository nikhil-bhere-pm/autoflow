package com.autoflow.app.data.model

enum class RecordType {
    EXPENSE,
    REFUND
}

enum class TransactionSource {
    SMS_AUTO,
    MANUAL_CASH
}

enum class ReviewStatus {
    CONFIRMED,
    NEEDS_REVIEW,
    EXCLUDED
}

enum class UncertaintyReason(val bit: Int) {
    UNKNOWN_MERCHANT(1 shl 0),   // 1
    AMBIGUOUS_AMOUNT(1 shl 1),   // 2
    AMBIGUOUS_REFUND(1 shl 2),   // 4
    UNMATCHED_REFUND(1 shl 3);   // 8

    companion object {
        fun toBitmask(reasons: Set<UncertaintyReason>): Int {
            return reasons.fold(0) { acc, reason -> acc or reason.bit }
        }

        fun fromBitmask(bitmask: Int): Set<UncertaintyReason> {
            return entries.filter { (bitmask and it.bit) != 0 }.toSet()
        }
    }
}

enum class MatchStatus {
    CONFIRMED,
    AMBIGUOUS_PROPOSED
}

enum class SavingsType {
    ACTUAL_SAVED,
    AVOIDED_PURCHASE
}

package com.autoflow.app.domain.search

import java.math.BigDecimal
import java.math.RoundingMode

object TransactionSearchHelper {
    const val MAX_AMOUNT_PAISE = 10_000_000_000L

    /**
     * Escapes SQLite LIKE pattern characters (% and _) using backslash as the escape char.
     */
    fun escapeLike(input: String): String {
        return input
            .replace("\\", "\\\\")
            .replace("%", "\\%")
            .replace("_", "\\_")
    }

    /**
     * Parses a user input string into exact paise if it represents a valid rupee amount.
     * Rules:
     * - Strips leading currency symbols/words (₹, rs., rs, in any case).
     * - Trims whitespace and trailing periods.
     * - Parses decimal number with up to 2 decimal places (rounds half-up to paise).
     * - Valid range: 0L..MAX_AMOUNT_PAISE.
     * - Examples: "320" -> 32000L, "320.00" -> 32000L, "320.5" -> 32050L, "15.75" -> 1575L.
     */
    fun parseRupeesToPaise(input: String): Long? {
        val clean = input.trim()
            .removePrefix("₹")
            .removePrefix("rs.")
            .removePrefix("rs")
            .removePrefix("Rs.")
            .removePrefix("Rs")
            .removePrefix("RS.")
            .removePrefix("RS")
            .trim()
            .trimEnd('.')
        if (clean.isBlank()) return null
        return try {
            val bd = BigDecimal(clean)
            if (bd < BigDecimal.ZERO) return null
            val paise = bd.multiply(BigDecimal(100)).setScale(0, RoundingMode.HALF_UP).toLong()
            if (paise in 0L..MAX_AMOUNT_PAISE) paise else null
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Extracts digits from the input to match as a substring against the raw paise integer,
     * only when the search query represents a numeric amount (optionally with currency symbols/decimals).
     */
    fun extractDigitsPattern(input: String): String? {
        val clean = input.trim()
            .removePrefix("₹")
            .removePrefix("rs.")
            .removePrefix("rs")
            .removePrefix("Rs.")
            .removePrefix("Rs")
            .removePrefix("RS.")
            .removePrefix("RS")
            .trim()
        val isNumeric = clean.isNotEmpty() && clean.all { it.isDigit() || it == '.' || it == ',' }
        if (!isNumeric) return null
        val digits = clean.filter { it.isDigit() }
        return if (digits.isNotEmpty()) "%" + escapeLike(digits) + "%" else null
    }

    /**
     * Constructs a LIKE pattern for case-insensitive substring search over text columns.
     */
    fun buildTextPattern(query: String): String {
        return "%" + escapeLike(query.trim()) + "%"
    }
}

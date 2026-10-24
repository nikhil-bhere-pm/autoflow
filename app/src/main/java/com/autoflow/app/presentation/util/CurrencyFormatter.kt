package com.autoflow.app.presentation.util

import java.util.Locale

object CurrencyFormatter {

    /**
     * Formats integer paise into Indian currency representation: ₹1,23,456.00
     * Always uses two decimals, with Indian numbering system grouping.
     */
    fun formatPaiseToInr(amountPaise: Long): String {
        val isNegative = amountPaise < 0
        val absPaise = if (isNegative) -amountPaise else amountPaise
        val rupees = absPaise / 100
        val paiseRemainder = absPaise % 100

        val paiseString = String.format(Locale.US, "%02d", paiseRemainder)
        val rupeesString = formatIndianRupees(rupees)

        val prefix = if (isNegative) "-₹" else "₹"
        return "$prefix$rupeesString.$paiseString"
    }

    /**
     * Parses a user-entered rupee string (e.g. "130", "130.5", "130.50", " 1,200 ")
     * directly into integer paise without floating point inaccuracy.
     * Returns null if input is invalid or non-positive.
     */
    fun parseRupeesToPaise(input: String): Long? {
        val cleaned = input.trim().replace(",", "")
        if (cleaned.isEmpty()) return null

        val parts = cleaned.split(".")
        if (parts.size > 2) return null

        val rupeesPart = parts[0]
        val paisePart = if (parts.size == 2) parts[1] else ""

        if (paisePart.length > 2) return null

        val rupees = if (rupeesPart.isEmpty()) 0L else rupeesPart.toLongOrNull() ?: return null
        if (rupees < 0) return null

        val paise = when (paisePart.length) {
            0 -> 0L
            1 -> {
                val digit = paisePart.toLongOrNull() ?: return null
                digit * 10
            }
            2 -> paisePart.toLongOrNull() ?: return null
            else -> return null
        }

        val totalPaise = rupees * 100 + paise
        return if (totalPaise > 0) totalPaise else null
    }

    private fun formatIndianRupees(amount: Long): String {
        val s = amount.toString()
        if (s.length <= 3) return s

        val last3 = s.substring(s.length - 3)
        val remaining = s.substring(0, s.length - 3)

        val result = StringBuilder()
        var count = 0
        for (i in remaining.length - 1 downTo 0) {
            result.append(remaining[i])
            count++
            if (count % 2 == 0 && i > 0) {
                result.append(',')
            }
        }
        return "${result.reverse()},$last3"
    }
}

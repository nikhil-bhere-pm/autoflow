package com.autoflow.app.domain.category

import com.autoflow.app.data.adapter.AutoFlowParserAdapter

object CategoryEngine {

    data class Resolution(
        val category: String,
        val isExcluded: Boolean = false
    )

    private val BUILT_IN_ALIASES: Map<String, String> = mapOf(
        "swiggy inst" to "Groceries",
        "instamart" to "Groceries",
        "makemytrip" to "Travel",
        "make my trip" to "Travel",
        "irctc" to "Travel"
    )

    /**
     * Normalization for keys and matching:
     * lowercase, replace every non-alphanumeric character with a space, collapse spaces, trim.
     * ("SWIGGY   INST." -> "swiggy inst")
     */
    fun normalizeKey(name: String): String {
        return name.lowercase()
            .map { ch -> if (ch.isLetterOrDigit()) ch else ' ' }
            .joinToString("")
            .replace(Regex("""\s+"""), " ")
            .trim()
    }

    /**
     * Decides whether a merchant name is eligible to have a merchant category rule stored.
     * Junk merchants ("Unknown Merchant", blank/null, or anything in adapter's junk set) return false.
     */
    fun shouldStoreRule(merchantName: String?): Boolean {
        if (merchantName.isNullOrBlank()) return false
        val normalized = normalizeKey(merchantName)
        if (normalized.isBlank()) return false
        if (AutoFlowParserAdapter.isJunkMerchant(merchantName)) return false
        if (AutoFlowParserAdapter.isJunkMerchant(normalized)) return false
        if (normalized == "unknown merchant" || normalized == "unknown") return false
        return true
    }

    /**
     * Resolves category according to strict design priorities:
     * 1. User rule (exact match on normalized key)
     * 2. Built-in aliases (exact match on normalized key)
     * 3. Keyword mapper (whole-word/phrase match on normalized text)
     * 4. "Others"
     */
    fun resolveCategory(
        merchantName: String,
        userCategory: String? = null,
        smsBodySnippet: String? = null
    ): Resolution {
        val normalized = normalizeKey(merchantName)

        // 1. User saved rule (exact match on normalized key; NO prefix or partial-key fallback)
        if (!userCategory.isNullOrBlank()) {
            return Resolution(category = userCategory.trim(), isExcluded = false)
        }

        // 2. Built-in aliases (exact match on normalized key)
        val alias = BUILT_IN_ALIASES[normalized]
        if (alias != null) {
            return Resolution(category = alias, isExcluded = false)
        }

        // 3. Keyword mapper (whole-word/phrase on normalized merchant)
        if (normalized.isNotBlank()) {
            val kwResolution = KeywordCategoryMapper.mapCategory(normalized)
            if (kwResolution != null) {
                return Resolution(
                    category = kwResolution.category,
                    isExcluded = kwResolution.isCreditCardPayment
                )
            }
        }

        // If merchant was generic/unknown or blank, check SMS body text for Credit Card Payment
        if (smsBodySnippet != null && (normalized.isBlank() || AutoFlowParserAdapter.isJunkMerchant(merchantName))) {
            val normalizedBody = normalizeKey(smsBodySnippet)
            val bodyKwResolution = KeywordCategoryMapper.mapCategory(normalizedBody)
            if (bodyKwResolution != null && bodyKwResolution.isCreditCardPayment) {
                return Resolution(
                    category = "Others",
                    isExcluded = true
                )
            }
        }

        // 4. Fallback to "Others"
        return Resolution(category = "Others", isExcluded = false)
    }
}

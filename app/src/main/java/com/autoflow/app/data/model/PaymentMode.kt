package com.autoflow.app.data.model

object PaymentMode {
    const val CASH = "CASH"
    const val CREDIT = "CREDIT"
    const val BANK = "BANK"
    const val UPI = "UPI"
    const val UNKNOWN = "UNKNOWN"

    val ALL: Set<String> = setOf(CASH, CREDIT, BANK, UPI, UNKNOWN)
    val UI_MODES: List<String> = listOf(CASH, CREDIT, BANK, UPI)

    fun isValid(mode: String): Boolean = ALL.contains(mode)
}

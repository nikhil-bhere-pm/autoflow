package com.autoflow.app.domain.goals

/**
 * Monetary limit: 10,00,00,000.00 rupees (10 crore rupees).
 * 1 rupee = 100 paise.
 * 10,00,00,000.00 * 100 = 10,000,000,000 paise (10 billion paise = 10^10 paise).
 */
const val MAX_AMOUNT_PAISE: Long = 10_000_000_000L

/** Maximum goal name length: 40 characters */
const val MAX_GOAL_NAME_LENGTH: Int = 40

/** Future tolerance window: 5 minutes in milliseconds */
const val FUTURE_TOLERANCE_MILLIS: Long = 5 * 60 * 1000L

/**
 * Domain errors for goals validation and operations.
 */
sealed interface GoalError {
    /** Goal name is blank or empty */
    data object BlankName : GoalError

    /** Goal name exceeds 40 characters */
    data class NameTooLong(val length: Int, val maxLength: Int = MAX_GOAL_NAME_LENGTH) : GoalError

    /** Goal name matches an existing non-archived goal case-insensitively */
    data class DuplicateName(val name: String) : GoalError

    /** Amount or target is <= 0 */
    data class BadAmount(val amountPaise: Long) : GoalError

    /** Amount or target exceeds MAX_AMOUNT_PAISE */
    data class OverLimit(val amountPaise: Long, val limitPaise: Long = MAX_AMOUNT_PAISE) : GoalError

    /** SET_ASIDE on an AVOIDANCE goal */
    data class WrongEntryType(val kind: GoalKind, val type: EntryType) : GoalError

    /** Entry occurredAt is more than 5 minutes in the future */
    data class FutureDate(val occurredAt: Long, val maxAllowedAt: Long) : GoalError

    /** Referenced goal ID was not found */
    data class GoalNotFound(val goalId: Long) : GoalError

    /** Referenced entry ID was not found */
    data class EntryNotFound(val entryId: Long) : GoalError
}

/**
 * Sealed result type for domain operations.
 */
sealed class GoalResult<out T> {
    data class Success<out T>(val data: T) : GoalResult<T>() {
        val value: T get() = data
    }

    data class Error(val error: GoalError) : GoalResult<Nothing>()

    val isSuccess: Boolean get() = this is Success
    val isError: Boolean get() = this is Error

    fun getOrNull(): T? = (this as? Success)?.data
    fun errorOrNull(): GoalError? = (this as? Error)?.error

    fun getOrThrow(): T = when (this) {
        is Success -> data
        is Error -> throw IllegalStateException("GoalResult was Error: $error")
    }
}

typealias Result<T> = GoalResult<T>

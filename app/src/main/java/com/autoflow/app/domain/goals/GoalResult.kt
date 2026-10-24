package com.autoflow.app.domain.goals

const val MAX_AMOUNT_PAISE: Long = 10_000_000_000L
const val MAX_GOAL_NAME_LENGTH: Int = 30
const val MAX_AVOIDED_DESCRIPTION_LENGTH: Int = 80
const val MAX_NOTE_LENGTH: Int = 120
const val FUTURE_TOLERANCE_MILLIS: Long = 5 * 60 * 1000L

sealed interface GoalError {
    data object BlankName : GoalError
    data class NameTooLong(val length: Int, val maxLength: Int = MAX_GOAL_NAME_LENGTH) : GoalError
    data class DuplicateName(val name: String) : GoalError
    data class BadAmount(val amountPaise: Long) : GoalError
    data class OverLimit(val amountPaise: Long, val limitPaise: Long = MAX_AMOUNT_PAISE) : GoalError
    data class GoalNotFound(val goalId: Long) : GoalError
    data class EntryNotFound(val entryId: Long) : GoalError
    data class ExpenseNotFound(val transactionId: Long) : GoalError
    data class WrongGoalKind(val kind: GoalKind, val message: String = "Entry type not allowed for goal kind") : GoalError
    data class WrongEntryType(val kind: GoalKind, val type: EntryType) : GoalError
    data class InsufficientBalance(val availablePaise: Long, val requestedPaise: Long) : GoalError
    data class AmountExceedsExpense(
        val amountPaise: Long,
        val expenseAmountPaise: Long,
        val remainingLinkablePaise: Long = expenseAmountPaise
    ) : GoalError
    data object ExpenseLocked : GoalError
    data class FutureDate(val occurredAt: Long, val maxAllowedAt: Long) : GoalError
    data object DescriptionRequired : GoalError
    data class DescriptionTooLong(val length: Int, val maxLength: Int = MAX_AVOIDED_DESCRIPTION_LENGTH) : GoalError
    data class NoteTooLong(val length: Int, val maxLength: Int = MAX_NOTE_LENGTH) : GoalError
}

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

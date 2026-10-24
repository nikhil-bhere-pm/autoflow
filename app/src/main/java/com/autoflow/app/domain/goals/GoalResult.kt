package com.autoflow.app.domain.goals

const val MAX_AMOUNT_PAISE: Long = 10_000_000_000L
const val MAX_GOAL_NAME_LENGTH: Int = 30
const val MAX_NOTE_LENGTH: Int = 100

sealed interface GoalError {
    data object BlankName : GoalError
    data class NameTooLong(val length: Int, val maxLength: Int = MAX_GOAL_NAME_LENGTH) : GoalError
    data class NameTaken(val name: String) : GoalError
    data class BadAmount(val amountPaise: Long) : GoalError
    data class OverLimit(val amountPaise: Long, val limitPaise: Long = MAX_AMOUNT_PAISE) : GoalError
    data object BlankNote : GoalError
    data class NoteTooLong(val length: Int, val maxLength: Int = MAX_NOTE_LENGTH) : GoalError
    data class PastDeadline(val deadlineAt: Long, val minAllowedAt: Long) : GoalError
    data class GoalNotFound(val goalId: Long) : GoalError
    data class GoalDeleted(val goalId: Long) : GoalError
    data class EntryNotFound(val entryId: Long) : GoalError
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

package com.autoflow.app.presentation.viewmodel

import com.autoflow.app.domain.goals.GoalError
import com.autoflow.app.domain.goals.GoalWithTotals
import com.autoflow.app.domain.goals.MAX_AMOUNT_PAISE
import com.autoflow.app.domain.goals.MAX_GOAL_NAME_LENGTH
import com.autoflow.app.domain.goals.MAX_NOTE_LENGTH

data class GoalsUiState(
    val goals: List<GoalWithTotals> = emptyList(),
    val isLoading: Boolean = false
) {
    val isEmpty: Boolean get() = goals.isEmpty()
}

data class GoalFormValidation(
    val isValid: Boolean,
    val nameError: String? = null,
    val targetError: String? = null
)

data class SavedEntryValidation(
    val isValid: Boolean,
    val amountError: String? = null,
    val noteError: String? = null
)

fun validateGoalForm(name: String, targetPaise: Long): GoalFormValidation {
    val trimmed = name.trim()
    val nameErr = when {
        trimmed.isEmpty() -> "Name can't be blank"
        trimmed.length > MAX_GOAL_NAME_LENGTH -> "Name is too long (max $MAX_GOAL_NAME_LENGTH characters)"
        else -> null
    }
    val targetErr = when {
        targetPaise <= 0L -> "Amount must be greater than zero"
        targetPaise > MAX_AMOUNT_PAISE -> "Amount exceeds maximum allowed limit"
        else -> null
    }
    return GoalFormValidation(
        isValid = nameErr == null && targetErr == null,
        nameError = nameErr,
        targetError = targetErr
    )
}

fun validateSavedEntry(amountPaise: Long, note: String): SavedEntryValidation {
    val amountErr = when {
        amountPaise <= 0L -> "Amount must be greater than zero"
        amountPaise > MAX_AMOUNT_PAISE -> "Amount exceeds maximum allowed limit"
        else -> null
    }
    val trimmedNote = note.trim()
    val noteErr = when {
        trimmedNote.isEmpty() -> "Note can't be blank"
        trimmedNote.length > MAX_NOTE_LENGTH -> "Note is too long (max $MAX_NOTE_LENGTH characters)"
        else -> null
    }
    return SavedEntryValidation(
        isValid = amountErr == null && noteErr == null,
        amountError = amountErr,
        noteError = noteErr
    )
}

fun mapGoalErrorToUserMessage(error: GoalError): String {
    return when (error) {
        GoalError.BlankName -> "Name can't be blank"
        is GoalError.NameTooLong -> "Name is too long (max ${error.maxLength} characters)"
        is GoalError.NameTaken -> "Name is already used by another goal"
        is GoalError.BadAmount -> "Amount must be greater than zero"
        is GoalError.OverLimit -> "Amount exceeds maximum allowed limit"
        GoalError.BlankNote -> "Note can't be blank"
        is GoalError.NoteTooLong -> "Note is too long (max ${error.maxLength} characters)"
        is GoalError.PastDeadline -> "Deadline must be today or in the future"
        is GoalError.GoalNotFound -> "Goal not found"
        is GoalError.GoalDeleted -> "Goal is deleted"
        is GoalError.EntryNotFound -> "Entry not found"
    }
}

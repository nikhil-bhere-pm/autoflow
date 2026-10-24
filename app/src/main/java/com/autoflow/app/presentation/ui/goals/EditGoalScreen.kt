package com.autoflow.app.presentation.ui.goals

import android.app.DatePickerDialog
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.autoflow.app.R
import com.autoflow.app.domain.goals.Goal
import com.autoflow.app.domain.goals.GoalError
import com.autoflow.app.domain.goals.GoalResult
import com.autoflow.app.presentation.theme.AppRadius
import com.autoflow.app.presentation.theme.AppSpacing
import com.autoflow.app.presentation.theme.AutoFlowTheme
import com.autoflow.app.presentation.ui.components.AFButton
import com.autoflow.app.presentation.ui.components.AFField
import com.autoflow.app.presentation.ui.components.AFNumberPad
import com.autoflow.app.presentation.ui.components.AFTopBar
import com.autoflow.app.presentation.util.CurrencyFormatter
import com.autoflow.app.presentation.util.GoalDeadlineFormatter
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@Composable
fun EditGoalScreen(
    goal: Goal?,
    onSaveGoal: suspend (id: Long, name: String, targetPaise: Long, deadlineAt: Long?) -> GoalResult<Goal>,
    onGoalSaved: () -> Unit,
    onDeleteGoal: (Long) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = AutoFlowTheme.colors
    val context = LocalContext.current
    val zone = remember { ZoneId.systemDefault() }
    val scrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()

    if (goal == null) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Goal not found",
                style = AutoFlowTheme.typography.bodyLarge,
                color = colors.muted
            )
        }
        return
    }

    val initialRupeesString = remember(goal.targetPaise) {
        val r = goal.targetPaise / 100
        val p = goal.targetPaise % 100
        if (p == 0L) r.toString() else "$r.${String.format(java.util.Locale.US, "%02d", p)}"
    }

    var nameInput by remember(goal) { mutableStateOf(goal.name) }
    var targetInput by remember(goal) { mutableStateOf(initialRupeesString) }
    var deadlineAt by remember(goal) { mutableStateOf<Long?>(goal.deadlineAt) }

    var nameError by remember { mutableStateOf<String?>(null) }
    var targetError by remember { mutableStateOf<String?>(null) }
    var deadlineError by remember { mutableStateOf<String?>(null) }
    var isSubmitting by remember { mutableStateOf(false) }

    val targetPaise = remember(targetInput) {
        CurrencyFormatter.parseRupeesToPaise(targetInput) ?: 0L
    }

    val isSaveEnabled = nameInput.trim().isNotEmpty() && targetPaise > 0L

    val openDatePicker = {
        val initialDate = if (deadlineAt != null) {
            Instant.ofEpochMilli(deadlineAt!!).atZone(zone).toLocalDate()
        } else {
            LocalDate.now(zone)
        }
        val dialog = DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                val localStart = GoalDeadlineFormatter.localDateToStartOfDayMillis(
                    year = year,
                    month = month + 1,
                    dayOfMonth = dayOfMonth,
                    zoneId = zone
                )
                deadlineAt = localStart
                deadlineError = null
            },
            initialDate.year,
            initialDate.monthValue - 1,
            initialDate.dayOfMonth
        )
        dialog.show()
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = colors.bg,
        topBar = {
            AFTopBar(
                title = stringResource(R.string.edit_goal_title),
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier
                            .defaultMinSize(minWidth = AppSpacing.minTapTarget, minHeight = AppSpacing.minTapTarget)
                            .semantics { contentDescription = "Navigate back" }
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null,
                            tint = colors.ink
                        )
                    }
                },
                showDivider = true
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .imePadding()
                .verticalScroll(scrollState)
                .padding(AppSpacing.md),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.md)
        ) {
            // Goal name field
            AFField(
                value = nameInput,
                onValueChange = {
                    nameInput = it
                    nameError = null
                },
                label = stringResource(R.string.new_goal_name_label),
                placeholder = stringResource(R.string.new_goal_name_placeholder),
                errorMessage = nameError
            )

            // Target amount display
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = AppSpacing.xs),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(R.string.new_goal_target_label),
                    style = AutoFlowTheme.typography.labelLarge,
                    color = colors.ink,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(AppSpacing.xs))
                Text(
                    text = if (targetInput.isEmpty()) "₹0" else "₹$targetInput",
                    style = AutoFlowTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    color = colors.ink,
                    textAlign = TextAlign.Center
                )
                if (!targetError.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(AppSpacing.xs))
                    Text(
                        text = targetError!!,
                        style = AutoFlowTheme.typography.bodySmall,
                        color = colors.red,
                        textAlign = TextAlign.Center
                    )
                }
            }

            // Number pad for target amount
            AFNumberPad(
                onDigitClick = { digit ->
                    val current = targetInput
                    if (current == "0" && digit == '0') return@AFNumberPad
                    if (current == "0" && digit != '0') {
                        targetInput = digit.toString()
                        targetError = null
                        return@AFNumberPad
                    }
                    val parts = current.split(".")
                    if (parts.size == 2 && parts[1].length >= 2) return@AFNumberPad
                    if (parts[0].length >= 9 && parts.size == 1) return@AFNumberPad
                    targetInput = current + digit
                    targetError = null
                },
                onDotClick = {
                    if (!targetInput.contains(".")) {
                        targetInput = if (targetInput.isEmpty()) "0." else "$targetInput."
                        targetError = null
                    }
                },
                onBackspaceClick = {
                    if (targetInput.isNotEmpty()) {
                        targetInput = targetInput.dropLast(1)
                        targetError = null
                    }
                }
            )

            // Deadline row
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.new_goal_deadline_label),
                    style = AutoFlowTheme.typography.labelLarge,
                    color = colors.ink,
                    modifier = Modifier.padding(bottom = AppSpacing.xs)
                )

                Surface(
                    shape = RoundedCornerShape(AppRadius.md),
                    color = colors.sheet,
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = AppSpacing.minTapTarget)
                        .clip(RoundedCornerShape(AppRadius.md))
                        .clickable(role = Role.Button, onClick = openDatePicker)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = AppSpacing.md, vertical = AppSpacing.sm),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.DateRange,
                            contentDescription = "Select deadline",
                            tint = colors.brand,
                            modifier = Modifier.size(24.dp)
                        )

                        Spacer(modifier = Modifier.width(AppSpacing.sm))

                        Text(
                            text = GoalDeadlineFormatter.formatPickerDate(deadlineAt, zone),
                            style = AutoFlowTheme.typography.bodyLarge,
                            color = if (deadlineAt == null) colors.muted else colors.ink,
                            modifier = Modifier.weight(1f)
                        )

                        if (deadlineAt != null) {
                            TextButton(
                                onClick = {
                                    deadlineAt = null
                                    deadlineError = null
                                },
                                modifier = Modifier.defaultMinSize(minHeight = AppSpacing.minTapTarget)
                            ) {
                                Text(
                                    text = stringResource(R.string.new_goal_remove_deadline),
                                    style = AutoFlowTheme.typography.labelMedium,
                                    color = colors.red
                                )
                            }
                        }
                    }
                }

                if (!deadlineError.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(AppSpacing.xs))
                    Text(
                        text = deadlineError!!,
                        style = AutoFlowTheme.typography.bodySmall,
                        color = colors.red,
                        modifier = Modifier.padding(start = AppSpacing.xs)
                    )
                }
            }

            // Note: Changing the target never changes what you have saved so far.
            Text(
                text = stringResource(R.string.edit_goal_target_note),
                style = AutoFlowTheme.typography.bodySmall,
                color = colors.muted
            )

            Spacer(modifier = Modifier.height(AppSpacing.sm))

            // Save changes button
            AFButton(
                text = stringResource(R.string.goal_save_changes_button),
                onClick = {
                    if (isSaveEnabled && !isSubmitting) {
                        isSubmitting = true
                        nameError = null
                        targetError = null
                        deadlineError = null
                        coroutineScope.launch {
                            val result = onSaveGoal(goal.id, nameInput.trim(), targetPaise, deadlineAt)
                            isSubmitting = false
                            when (result) {
                                is GoalResult.Success -> onGoalSaved()
                                is GoalResult.Error -> {
                                    when (val err = result.error) {
                                        GoalError.BlankName -> nameError = "Name can't be blank"
                                        is GoalError.NameTooLong -> nameError = "Name is too long (max ${err.maxLength} characters)"
                                        is GoalError.NameTaken -> nameError = "Name is already used by another goal"
                                        is GoalError.BadAmount -> targetError = "Amount must be greater than zero"
                                        is GoalError.OverLimit -> targetError = "Amount exceeds maximum allowed limit"
                                        is GoalError.PastDeadline -> deadlineError = "Deadline must be today or in the future"
                                        else -> nameError = "Failed to update goal"
                                    }
                                }
                            }
                        }
                    }
                },
                enabled = isSaveEnabled && !isSubmitting,
                modifier = Modifier.fillMaxWidth()
            )

            // Red text button "Delete goal"
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                TextButton(
                    onClick = { onDeleteGoal(goal.id) },
                    modifier = Modifier.defaultMinSize(minHeight = AppSpacing.minTapTarget)
                ) {
                    Text(
                        text = stringResource(R.string.goal_delete_button),
                        style = AutoFlowTheme.typography.labelLarge,
                        color = colors.red,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

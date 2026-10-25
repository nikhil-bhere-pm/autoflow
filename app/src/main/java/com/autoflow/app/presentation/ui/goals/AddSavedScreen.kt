package com.autoflow.app.presentation.ui.goals

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.autoflow.app.R
import com.autoflow.app.domain.goals.Goal
import com.autoflow.app.presentation.theme.AppSpacing
import com.autoflow.app.presentation.theme.AutoFlowTheme
import com.autoflow.app.presentation.ui.components.AFButton
import com.autoflow.app.presentation.ui.components.AFCategoryChip
import com.autoflow.app.presentation.ui.components.AFField
import com.autoflow.app.presentation.ui.components.AFNumberPad
import com.autoflow.app.presentation.ui.components.AFTopBar
import com.autoflow.app.presentation.util.CurrencyFormatter

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddSavedScreen(
    goalId: Long,
    onSave: (amountPaise: Long, note: String) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    availableGoals: List<Goal> = emptyList(),
    showGoalChips: Boolean = false,
    onSaveWithGoal: ((selectedGoalId: Long, amountPaise: Long, note: String) -> Unit)? = null
) {
    val colors = AutoFlowTheme.colors
    var selectedGoalId by remember(goalId) { mutableLongStateOf(goalId) }
    var amountInput by remember { mutableStateOf("") }
    var noteInput by remember { mutableStateOf("") }
    val scrollState = rememberScrollState()

    val amountPaise = remember(amountInput) {
        CurrencyFormatter.parseRupeesToPaise(amountInput) ?: 0L
    }

    val isSaveEnabled = amountPaise > 0L && noteInput.trim().isNotBlank() && selectedGoalId > 0L

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = colors.bg,
        topBar = {
            AFTopBar(
                title = stringResource(R.string.saved_screen_title),
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier
                            .defaultMinSize(minWidth = AppSpacing.minTapTarget, minHeight = AppSpacing.minTapTarget)
                            .semantics { contentDescription = "Close" }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
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
            // Big amount display
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = AppSpacing.sm),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = if (amountInput.isEmpty()) "₹0" else "₹$amountInput",
                    style = AutoFlowTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    color = colors.ink,
                    textAlign = TextAlign.Center
                )
            }

            // Goal selection chips ("Which goal?") when opened from widget
            if (showGoalChips && availableGoals.isNotEmpty()) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = stringResource(R.string.saved_screen_which_goal),
                        style = AutoFlowTheme.typography.labelLarge,
                        color = colors.ink,
                        modifier = Modifier.padding(bottom = AppSpacing.xs)
                    )

                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
                        verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)
                    ) {
                        availableGoals.forEach { goal ->
                            val isSelected = goal.id == selectedGoalId
                            AFCategoryChip(
                                text = goal.name,
                                selected = isSelected,
                                onClick = { selectedGoalId = goal.id }
                            )
                        }
                    }
                }
            }

            // Note input field
            AFField(
                value = noteInput,
                onValueChange = { noteInput = it },
                label = stringResource(R.string.saved_screen_note_label),
                placeholder = stringResource(R.string.saved_screen_note_placeholder)
            )

            // Number pad
            AFNumberPad(
                onDigitClick = { digit ->
                    val current = amountInput
                    if (current == "0" && digit == '0') return@AFNumberPad
                    if (current == "0" && digit != '0') {
                        amountInput = digit.toString()
                        return@AFNumberPad
                    }
                    val parts = current.split(".")
                    if (parts.size == 2 && parts[1].length >= 2) return@AFNumberPad
                    if (parts[0].length >= 9 && parts.size == 1) return@AFNumberPad
                    amountInput = current + digit
                },
                onDotClick = {
                    if (!amountInput.contains(".")) {
                        amountInput = if (amountInput.isEmpty()) "0." else "$amountInput."
                    }
                },
                onBackspaceClick = {
                    if (amountInput.isNotEmpty()) {
                        amountInput = amountInput.dropLast(1)
                    }
                }
            )

            Spacer(modifier = Modifier.height(AppSpacing.xs))

            // Save button
            AFButton(
                text = stringResource(R.string.saved_screen_save_button),
                onClick = {
                    if (isSaveEnabled) {
                        if (onSaveWithGoal != null) {
                            onSaveWithGoal(selectedGoalId, amountPaise, noteInput.trim())
                        } else {
                            onSave(amountPaise, noteInput.trim())
                        }
                    }
                },
                enabled = isSaveEnabled,
                modifier = Modifier.fillMaxWidth()
            )

            // Helper hint
            Text(
                text = stringResource(R.string.saved_screen_helper_hint),
                style = AutoFlowTheme.typography.bodySmall,
                color = colors.muted,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AppSpacing.md)
            )
        }
    }
}

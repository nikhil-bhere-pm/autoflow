package com.autoflow.app.presentation.ui.goals

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.autoflow.app.R
import com.autoflow.app.domain.goals.GoalEntry
import com.autoflow.app.domain.goals.GoalError
import com.autoflow.app.domain.goals.GoalResult
import com.autoflow.app.presentation.theme.AppRadius
import com.autoflow.app.presentation.theme.AppSpacing
import com.autoflow.app.presentation.theme.AutoFlowTheme
import com.autoflow.app.presentation.ui.components.AFBadge
import com.autoflow.app.presentation.ui.components.AFBadgeVariant
import com.autoflow.app.presentation.ui.components.AFButton
import com.autoflow.app.presentation.ui.components.AFField
import com.autoflow.app.presentation.ui.components.AFNumberPad
import com.autoflow.app.presentation.ui.components.AFTopBar
import com.autoflow.app.presentation.util.CurrencyFormatter
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun EditEntryScreen(
    entry: GoalEntry?,
    onSaveEntry: suspend (id: Long, amountPaise: Long, note: String) -> GoalResult<GoalEntry>,
    onEntrySaved: () -> Unit,
    onDeleteEntry: (Long) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = AutoFlowTheme.colors
    val zone = remember { ZoneId.systemDefault() }
    val scrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()

    if (entry == null) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Entry not found",
                style = AutoFlowTheme.typography.bodyLarge,
                color = colors.muted
            )
        }
        return
    }

    val initialRupeesString = remember(entry.amountPaise) {
        val r = entry.amountPaise / 100
        val p = entry.amountPaise % 100
        if (p == 0L) r.toString() else "$r.${String.format(Locale.US, "%02d", p)}"
    }

    var amountInput by remember(entry) { mutableStateOf(initialRupeesString) }
    var noteInput by remember(entry) { mutableStateOf(entry.note) }

    var amountError by remember { mutableStateOf<String?>(null) }
    var noteError by remember { mutableStateOf<String?>(null) }
    var isSubmitting by remember { mutableStateOf(false) }

    val amountPaise = remember(amountInput) {
        CurrencyFormatter.parseRupeesToPaise(amountInput) ?: 0L
    }

    val isSaveEnabled = amountPaise > 0L && noteInput.trim().isNotBlank()

    val formattedDateTime = remember(entry.occurredAt) {
        Instant.ofEpochMilli(entry.occurredAt)
            .atZone(zone)
            .format(DateTimeFormatter.ofPattern("EEE, d MMM yyyy, h:mm a", Locale.ENGLISH))
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = colors.bg,
        topBar = {
            AFTopBar(
                title = stringResource(R.string.edit_entry_title),
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
            // Badge row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AFBadge(
                    text = stringResource(R.string.saved_badge),
                    variant = AFBadgeVariant.CHECK
                )
            }

            // Big amount display
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = AppSpacing.xs),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = if (amountInput.isEmpty()) "₹0" else "₹$amountInput",
                    style = AutoFlowTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    color = colors.ink,
                    textAlign = TextAlign.Center
                )
                if (!amountError.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(AppSpacing.xs))
                    Text(
                        text = amountError!!,
                        style = AutoFlowTheme.typography.bodySmall,
                        color = colors.red,
                        textAlign = TextAlign.Center
                    )
                }
            }

            // Number pad
            AFNumberPad(
                onDigitClick = { digit ->
                    val current = amountInput
                    if (current == "0" && digit == '0') return@AFNumberPad
                    if (current == "0" && digit != '0') {
                        amountInput = digit.toString()
                        amountError = null
                        return@AFNumberPad
                    }
                    val parts = current.split(".")
                    if (parts.size == 2 && parts[1].length >= 2) return@AFNumberPad
                    if (parts[0].length >= 9 && parts.size == 1) return@AFNumberPad
                    amountInput = current + digit
                    amountError = null
                },
                onDotClick = {
                    if (!amountInput.contains(".")) {
                        amountInput = if (amountInput.isEmpty()) "0." else "$amountInput."
                        amountError = null
                    }
                },
                onBackspaceClick = {
                    if (amountInput.isNotEmpty()) {
                        amountInput = amountInput.dropLast(1)
                        amountError = null
                    }
                }
            )

            // Note input field: How did you save? (required)
            AFField(
                value = noteInput,
                onValueChange = {
                    noteInput = it
                    noteError = null
                },
                label = stringResource(R.string.saved_screen_note_label),
                placeholder = stringResource(R.string.saved_screen_note_placeholder),
                errorMessage = noteError
            )

            // Date & time display
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.edit_entry_datetime_label),
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
                ) {
                    Box(
                        modifier = Modifier.padding(horizontal = AppSpacing.md, vertical = AppSpacing.sm),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Text(
                            text = formattedDateTime,
                            style = AutoFlowTheme.typography.bodyMedium,
                            color = colors.ink
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(AppSpacing.sm))

            // Save changes button
            AFButton(
                text = stringResource(R.string.goal_save_changes_button),
                onClick = {
                    if (isSaveEnabled && !isSubmitting) {
                        isSubmitting = true
                        amountError = null
                        noteError = null
                        coroutineScope.launch {
                            val result = onSaveEntry(entry.id, amountPaise, noteInput.trim())
                            isSubmitting = false
                            when (result) {
                                is GoalResult.Success -> onEntrySaved()
                                is GoalResult.Error -> {
                                    when (val err = result.error) {
                                        GoalError.BlankNote -> noteError = "Note can't be blank"
                                        is GoalError.NoteTooLong -> noteError = "Note is too long (max ${err.maxLength} characters)"
                                        is GoalError.BadAmount -> amountError = "Amount must be greater than zero"
                                        is GoalError.OverLimit -> amountError = "Amount exceeds maximum allowed limit"
                                        else -> noteError = "Failed to update entry"
                                    }
                                }
                            }
                        }
                    }
                },
                enabled = isSaveEnabled && !isSubmitting,
                modifier = Modifier.fillMaxWidth()
            )

            // Red text button "Delete entry"
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                TextButton(
                    onClick = { onDeleteEntry(entry.id) },
                    modifier = Modifier.defaultMinSize(minHeight = AppSpacing.minTapTarget)
                ) {
                    Text(
                        text = stringResource(R.string.entry_delete_button),
                        style = AutoFlowTheme.typography.labelLarge,
                        color = colors.red,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

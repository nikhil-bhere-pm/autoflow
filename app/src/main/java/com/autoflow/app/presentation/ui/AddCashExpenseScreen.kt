package com.autoflow.app.presentation.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import com.autoflow.app.presentation.theme.AppRadius
import com.autoflow.app.presentation.theme.AppSpacing
import com.autoflow.app.presentation.util.CurrencyFormatter
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddCashExpenseScreen(
    onSave: (amountPaise: Long, merchant: String, category: String, dateTime: Long) -> Unit,
    onNavigateBack: () -> Unit
) {
    var amountInput by remember { mutableStateOf("") }
    var merchantInput by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("Food") }
    var selectedTimestamp by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var isCustomDate by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val parsedPaise = remember(amountInput) {
        CurrencyFormatter.parseRupeesToPaise(amountInput)
    }

    val dateTimeFormatter = remember {
        DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a")
    }
    val formattedDateTime = remember(selectedTimestamp) {
        Instant.ofEpochMilli(selectedTimestamp)
            .atZone(ZoneId.systemDefault())
            .format(dateTimeFormatter)
    }

    val openTimePicker: () -> Unit = {
        val currentZoned = Instant.ofEpochMilli(selectedTimestamp).atZone(ZoneId.systemDefault())
        val timeDialog = android.app.TimePickerDialog(
            context,
            { _, hourOfDay, minute ->
                val updated = Instant.ofEpochMilli(selectedTimestamp)
                    .atZone(ZoneId.systemDefault())
                    .withHour(hourOfDay)
                    .withMinute(minute)
                    .withSecond(0)
                selectedTimestamp = updated.toInstant().toEpochMilli()
                isCustomDate = true
            },
            currentZoned.hour,
            currentZoned.minute,
            false
        )
        timeDialog.show()
    }

    val openDatePicker: () -> Unit = {
        val currentZoned = Instant.ofEpochMilli(selectedTimestamp).atZone(ZoneId.systemDefault())
        val dateDialog = android.app.DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                val updated = Instant.ofEpochMilli(selectedTimestamp)
                    .atZone(ZoneId.systemDefault())
                    .withYear(year)
                    .withMonth(month + 1)
                    .withDayOfMonth(dayOfMonth)
                selectedTimestamp = updated.toInstant().toEpochMilli()
                isCustomDate = true
                openTimePicker()
            },
            currentZoned.year,
            currentZoned.monthValue - 1,
            currentZoned.dayOfMonth
        )
        dateDialog.show()
    }

    val scrollState = rememberScrollState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Add Cash Expense",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier
                            .heightIn(min = AppSpacing.minTapTarget)
                            .width(AppSpacing.minTapTarget)
                            .semantics { contentDescription = "Navigate back" }
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(scrollState)
                .padding(horizontal = AppSpacing.md, vertical = AppSpacing.md),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.lg)
        ) {
            // AMOUNT INPUT CARD
            Card(
                shape = RoundedCornerShape(AppRadius.md),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(AppSpacing.md)) {
                    Text(
                        text = "Amount (₹)",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(AppSpacing.xs))
                    OutlinedTextField(
                        value = amountInput,
                        onValueChange = { amountInput = it },
                        placeholder = {
                            Text(
                                text = "e.g. 130 or 130.50",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = AppSpacing.minTapTarget)
                            .semantics { contentDescription = "Amount in rupees" },
                        shape = RoundedCornerShape(AppRadius.sm),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline
                        )
                    )

                    if (parsedPaise != null) {
                        Spacer(modifier = Modifier.height(AppSpacing.xs))
                        Text(
                            text = "Formatted: ${CurrencyFormatter.formatPaiseToInr(parsedPaise)}",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            // MERCHANT / NOTE INPUT
            Column {
                Text(
                    text = "Merchant / Note",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(AppSpacing.xs))
                OutlinedTextField(
                    value = merchantInput,
                    onValueChange = { merchantInput = it },
                    placeholder = {
                        Text(
                            text = "e.g. Tea Stall, Vegetables",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = AppSpacing.minTapTarget)
                        .semantics { contentDescription = "Merchant or note input" },
                    shape = RoundedCornerShape(AppRadius.sm),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                    )
                )
            }

            // CATEGORY SELECTOR CHIPS
            Column {
                Text(
                    text = "Category",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(AppSpacing.sm))
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
                    verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)
                ) {
                    CATEGORIES.forEach { category ->
                        val isSelected = selectedCategory == category
                        FilterChip(
                            selected = isSelected,
                            onClick = { selectedCategory = category },
                            label = {
                                Text(
                                    text = category,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            },
                            shape = RoundedCornerShape(AppRadius.sm),
                            modifier = Modifier
                                .heightIn(min = AppSpacing.minTapTarget)
                                .semantics { contentDescription = "Select category $category" },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                                containerColor = MaterialTheme.colorScheme.surface,
                                labelColor = MaterialTheme.colorScheme.onSurface
                            )
                        )
                    }
                }
            }

            // DATE & TIME DISPLAY
            Card(
                shape = RoundedCornerShape(AppRadius.sm),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = AppSpacing.xxs),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        onClick = openDatePicker,
                        onClickLabel = "Change date and time"
                    )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(AppSpacing.md),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Date & Time",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(AppSpacing.xxs))
                        Text(
                            text = formattedDateTime,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Text(
                        text = if (isCustomDate) "Change" else "Now",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(AppSpacing.sm))

            // SAVE EXPENSE BUTTON
            Button(
                onClick = {
                    val paise = parsedPaise ?: return@Button
                    val merchant = merchantInput.trim().ifBlank { "Cash Expense" }
                    onSave(paise, merchant, selectedCategory, selectedTimestamp)
                    onNavigateBack()
                },
                enabled = parsedPaise != null,
                shape = RoundedCornerShape(AppRadius.sm),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = AppSpacing.minTapTarget)
                    .semantics { contentDescription = "Save cash expense" }
            ) {
                Text(
                    text = "Save Expense",
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}

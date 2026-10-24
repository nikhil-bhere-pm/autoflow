package com.autoflow.app.presentation.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.autoflow.app.data.model.ReviewStatus
import com.autoflow.app.data.model.TransactionSource
import com.autoflow.app.data.repository.UpdateTransactionResult
import com.autoflow.app.domain.category.BUILT_IN_CATEGORIES
import com.autoflow.app.domain.model.FinancialTransaction
import com.autoflow.app.presentation.theme.AppRadius
import com.autoflow.app.presentation.theme.AppSpacing
import com.autoflow.app.presentation.theme.AutoFlowTheme
import com.autoflow.app.presentation.ui.components.AFButton
import com.autoflow.app.presentation.ui.components.AFButtonVariant
import com.autoflow.app.presentation.ui.components.AFCategoryChip
import com.autoflow.app.presentation.ui.components.AFCategoryIcons
import com.autoflow.app.presentation.ui.components.AFField
import com.autoflow.app.presentation.ui.components.AFSegmented
import com.autoflow.app.presentation.ui.components.AFTopBar
import com.autoflow.app.presentation.ui.form.EditTransactionFormState
import com.autoflow.app.presentation.ui.form.ExpenseFormLogic
import com.autoflow.app.presentation.util.CurrencyFormatter
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EditTransactionScreen(
    transactionId: Long,
    loadTransaction: suspend (Long) -> FinancialTransaction?,
    onSave: (id: Long, merchant: String, category: String, dateTime: Long, paymentMode: String, note: String?, amountPaise: Long?, onResult: (UpdateTransactionResult) -> Unit) -> Unit,
    onMarkNotAnExpense: (id: Long) -> Unit,
    onRestore: (id: Long) -> Unit,
    onDelete: (id: Long) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    categories: List<String> = BUILT_IN_CATEGORIES
) {
    val colors = AutoFlowTheme.colors
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    var loadedTx by remember { mutableStateOf<FinancialTransaction?>(null) }
    var amountInput by remember { mutableStateOf("") }
    var merchantInput by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("Others") }
    var selectedDateTime by remember { mutableLongStateOf(0L) }
    var selectedPaymentMode by remember { mutableStateOf("UNKNOWN") }
    var noteInput by remember { mutableStateOf("") }

    var showDeleteDialog by remember { mutableStateOf(false) }
    var showDiscardDialog by remember { mutableStateOf(false) }
    var serverError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(transactionId) {
        val tx = loadTransaction(transactionId)
        if (tx != null) {
            loadedTx = tx
            val rupees = tx.amountPaise / 100
            val paiseRemainder = tx.amountPaise % 100
            amountInput = if (paiseRemainder == 0L) rupees.toString() else String.format(Locale.US, "%d.%02d", rupees, paiseRemainder)
            merchantInput = if (tx.merchantName == "Unknown Merchant") "" else tx.merchantName
            selectedCategory = tx.category
            selectedDateTime = tx.dateTime
            selectedPaymentMode = tx.paymentMode
            noteInput = tx.note ?: ""
        }
    }

    val currentTx = loadedTx

    if (currentTx == null) {
        Scaffold(
            modifier = modifier.fillMaxSize(),
            containerColor = colors.bg,
            topBar = {
                AFTopBar(
                    title = "Edit transaction",
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Navigate back", tint = colors.ink)
                        }
                    }
                )
            }
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Loading transaction...",
                    style = AutoFlowTheme.typography.bodyMedium,
                    color = colors.muted
                )
            }
        }
        return
    }

    val formState = EditTransactionFormState(
        initialId = currentTx.id,
        initialAmountPaise = currentTx.amountPaise,
        initialSource = currentTx.source,
        initialMerchant = currentTx.merchantName,
        initialCategory = currentTx.category,
        initialDateTime = currentTx.dateTime,
        initialPaymentMode = currentTx.paymentMode,
        initialNote = currentTx.note,
        initialReviewStatus = currentTx.reviewStatus,
        amountInput = amountInput,
        merchantInput = merchantInput,
        selectedCategory = selectedCategory,
        selectedDateTime = selectedDateTime,
        selectedPaymentMode = selectedPaymentMode,
        noteInput = noteInput,
        now = System.currentTimeMillis()
    )

    val handleBack = {
        if (formState.shouldShowDiscardDialog) {
            showDiscardDialog = true
        } else {
            onNavigateBack()
        }
    }

    BackHandler(enabled = true) {
        handleBack()
    }

    val openDatePicker: () -> Unit = {
        val zone = ZoneId.systemDefault()
        val currentZoned = Instant.ofEpochMilli(if (selectedDateTime > 0) selectedDateTime else System.currentTimeMillis()).atZone(zone)

        val datePicker = DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                val timePicker = TimePickerDialog(
                    context,
                    { _, hourOfDay, minute ->
                        val pickedZoned = currentZoned
                            .withYear(year)
                            .withMonth(month + 1)
                            .withDayOfMonth(dayOfMonth)
                            .withHour(hourOfDay)
                            .withMinute(minute)
                            .withSecond(0)
                        selectedDateTime = pickedZoned.toInstant().toEpochMilli()
                    },
                    currentZoned.hour,
                    currentZoned.minute,
                    false
                )
                timePicker.show()
            },
            currentZoned.year,
            currentZoned.monthValue - 1,
            currentZoned.dayOfMonth
        )
        datePicker.datePicker.maxDate = System.currentTimeMillis()
        datePicker.show()
    }

    val formattedDateTime = remember(selectedDateTime) {
        val zone = ZoneId.systemDefault()
        Instant.ofEpochMilli(selectedDateTime)
            .atZone(zone)
            .format(DateTimeFormatter.ofPattern("EEE, d MMM yyyy, h:mm a", Locale.getDefault()))
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = colors.bg,
        topBar = {
            AFTopBar(
                title = "Edit transaction",
                navigationIcon = {
                    IconButton(
                        onClick = handleBack,
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
                actions = {
                    IconButton(
                        onClick = { showDeleteDialog = true },
                        modifier = Modifier
                            .defaultMinSize(minWidth = AppSpacing.minTapTarget, minHeight = AppSpacing.minTapTarget)
                            .semantics { contentDescription = "Delete transaction" }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = null,
                            tint = colors.red
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
                .padding(horizontal = AppSpacing.md, vertical = AppSpacing.md),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.md)
        ) {
            // 1. TOP CARD (Amount, Source, and Lock note)
            Surface(
                shape = RoundedCornerShape(AppRadius.lg),
                color = colors.sheet,
                border = BorderStroke(1.dp, colors.line),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(AppSpacing.md)
                ) {
                    val sourceLabel = if (formState.isSms) "From bank SMS" else "Cash entry"
                    Text(
                        text = sourceLabel,
                        style = AutoFlowTheme.typography.labelMedium,
                        color = colors.muted
                    )

                    Spacer(modifier = Modifier.height(AppSpacing.xs))

                    if (formState.isAmountEditable) {
                        AFField(
                            value = amountInput,
                            onValueChange = { amountInput = it },
                            label = "Amount",
                            placeholder = "0.00",
                            leadingIcon = {
                                Text(
                                    text = "₹",
                                    style = AutoFlowTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.ink,
                                    modifier = Modifier.padding(start = AppSpacing.sm)
                                )
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                        )
                    } else {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = CurrencyFormatter.formatPaiseToInr(currentTx.amountPaise),
                                style = AutoFlowTheme.typography.displaySmall,
                                fontWeight = FontWeight.Bold,
                                color = colors.ink
                            )

                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = "Locked",
                                    tint = colors.muted,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(AppSpacing.xxs))
                                Text(
                                    text = "Amount is fixed",
                                    style = AutoFlowTheme.typography.bodySmall,
                                    color = colors.muted
                                )
                            }
                        }
                    }
                }
            }

            // 2. MERCHANT FIELD
            AFField(
                value = merchantInput,
                onValueChange = { merchantInput = it },
                label = "Merchant",
                placeholder = "Merchant name"
            )

            // 3. DATE AND TIME ROW
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Date and time",
                    style = AutoFlowTheme.typography.labelLarge,
                    color = colors.ink,
                    modifier = Modifier.padding(bottom = AppSpacing.xs)
                )

                Surface(
                    shape = RoundedCornerShape(AppRadius.md),
                    color = colors.field,
                    border = BorderStroke(1.dp, if (formState.isFutureDate) colors.red else colors.line),
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = AppSpacing.minTapTarget)
                        .clip(RoundedCornerShape(AppRadius.md))
                        .clickable { openDatePicker() }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = AppSpacing.md, vertical = AppSpacing.sm),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.DateRange,
                            contentDescription = "Select date and time",
                            tint = colors.muted,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(AppSpacing.sm))
                        Text(
                            text = formattedDateTime,
                            style = AutoFlowTheme.typography.bodyLarge,
                            color = colors.ink
                        )
                    }
                }

                if (formState.isFutureDate) {
                    Spacer(modifier = Modifier.height(AppSpacing.xs))
                    Text(
                        text = "Future date and time not allowed",
                        style = AutoFlowTheme.typography.bodySmall,
                        color = colors.red,
                        modifier = Modifier.padding(start = AppSpacing.xs)
                    )
                }
            }

            // 4. CATEGORY CHIPS
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Category",
                    style = AutoFlowTheme.typography.labelLarge,
                    color = colors.ink,
                    modifier = Modifier.padding(bottom = AppSpacing.xs)
                )

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
                    verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)
                ) {
                    categories.forEach { categoryName ->
                        val isSelected = categoryName.equals(selectedCategory, ignoreCase = true)
                        val iconRes = AFCategoryIcons.getDrawableRes(categoryName)
                        AFCategoryChip(
                            text = categoryName,
                            selected = isSelected,
                            onClick = { selectedCategory = categoryName },
                            icon = {
                                Icon(
                                    painter = painterResource(id = iconRes),
                                    contentDescription = null,
                                    tint = if (isSelected) colors.buttonText else colors.forCategory(categoryName),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        )
                    }
                }
            }

            // 5. PAYMENT MODE SEGMENTED CONTROL
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Payment mode",
                    style = AutoFlowTheme.typography.labelLarge,
                    color = colors.ink,
                    modifier = Modifier.padding(bottom = AppSpacing.xs)
                )

                val modes = listOf("Cash", "Credit", "Bank", "UPI")
                val modeKeys = listOf("CASH", "CREDIT", "BANK", "UPI")

                AFSegmented(
                    items = modes,
                    selectedIndex = formState.modeSelectedIndex,
                    onSelect = { index ->
                        if (index in modeKeys.indices) {
                            selectedPaymentMode = modeKeys[index]
                        }
                    }
                )
            }

            // 6. NOTE FIELD WITH LIVE COUNTER
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Note (optional)",
                        style = AutoFlowTheme.typography.labelLarge,
                        color = colors.ink
                    )
                    Text(
                        text = formState.noteCounterText,
                        style = AutoFlowTheme.typography.bodySmall,
                        color = if (formState.isNoteValid) colors.muted else colors.red
                    )
                }

                Spacer(modifier = Modifier.height(AppSpacing.xs))

                AFField(
                    value = noteInput,
                    onValueChange = { noteInput = it },
                    placeholder = "Add a personal note",
                    singleLine = false,
                    errorMessage = if (!formState.isNoteValid) "Note cannot exceed 200 characters" else null
                )
            }

            // SERVER ERROR MESSAGE
            if (serverError != null) {
                Text(
                    text = serverError ?: "",
                    style = AutoFlowTheme.typography.bodyMedium,
                    color = colors.red,
                    modifier = Modifier.padding(vertical = AppSpacing.xs)
                )
            }

            // 7. BOTTOM ACTION BUTTONS
            Spacer(modifier = Modifier.height(AppSpacing.sm))

            AFButton(
                text = "Save changes",
                onClick = {
                    val amountPaiseToSave = if (formState.isAmountEditable) formState.parsedAmountPaise else null
                    onSave(
                        currentTx.id,
                        merchantInput,
                        selectedCategory,
                        selectedDateTime,
                        selectedPaymentMode,
                        noteInput,
                        amountPaiseToSave
                    ) { result ->
                        when (result) {
                            is UpdateTransactionResult.Success -> {
                                onNavigateBack()
                            }
                            is UpdateTransactionResult.FutureDateNotAllowed -> {
                                serverError = "Future date and time not allowed"
                            }
                            is UpdateTransactionResult.NoteTooLong -> {
                                serverError = "Note cannot exceed 200 characters"
                            }
                            is UpdateTransactionResult.InvalidAmount,
                            is UpdateTransactionResult.CannotEditSmsAmount -> {
                                serverError = "Invalid transaction amount"
                            }
                            is UpdateTransactionResult.CategoryNotFound -> {
                                serverError = "Selected category does not exist"
                            }
                            is UpdateTransactionResult.InvalidPaymentMode -> {
                                serverError = "Invalid payment mode selected"
                            }
                            is UpdateTransactionResult.NotFound -> {
                                serverError = "Transaction not found"
                            }
                        }
                    }
                },
                variant = AFButtonVariant.PRIMARY,
                enabled = formState.isSaveEnabled,
                modifier = Modifier.fillMaxWidth()
            )

            AFButton(
                text = formState.secondaryButtonText,
                onClick = {
                    if (formState.isExcluded) {
                        onRestore(currentTx.id)
                    } else {
                        onMarkNotAnExpense(currentTx.id)
                    }
                    onNavigateBack()
                },
                variant = AFButtonVariant.SECONDARY,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }

    // CONFIRM DELETE DIALOG
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            containerColor = colors.sheet,
            titleContentColor = colors.ink,
            textContentColor = colors.muted,
            title = {
                Text(
                    text = "Delete this transaction?",
                    style = AutoFlowTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "Are you sure you want to delete this transaction? This cannot be undone.",
                    style = AutoFlowTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        onDelete(currentTx.id)
                        onNavigateBack()
                    }
                ) {
                    Text(
                        text = "Delete",
                        style = AutoFlowTheme.typography.labelLarge,
                        color = colors.red
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDeleteDialog = false }
                ) {
                    Text(
                        text = "Cancel",
                        style = AutoFlowTheme.typography.labelLarge,
                        color = colors.muted
                    )
                }
            }
        )
    }

    // DISCARD CHANGES DIALOG
    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            containerColor = colors.sheet,
            titleContentColor = colors.ink,
            textContentColor = colors.muted,
            title = {
                Text(
                    text = "Discard changes?",
                    style = AutoFlowTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "You have unsaved changes. Are you sure you want to discard them?",
                    style = AutoFlowTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDiscardDialog = false
                        onNavigateBack()
                    }
                ) {
                    Text(
                        text = "Discard",
                        style = AutoFlowTheme.typography.labelLarge,
                        color = colors.red
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDiscardDialog = false }
                ) {
                    Text(
                        text = "Keep editing",
                        style = AutoFlowTheme.typography.labelLarge,
                        color = colors.brand
                    )
                }
            }
        )
    }
}

@Preview(name = "Light - EditTransactionScreen")
@Composable
private fun EditTransactionScreenLightPreview() {
    AutoFlowTheme(darkTheme = false) {
        EditTransactionScreen(
            transactionId = 1L,
            loadTransaction = {
                FinancialTransaction(
                    id = 1L,
                    amountPaise = 25000L,
                    recordType = com.autoflow.app.data.model.RecordType.EXPENSE,
                    dateTime = System.currentTimeMillis(),
                    merchantName = "Zomato",
                    category = "Food",
                    categorySource = "USER",
                    paymentMode = "UPI",
                    note = "Dinner with friends",
                    accountNumber = null,
                    bankName = null,
                    reference = null,
                    transactionHash = "h1",
                    source = TransactionSource.SMS_AUTO,
                    reviewStatus = ReviewStatus.CONFIRMED,
                    uncertaintyReasons = emptySet(),
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis(),
                    isDeleted = false
                )
            },
            onSave = { _, _, _, _, _, _, _, _ -> },
            onMarkNotAnExpense = {},
            onRestore = {},
            onDelete = {},
            onNavigateBack = {}
        )
    }
}

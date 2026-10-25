package com.autoflow.app.presentation.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.res.Configuration
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
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.autoflow.app.domain.category.BUILT_IN_CATEGORIES
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
import com.autoflow.app.presentation.ui.form.AddExpenseFormState
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddCashExpenseScreen(
    onSave: (amountPaise: Long, merchant: String, category: String, dateTime: Long, paymentMode: String) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    categories: List<String> = BUILT_IN_CATEGORIES
) {
    val colors = AutoFlowTheme.colors
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    var amountInput by remember { mutableStateOf("") }
    var merchantInput by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("Food") }
    var selectedMode by remember { mutableStateOf("CASH") }
    var isCustomDate by remember { mutableStateOf(false) }
    var selectedTimestamp by remember { mutableLongStateOf(System.currentTimeMillis()) }

    val now = remember { System.currentTimeMillis() }

    val formState = AddExpenseFormState(
        amountInput = amountInput,
        merchantInput = merchantInput,
        selectedCategory = selectedCategory,
        selectedPaymentMode = selectedMode,
        isCustomDate = isCustomDate,
        selectedTimestamp = selectedTimestamp,
        now = System.currentTimeMillis()
    )

    val dateLabel = remember(isCustomDate, selectedTimestamp) {
        val zone = ZoneId.systemDefault()
        if (!isCustomDate) {
            val dateStr = Instant.ofEpochMilli(System.currentTimeMillis())
                .atZone(zone)
                .format(DateTimeFormatter.ofPattern("EEE, d MMM", Locale.getDefault()))
            "Now · $dateStr"
        } else {
            Instant.ofEpochMilli(selectedTimestamp)
                .atZone(zone)
                .format(DateTimeFormatter.ofPattern("EEE, d MMM, h:mm a", Locale.getDefault()))
        }
    }

    val openDatePicker: () -> Unit = {
        val zone = ZoneId.systemDefault()
        val currentZoned = Instant.ofEpochMilli(selectedTimestamp).atZone(zone)

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
                        selectedTimestamp = pickedZoned.toInstant().toEpochMilli()
                        isCustomDate = true
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

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = colors.bg,
        topBar = {
            AFTopBar(
                title = "Add expense",
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
        },
        bottomBar = {
            Surface(
                color = colors.bg,
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .imePadding()
            ) {
                Box(
                    modifier = Modifier.padding(horizontal = AppSpacing.md, vertical = AppSpacing.sm)
                ) {
                    AFButton(
                        text = "Save",
                        onClick = {
                            val amountPaise = formState.parsedAmountPaise ?: return@AFButton
                            val finalMerchant = merchantInput.trim().ifBlank { "Cash Expense" }
                            val finalTimestamp = formState.effectiveDateTime
                            onSave(amountPaise, finalMerchant, selectedCategory, finalTimestamp, selectedMode)
                            onNavigateBack()
                        },
                        variant = AFButtonVariant.PRIMARY,
                        enabled = formState.isSaveEnabled,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(scrollState)
                .padding(horizontal = AppSpacing.md, vertical = AppSpacing.md),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.md)
        ) {
            // 1. AMOUNT FIELD
            Column(modifier = Modifier.fillMaxWidth()) {
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

                if (formState.amountHint != null) {
                    Spacer(modifier = Modifier.height(AppSpacing.xs))
                    Text(
                        text = formState.amountHint,
                        style = AutoFlowTheme.typography.bodySmall,
                        color = colors.muted,
                        modifier = Modifier.padding(start = AppSpacing.xs)
                    )
                }
            }

            // 2. MERCHANT OR NOTE FIELD
            AFField(
                value = merchantInput,
                onValueChange = { merchantInput = it },
                label = "Merchant or note",
                placeholder = "e.g. Tea stall"
            )

            // 3. CATEGORY CHIPS
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Category",
                    style = AutoFlowTheme.typography.labelLarge,
                    color = colors.ink,
                    modifier = Modifier.padding(bottom = AppSpacing.xs)
                )

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(AppSpacing.chipGap),
                    verticalArrangement = Arrangement.spacedBy(AppSpacing.chipGap)
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

            // 4. PAYMENT MODE SEGMENTED CONTROL
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Payment mode",
                    style = AutoFlowTheme.typography.labelLarge,
                    color = colors.ink,
                    modifier = Modifier.padding(bottom = AppSpacing.xs)
                )

                val modes = listOf("Cash", "Credit", "Bank", "UPI")
                val modeKeys = listOf("CASH", "CREDIT", "BANK", "UPI")
                val selectedIndex = modeKeys.indexOf(selectedMode).coerceAtLeast(0)

                AFSegmented(
                    items = modes,
                    selectedIndex = selectedIndex,
                    onSelect = { index ->
                        if (index in modeKeys.indices) {
                            selectedMode = modeKeys[index]
                        }
                    }
                )
            }

            // 5. DATE AND TIME ROW
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
                            text = dateLabel,
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
        }
    }
}

@Preview(name = "Light - AddCashExpenseScreen")
@Composable
private fun AddCashExpenseScreenLightPreview() {
    AutoFlowTheme(darkTheme = false) {
        AddCashExpenseScreen(
            onSave = { _, _, _, _, _ -> },
            onNavigateBack = {}
        )
    }
}

@Preview(name = "Dark - AddCashExpenseScreen", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun AddCashExpenseScreenDarkPreview() {
    AutoFlowTheme(darkTheme = true) {
        AddCashExpenseScreen(
            onSave = { _, _, _, _, _ -> },
            onNavigateBack = {}
        )
    }
}

package com.autoflow.app.presentation.ui

import android.content.res.Configuration
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.autoflow.app.R
import com.autoflow.app.domain.category.BUILT_IN_CATEGORIES
import com.autoflow.app.presentation.theme.AppSpacing
import com.autoflow.app.presentation.theme.AutoFlowTheme
import com.autoflow.app.presentation.ui.components.AFButton
import com.autoflow.app.presentation.ui.components.AFButtonVariant
import com.autoflow.app.presentation.ui.components.AFCategoryChip
import com.autoflow.app.presentation.ui.components.AFCategoryIcons
import com.autoflow.app.presentation.ui.components.AFField
import com.autoflow.app.presentation.ui.components.AFNumberPad
import com.autoflow.app.presentation.ui.components.AFTopBar
import com.autoflow.app.presentation.ui.form.ExpenseFormLogic
import com.autoflow.app.presentation.util.CurrencyFormatter

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun QuickExpenseScreen(
    onSave: (amountPaise: Long, merchant: String, category: String) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    categories: List<String> = BUILT_IN_CATEGORIES
) {
    val colors = AutoFlowTheme.colors
    val scrollState = rememberScrollState()

    var amountInput by remember { mutableStateOf("") }
    var merchantInput by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("Food") }

    val amountPaise = remember(amountInput) {
        ExpenseFormLogic.parseAmount(amountInput) ?: 0L
    }

    val isSaveEnabled = amountPaise > 0L && amountPaise <= ExpenseFormLogic.MAX_AMOUNT_PAISE

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = colors.bg,
        topBar = {
            AFTopBar(
                title = stringResource(R.string.quick_expense_title),
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
        },
        bottomBar = {
            Surface(
                color = colors.bg,
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .imePadding()
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = AppSpacing.md, vertical = AppSpacing.sm),
                    verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)
                ) {
                    AFButton(
                        text = stringResource(R.string.quick_expense_save_button),
                        onClick = {
                            if (isSaveEnabled) {
                                val finalMerchant = merchantInput.trim().ifBlank { "Cash Expense" }
                                onSave(amountPaise, finalMerchant, selectedCategory)
                            }
                        },
                        variant = AFButtonVariant.PRIMARY,
                        enabled = isSaveEnabled,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text(
                        text = stringResource(R.string.quick_expense_footnote),
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
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(scrollState)
                .padding(AppSpacing.md),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.md)
        ) {
            // Large amount display at top
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

            // Merchant or note (optional)
            AFField(
                value = merchantInput,
                onValueChange = { merchantInput = it },
                label = stringResource(R.string.quick_expense_merchant_label),
                placeholder = stringResource(R.string.quick_expense_merchant_placeholder)
            )

            // Category chips (all categories including custom ones)
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.quick_expense_category_label),
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
        }
    }
}

@Preview(name = "Light - QuickExpenseScreen")
@Composable
private fun QuickExpenseScreenLightPreview() {
    AutoFlowTheme(darkTheme = false) {
        QuickExpenseScreen(
            onSave = { _, _, _ -> },
            onNavigateBack = {}
        )
    }
}

@Preview(name = "Dark - QuickExpenseScreen", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun QuickExpenseScreenDarkPreview() {
    AutoFlowTheme(darkTheme = true) {
        QuickExpenseScreen(
            onSave = { _, _, _ -> },
            onNavigateBack = {}
        )
    }
}

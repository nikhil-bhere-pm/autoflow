package com.autoflow.app.presentation.ui

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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import com.autoflow.app.domain.model.FinancialTransaction
import com.autoflow.app.presentation.theme.AppRadius
import com.autoflow.app.presentation.theme.AppSpacing
import com.autoflow.app.presentation.util.CurrencyFormatter
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

val CATEGORIES = listOf(
    "Food",
    "Groceries",
    "Transport",
    "Shopping",
    "Bills",
    "Entertainment",
    "Health",
    "Travel",
    "Others"
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EditTransactionScreen(
    transactionId: Long,
    loadTransaction: suspend (Long) -> FinancialTransaction?,
    onSaveCorrection: (id: Long, newMerchant: String, newCategory: String) -> Unit,
    onMarkNotAnExpense: (id: Long) -> Unit,
    onDelete: (id: Long) -> Unit,
    onNavigateBack: () -> Unit
) {
    var transaction by remember { mutableStateOf<FinancialTransaction?>(null) }
    var merchantNameInput by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("Others") }

    LaunchedEffect(transactionId) {
        val loaded = loadTransaction(transactionId)
        if (loaded != null) {
            transaction = loaded
            merchantNameInput = if (loaded.merchantName == "Unknown Merchant") "" else loaded.merchantName
            selectedCategory = if (loaded.category in CATEGORIES) loaded.category else "Others"
        }
    }

    val currentTx = transaction
    val scrollState = rememberScrollState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Edit Transaction",
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
        if (currentTx == null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(AppSpacing.lg),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "Loading transaction details...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            val dateTimeFormatter = remember {
                DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a")
            }
            val formattedDate = remember(currentTx.dateTime) {
                Instant.ofEpochMilli(currentTx.dateTime)
                    .atZone(ZoneId.systemDefault())
                    .format(dateTimeFormatter)
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .verticalScroll(scrollState)
                    .padding(horizontal = AppSpacing.md, vertical = AppSpacing.md),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.lg)
            ) {
                // SUMMARY CARD
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
                            text = "Amount",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(AppSpacing.xxs))
                        Text(
                            text = CurrencyFormatter.formatPaiseToInr(currentTx.amountPaise),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(AppSpacing.xs))
                        Text(
                            text = formattedDate,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // MERCHANT NAME FIELD
                Column {
                    Text(
                        text = "Merchant / Payee Name",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(AppSpacing.xs))
                    OutlinedTextField(
                        value = merchantNameInput,
                        onValueChange = { merchantNameInput = it },
                        placeholder = {
                            Text(
                                text = "Merchant name (optional)",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = AppSpacing.minTapTarget)
                            .semantics { contentDescription = "Merchant name input" },
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

                Spacer(modifier = Modifier.height(AppSpacing.sm))

                // ACTION BUTTONS
                Column(
                    verticalArrangement = Arrangement.spacedBy(AppSpacing.sm),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (currentTx.reviewStatus == com.autoflow.app.data.model.ReviewStatus.EXCLUDED) {
                        // RESTORE BUTTON
                        Button(
                            onClick = {
                                val finalMerchant = merchantNameInput.trim().ifBlank { "Unknown Merchant" }
                                onSaveCorrection(currentTx.id, finalMerchant, selectedCategory)
                                onNavigateBack()
                            },
                            shape = RoundedCornerShape(AppRadius.sm),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = AppSpacing.minTapTarget)
                                .semantics { contentDescription = "Restore transaction" }
                        ) {
                            Text(
                                text = "Restore",
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    } else {
                        // SAVE CHANGES (Sets reviewStatus to CONFIRMED, updates row)
                        Button(
                            onClick = {
                                val finalMerchant = merchantNameInput.trim().ifBlank { "Unknown Merchant" }
                                onSaveCorrection(currentTx.id, finalMerchant, selectedCategory)
                                onNavigateBack()
                            },
                            shape = RoundedCornerShape(AppRadius.sm),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = AppSpacing.minTapTarget)
                                .semantics { contentDescription = "Save changes" }
                        ) {
                            Text(
                                text = "Save Changes",
                                style = MaterialTheme.typography.labelLarge
                            )
                        }

                        // NOT AN EXPENSE (Sets reviewStatus to EXCLUDED)
                        OutlinedButton(
                            onClick = {
                                onMarkNotAnExpense(currentTx.id)
                                onNavigateBack()
                            },
                            shape = RoundedCornerShape(AppRadius.sm),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.secondary
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = AppSpacing.minTapTarget)
                                .semantics { contentDescription = "Mark as not an expense" }
                        ) {
                            Text(
                                text = "Not an expense",
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }

                    // DELETE (Soft delete)
                    OutlinedButton(
                        onClick = {
                            onDelete(currentTx.id)
                            onNavigateBack()
                        },
                        shape = RoundedCornerShape(AppRadius.sm),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = AppSpacing.minTapTarget)
                            .semantics { contentDescription = "Delete transaction" }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.width(AppSpacing.xs))
                            Text(
                                text = "Delete transaction",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        }
    }
}

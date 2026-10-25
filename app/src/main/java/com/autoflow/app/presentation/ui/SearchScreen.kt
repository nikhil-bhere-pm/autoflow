package com.autoflow.app.presentation.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.autoflow.app.data.model.RecordType
import com.autoflow.app.data.model.ReviewStatus
import com.autoflow.app.domain.model.FinancialTransaction
import com.autoflow.app.presentation.theme.AppSpacing
import com.autoflow.app.presentation.theme.AutoFlowTheme
import com.autoflow.app.presentation.ui.components.AFTopBar
import com.autoflow.app.presentation.ui.components.AFTransactionRow
import com.autoflow.app.presentation.util.CurrencyFormatter
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun SearchScreen(
    query: String,
    results: List<FinancialTransaction>,
    onQueryChange: (String) -> Unit,
    onNavigateBack: () -> Unit,
    onNavigateToEdit: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = AutoFlowTheme.colors
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = colors.bg,
        topBar = {
            AFTopBar(
                title = "Search",
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = colors.ink
                        )
                    }
                },
                showDivider = true
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
        ) {
            // Search input field
            Surface(
                color = colors.sheet,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AppSpacing.md, vertical = AppSpacing.sm),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = colors.muted,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(modifier = Modifier.weight(1f)) {
                        if (query.isEmpty()) {
                            Text(
                                text = "Search merchant, note, category or amount",
                                style = AutoFlowTheme.typography.bodyMedium,
                                color = colors.muted,
                                maxLines = 1
                            )
                        }
                        BasicTextField(
                            value = query,
                            onValueChange = onQueryChange,
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(focusRequester),
                            textStyle = AutoFlowTheme.typography.bodyMedium.copy(color = colors.ink),
                            cursorBrush = Brush.verticalGradient(listOf(colors.brand, colors.brand)),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = {})
                        )
                    }
                    if (query.isNotEmpty()) {
                        IconButton(
                            onClick = { onQueryChange("") },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "Clear search",
                                tint = colors.muted,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(AppSpacing.sm))

            // Body: Empty query vs No matches vs Results
            when {
                query.trim().isEmpty() -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = 48.dp, start = AppSpacing.md, end = AppSpacing.md),
                        contentAlignment = Alignment.TopCenter
                    ) {
                        Text(
                            text = "Search merchant, note, category or amount",
                            style = AutoFlowTheme.typography.bodyLarge,
                            color = colors.muted,
                            textAlign = TextAlign.Center
                        )
                    }
                }
                results.isEmpty() -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = 48.dp, start = AppSpacing.md, end = AppSpacing.md),
                        contentAlignment = Alignment.TopCenter
                    ) {
                        Text(
                            text = "No matches",
                            style = AutoFlowTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                            color = colors.muted,
                            textAlign = TextAlign.Center
                        )
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(
                            items = results,
                            key = { it.id }
                        ) { tx ->
                            val isRefund = tx.recordType == RecordType.REFUND
                            val formattedAmount = if (isRefund) {
                                "+${CurrencyFormatter.formatPaiseToInr(tx.amountPaise)}"
                            } else {
                                CurrencyFormatter.formatPaiseToInr(tx.amountPaise)
                            }
                            val isNotCounted = tx.reviewStatus == ReviewStatus.EXCLUDED
                            val dateTimeStr = formatSearchDateTime(tx.dateTime)
                            val subtitle = if (isNotCounted) "Not counted · $dateTimeStr" else "${tx.category} · $dateTimeStr"
                            val merchant = tx.merchantName.trim().ifBlank { "Unknown merchant" }

                            AFTransactionRow(
                                title = merchant,
                                subtitle = subtitle,
                                amountText = formattedAmount,
                                category = tx.category,
                                isPositive = isRefund,
                                isNotCounted = isNotCounted,
                                modifier = Modifier.padding(horizontal = AppSpacing.md),
                                onClick = { onNavigateToEdit(tx.id) }
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun formatSearchDateTime(epochMillis: Long): String {
    val zone = ZoneId.systemDefault()
    return Instant.ofEpochMilli(epochMillis)
        .atZone(zone)
        .format(DateTimeFormatter.ofPattern("d MMM, h:mm a"))
}

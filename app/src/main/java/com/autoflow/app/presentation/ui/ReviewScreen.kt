package com.autoflow.app.presentation.ui

import android.content.res.Configuration
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import com.autoflow.app.R
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.autoflow.app.domain.category.BUILT_IN_CATEGORIES
import com.autoflow.app.domain.category.CategoryEngine
import com.autoflow.app.domain.model.FinancialTransaction
import com.autoflow.app.presentation.theme.AppRadius
import com.autoflow.app.presentation.theme.AppSpacing
import com.autoflow.app.presentation.theme.AutoFlowTheme
import com.autoflow.app.presentation.ui.components.AFBottomNav
import com.autoflow.app.presentation.ui.components.AFBottomNavItem
import com.autoflow.app.presentation.ui.components.AFButton
import com.autoflow.app.presentation.ui.components.AFButtonVariant
import com.autoflow.app.presentation.ui.components.AFCategoryChip
import androidx.compose.ui.res.painterResource
import com.autoflow.app.presentation.ui.components.AFCategoryIcons
import com.autoflow.app.presentation.ui.components.AFTopBar
import com.autoflow.app.presentation.util.CurrencyFormatter
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

fun getTopLikelyCategories(
    tx: FinancialTransaction,
    allCategories: List<String> = BUILT_IN_CATEGORIES
): List<String> {
    val candidates = linkedSetOf<String>()

    // 1. Try CategoryEngine keyword/rule resolution from merchant
    val resolved = CategoryEngine.resolveCategory(tx.merchantName)
    if (resolved.category.isNotBlank() && !resolved.category.equals("Others", ignoreCase = true)) {
        candidates.add(resolved.category)
    }

    // 2. Existing transaction category if set and not Others
    if (tx.category.isNotBlank() && !tx.category.equals("Others", ignoreCase = true)) {
        candidates.add(tx.category)
    }

    // 3. Fallback common built-in categories
    val defaults = listOf("Food", "Groceries", "Shopping", "Transport", "Bills", "Health", "Entertainment", "Others")
    for (cat in defaults) {
        if (candidates.size >= 3) break
        candidates.add(cat)
    }

    // 4. Fill from custom registry if still needed
    for (cat in allCategories) {
        if (candidates.size >= 3) break
        candidates.add(cat)
    }

    return candidates.take(3)
}

@Composable
fun ReviewScreen(
    transactions: List<FinancialTransaction>,
    categories: List<String>,
    onNavigateToHome: () -> Unit,
    onNavigateToAddCash: () -> Unit,
    onNavigateToEdit: (Long) -> Unit,
    onConfirmCategory: (Long, String) -> Unit,
    onLater: (Long) -> Unit,
    modifier: Modifier = Modifier,
    onNavigateToCategories: () -> Unit = {},
    onNavigateToGoals: () -> Unit = {},
    snackbarHostState: androidx.compose.material3.SnackbarHostState = remember { androidx.compose.material3.SnackbarHostState() }
) {
    val colors = AutoFlowTheme.colors
    val count = transactions.size

    // In-memory skipped items tracking for "Later"
    var skippedIds by remember { mutableStateOf(emptySet<Long>()) }

    // Partition transactions: active vs skipped
    val activeTransactions = transactions.filter { it.id !in skippedIds }
    val laterTransactions = transactions.filter { it.id in skippedIds }
    val displayList = activeTransactions + laterTransactions

    val zone = ZoneId.systemDefault()
    val groupedMap = displayList.groupBy {
        Instant.ofEpochMilli(it.dateTime).atZone(zone).toLocalDate()
    }
    val sortedDates = groupedMap.keys.sortedDescending()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = colors.bg,
        snackbarHost = { androidx.compose.material3.SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            AFTopBar(
                title = "Needs a check ($count)",
                showDivider = true
            )
        },
        bottomBar = {
            AFBottomNav(
                items = com.autoflow.app.presentation.ui.components.createStandardNavItems(unreviewedCount = count),
                selectedIndex = 2,
                onSelect = { idx ->
                    when (idx) {
                        0 -> onNavigateToHome()
                        1 -> onNavigateToGoals()
                        2 -> { /* Already on Review */ }
                        3 -> onNavigateToCategories()
                    }
                },
                centerButton = {
                    Surface(
                        shape = CircleShape,
                        color = colors.button,
                        contentColor = colors.buttonText,
                        modifier = Modifier
                            .size(AppSpacing.minTapTarget)
                            .clickable(onClick = onNavigateToAddCash)
                            .semantics { contentDescription = "Add expense" }
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                                tint = colors.buttonText
                            )
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        if (transactions.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                ReviewEmptyState(
                    modifier = Modifier.padding(horizontal = AppSpacing.md)
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(
                    top = AppSpacing.md,
                    bottom = AppSpacing.xl
                ),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.md)
            ) {
                sortedDates.forEach { date ->
                    val dayItems = groupedMap[date] ?: emptyList()

                    item(key = "review_header_${date}") {
                        ReviewDayHeader(
                            date = date,
                            modifier = Modifier.padding(horizontal = AppSpacing.md)
                        )
                    }

                    items(
                        items = dayItems,
                        key = { it.id }
                    ) { tx ->
                        val isLater = tx.id in skippedIds
                        ReviewItemCard(
                            tx = tx,
                            isLater = isLater,
                            suggestedCategories = getTopLikelyCategories(tx, categories),
                            onCardClick = { onNavigateToEdit(tx.id) },
                            onConfirmCategory = { category ->
                                onConfirmCategory(tx.id, category)
                            },
                            onLater = {
                                skippedIds = skippedIds + tx.id
                                onLater(tx.id)
                            },
                            modifier = Modifier.padding(horizontal = AppSpacing.md)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ReviewDayHeader(
    date: LocalDate,
    modifier: Modifier = Modifier
) {
    val colors = AutoFlowTheme.colors
    val today = LocalDate.now(ZoneId.systemDefault())
    val headerDateString = when (date) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        else -> date.format(DateTimeFormatter.ofPattern("EEEE, d MMM"))
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = AppSpacing.sm, bottom = AppSpacing.xxs),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = headerDateString,
            style = AutoFlowTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = colors.ink
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReviewItemCard(
    tx: FinancialTransaction,
    isLater: Boolean,
    suggestedCategories: List<String>,
    onCardClick: () -> Unit,
    onConfirmCategory: (String) -> Unit,
    onLater: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = AutoFlowTheme.colors
    val merchant = tx.merchantName.trim().ifBlank { "Unknown merchant" }
    val zone = ZoneId.systemDefault()
    val time = Instant.ofEpochMilli(tx.dateTime).atZone(zone).format(DateTimeFormatter.ofPattern("h:mm a"))
    val formattedAmount = CurrencyFormatter.formatPaiseToInr(tx.amountPaise)

    Surface(
        shape = RoundedCornerShape(AppRadius.md),
        color = colors.sheet,
        border = BorderStroke(1.dp, colors.line),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(AppSpacing.md)
        ) {
            // Card body row (clicking opens Edit Transaction screen)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(AppRadius.sm))
                    .clickable(onClick = onCardClick)
                    .padding(vertical = AppSpacing.xs),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = merchant,
                        style = AutoFlowTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = time,
                        style = AutoFlowTheme.typography.bodySmall,
                        color = colors.muted
                    )
                }

                Spacer(modifier = Modifier.width(AppSpacing.md))

                Text(
                    text = formattedAmount,
                    style = AutoFlowTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = colors.ink
                )
            }

            HorizontalDivider(
                thickness = 1.dp,
                color = colors.line.copy(alpha = 0.5f),
                modifier = Modifier.padding(vertical = AppSpacing.sm)
            )

            // Category Chips: 3 top categories + Later button (wrapping rows)
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.chipGap),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.chipGap)
            ) {
                suggestedCategories.forEach { category ->
                    val iconRes = AFCategoryIcons.getDrawableRes(category)
                    AFCategoryChip(
                        text = category,
                        selected = false,
                        onClick = { onConfirmCategory(category) },
                        icon = {
                            Icon(
                                painter = painterResource(id = iconRes),
                                contentDescription = null,
                                tint = colors.forCategory(category),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    )
                }

                AFButton(
                    text = if (isLater) "Pending" else "Later",
                    onClick = onLater,
                    variant = AFButtonVariant.SECONDARY,
                    modifier = Modifier.defaultMinSize(minHeight = AppSpacing.chipVisualHeight)
                )
            }
        }
    }
}

@Composable
private fun ReviewEmptyState(
    modifier: Modifier = Modifier
) {
    val colors = AutoFlowTheme.colors

    Surface(
        shape = RoundedCornerShape(AppRadius.lg),
        color = colors.sheet,
        border = BorderStroke(1.dp, colors.line),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(AppSpacing.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                tint = colors.brand,
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(AppSpacing.md))
            Text(
                text = "All caught up",
                style = AutoFlowTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = colors.ink
            )
            Spacer(modifier = Modifier.height(AppSpacing.xs))
            Text(
                text = "Nothing needs your attention",
                style = AutoFlowTheme.typography.bodyMedium,
                color = colors.muted
            )
        }
    }
}

@Preview(name = "Light - ReviewScreen")
@Composable
private fun ReviewScreenLightPreview() {
    AutoFlowTheme(darkTheme = false) {
        ReviewScreen(
            transactions = emptyList(),
            categories = BUILT_IN_CATEGORIES,
            onNavigateToHome = {},
            onNavigateToAddCash = {},
            onNavigateToEdit = {},
            onConfirmCategory = { _, _ -> },
            onLater = {}
        )
    }
}

@Preview(name = "Dark - ReviewScreen", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ReviewScreenDarkPreview() {
    AutoFlowTheme(darkTheme = true) {
        ReviewScreen(
            transactions = emptyList(),
            categories = BUILT_IN_CATEGORIES,
            onNavigateToHome = {},
            onNavigateToAddCash = {},
            onNavigateToEdit = {},
            onConfirmCategory = { _, _ -> },
            onLater = {}
        )
    }
}

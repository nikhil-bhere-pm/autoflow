package com.autoflow.app.presentation.ui

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.autoflow.app.R
import com.autoflow.app.data.model.RecordType
import com.autoflow.app.domain.model.FinancialTransaction
import com.autoflow.app.domain.stats.StatsPeriod
import com.autoflow.app.presentation.theme.AppRadius
import com.autoflow.app.presentation.theme.AppSpacing
import com.autoflow.app.presentation.theme.AutoFlowTheme
import com.autoflow.app.presentation.ui.components.AFBadge
import com.autoflow.app.presentation.ui.components.AFBadgeVariant
import com.autoflow.app.presentation.ui.components.AFBottomNav
import com.autoflow.app.presentation.ui.components.AFBottomNavItem
import com.autoflow.app.presentation.ui.components.AFCategoryIcons
import com.autoflow.app.presentation.ui.components.AFPeriodPill
import com.autoflow.app.presentation.ui.components.AFRingChart
import com.autoflow.app.presentation.ui.components.AFTopBar
import com.autoflow.app.presentation.ui.components.AFTransactionRow
import com.autoflow.app.presentation.util.CurrencyFormatter
import com.autoflow.app.presentation.viewmodel.CategoriesUiState
import com.autoflow.app.presentation.viewmodel.CategorySpend
import com.autoflow.app.presentation.viewmodel.HomeBadgeType
import com.autoflow.app.presentation.viewmodel.PeriodFilter
import com.autoflow.app.presentation.viewmodel.getHomeBadgeType
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun CategoriesScreen(
    uiState: CategoriesUiState,
    unreviewedCount: Int,
    onSelectPeriod: (StatsPeriod) -> Unit,
    onSelectMonth: (YearMonth) -> Unit,
    onSelectCategory: (String?) -> Unit,
    onNavigateToHome: () -> Unit,
    onNavigateToReview: () -> Unit,
    onNavigateToAddCash: () -> Unit,
    onNavigateToEdit: (Long) -> Unit,
    modifier: Modifier = Modifier,
    onNavigateToGoals: () -> Unit = {}
) {
    val colors = AutoFlowTheme.colors

    BackHandler(enabled = uiState.selectedCategory != null) {
        onSelectCategory(null)
    }

    if (uiState.selectedCategory != null) {
        CategoryDetailScreen(
            category = uiState.selectedCategory,
            transactions = uiState.categoryTransactions,
            pillLabel = uiState.pillLabel,
            onBack = { onSelectCategory(null) },
            onNavigateToEdit = onNavigateToEdit,
            modifier = modifier
        )
        return
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = colors.bg,
        topBar = {
            AFTopBar(
                title = "Categories",
                showDivider = true
            )
        },
        bottomBar = {
            AFBottomNav(
                items = listOf(
                    AFBottomNavItem(
                        title = "Home",
                        icon = { selected ->
                            Icon(
                                imageVector = Icons.Default.Home,
                                contentDescription = "Home",
                                tint = if (selected) colors.brand else colors.muted
                            )
                        }
                    ),
                    AFBottomNavItem(
                        title = "Goals",
                        icon = { selected ->
                            Icon(
                                painter = painterResource(id = R.drawable.ic_nav_goals),
                                contentDescription = "Goals",
                                tint = if (selected) colors.brand else colors.muted
                            )
                        }
                    ),
                    AFBottomNavItem(
                        title = "Review",
                        badgeCount = unreviewedCount,
                        icon = { selected ->
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Review",
                                tint = if (selected) colors.brand else colors.muted
                            )
                        }
                    ),
                    AFBottomNavItem(
                        title = "Categories",
                        icon = { selected ->
                            Icon(
                                painter = painterResource(id = R.drawable.ic_cat_others),
                                contentDescription = "Categories",
                                tint = if (selected) colors.brand else colors.muted
                            )
                        }
                    )
                ),
                selectedIndex = 3,
                onSelect = { idx ->
                    when (idx) {
                        0 -> onNavigateToHome()
                        1 -> onNavigateToGoals()
                        2 -> onNavigateToReview()
                        3 -> { /* Already on Categories */ }
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
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(
                start = AppSpacing.md,
                end = AppSpacing.md,
                top = AppSpacing.md,
                bottom = AppSpacing.xl
            ),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.md)
        ) {
            // Period selector pill row
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Start
                ) {
                    AFPeriodPill(
                        pillLabel = uiState.pillLabel,
                        onSelectPeriod = onSelectPeriod,
                        onSelectMonth = onSelectMonth
                    )
                }
            }

            if (uiState.isEmpty) {
                item {
                    CategoriesEmptyState()
                }
            } else {
                // Hero: Ring Chart
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = AppSpacing.md),
                        contentAlignment = Alignment.Center
                    ) {
                        AFRingChart(
                            categories = uiState.categories,
                            totalSpendPaise = uiState.totalSpendPaise,
                            topCategory = uiState.topCategory
                        )
                    }
                }

                // Header for Category breakdown list
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = AppSpacing.xs),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Spend by category",
                            style = AutoFlowTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = colors.ink
                        )
                        Text(
                            text = CurrencyFormatter.formatPaiseToInr(uiState.totalSpendPaise),
                            style = AutoFlowTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = colors.muted
                        )
                    }
                }

                // Category rows sorted high to low
                items(uiState.categories) { cat ->
                    CategoryRow(
                        category = cat,
                        onClick = { onSelectCategory(cat.category) }
                    )
                }
            }
        }
    }
}

@Composable
private fun CategoryRow(
    category: CategorySpend,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = AutoFlowTheme.colors
    val catColor = colors.forCategory(category.category)
    val catBg = colors.categoryTileBackground(category.category)
    val iconRes = AFCategoryIcons.getDrawableRes(category.category)

    Surface(
        shape = RoundedCornerShape(AppRadius.md),
        color = colors.sheet,
        border = androidx.compose.foundation.BorderStroke(1.dp, colors.line),
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AppSpacing.md, vertical = AppSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.md)
            ) {
                // Icon tile
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(AppRadius.sm))
                        .clickable(enabled = false) {},
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        shape = RoundedCornerShape(AppRadius.sm),
                        color = catBg,
                        modifier = Modifier.fillMaxSize()
                    ) {}
                    Icon(
                        painter = painterResource(id = iconRes),
                        contentDescription = null,
                        tint = catColor,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Column {
                    Text(
                        text = category.category,
                        style = AutoFlowTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.ink
                    )
                    Text(
                        text = "${category.percent}% of total",
                        style = AutoFlowTheme.typography.bodySmall,
                        color = colors.muted
                    )
                }
            }

            Text(
                text = CurrencyFormatter.formatPaiseToInr(category.totalPaise),
                style = AutoFlowTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = colors.ink
            )
        }
    }
}

@Composable
private fun CategoriesEmptyState(
    modifier: Modifier = Modifier
) {
    val colors = AutoFlowTheme.colors

    Surface(
        shape = RoundedCornerShape(AppRadius.lg),
        color = colors.sheet,
        border = androidx.compose.foundation.BorderStroke(1.dp, colors.line),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = AppSpacing.xl, horizontal = AppSpacing.md),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "No spending in this period",
                style = AutoFlowTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = colors.ink
            )

            Spacer(modifier = Modifier.height(AppSpacing.xs))

            Text(
                text = "Expenses for this period will appear broken down by category.",
                style = AutoFlowTheme.typography.bodyMedium,
                color = colors.muted,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun CategoryDetailScreen(
    category: String,
    transactions: List<FinancialTransaction>,
    pillLabel: String,
    onBack: () -> Unit,
    onNavigateToEdit: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = AutoFlowTheme.colors
    val totalPaise = transactions.fold(0L) { acc, tx ->
        when (tx.recordType) {
            RecordType.EXPENSE -> Math.addExact(acc, tx.amountPaise)
            RecordType.REFUND -> Math.subtractExact(acc, tx.amountPaise)
            else -> acc
        }
    }.coerceAtLeast(0L)

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = colors.bg,
        topBar = {
            AFTopBar(
                title = category,
                showDivider = true,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back to categories",
                            tint = colors.ink
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(
                start = AppSpacing.md,
                end = AppSpacing.md,
                top = AppSpacing.md,
                bottom = AppSpacing.xl
            ),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)
        ) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = AppSpacing.xs),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = pillLabel,
                        style = AutoFlowTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.muted
                    )
                    Text(
                        text = CurrencyFormatter.formatPaiseToInr(totalPaise),
                        style = AutoFlowTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = colors.ink
                    )
                }
            }

            if (transactions.isEmpty()) {
                item {
                    CategoriesEmptyState()
                }
            } else {
                items(transactions) { tx ->
                    val isRefund = tx.recordType == RecordType.REFUND
                    val formattedAmount = if (isRefund) {
                        "+${CurrencyFormatter.formatPaiseToInr(tx.amountPaise)}"
                    } else {
                        CurrencyFormatter.formatPaiseToInr(tx.amountPaise)
                    }

                    val timeString = formatCategoryTxTime(tx.dateTime)
                    val merchant = tx.merchantName.trim().ifBlank { "Unknown merchant" }
                    val badgeType = tx.getHomeBadgeType()

                    AFTransactionRow(
                        title = merchant,
                        subtitle = timeString,
                        amountText = formattedAmount,
                        category = tx.category,
                        isPositive = isRefund,
                        badge = if (badgeType != null) {
                            {
                                when (badgeType) {
                                    HomeBadgeType.CHECK -> AFBadge(text = "Check", variant = AFBadgeVariant.AMBER)
                                    HomeBadgeType.NOT_COUNTED -> AFBadge(text = "Not counted", variant = AFBadgeVariant.MUTED)
                                    HomeBadgeType.REFUND -> AFBadge(text = "Refund", variant = AFBadgeVariant.REFUND)
                                }
                            }
                        } else null,
                        onClick = { onNavigateToEdit(tx.id) }
                    )
                }
            }
        }
    }
}

private fun formatCategoryTxTime(epochMillis: Long): String {
    val zone = ZoneId.systemDefault()
    return Instant.ofEpochMilli(epochMillis)
        .atZone(zone)
        .format(DateTimeFormatter.ofPattern("d MMM, h:mm a"))
}

@Preview(name = "Light - CategoriesScreen")
@Composable
private fun CategoriesScreenLightPreview() {
    AutoFlowTheme(darkTheme = false) {
        val fakeState = CategoriesUiState(
            periodFilter = PeriodFilter.Predefined(StatsPeriod.THIS_MONTH),
            pillLabel = "This month",
            totalSpendPaise = 250000L,
            categories = listOf(
                CategorySpend("Food", 125000L, 50, 4),
                CategorySpend("Shopping", 75000L, 30, 2),
                CategorySpend("Transport", 50000L, 20, 3)
            ),
            topCategory = CategorySpend("Food", 125000L, 50, 4),
            isEmpty = false
        )

        CategoriesScreen(
            uiState = fakeState,
            unreviewedCount = 1,
            onSelectPeriod = {},
            onSelectMonth = {},
            onSelectCategory = {},
            onNavigateToHome = {},
            onNavigateToReview = {},
            onNavigateToAddCash = {},
            onNavigateToEdit = {}
        )
    }
}

@Preview(name = "Dark - CategoriesScreen", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun CategoriesScreenDarkPreview() {
    AutoFlowTheme(darkTheme = true) {
        val fakeState = CategoriesUiState(
            periodFilter = PeriodFilter.Predefined(StatsPeriod.THIS_MONTH),
            pillLabel = "This month",
            totalSpendPaise = 0L,
            categories = emptyList(),
            topCategory = null,
            isEmpty = true
        )

        CategoriesScreen(
            uiState = fakeState,
            unreviewedCount = 0,
            onSelectPeriod = {},
            onSelectMonth = {},
            onSelectCategory = {},
            onNavigateToHome = {},
            onNavigateToReview = {},
            onNavigateToAddCash = {},
            onNavigateToEdit = {}
        )
    }
}

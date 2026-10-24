package com.autoflow.app.presentation.ui

import android.Manifest
import android.content.pm.PackageManager
import android.content.res.Configuration
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.autoflow.app.data.model.RecordType
import com.autoflow.app.domain.model.FinancialTransaction
import com.autoflow.app.domain.stats.MonthPoint
import com.autoflow.app.domain.stats.StatsPeriod
import com.autoflow.app.presentation.theme.AppRadius
import com.autoflow.app.presentation.theme.AppSpacing
import com.autoflow.app.presentation.theme.AutoFlowTheme
import com.autoflow.app.presentation.ui.components.AFBadge
import com.autoflow.app.presentation.ui.components.AFBadgeVariant
import com.autoflow.app.presentation.ui.components.AFBottomNav
import com.autoflow.app.presentation.ui.components.AFBottomNavItem
import com.autoflow.app.presentation.ui.components.AFButton
import com.autoflow.app.presentation.ui.components.AFButtonVariant
import com.autoflow.app.presentation.ui.components.AFSmoothLineChart
import com.autoflow.app.presentation.ui.components.AFTopBar
import com.autoflow.app.presentation.ui.components.AFTransactionRow
import com.autoflow.app.presentation.util.CurrencyFormatter
import com.autoflow.app.presentation.viewmodel.DayGroup
import com.autoflow.app.presentation.viewmodel.HomeBadgeType
import com.autoflow.app.presentation.viewmodel.HomeUiState
import com.autoflow.app.presentation.viewmodel.PeriodFilter
import com.autoflow.app.presentation.viewmodel.getHomeBadgeType
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun HomeScreen(
    uiState: HomeUiState,
    onSelectPeriod: (StatsPeriod) -> Unit,
    onSelectMonth: (YearMonth) -> Unit,
    onNavigateToEdit: (Long) -> Unit,
    onNavigateToAddCash: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = AutoFlowTheme.colors
    val context = LocalContext.current
    var hasSmsPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECEIVE_SMS
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasSmsPermission = isGranted
    }

    var showRefundInfoDialog by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = colors.bg,
        topBar = {
            // Top Bar with title only (settings icon omitted because settings screen is not built yet)
            AFTopBar(
                title = "AutoFlow",
                showDivider = true
            )
        },
        bottomBar = {
            // App Frame Bottom Bar: Home tab on left, round + button in center (opens Add Cash),
            // Review and Categories tabs omitted because those screens do not exist yet.
            // Floating action button removed per prompt requirements.
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
                    )
                ),
                selectedIndex = 0,
                onSelect = { /* Already on Home */ },
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
            // 1. Period Pill
            item {
                PeriodPill(
                    pillLabel = uiState.pillLabel,
                    onSelectPeriod = onSelectPeriod,
                    modifier = Modifier.padding(horizontal = AppSpacing.md)
                )
            }

            // 2. One Big Number & Transaction Count
            item {
                BigNumberSection(
                    countedSpendPaise = uiState.countedSpendPaise,
                    countedCount = uiState.countedTransactionCount,
                    onInfoClick = { showRefundInfoDialog = true },
                    modifier = Modifier.padding(horizontal = AppSpacing.md)
                )
            }

            // 3. 5-Month Spending Chart (Edge to edge on Home background)
            item {
                AFSmoothLineChart(
                    monthlySeries = uiState.monthlySeries,
                    periodFilter = uiState.periodFilter,
                    accessibilityDescription = uiState.chartAccessibilityDescription,
                    onSelectMonth = onSelectMonth
                )
            }

            // 4. SMS Permission Card (Shown ONLY when permission is NOT granted)
            if (!hasSmsPermission) {
                item {
                    SmsPermissionBanner(
                        onRequestPermission = {
                            permissionLauncher.launch(Manifest.permission.RECEIVE_SMS)
                        },
                        modifier = Modifier.padding(horizontal = AppSpacing.md)
                    )
                }
            }

            // 5. Grouped List or Empty State
            if (uiState.isEmpty) {
                item {
                    EmptyStateView(
                        onAddExpense = onNavigateToAddCash,
                        modifier = Modifier.padding(horizontal = AppSpacing.md)
                    )
                }
            } else {
                uiState.groupedTransactions.forEach { dayGroup ->
                    item(key = "header_${dayGroup.date}") {
                        DayHeader(
                            date = dayGroup.date,
                            dayTotalPaise = dayGroup.dayTotalPaise,
                            modifier = Modifier.padding(horizontal = AppSpacing.md)
                        )
                    }

                    items(
                        items = dayGroup.transactions,
                        key = { it.id }
                    ) { tx ->
                        val isRefund = tx.recordType == RecordType.REFUND
                        val formattedAmount = if (isRefund) {
                            "+${CurrencyFormatter.formatPaiseToInr(tx.amountPaise)}"
                        } else {
                            CurrencyFormatter.formatPaiseToInr(tx.amountPaise)
                        }

                        val timeString = formatTime(tx.dateTime)
                        val subtitle = "${tx.category} · $timeString"
                        val merchant = tx.merchantName.trim().ifBlank { "Unknown merchant" }
                        val badgeType = tx.getHomeBadgeType()

                        AFTransactionRow(
                            title = merchant,
                            subtitle = subtitle,
                            amountText = formattedAmount,
                            category = tx.category,
                            isPositive = isRefund,
                            modifier = Modifier.padding(horizontal = AppSpacing.md),
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

    if (showRefundInfoDialog) {
        AlertDialog(
            onDismissRequest = { showRefundInfoDialog = false },
            containerColor = colors.sheet,
            titleContentColor = colors.ink,
            textContentColor = colors.muted,
            title = {
                Text(
                    text = "Refunds",
                    style = AutoFlowTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "Refunds are not included yet.",
                    style = AutoFlowTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { showRefundInfoDialog = false }
                ) {
                    Text(
                        text = "OK",
                        style = AutoFlowTheme.typography.labelLarge,
                        color = colors.brand
                    )
                }
            }
        )
    }
}

@Composable
private fun PeriodPill(
    pillLabel: String,
    onSelectPeriod: (StatsPeriod) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = AutoFlowTheme.colors
    var menuExpanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        Surface(
            shape = RoundedCornerShape(AppRadius.full),
            color = colors.chip,
            border = androidx.compose.foundation.BorderStroke(1.dp, colors.line),
            modifier = Modifier
                .defaultMinSize(minHeight = AppSpacing.minTapTarget)
                .clickable { menuExpanded = true }
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.padding(horizontal = AppSpacing.md, vertical = AppSpacing.xs)
            ) {
                Text(
                    text = pillLabel,
                    style = AutoFlowTheme.typography.labelLarge,
                    color = colors.ink,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.width(AppSpacing.xs))
                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = "Select period",
                    tint = colors.muted,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        DropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = { menuExpanded = false },
            modifier = Modifier.background(colors.sheet)
        ) {
            DropdownMenuItem(
                text = { Text("Today", style = AutoFlowTheme.typography.bodyMedium, color = colors.ink) },
                onClick = {
                    onSelectPeriod(StatsPeriod.TODAY)
                    menuExpanded = false
                }
            )
            DropdownMenuItem(
                text = { Text("This week", style = AutoFlowTheme.typography.bodyMedium, color = colors.ink) },
                onClick = {
                    onSelectPeriod(StatsPeriod.THIS_WEEK)
                    menuExpanded = false
                }
            )
            DropdownMenuItem(
                text = { Text("This month", style = AutoFlowTheme.typography.bodyMedium, color = colors.ink) },
                onClick = {
                    onSelectPeriod(StatsPeriod.THIS_MONTH)
                    menuExpanded = false
                }
            )
        }
    }
}

@Composable
private fun BigNumberSection(
    countedSpendPaise: Long,
    countedCount: Int,
    onInfoClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = AutoFlowTheme.colors

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = CurrencyFormatter.formatPaiseToInr(countedSpendPaise),
            style = AutoFlowTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
            color = colors.ink
        )

        Spacer(modifier = Modifier.height(AppSpacing.xxs))

        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            val countText = if (countedCount == 1) "Across 1 transaction" else "Across $countedCount transactions"
            Text(
                text = countText,
                style = AutoFlowTheme.typography.bodyMedium,
                color = colors.muted
            )

            IconButton(
                onClick = onInfoClick,
                modifier = Modifier
                    .size(AppSpacing.minTapTarget)
                    .semantics { contentDescription = "Refund information" }
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = colors.muted,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun DayHeader(
    date: LocalDate,
    dayTotalPaise: Long,
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
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = headerDateString,
            style = AutoFlowTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = colors.ink
        )

        Text(
            text = CurrencyFormatter.formatPaiseToInr(dayTotalPaise),
            style = AutoFlowTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = colors.muted
        )
    }
}

@Composable
private fun EmptyStateView(
    onAddExpense: () -> Unit,
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
                text = "No transactions found",
                style = AutoFlowTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = colors.ink
            )

            Spacer(modifier = Modifier.height(AppSpacing.xs))

            Text(
                text = "Expenses detected from bank SMS or added manually will appear here.",
                style = AutoFlowTheme.typography.bodyMedium,
                color = colors.muted,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(AppSpacing.md))

            AFButton(
                text = "Add expense",
                onClick = onAddExpense,
                variant = AFButtonVariant.PRIMARY,
                modifier = Modifier.defaultMinSize(minHeight = AppSpacing.minTapTarget)
            )
        }
    }
}

@Composable
private fun SmsPermissionBanner(
    onRequestPermission: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = AutoFlowTheme.colors

    Surface(
        shape = RoundedCornerShape(AppRadius.md),
        color = colors.amberBg,
        border = androidx.compose.foundation.BorderStroke(1.dp, colors.amberFg.copy(alpha = 0.2f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(AppSpacing.md),
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                text = "Enable Live SMS Detection",
                style = AutoFlowTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = colors.amberFg
            )
            Spacer(modifier = Modifier.height(AppSpacing.xs))
            Text(
                text = "AutoFlow detects expenses locally when you receive payment SMS from your bank. SMS messages are processed on-device and raw text is never stored or sent anywhere.",
                style = AutoFlowTheme.typography.bodySmall,
                color = colors.amberFg
            )
            Spacer(modifier = Modifier.height(AppSpacing.sm))
            AFButton(
                text = "Enable SMS Detection",
                onClick = onRequestPermission,
                variant = AFButtonVariant.PRIMARY,
                modifier = Modifier.defaultMinSize(minHeight = AppSpacing.minTapTarget)
            )
        }
    }
}

private fun formatTime(epochMillis: Long): String {
    val zone = ZoneId.systemDefault()
    return Instant.ofEpochMilli(epochMillis)
        .atZone(zone)
        .format(DateTimeFormatter.ofPattern("h:mm a"))
}

@Preview(name = "Light - HomeScreen")
@Composable
private fun HomeScreenLightPreview() {
    AutoFlowTheme(darkTheme = false) {
        val fakeState = HomeUiState(
            periodFilter = PeriodFilter.Predefined(StatsPeriod.THIS_MONTH),
            pillLabel = "This month",
            countedSpendPaise = 1845000L,
            countedTransactionCount = 4,
            monthlySeries = listOf(
                MonthPoint(YearMonth.of(2026, 8), 1200000L, false),
                MonthPoint(YearMonth.of(2026, 9), 1540000L, false),
                MonthPoint(YearMonth.of(2026, 10), 1845000L, false),
                MonthPoint(YearMonth.of(2026, 11), 0L, true),
                MonthPoint(YearMonth.of(2026, 12), 0L, true)
            ),
            chartAccessibilityDescription = "Spending by month, August to December, October selected",
            groupedTransactions = listOf(
                DayGroup(
                    date = LocalDate.now(),
                    dayTotalPaise = 77000L,
                    transactions = listOf(
                        FinancialTransaction(
                            id = 1L,
                            amountPaise = 45000L,
                            recordType = RecordType.EXPENSE,
                            dateTime = System.currentTimeMillis(),
                            merchantName = "Zomato",
                            category = "Food",
                            paymentMode = "UPI",
                            accountNumber = null,
                            bankName = null,
                            reference = null,
                            transactionHash = "h1",
                            source = com.autoflow.app.data.model.TransactionSource.SMS_AUTO,
                            reviewStatus = com.autoflow.app.data.model.ReviewStatus.CONFIRMED,
                            uncertaintyReasons = emptySet(),
                            createdAt = System.currentTimeMillis(),
                            updatedAt = System.currentTimeMillis(),
                            isDeleted = false
                        ),
                        FinancialTransaction(
                            id = 2L,
                            amountPaise = 32000L,
                            recordType = RecordType.EXPENSE,
                            dateTime = System.currentTimeMillis() - 3600000,
                            merchantName = "Uber",
                            category = "Transport",
                            paymentMode = "UPI",
                            accountNumber = null,
                            bankName = null,
                            reference = null,
                            transactionHash = "h2",
                            source = com.autoflow.app.data.model.TransactionSource.SMS_AUTO,
                            reviewStatus = com.autoflow.app.data.model.ReviewStatus.NEEDS_REVIEW,
                            uncertaintyReasons = emptySet(),
                            createdAt = System.currentTimeMillis(),
                            updatedAt = System.currentTimeMillis(),
                            isDeleted = false
                        )
                    )
                )
            ),
            isEmpty = false
        )

        HomeScreen(
            uiState = fakeState,
            onSelectPeriod = {},
            onSelectMonth = {},
            onNavigateToEdit = {},
            onNavigateToAddCash = {}
        )
    }
}

@Preview(name = "Dark - HomeScreen", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun HomeScreenDarkPreview() {
    AutoFlowTheme(darkTheme = true) {
        val fakeState = HomeUiState(
            periodFilter = PeriodFilter.Predefined(StatsPeriod.THIS_MONTH),
            pillLabel = "This month",
            countedSpendPaise = 1845000L,
            countedTransactionCount = 4,
            monthlySeries = listOf(
                MonthPoint(YearMonth.of(2026, 8), 1200000L, false),
                MonthPoint(YearMonth.of(2026, 9), 1540000L, false),
                MonthPoint(YearMonth.of(2026, 10), 1845000L, false),
                MonthPoint(YearMonth.of(2026, 11), 0L, true),
                MonthPoint(YearMonth.of(2026, 12), 0L, true)
            ),
            chartAccessibilityDescription = "Spending by month, August to December, October selected",
            groupedTransactions = emptyList(),
            isEmpty = true
        )

        HomeScreen(
            uiState = fakeState,
            onSelectPeriod = {},
            onSelectMonth = {},
            onNavigateToEdit = {},
            onNavigateToAddCash = {}
        )
    }
}

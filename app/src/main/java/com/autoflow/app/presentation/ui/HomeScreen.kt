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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.autoflow.app.data.model.RecordType
import com.autoflow.app.data.model.ReviewStatus
import com.autoflow.app.domain.model.FinancialTransaction
import com.autoflow.app.domain.stats.MonthPoint
import com.autoflow.app.domain.stats.StatsPeriod
import com.autoflow.app.presentation.theme.AppRadius
import com.autoflow.app.presentation.theme.AppSpacing
import com.autoflow.app.presentation.theme.AutoFlowTheme
import com.autoflow.app.presentation.ui.components.AFBadge
import com.autoflow.app.presentation.ui.components.AFBadgeVariant
import com.autoflow.app.presentation.ui.components.AFBottomNav
import com.autoflow.app.presentation.ui.components.AFButton
import com.autoflow.app.presentation.ui.components.AFButtonVariant
import com.autoflow.app.presentation.ui.components.AFPeriodPill
import com.autoflow.app.presentation.ui.components.AFSmoothLineChart
import com.autoflow.app.presentation.ui.components.AFTransactionRow
import com.autoflow.app.presentation.ui.components.createStandardNavItems
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

fun formatDayHeader(date: LocalDate, now: LocalDate = LocalDate.now(ZoneId.systemDefault())): String {
    return when (date) {
        now -> "Today"
        now.minusDays(1) -> "Yesterday"
        else -> date.format(DateTimeFormatter.ofPattern("EEEE, d MMM"))
    }
}

fun formatReviewCheckLabel(count: Int): String {
    return if (count == 1) "1 needs a check" else "$count need a check"
}

@Composable
fun HomeScreen(
    uiState: HomeUiState,
    onSelectPeriod: (StatsPeriod) -> Unit,
    onSelectMonth: (YearMonth) -> Unit,
    onNavigateToEdit: (Long) -> Unit,
    onNavigateToAddCash: () -> Unit,
    modifier: Modifier = Modifier,
    onNavigateToReview: () -> Unit = {},
    onNavigateToCategories: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    onNavigateToGoals: () -> Unit = {},
    onNavigateToSearch: () -> Unit = {},
    snackbarHostState: androidx.compose.material3.SnackbarHostState = remember { androidx.compose.material3.SnackbarHostState() }
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
        snackbarHost = { androidx.compose.material3.SnackbarHost(hostState = snackbarHostState) },
        bottomBar = {
            AFBottomNav(
                items = createStandardNavItems(unreviewedCount = uiState.unreviewedCount),
                selectedIndex = 0,
                onSelect = { idx ->
                    when (idx) {
                        0 -> { /* Already on Home */ }
                        1 -> onNavigateToGoals()
                        2 -> onNavigateToReview()
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
        val density = LocalDensity.current
        val gradientEndY = with(density) { 240.dp.toPx() }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    top = paddingValues.calculateTopPadding(),
                    bottom = paddingValues.calculateBottomPadding()
                )
                .background(
                    Brush.verticalGradient(
                        colors = listOf(colors.hero, colors.bg),
                        startY = 0f,
                        endY = gradientEndY
                    )
                )
        ) {
            // TOP BLOCK (max 40% screen height)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Top Row: Settings (left, 44dp), Period Pill (center), Search (right, 44dp)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onNavigateToSettings,
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = colors.ink
                        )
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    AFPeriodPill(
                        pillLabel = uiState.pillLabel,
                        onSelectPeriod = onSelectPeriod,
                        onSelectMonth = onSelectMonth
                    )

                    Spacer(modifier = Modifier.weight(1f))

                    IconButton(
                        onClick = onNavigateToSearch,
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search",
                            tint = colors.ink
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Big Number Section
                BigNumberSection(
                    countedSpendPaise = uiState.countedSpendPaise,
                    countedCount = uiState.countedTransactionCount,
                    onInfoClick = { showRefundInfoDialog = true }
                )

                Spacer(modifier = Modifier.height(10.dp))

                // 5-Month Spending Chart
                AFSmoothLineChart(
                    monthlySeries = uiState.monthlySeries,
                    periodFilter = uiState.periodFilter,
                    accessibilityDescription = uiState.chartAccessibilityDescription,
                    onSelectMonth = onSelectMonth
                )

                // "N need a check" pill (wireframe 2)
                if (uiState.unreviewedCount > 0) {
                    Spacer(modifier = Modifier.height(10.dp))
                    ReviewCheckPill(
                        unreviewedCount = uiState.unreviewedCount,
                        onClick = onNavigateToReview
                    )
                }
            }

            // BOTTOM LIST SHEET (remaining height)
            Surface(
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                color = colors.sheet,
                shadowElevation = 6.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(top = 24.dp, bottom = 64.dp)
                    ) {
                        if (!hasSmsPermission) {
                            item {
                                SmsPermissionBanner(
                                    onRequestPermission = {
                                        permissionLauncher.launch(Manifest.permission.RECEIVE_SMS)
                                    },
                                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                                )
                            }
                        }

                        if (uiState.isEmpty) {
                            item {
                                EmptyStateView(
                                    onAddExpense = onNavigateToAddCash,
                                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 24.dp)
                                )
                            }
                        } else {
                            uiState.groupedTransactions.forEach { dayGroup ->
                                item(key = "header_${dayGroup.date}") {
                                    DayHeader(
                                        date = dayGroup.date,
                                        dayTotalPaise = dayGroup.dayTotalPaise,
                                        modifier = Modifier.padding(horizontal = 20.dp)
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

                                    val isNotCounted = tx.reviewStatus == ReviewStatus.EXCLUDED
                                    val timeString = formatTime(tx.dateTime)
                                    val subtitle = if (isNotCounted) "Not counted · $timeString" else "${tx.category} · $timeString"
                                    val merchant = tx.merchantName.trim().ifBlank { "Unknown merchant" }
                                    val badgeType = tx.getHomeBadgeType()

                                    AFTransactionRow(
                                        title = merchant,
                                        subtitle = subtitle,
                                        amountText = formattedAmount,
                                        category = tx.category,
                                        isPositive = isRefund,
                                        isNotCounted = isNotCounted,
                                        isFlat = true,
                                        badge = if (!isNotCounted && badgeType != null) {
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

                    // Grab handle at top centre: 36x4dp, Theme line color, 10dp from sheet top
                    Box(
                        modifier = Modifier
                            .padding(top = 10.dp)
                            .size(width = 36.dp, height = 4.dp)
                            .background(color = colors.line, shape = RoundedCornerShape(2.dp))
                            .align(Alignment.TopCenter)
                    )

                    // 56dp fade to transparent at the bottom
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .align(Alignment.BottomCenter)
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        colors.sheet
                                    )
                                )
                            )
                    )
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
private fun BigNumberSection(
    countedSpendPaise: Long,
    countedCount: Int,
    onInfoClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = AutoFlowTheme.colors

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = CurrencyFormatter.formatPaiseToInr(countedSpendPaise),
            style = AutoFlowTheme.typography.displaySmall.copy(
                fontSize = 46.sp,
                fontWeight = FontWeight.ExtraBold,
                fontFeatureSettings = "tnum",
                letterSpacing = (-1).sp
            ),
            color = colors.ink,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(2.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            val countText = if (countedCount == 1) "Across 1 transaction" else "Across $countedCount transactions"
            Text(
                text = countText,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.muted
            )

            IconButton(
                onClick = onInfoClick,
                modifier = Modifier
                    .size(width = 44.dp, height = 28.dp)
                    .semantics { contentDescription = "Refund information" }
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = colors.muted,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
private fun ReviewCheckPill(
    unreviewedCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = AutoFlowTheme.colors
    val label = formatReviewCheckLabel(unreviewedCount)

    Surface(
        shape = RoundedCornerShape(17.dp),
        color = colors.amberBg,
        border = androidx.compose.foundation.BorderStroke(1.dp, colors.amberFg.copy(alpha = 0.2f)),
        modifier = modifier
            .height(34.dp)
            .clickable(onClick = onClick)
            .semantics { contentDescription = label }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = null,
                tint = colors.amberFg,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = label,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = colors.amberFg
            )
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = colors.amberFg,
                modifier = Modifier.size(16.dp)
            )
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
    val headerDateString = formatDayHeader(date)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = headerDateString,
            fontSize = 15.sp,
            fontWeight = FontWeight.ExtraBold,
            color = colors.ink
        )

        Text(
            text = CurrencyFormatter.formatPaiseToInr(dayTotalPaise),
            fontSize = 15.sp,
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

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp, horizontal = 16.dp),
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
            unreviewedCount = 2,
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

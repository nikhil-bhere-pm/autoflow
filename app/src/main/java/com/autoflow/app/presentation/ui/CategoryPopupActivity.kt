package com.autoflow.app.presentation.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.autoflow.app.AutoFlowApplication
import com.autoflow.app.MainActivity
import com.autoflow.app.domain.category.BUILT_IN_CATEGORIES
import com.autoflow.app.domain.model.FinancialTransaction
import com.autoflow.app.notification.CapturedExpenseNotificationBuilder
import com.autoflow.app.notification.CapturedExpenseNotificationHelper
import com.autoflow.app.presentation.theme.AppRadius
import com.autoflow.app.presentation.theme.AppSpacing
import com.autoflow.app.presentation.theme.AppThemeMode
import com.autoflow.app.presentation.theme.AutoFlowTheme
import com.autoflow.app.presentation.theme.SharedPreferencesThemePreferences
import com.autoflow.app.presentation.ui.components.AFButton
import com.autoflow.app.presentation.ui.components.AFButtonVariant
import com.autoflow.app.presentation.ui.components.AFCategoryIcons
import com.autoflow.app.presentation.util.CurrencyFormatter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class CategoryPopupActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val txId = intent?.getLongExtra(CapturedExpenseNotificationBuilder.EXTRA_TRANSACTION_ID, -1L) ?: -1L
        if (txId <= 0L) {
            finish()
            return
        }

        enableEdgeToEdge()

        val app = application as AutoFlowApplication
        val themePreferences = SharedPreferencesThemePreferences.fromContext(app)

        setContent {
            val themeMode by themePreferences.themeMode.collectAsState()
            val isDark = when (themeMode) {
                AppThemeMode.LIGHT -> false
                AppThemeMode.DARK -> true
                AppThemeMode.SYSTEM -> isSystemInDarkTheme()
            }

            var transaction by remember { mutableStateOf<FinancialTransaction?>(null) }
            var categories by remember { mutableStateOf<List<String>>(BUILT_IN_CATEGORIES) }
            var isLoaded by remember { mutableStateOf(false) }
            val coroutineScope = rememberCoroutineScope()

            LaunchedEffect(txId) {
                val loadedTx = app.transactionRepository.getTransactionById(txId)
                if (loadedTx == null || loadedTx.isDeleted) {
                    finish()
                    return@LaunchedEffect
                }
                transaction = loadedTx

                val loadedCategories = try {
                    app.categoryRepository.getAllCategories().first().map { it.name }
                } catch (e: Exception) {
                    BUILT_IN_CATEGORIES
                }
                categories = loadedCategories.ifEmpty { BUILT_IN_CATEGORIES }
                isLoaded = true
            }

            AutoFlowTheme(darkTheme = isDark) {
                if (isLoaded && transaction != null) {
                    CategoryPopupContent(
                        transaction = transaction!!,
                        categories = categories,
                        onCategorySelected = { selectedCategory ->
                            coroutineScope.launch {
                                CapturedExpenseNotificationHelper.applyCategoryAndNotify(
                                    context = this@CategoryPopupActivity,
                                    transactionId = txId,
                                    category = selectedCategory
                                )
                                finish()
                            }
                        },
                        onNotAnExpense = {
                            coroutineScope.launch {
                                CapturedExpenseNotificationHelper.markNotAnExpenseAndNotify(
                                    context = this@CategoryPopupActivity,
                                    transactionId = txId
                                )
                                finish()
                            }
                        },
                        onMoreDetails = {
                            val editIntent = Intent(this@CategoryPopupActivity, MainActivity::class.java).apply {
                                action = CapturedExpenseNotificationBuilder.ACTION_EDIT_TRANSACTION
                                putExtra(CapturedExpenseNotificationBuilder.EXTRA_TRANSACTION_ID, txId)
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                            }
                            startActivity(editIntent)
                            finish()
                        },
                        onDismiss = {
                            finish()
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun CategoryPopupContent(
    transaction: FinancialTransaction,
    categories: List<String>,
    onCategorySelected: (String) -> Unit,
    onNotAnExpense: () -> Unit,
    onMoreDetails: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = AutoFlowTheme.colors
    val scrollState = rememberScrollState()

    BackHandler {
        onDismiss()
    }

    val displayMerchant = if (transaction.merchantName.isBlank()) "Unknown Merchant" else transaction.merchantName
    val formattedAmount = CurrencyFormatter.formatPaiseToInr(transaction.amountPaise)
    val title = "$formattedAmount at $displayMerchant"

    // Dimmed background scrim that dismisses on tap outside
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss
            ),
        contentAlignment = Alignment.Center
    ) {
        // Modal Card
        Surface(
            shape = RoundedCornerShape(AppRadius.lg),
            color = colors.sheet,
            border = BorderStroke(1.dp, colors.line),
            modifier = Modifier
                .widthIn(min = 300.dp, max = 380.dp)
                .padding(horizontal = AppSpacing.lg, vertical = AppSpacing.xl)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { /* Intercept tap inside card */ }
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(AppSpacing.md),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)
            ) {
                // Title: <amount> at <merchant>
                Text(
                    text = title,
                    style = AutoFlowTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = colors.ink
                )

                // Subtitle: Pick a category. Future spends here will use it.
                Text(
                    text = "Pick a category. Future spends here will use it.",
                    style = AutoFlowTheme.typography.bodySmall,
                    color = colors.muted
                )

                Spacer(modifier = Modifier.height(AppSpacing.xs))

                // Two columns of category buttons
                val rows = categories.chunked(2)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 320.dp)
                        .verticalScroll(scrollState),
                    verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)
                ) {
                    rows.forEach { rowPair ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)
                        ) {
                            val first = rowPair[0]
                            val isFirstSelected = first.equals(transaction.category, ignoreCase = true)
                            Box(modifier = Modifier.weight(1f)) {
                                AFButton(
                                    text = first,
                                    onClick = { onCategorySelected(first) },
                                    variant = if (isFirstSelected) AFButtonVariant.PRIMARY else AFButtonVariant.SECONDARY,
                                    icon = {
                                        Icon(
                                            painter = painterResource(id = AFCategoryIcons.getDrawableRes(first)),
                                            contentDescription = null,
                                            tint = if (isFirstSelected) colors.buttonText else colors.forCategory(first),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }

                            if (rowPair.size > 1) {
                                val second = rowPair[1]
                                val isSecondSelected = second.equals(transaction.category, ignoreCase = true)
                                Box(modifier = Modifier.weight(1f)) {
                                    AFButton(
                                        text = second,
                                        onClick = { onCategorySelected(second) },
                                        variant = if (isSecondSelected) AFButtonVariant.PRIMARY else AFButtonVariant.SECONDARY,
                                        icon = {
                                            Icon(
                                                painter = painterResource(id = AFCategoryIcons.getDrawableRes(second)),
                                                contentDescription = null,
                                                tint = if (isSecondSelected) colors.buttonText else colors.forCategory(second),
                                                modifier = Modifier.size(16.dp)
                                            )
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            } else {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(AppSpacing.xs))

                HorizontalDivider(
                    thickness = 1.dp,
                    color = colors.line
                )

                // "Not an expense" (red text)
                TextButton(
                    onClick = onNotAnExpense,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Not an expense",
                        style = AutoFlowTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.red
                    )
                }

                // "More details (opens Edit transaction)"
                TextButton(
                    onClick = onMoreDetails,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "More details (opens Edit transaction)",
                        style = AutoFlowTheme.typography.labelLarge,
                        color = colors.ink
                    )
                }
            }
        }
    }
}

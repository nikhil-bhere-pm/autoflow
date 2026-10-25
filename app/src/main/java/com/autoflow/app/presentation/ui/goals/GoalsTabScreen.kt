package com.autoflow.app.presentation.ui.goals

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.autoflow.app.R
import com.autoflow.app.domain.goals.GoalWithTotals
import com.autoflow.app.presentation.theme.AppRadius
import com.autoflow.app.presentation.theme.AppSpacing
import com.autoflow.app.presentation.theme.AutoFlowTheme
import com.autoflow.app.presentation.ui.components.AFBottomNav
import com.autoflow.app.presentation.ui.components.AFBottomNavItem
import com.autoflow.app.presentation.ui.components.AFButton
import com.autoflow.app.presentation.ui.components.AFButtonVariant
import com.autoflow.app.presentation.ui.components.AFProgressBar
import com.autoflow.app.presentation.ui.components.AFTopBar
import com.autoflow.app.presentation.util.CurrencyFormatter
import com.autoflow.app.presentation.util.GoalDeadlineFormatter
import com.autoflow.app.presentation.viewmodel.GoalsUiState

@Composable
fun GoalsTabScreen(
    uiState: GoalsUiState,
    unreviewedCount: Int,
    snackbarHostState: SnackbarHostState,
    onNavigateToNewGoal: () -> Unit,
    onNavigateToGoalDetail: (Long) -> Unit,
    onNavigateToHome: () -> Unit,
    onNavigateToAddCash: () -> Unit,
    onNavigateToReview: () -> Unit,
    onNavigateToCategories: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = AutoFlowTheme.colors

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = colors.bg,
        topBar = {
            AFTopBar(
                title = stringResource(R.string.goals_title),
                actions = {
                    AFButton(
                        text = stringResource(R.string.goals_add_goal),
                        onClick = onNavigateToNewGoal,
                        variant = AFButtonVariant.TINTED,
                        modifier = Modifier.defaultMinSize(minHeight = 36.dp)
                    )
                },
                showDivider = true
            )
        },
        bottomBar = {
            AFBottomNav(
                items = com.autoflow.app.presentation.ui.components.createStandardNavItems(unreviewedCount = unreviewedCount),
                selectedIndex = 1,
                onSelect = { idx ->
                    when (idx) {
                        0 -> onNavigateToHome()
                        1 -> { /* Already on Goals */ }
                        2 -> onNavigateToReview()
                        3 -> onNavigateToCategories()
                    }
                },
                centerButton = {
                    FloatingActionButton(
                        onClick = onNavigateToAddCash,
                        shape = CircleShape,
                        containerColor = colors.brand,
                        contentColor = colors.buttonText,
                        elevation = FloatingActionButtonDefaults.elevation(0.dp, 0.dp),
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Add Expense"
                        )
                    }
                }
            )
        },
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState) { data ->
                Snackbar(
                    snackbarData = data,
                    containerColor = colors.ink,
                    contentColor = colors.sheet,
                    actionColor = colors.brand
                )
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (uiState.isEmpty) {
                GoalsEmptyState(
                    onNavigateToNewGoal = onNavigateToNewGoal,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        horizontal = AppSpacing.md,
                        vertical = AppSpacing.md
                    ),
                    verticalArrangement = Arrangement.spacedBy(AppSpacing.md)
                ) {
                    items(
                        items = uiState.goals,
                        key = { it.id }
                    ) { goal ->
                        GoalCard(
                            goal = goal,
                            onClick = { onNavigateToGoalDetail(goal.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun GoalCard(
    goal: GoalWithTotals,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = AutoFlowTheme.colors
    val subtitle = GoalDeadlineFormatter.formatDeadlineSubtitle(
        deadlineAt = goal.deadlineAt,
        isReached = goal.reached
    )

    Surface(
        shape = RoundedCornerShape(AppRadius.lg),
        color = colors.sheet,
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = AppSpacing.minTapTarget)
            .clip(RoundedCornerShape(AppRadius.lg))
            .clickable(
                role = Role.Button,
                onClick = onClick
            ),
        tonalElevation = 1.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(AppSpacing.md)
        ) {
            // Row with icon tile, name and deadline status
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(AppRadius.md))
                        .background(colors.tint),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_cat_wallet),
                        contentDescription = null,
                        tint = colors.onTint,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(modifier = Modifier.size(AppSpacing.sm))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = goal.name,
                        style = AutoFlowTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.ink
                    )
                    Text(
                        text = subtitle,
                        style = AutoFlowTheme.typography.bodySmall,
                        color = when {
                            goal.reached -> colors.brand
                            subtitle == "Deadline passed" -> colors.red
                            else -> colors.muted
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(AppSpacing.md))

            // Large saved total on left, "of ₹target · NN%" on right
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = CurrencyFormatter.formatPaiseToInr(goal.totalPaise),
                    style = AutoFlowTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = colors.ink
                )

                Text(
                    text = stringResource(
                        R.string.goals_card_target_pct,
                        CurrencyFormatter.formatPaiseWithoutDecimalsIfZero(goal.targetPaise),
                        goal.percent
                    ),
                    style = AutoFlowTheme.typography.bodyMedium,
                    color = colors.muted
                )
            }

            Spacer(modifier = Modifier.height(AppSpacing.sm))

            // Progress bar
            AFProgressBar(
                progress = if (goal.targetPaise > 0L) {
                    goal.totalPaise.toFloat() / goal.targetPaise.toFloat()
                } else 0f
            )
        }
    }
}

@Composable
fun GoalsEmptyState(
    onNavigateToNewGoal: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = AutoFlowTheme.colors

    Column(
        modifier = modifier
            .padding(horizontal = AppSpacing.xl),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(AppRadius.lg))
                .background(colors.tint),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_cat_wallet),
                contentDescription = null,
                tint = colors.onTint,
                modifier = Modifier.size(36.dp)
            )
        }

        Spacer(modifier = Modifier.height(AppSpacing.lg))

        Text(
            text = stringResource(R.string.goals_empty_title),
            style = AutoFlowTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = colors.ink,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(AppSpacing.sm))

        Text(
            text = stringResource(R.string.goals_empty_description),
            style = AutoFlowTheme.typography.bodyMedium,
            color = colors.muted,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(AppSpacing.xl))

        AFButton(
            text = stringResource(R.string.goals_create_first),
            onClick = onNavigateToNewGoal,
            variant = AFButtonVariant.PRIMARY
        )
    }
}

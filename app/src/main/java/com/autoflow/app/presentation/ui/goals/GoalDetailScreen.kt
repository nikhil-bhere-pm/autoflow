package com.autoflow.app.presentation.ui.goals

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.autoflow.app.R
import com.autoflow.app.domain.goals.GoalEntry
import com.autoflow.app.domain.goals.GoalWithTotals
import com.autoflow.app.presentation.theme.AppRadius
import com.autoflow.app.presentation.theme.AppSpacing
import com.autoflow.app.presentation.theme.AutoFlowTheme
import com.autoflow.app.presentation.ui.components.AFBadge
import com.autoflow.app.presentation.ui.components.AFBadgeVariant
import com.autoflow.app.presentation.ui.components.AFButton
import com.autoflow.app.presentation.ui.components.AFProgressBar
import com.autoflow.app.presentation.ui.components.AFTopBar
import com.autoflow.app.presentation.util.CurrencyFormatter
import com.autoflow.app.presentation.util.GoalDeadlineFormatter
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun GoalDetailScreen(
    goalWithTotals: GoalWithTotals?,
    entries: List<GoalEntry>,
    snackbarHostState: SnackbarHostState,
    onNavigateBack: () -> Unit,
    onNavigateToEditGoal: (Long) -> Unit,
    onDeleteGoal: (Long) -> Unit,
    onNavigateToAddSaved: (Long) -> Unit,
    onNavigateToEditEntry: (Long, Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = AutoFlowTheme.colors
    var showMenu by remember { mutableStateOf(false) }

    if (goalWithTotals == null) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Goal not found",
                style = AutoFlowTheme.typography.bodyLarge,
                color = colors.muted
            )
        }
        return
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = colors.bg,
        topBar = {
            AFTopBar(
                title = goalWithTotals.name,
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
                actions = {
                    Box {
                        IconButton(
                            onClick = { showMenu = true },
                            modifier = Modifier
                                .defaultMinSize(minWidth = AppSpacing.minTapTarget, minHeight = AppSpacing.minTapTarget)
                                .semantics { contentDescription = "More options" }
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = null,
                                tint = colors.ink
                            )
                        }

                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false },
                            modifier = Modifier.background(colors.sheet)
                        ) {
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = stringResource(R.string.goal_detail_edit_goal),
                                        style = AutoFlowTheme.typography.bodyLarge,
                                        color = colors.ink
                                    )
                                },
                                onClick = {
                                    showMenu = false
                                    onNavigateToEditGoal(goalWithTotals.id)
                                }
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = stringResource(R.string.goal_detail_delete_goal),
                                        style = AutoFlowTheme.typography.bodyLarge,
                                        color = colors.red
                                    )
                                },
                                onClick = {
                                    showMenu = false
                                    onDeleteGoal(goalWithTotals.id)
                                }
                            )
                        }
                    }
                },
                showDivider = true
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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(
                horizontal = AppSpacing.md,
                vertical = AppSpacing.md
            ),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.md)
        ) {
            // Hero card
            item {
                GoalDetailHeroCard(goal = goalWithTotals)
            }

            // Primary "Saved" button
            item {
                AFButton(
                    text = stringResource(R.string.goal_detail_saved_button),
                    onClick = { onNavigateToAddSaved(goalWithTotals.id) },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // History header
            item {
                Spacer(modifier = Modifier.height(AppSpacing.xs))
                Text(
                    text = stringResource(R.string.goal_detail_history_title),
                    style = AutoFlowTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = colors.ink
                )
            }

            // History list or empty line
            if (entries.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.goal_detail_history_empty),
                        style = AutoFlowTheme.typography.bodyMedium,
                        color = colors.muted,
                        modifier = Modifier.padding(vertical = AppSpacing.sm)
                    )
                }
            } else {
                items(
                    items = entries,
                    key = { it.id }
                ) { entry ->
                    GoalEntryRow(
                        entry = entry,
                        onClick = { onNavigateToEditEntry(goalWithTotals.id, entry.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun GoalDetailHeroCard(
    goal: GoalWithTotals,
    modifier: Modifier = Modifier
) {
    val colors = AutoFlowTheme.colors
    val deadlineLine = GoalDeadlineFormatter.formatDeadlineSubtitle(
        deadlineAt = goal.deadlineAt,
        isReached = goal.reached
    )

    Surface(
        shape = RoundedCornerShape(AppRadius.lg),
        color = colors.sheet,
        modifier = modifier.fillMaxWidth(),
        tonalElevation = 1.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(AppSpacing.md)
        ) {
            Text(
                text = stringResource(R.string.goal_detail_saved_so_far),
                style = AutoFlowTheme.typography.labelMedium,
                color = colors.muted
            )

            Spacer(modifier = Modifier.height(AppSpacing.xs))

            Text(
                text = CurrencyFormatter.formatPaiseToInr(goal.totalPaise),
                style = AutoFlowTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = colors.ink
            )

            Spacer(modifier = Modifier.height(AppSpacing.xs))

            val targetFormatted = CurrencyFormatter.formatPaiseWithoutDecimalsIfZero(goal.targetPaise)
            Text(
                text = "of $targetFormatted · ${goal.percent}% · $deadlineLine",
                style = AutoFlowTheme.typography.bodyMedium,
                color = colors.muted
            )

            Spacer(modifier = Modifier.height(AppSpacing.md))

            AFProgressBar(
                progress = if (goal.targetPaise > 0L) {
                    goal.totalPaise.toFloat() / goal.targetPaise.toFloat()
                } else 0f
            )

            if (goal.reached) {
                Spacer(modifier = Modifier.height(AppSpacing.sm))
                Surface(
                    color = colors.tint,
                    shape = RoundedCornerShape(AppRadius.sm),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = AppSpacing.sm, vertical = AppSpacing.xs),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = colors.onTint,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(AppSpacing.xs))
                        Text(
                            text = stringResource(R.string.goal_detail_reached_banner),
                            style = AutoFlowTheme.typography.labelMedium,
                            color = colors.onTint,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun GoalEntryRow(
    entry: GoalEntry,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = AutoFlowTheme.colors
    val zone = remember { ZoneId.systemDefault() }
    val formattedDateTime = remember(entry.occurredAt) {
        Instant.ofEpochMilli(entry.occurredAt)
            .atZone(zone)
            .format(DateTimeFormatter.ofPattern("d MMM, h:mm a", Locale.ENGLISH))
    }

    Surface(
        shape = RoundedCornerShape(AppRadius.md),
        color = colors.sheet,
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = AppSpacing.minTapTarget)
            .clip(RoundedCornerShape(AppRadius.md))
            .clickable(
                role = Role.Button,
                onClick = onClick
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AppSpacing.md, vertical = AppSpacing.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AFBadge(
                text = stringResource(R.string.saved_badge),
                variant = AFBadgeVariant.CHECK
            )

            Spacer(modifier = Modifier.width(AppSpacing.sm))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.note,
                    style = AutoFlowTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = colors.ink
                )
                Text(
                    text = formattedDateTime,
                    style = AutoFlowTheme.typography.bodySmall,
                    color = colors.muted
                )
            }

            Spacer(modifier = Modifier.width(AppSpacing.sm))

            Text(
                text = "+${CurrencyFormatter.formatPaiseToInr(entry.amountPaise)}",
                style = AutoFlowTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = colors.brand
            )
        }
    }
}

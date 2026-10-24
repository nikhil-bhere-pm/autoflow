package com.autoflow.app.presentation.ui

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.autoflow.app.presentation.theme.AppRadius
import com.autoflow.app.presentation.theme.AppSpacing
import com.autoflow.app.presentation.theme.AutoFlowTheme
import com.autoflow.app.presentation.ui.components.AFButton
import com.autoflow.app.presentation.ui.components.AFCategoryTile
import com.autoflow.app.presentation.ui.components.AFTopBar
import com.autoflow.app.presentation.viewmodel.CustomCategoryRowUiModel

@Composable
fun MyCategoriesScreen(
    customCategories: List<CustomCategoryRowUiModel>,
    onAddCategory: () -> Unit,
    onEditCategory: (String) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = AutoFlowTheme.colors
    val usedCount = customCategories.size

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = colors.bg,
        topBar = {
            AFTopBar(
                title = "My categories",
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
        },
        bottomBar = {
            Surface(
                color = colors.sheet,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(AppSpacing.md)
                ) {
                    AFButton(
                        text = "Add category",
                        onClick = onAddCategory,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = usedCount < 30
                    )
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = AppSpacing.md, vertical = AppSpacing.sm)
        ) {
            // Count Banner
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = AppSpacing.sm),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Custom categories",
                    style = AutoFlowTheme.typography.titleSmall,
                    color = colors.ink
                )
                Text(
                    text = "$usedCount of 30 used",
                    style = AutoFlowTheme.typography.bodySmall,
                    color = colors.muted
                )
            }

            if (customCategories.isEmpty()) {
                // Empty state card
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = AppSpacing.xl),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        shape = RoundedCornerShape(AppRadius.lg),
                        color = colors.sheet,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(AppSpacing.xl),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(AppSpacing.md)
                        ) {
                            Text(
                                text = "No custom categories yet",
                                style = AutoFlowTheme.typography.titleMedium,
                                color = colors.ink
                            )
                            Text(
                                text = "Create your own categories with custom icons and colors to organize your spending.",
                                style = AutoFlowTheme.typography.bodyMedium,
                                color = colors.muted,
                                modifier = Modifier.padding(horizontal = AppSpacing.sm)
                            )
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)
                ) {
                    items(customCategories, key = { it.category.name }) { item ->
                        Surface(
                            shape = RoundedCornerShape(AppRadius.md),
                            color = colors.sheet,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(AppRadius.md))
                                .clickable { onEditCategory(item.category.name) }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .defaultMinSize(minHeight = 64.dp)
                                    .padding(horizontal = AppSpacing.md, vertical = AppSpacing.sm),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                AFCategoryTile(category = item.category.name)
                                Spacer(modifier = Modifier.width(AppSpacing.md))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = item.category.name,
                                        style = AutoFlowTheme.typography.titleMedium,
                                        color = colors.ink
                                    )
                                    Spacer(modifier = Modifier.height(AppSpacing.xxs))
                                    val countText = when (item.transactionCount) {
                                        1 -> "1 transaction"
                                        else -> "${item.transactionCount} transactions"
                                    }
                                    Text(
                                        text = countText,
                                        style = AutoFlowTheme.typography.bodySmall,
                                        color = colors.muted
                                    )
                                }
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                    contentDescription = "Edit ${item.category.name}",
                                    tint = colors.muted
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

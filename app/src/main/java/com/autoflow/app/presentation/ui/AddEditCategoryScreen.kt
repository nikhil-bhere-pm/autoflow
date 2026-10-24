package com.autoflow.app.presentation.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.autoflow.app.domain.category.CategoryItem
import com.autoflow.app.domain.category.CategoryOperationResult
import com.autoflow.app.presentation.theme.AppRadius
import com.autoflow.app.presentation.theme.AppSpacing
import com.autoflow.app.presentation.theme.AutoFlowTheme
import com.autoflow.app.presentation.ui.components.AFButton
import com.autoflow.app.presentation.ui.components.AFButtonVariant
import com.autoflow.app.presentation.ui.components.AFCategoryIcons
import com.autoflow.app.presentation.ui.components.AFField
import com.autoflow.app.presentation.ui.components.AFTopBar
import com.autoflow.app.presentation.viewmodel.toUserMessage
import kotlinx.coroutines.launch

private val ICON_KEYS = listOf(
    "PAW", "HOME", "GIFT", "BOOK", "COFFEE", "TOOL",
    "LEAF", "MUSIC", "PHONE", "WALLET", "BAG", "CALENDAR"
)

private val COLOR_KEYS = listOf(
    "ORANGE", "GREEN", "BLUE", "PINK", "GOLD", "PURPLE", "TEAL", "RED"
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddEditCategoryScreen(
    initialCategory: CategoryItem?,
    onSave: suspend (name: String, iconKey: String, colorKey: String) -> CategoryOperationResult,
    onDelete: (suspend (name: String) -> CategoryOperationResult)?,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = AutoFlowTheme.colors
    val scope = rememberCoroutineScope()
    val isEditMode = initialCategory != null

    var name by remember { mutableStateOf(initialCategory?.name ?: "") }
    var selectedIcon by remember { mutableStateOf(initialCategory?.iconKey ?: "PAW") }
    var selectedColor by remember { mutableStateOf(initialCategory?.colorKey ?: "ORANGE") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = colors.bg,
        topBar = {
            AFTopBar(
                title = if (isEditMode) "Edit category" else "Add category",
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = colors.ink
                        )
                    }
                },
                actions = {
                    if (isEditMode && onDelete != null) {
                        IconButton(onClick = { showDeleteDialog = true }) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete category",
                                tint = colors.red
                            )
                        }
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
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(AppSpacing.md)
                ) {
                    AFButton(
                        text = "Save",
                        onClick = {
                            if (name.isBlank()) {
                                errorMessage = "Name cannot be empty"
                                return@AFButton
                            }
                            isSaving = true
                            scope.launch {
                                val result = onSave(name, selectedIcon, selectedColor)
                                val msg = result.toUserMessage()
                                if (msg != null) {
                                    errorMessage = msg
                                    isSaving = false
                                } else {
                                    onNavigateBack()
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = name.isNotBlank() && !isSaving
                    )
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = AppSpacing.md, vertical = AppSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.lg)
        ) {
            // Live Preview Card
            Surface(
                shape = RoundedCornerShape(AppRadius.lg),
                color = colors.sheet,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(AppSpacing.md),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val tileColor = colors.forColorKey(selectedColor)
                    val tileBg = tileColor.copy(alpha = 0.11f)

                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(AppRadius.md))
                            .background(tileBg),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(id = AFCategoryIcons.getDrawableResForIconKey(selectedIcon)),
                            contentDescription = selectedIcon,
                            tint = tileColor,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(AppSpacing.md))

                    Column {
                        Text(
                            text = if (name.isBlank()) "Category preview" else name.trim(),
                            style = AutoFlowTheme.typography.titleMedium,
                            color = colors.ink
                        )
                        Spacer(modifier = Modifier.height(AppSpacing.xxs))
                        Text(
                            text = "$selectedColor · $selectedIcon",
                            style = AutoFlowTheme.typography.bodySmall,
                            color = colors.muted
                        )
                    }
                }
            }

            // Name Field
            Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
                AFField(
                    value = name,
                    onValueChange = { newText ->
                        if (newText.length <= 20) {
                            name = newText
                            errorMessage = null
                        }
                    },
                    label = "Category name",
                    placeholder = "e.g. Gaming, Pet Care",
                    errorMessage = errorMessage,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = "${name.length}/20",
                    style = AutoFlowTheme.typography.bodySmall,
                    color = colors.muted,
                    modifier = Modifier.align(Alignment.End)
                )
            }

            // Icon Picker (12 icon keys)
            Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
                Text(
                    text = "Choose an icon",
                    style = AutoFlowTheme.typography.titleSmall,
                    color = colors.ink
                )
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
                    verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)
                ) {
                    for (iconKey in ICON_KEYS) {
                        val isSelected = selectedIcon == iconKey
                        val activeColor = colors.forColorKey(selectedColor)
                        val iconBg = if (isSelected) activeColor.copy(alpha = 0.15f) else colors.chip
                        val border = if (isSelected) BorderStroke(2.dp, activeColor) else null

                        Surface(
                            shape = RoundedCornerShape(AppRadius.md),
                            color = iconBg,
                            border = border,
                            modifier = Modifier
                                .size(AppSpacing.minTapTarget)
                                .clip(RoundedCornerShape(AppRadius.md))
                                .clickable { selectedIcon = iconKey }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    painter = painterResource(id = AFCategoryIcons.getDrawableResForIconKey(iconKey)),
                                    contentDescription = iconKey,
                                    tint = if (isSelected) activeColor else colors.muted,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Color Picker (8 color keys)
            Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
                Text(
                    text = "Choose a color",
                    style = AutoFlowTheme.typography.titleSmall,
                    color = colors.ink
                )
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
                    verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)
                ) {
                    for (colorKey in COLOR_KEYS) {
                        val isSelected = selectedColor == colorKey
                        val circleColor = colors.forColorKey(colorKey)

                        Surface(
                            shape = CircleShape,
                            color = circleColor,
                            modifier = Modifier
                                .size(AppSpacing.minTapTarget)
                                .clip(CircleShape)
                                .clickable { selectedColor = colorKey }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Selected $colorKey",
                                        tint = colors.sheet,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Delete Button on Edit Mode
            if (isEditMode && onDelete != null) {
                Spacer(modifier = Modifier.height(AppSpacing.md))
                AFButton(
                    text = "Delete category",
                    onClick = { showDeleteDialog = true },
                    variant = AFButtonVariant.DESTRUCTIVE,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }

    if (showDeleteDialog && onDelete != null && initialCategory != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = {
                Text(
                    text = "Delete category?",
                    style = AutoFlowTheme.typography.titleLarge,
                    color = colors.ink
                )
            },
            text = {
                Text(
                    text = "Its transactions will move to Others and its merchant rules will be removed.",
                    style = AutoFlowTheme.typography.bodyMedium,
                    color = colors.muted
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        scope.launch {
                            val result = onDelete(initialCategory.name)
                            if (result is CategoryOperationResult.Success) {
                                onNavigateBack()
                            } else {
                                errorMessage = result.toUserMessage()
                            }
                        }
                    }
                ) {
                    Text(
                        text = "Delete",
                        color = colors.red,
                        style = AutoFlowTheme.typography.labelLarge
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(
                        text = "Cancel",
                        color = colors.muted,
                        style = AutoFlowTheme.typography.labelLarge
                    )
                }
            },
            containerColor = colors.sheet
        )
    }
}

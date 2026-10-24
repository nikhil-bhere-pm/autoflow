package com.autoflow.app.presentation.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.autoflow.app.presentation.theme.AppRadius
import com.autoflow.app.presentation.theme.AppSpacing
import com.autoflow.app.presentation.theme.AppThemeMode
import com.autoflow.app.presentation.theme.AutoFlowTheme
import com.autoflow.app.presentation.ui.components.AFSegmented
import com.autoflow.app.presentation.ui.components.AFTopBar

@Composable
fun SettingsScreen(
    currentThemeMode: AppThemeMode,
    onSelectThemeMode: (AppThemeMode) -> Unit,
    onNavigateToMyCategories: () -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = AutoFlowTheme.colors
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasSmsPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECEIVE_SMS
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasSmsPermission = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.RECEIVE_SMS
                ) == PackageManager.PERMISSION_GRANTED
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = colors.bg,
        topBar = {
            AFTopBar(
                title = "Settings",
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
                .verticalScroll(rememberScrollState())
                .padding(horizontal = AppSpacing.md, vertical = AppSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.lg)
        ) {
            // Appearance Section
            Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
                Text(
                    text = "Appearance",
                    style = AutoFlowTheme.typography.titleMedium,
                    color = colors.ink
                )
                Text(
                    text = "Choose your preferred theme across the app",
                    style = AutoFlowTheme.typography.bodySmall,
                    color = colors.muted
                )
                Spacer(modifier = Modifier.height(AppSpacing.xs))
                AFSegmented(
                    items = listOf("System", "Light", "Dark"),
                    selectedIndex = when (currentThemeMode) {
                        AppThemeMode.SYSTEM -> 0
                        AppThemeMode.LIGHT -> 1
                        AppThemeMode.DARK -> 2
                    },
                    onSelect = { index ->
                        val mode = when (index) {
                            0 -> AppThemeMode.SYSTEM
                            1 -> AppThemeMode.LIGHT
                            else -> AppThemeMode.DARK
                        }
                        onSelectThemeMode(mode)
                    }
                )
            }

            // Categories Section
            Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
                Text(
                    text = "Categories",
                    style = AutoFlowTheme.typography.titleMedium,
                    color = colors.ink
                )
                Surface(
                    shape = RoundedCornerShape(AppRadius.md),
                    color = colors.sheet,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(AppRadius.md))
                        .clickable(onClick = onNavigateToMyCategories)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = AppSpacing.minTapTarget)
                            .padding(horizontal = AppSpacing.md, vertical = AppSpacing.md),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Your categories",
                                style = AutoFlowTheme.typography.bodyLarge,
                                color = colors.ink
                            )
                            Spacer(modifier = Modifier.height(AppSpacing.xxs))
                            Text(
                                text = "Add, rename or delete your own",
                                style = AutoFlowTheme.typography.bodySmall,
                                color = colors.muted
                            )
                        }
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = "Open My Categories",
                            tint = colors.muted
                        )
                    }
                }
            }

            // Privacy Section
            Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
                Text(
                    text = "Privacy",
                    style = AutoFlowTheme.typography.titleMedium,
                    color = colors.ink
                )
                Surface(
                    shape = RoundedCornerShape(AppRadius.md),
                    color = colors.sheet,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(AppRadius.md))
                        .clickable {
                            if (!hasSmsPermission) {
                                openAppSettings(context)
                            }
                        }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = AppSpacing.minTapTarget)
                            .padding(horizontal = AppSpacing.md, vertical = AppSpacing.md),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "SMS access",
                                style = AutoFlowTheme.typography.bodyLarge,
                                color = colors.ink
                            )
                            Spacer(modifier = Modifier.height(AppSpacing.xxs))
                            Text(
                                text = "Messages are read on this phone only",
                                style = AutoFlowTheme.typography.bodySmall,
                                color = colors.muted
                            )
                        }
                        Spacer(modifier = Modifier.width(AppSpacing.sm))
                        Surface(
                            shape = RoundedCornerShape(AppRadius.full),
                            color = if (hasSmsPermission) colors.tint else colors.amberBg
                        ) {
                            Text(
                                text = if (hasSmsPermission) "On" else "Off",
                                style = AutoFlowTheme.typography.labelMedium,
                                color = if (hasSmsPermission) colors.onTint else colors.amberFg,
                                modifier = Modifier.padding(horizontal = AppSpacing.sm, vertical = AppSpacing.xs)
                            )
                        }
                        if (!hasSmsPermission) {
                            Spacer(modifier = Modifier.width(AppSpacing.xs))
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = "Open App Settings",
                                tint = colors.muted
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun openAppSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.fromParts("package", context.packageName, null)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(intent)
}

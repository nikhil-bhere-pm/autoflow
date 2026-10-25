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
import com.autoflow.app.presentation.ui.components.AFButton
import com.autoflow.app.presentation.ui.components.AFButtonVariant
import com.autoflow.app.presentation.ui.components.AFSegmented
import com.autoflow.app.presentation.ui.components.AFTopBar

@Composable
fun SettingsScreen(
    currentThemeMode: AppThemeMode,
    onSelectThemeMode: (AppThemeMode) -> Unit,
    onNavigateToMyCategories: () -> Unit,
    onNavigateToPermissionHelp: () -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    onResetFirstRun: (() -> Unit)? = null
) {
    val colors = AutoFlowTheme.colors
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val notificationPrefs = remember {
        com.autoflow.app.presentation.preferences.SharedPreferencesNotificationPreferences.fromContext(context)
    }

    var isNotificationEnabled by remember {
        mutableStateOf(notificationPrefs.isCapturedExpenseNotificationEnabled())
    }

    var isDailyRecapEnabled by remember {
        mutableStateOf(notificationPrefs.isDailyRecapEnabled())
    }

    var recapHour by remember {
        mutableStateOf(notificationPrefs.getDailyRecapTimeHour())
    }

    var recapMinute by remember {
        mutableStateOf(notificationPrefs.getDailyRecapTimeMinute())
    }

    var hasNotificationPermission by remember {
        mutableStateOf(
            androidx.core.app.NotificationManagerCompat.from(context).areNotificationsEnabled()
        )
    }

    val postNotificationLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasNotificationPermission = granted
    }

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
                hasNotificationPermission = androidx.core.app.NotificationManagerCompat.from(context).areNotificationsEnabled()
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
                                onNavigateToPermissionHelp()
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
                                contentDescription = "Open Permission Help",
                                tint = colors.muted
                            )
                        }
                    }
                }
            }

            // Notifications Section
            val handleNotificationPermissionRequest: () -> Unit = {
                val activity = context as? android.app.Activity
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU &&
                    activity != null &&
                    androidx.core.app.ActivityCompat.shouldShowRequestPermissionRationale(
                        activity,
                        Manifest.permission.POST_NOTIFICATIONS
                    )
                ) {
                    postNotificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU &&
                    !notificationPrefs.hasRequestedNotificationPermission()
                ) {
                    notificationPrefs.setHasRequestedNotificationPermission(true)
                    postNotificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    openAppNotificationSettings(context)
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
                Text(
                    text = "Notifications",
                    style = AutoFlowTheme.typography.titleMedium,
                    color = colors.ink
                )
                Surface(
                    shape = RoundedCornerShape(AppRadius.md),
                    color = colors.sheet,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(AppRadius.md))
                ) {
                    Column {
                        // 1. Captured expense notification
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = AppSpacing.minTapTarget)
                                .clickable {
                                    if (!hasNotificationPermission) {
                                        handleNotificationPermissionRequest()
                                    } else {
                                        val newEnabled = !isNotificationEnabled
                                        isNotificationEnabled = newEnabled
                                        notificationPrefs.setCapturedExpenseNotificationEnabled(newEnabled)
                                    }
                                }
                                .padding(horizontal = AppSpacing.md, vertical = AppSpacing.md),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Notify me when an expense is captured",
                                    style = AutoFlowTheme.typography.bodyLarge,
                                    color = colors.ink
                                )
                                Spacer(modifier = Modifier.height(AppSpacing.xxs))
                                Text(
                                    text = if (!hasNotificationPermission) {
                                        "Allow notifications in Android settings"
                                    } else {
                                        "Shows amount and merchant"
                                    },
                                    style = AutoFlowTheme.typography.bodySmall,
                                    color = if (!hasNotificationPermission) colors.amberFg else colors.muted
                                )
                            }
                            Spacer(modifier = Modifier.width(AppSpacing.sm))
                            androidx.compose.material3.Switch(
                                checked = hasNotificationPermission && isNotificationEnabled,
                                onCheckedChange = { _ ->
                                    if (!hasNotificationPermission) {
                                        handleNotificationPermissionRequest()
                                    } else {
                                        val newEnabled = !isNotificationEnabled
                                        isNotificationEnabled = newEnabled
                                        notificationPrefs.setCapturedExpenseNotificationEnabled(newEnabled)
                                    }
                                },
                                colors = androidx.compose.material3.SwitchDefaults.colors(
                                    checkedThumbColor = colors.buttonText,
                                    checkedTrackColor = colors.button,
                                    uncheckedThumbColor = colors.muted,
                                    uncheckedTrackColor = colors.track
                                )
                            )
                        }

                        androidx.compose.material3.HorizontalDivider(
                            color = colors.line,
                            thickness = 1.dp,
                            modifier = Modifier.padding(horizontal = AppSpacing.md)
                        )

                        // 2. Daily recap switch
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = AppSpacing.minTapTarget)
                                .clickable {
                                    if (!hasNotificationPermission) {
                                        handleNotificationPermissionRequest()
                                    } else {
                                        val newEnabled = !isDailyRecapEnabled
                                        isDailyRecapEnabled = newEnabled
                                        notificationPrefs.setDailyRecapEnabled(newEnabled)
                                        com.autoflow.app.notification.recap.DailyRecapScheduler.scheduleNextRun(context, replaceExisting = true)
                                    }
                                }
                                .padding(horizontal = AppSpacing.md, vertical = AppSpacing.md),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Daily recap",
                                    style = AutoFlowTheme.typography.bodyLarge,
                                    color = colors.ink
                                )
                                Spacer(modifier = Modifier.height(AppSpacing.xxs))
                                Text(
                                    text = if (!hasNotificationPermission) {
                                        "Allow notifications in Android settings"
                                    } else {
                                        "A summary of the day's spending"
                                    },
                                    style = AutoFlowTheme.typography.bodySmall,
                                    color = if (!hasNotificationPermission) colors.amberFg else colors.muted
                                )
                            }
                            Spacer(modifier = Modifier.width(AppSpacing.sm))
                            androidx.compose.material3.Switch(
                                checked = hasNotificationPermission && isDailyRecapEnabled,
                                onCheckedChange = { _ ->
                                    if (!hasNotificationPermission) {
                                        handleNotificationPermissionRequest()
                                    } else {
                                        val newEnabled = !isDailyRecapEnabled
                                        isDailyRecapEnabled = newEnabled
                                        notificationPrefs.setDailyRecapEnabled(newEnabled)
                                        com.autoflow.app.notification.recap.DailyRecapScheduler.scheduleNextRun(context, replaceExisting = true)
                                    }
                                },
                                colors = androidx.compose.material3.SwitchDefaults.colors(
                                    checkedThumbColor = colors.buttonText,
                                    checkedTrackColor = colors.button,
                                    uncheckedThumbColor = colors.muted,
                                    uncheckedTrackColor = colors.track
                                )
                            )
                        }

                        androidx.compose.material3.HorizontalDivider(
                            color = colors.line,
                            thickness = 1.dp,
                            modifier = Modifier.padding(horizontal = AppSpacing.md)
                        )

                        // 3. Recap time row
                        val recapTimeEnabled = hasNotificationPermission && isDailyRecapEnabled
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = AppSpacing.minTapTarget)
                                .clickable(enabled = recapTimeEnabled) {
                                    android.app.TimePickerDialog(
                                        context,
                                        { _, h, m ->
                                            recapHour = h
                                            recapMinute = m
                                            notificationPrefs.setDailyRecapTime(h, m)
                                            com.autoflow.app.notification.recap.DailyRecapScheduler.scheduleNextRun(context, replaceExisting = true)
                                        },
                                        recapHour,
                                        recapMinute,
                                        false
                                    ).show()
                                }
                                .padding(horizontal = AppSpacing.md, vertical = AppSpacing.md),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Recap time",
                                    style = AutoFlowTheme.typography.bodyLarge,
                                    color = if (recapTimeEnabled) colors.ink else colors.muted
                                )
                                Spacer(modifier = Modifier.height(AppSpacing.xxs))
                                Text(
                                    text = formatRecapTime(recapHour, recapMinute),
                                    style = AutoFlowTheme.typography.bodySmall,
                                    color = if (recapTimeEnabled) colors.muted else colors.muted.copy(alpha = 0.5f)
                                )
                            }
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = "Edit recap time",
                                tint = if (recapTimeEnabled) colors.muted else colors.muted.copy(alpha = 0.5f)
                            )
                        }
                    }
                }
            }

            // Debug-only Section (reset first run / test permission help)
            val isDebuggable = (context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
            if (isDebuggable && onResetFirstRun != null) {
                Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
                    Text(
                        text = "Debug",
                        style = AutoFlowTheme.typography.titleMedium,
                        color = colors.ink
                    )
                    AFButton(
                        text = "Reset first run (Debug)",
                        variant = AFButtonVariant.SECONDARY,
                        onClick = onResetFirstRun,
                        modifier = Modifier.fillMaxWidth()
                    )
                    AFButton(
                        text = "Toggle SMS Off/On (Debug)",
                        variant = AFButtonVariant.SECONDARY,
                        onClick = { hasSmsPermission = !hasSmsPermission },
                        modifier = Modifier.fillMaxWidth()
                    )
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

private fun openAppNotificationSettings(context: Context) {
    val intent = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        }
    } else {
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
        }
    }.apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(intent)
}

private fun formatRecapTime(hour: Int, minute: Int): String {
    val h = if (hour % 12 == 0) 12 else hour % 12
    val amPm = if (hour >= 12) "PM" else "AM"
    return String.format(java.util.Locale.getDefault(), "%d:%02d %s", h, minute, amPm)
}



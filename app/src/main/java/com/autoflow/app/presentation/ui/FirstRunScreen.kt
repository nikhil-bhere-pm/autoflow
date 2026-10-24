package com.autoflow.app.presentation.ui

import android.Manifest
import android.app.Activity
import android.app.AppOpsManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.autoflow.app.R
import com.autoflow.app.presentation.theme.AppRadius
import com.autoflow.app.presentation.theme.AppSpacing
import com.autoflow.app.presentation.theme.AutoFlowTheme
import com.autoflow.app.presentation.ui.components.AFButton
import com.autoflow.app.presentation.ui.firstrun.PermissionPromptResult

fun isRestrictedSettingsBlocked(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        return false
    }
    return try {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager ?: return false
        val mode = appOps.unsafeCheckOpNoThrow(
            "android:access_restricted_settings",
            context.applicationInfo.uid,
            context.packageName
        )
        mode != AppOpsManager.MODE_ALLOWED
    } catch (_: Exception) {
        false
    }
}

@Composable
fun FirstRunScreen(
    onPermissionResult: (PermissionPromptResult) -> Unit,
    onNotNow: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = AutoFlowTheme.colors
    val context = LocalContext.current
    val activity = context as? Activity

    var hadRationaleBefore by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            onPermissionResult(PermissionPromptResult.Granted)
        } else {
            val hasRationaleAfter = activity?.let {
                ActivityCompat.shouldShowRequestPermissionRationale(it, Manifest.permission.RECEIVE_SMS)
            } ?: false
            val restricted = isRestrictedSettingsBlocked(context)

            if (restricted || (!hadRationaleBefore && !hasRationaleAfter)) {
                // Android refused to show the prompt because the app is sideloaded/restricted or blocked
                onPermissionResult(PermissionPromptResult.BlockedOrRestricted)
            } else {
                // Denied by the user in the prompt -> go to Home (cash still works), no nagging
                onPermissionResult(PermissionPromptResult.Denied)
            }
        }
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = colors.bg
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = AppSpacing.lg, vertical = AppSpacing.md)
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.Center
            ) {
                Spacer(modifier = Modifier.height(AppSpacing.md))

                // Shield icon in a tinted rounded square
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(RoundedCornerShape(AppRadius.lg))
                        .background(colors.tint),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_shield_private),
                        contentDescription = null,
                        tint = colors.brand,
                        modifier = Modifier.size(40.dp)
                    )
                }

                Spacer(modifier = Modifier.height(AppSpacing.lg))

                // Title
                Text(
                    text = "Your spending, tracked privately",
                    style = AutoFlowTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = colors.ink
                )

                Spacer(modifier = Modifier.height(AppSpacing.sm))

                // Text
                Text(
                    text = "AutoFlow reads bank SMS on your phone only. Nothing leaves your device. Messages are not saved.",
                    style = AutoFlowTheme.typography.bodyMedium,
                    color = colors.muted
                )

                Spacer(modifier = Modifier.height(AppSpacing.xl))

                // Three points with icons
                Column(
                    verticalArrangement = Arrangement.spacedBy(AppSpacing.md)
                ) {
                    PointRow(
                        iconRes = R.drawable.ic_point_device,
                        headline = "Stays on your phone",
                        detail = "No account, no upload"
                    )
                    PointRow(
                        iconRes = R.drawable.ic_point_no_save,
                        headline = "Messages are not saved",
                        detail = "Only the amount, merchant and time are kept"
                    )
                    PointRow(
                        iconRes = R.drawable.ic_point_cash,
                        headline = "Cash works without SMS",
                        detail = "Add cash spending any time"
                    )
                }

                Spacer(modifier = Modifier.height(AppSpacing.lg))
            }

            // Buttons
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                AFButton(
                    text = "Allow SMS access",
                    onClick = {
                        val isGranted = ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.RECEIVE_SMS
                        ) == PackageManager.PERMISSION_GRANTED

                        if (isGranted) {
                            onPermissionResult(PermissionPromptResult.Granted)
                        } else {
                            hadRationaleBefore = activity?.let {
                                ActivityCompat.shouldShowRequestPermissionRationale(it, Manifest.permission.RECEIVE_SMS)
                            } ?: false

                            if (isRestrictedSettingsBlocked(context)) {
                                onPermissionResult(PermissionPromptResult.BlockedOrRestricted)
                            } else {
                                permissionLauncher.launch(Manifest.permission.RECEIVE_SMS)
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(AppSpacing.xs))

                TextButton(
                    onClick = onNotNow,
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = AppSpacing.minTapTarget)
                ) {
                    Text(
                        text = "Not now, I will add cash only",
                        style = AutoFlowTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = colors.muted
                    )
                }
            }
        }
    }
}

@Composable
private fun PointRow(
    iconRes: Int,
    headline: String,
    detail: String
) {
    val colors = AutoFlowTheme.colors

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(AppRadius.md))
                .background(colors.sheet),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(id = iconRes),
                contentDescription = null,
                tint = colors.ink,
                modifier = Modifier.size(22.dp)
            )
        }

        Spacer(modifier = Modifier.width(AppSpacing.md))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = headline,
                style = AutoFlowTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = colors.ink
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = detail,
                style = AutoFlowTheme.typography.bodySmall,
                color = colors.muted
            )
        }
    }
}

package com.autoflow.app.presentation.ui.components

import android.content.res.Configuration
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.autoflow.app.presentation.theme.AppRadius
import com.autoflow.app.presentation.theme.AppSpacing
import com.autoflow.app.presentation.theme.AutoFlowTheme

enum class AFBadgeVariant {
    CHECK,
    MUTED,
    REFUND,
    OUTLINE,
    AMBER
}

@Composable
fun AFBadge(
    text: String,
    variant: AFBadgeVariant,
    modifier: Modifier = Modifier,
    icon: (@Composable () -> Unit)? = null
) {
    val colors = AutoFlowTheme.colors

    val (containerColor, contentColor, border) = when (variant) {
        AFBadgeVariant.CHECK -> Triple(colors.tint, colors.onTint, null)
        AFBadgeVariant.MUTED -> Triple(colors.chip, colors.muted, null)
        AFBadgeVariant.REFUND -> Triple(colors.tint, colors.onTint, null)
        AFBadgeVariant.OUTLINE -> Triple(Color.Transparent, colors.muted, BorderStroke(1.dp, colors.line))
        AFBadgeVariant.AMBER -> Triple(colors.amberBg, colors.amberFg, null)
    }

    Surface(
        shape = RoundedCornerShape(AppRadius.full),
        color = containerColor,
        contentColor = contentColor,
        border = border,
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = AppSpacing.sm, vertical = AppSpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (icon != null) {
                icon()
                Spacer(modifier = Modifier.width(AppSpacing.xs))
            }
            Text(
                text = text,
                style = AutoFlowTheme.typography.labelSmall
            )
        }
    }
}

@Preview(name = "Light - AFBadge")
@Composable
private fun AFBadgeLightPreview() {
    AutoFlowTheme(darkTheme = false) {
        Surface(color = AutoFlowTheme.colors.bg) {
            Column(
                modifier = Modifier.padding(AppSpacing.md),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)
            ) {
                AFBadge(text = "Confirmed", variant = AFBadgeVariant.CHECK)
                AFBadge(text = "Pending", variant = AFBadgeVariant.MUTED)
                AFBadge(text = "Refund", variant = AFBadgeVariant.REFUND)
                AFBadge(text = "Unreviewed", variant = AFBadgeVariant.OUTLINE)
            }
        }
    }
}

@Preview(name = "Dark - AFBadge", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun AFBadgeDarkPreview() {
    AutoFlowTheme(darkTheme = true) {
        Surface(color = AutoFlowTheme.colors.bg) {
            Column(
                modifier = Modifier.padding(AppSpacing.md),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)
            ) {
                AFBadge(text = "Confirmed", variant = AFBadgeVariant.CHECK)
                AFBadge(text = "Pending", variant = AFBadgeVariant.MUTED)
                AFBadge(text = "Refund", variant = AFBadgeVariant.REFUND)
                AFBadge(text = "Unreviewed", variant = AFBadgeVariant.OUTLINE)
            }
        }
    }
}

package com.autoflow.app.presentation.ui.components

import android.content.res.Configuration
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.autoflow.app.presentation.theme.AppRadius
import com.autoflow.app.presentation.theme.AppSpacing
import com.autoflow.app.presentation.theme.AutoFlowTheme

enum class AFButtonVariant {
    PRIMARY,
    SECONDARY,
    DESTRUCTIVE
}

@Composable
fun AFButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: AFButtonVariant = AFButtonVariant.PRIMARY,
    enabled: Boolean = true,
    icon: (@Composable () -> Unit)? = null
) {
    val colors = AutoFlowTheme.colors

    val (containerColor, contentColor, border) = when (variant) {
        AFButtonVariant.PRIMARY -> Triple(
            colors.button,
            colors.buttonText,
            null
        )
        AFButtonVariant.SECONDARY -> Triple(
            colors.chip,
            colors.ink,
            BorderStroke(1.dp, colors.line)
        )
        AFButtonVariant.DESTRUCTIVE -> Triple(
            colors.chip,
            colors.red,
            BorderStroke(1.dp, colors.red)
        )
    }

    Button(
        onClick = onClick,
        modifier = modifier.defaultMinSize(minHeight = AppSpacing.minTapTarget, minWidth = AppSpacing.minTapTarget),
        enabled = enabled,
        shape = RoundedCornerShape(AppRadius.md),
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor,
            disabledContainerColor = colors.chip,
            disabledContentColor = colors.muted
        ),
        border = if (enabled) border else BorderStroke(1.dp, colors.line),
        contentPadding = PaddingValues(horizontal = AppSpacing.md, vertical = AppSpacing.sm)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (icon != null) {
                icon()
                Spacer(modifier = Modifier.width(AppSpacing.sm))
            }
            Text(
                text = text,
                style = AutoFlowTheme.typography.labelLarge
            )
        }
    }
}

@Preview(name = "Light - AFButton")
@Composable
private fun AFButtonLightPreview() {
    AutoFlowTheme(darkTheme = false) {
        Surface(color = AutoFlowTheme.colors.bg) {
            Column(
                modifier = Modifier.padding(AppSpacing.md),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)
            ) {
                AFButton(text = "Primary Button", onClick = {}, variant = AFButtonVariant.PRIMARY)
                AFButton(text = "Secondary Button", onClick = {}, variant = AFButtonVariant.SECONDARY)
                AFButton(text = "Destructive Button", onClick = {}, variant = AFButtonVariant.DESTRUCTIVE)
            }
        }
    }
}

@Preview(name = "Dark - AFButton", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun AFButtonDarkPreview() {
    AutoFlowTheme(darkTheme = true) {
        Surface(color = AutoFlowTheme.colors.bg) {
            Column(
                modifier = Modifier.padding(AppSpacing.md),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)
            ) {
                AFButton(text = "Primary Button", onClick = {}, variant = AFButtonVariant.PRIMARY)
                AFButton(text = "Secondary Button", onClick = {}, variant = AFButtonVariant.SECONDARY)
                AFButton(text = "Destructive Button", onClick = {}, variant = AFButtonVariant.DESTRUCTIVE)
            }
        }
    }
}

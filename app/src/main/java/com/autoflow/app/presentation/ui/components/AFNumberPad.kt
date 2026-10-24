package com.autoflow.app.presentation.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.autoflow.app.presentation.theme.AppRadius
import com.autoflow.app.presentation.theme.AppSpacing
import com.autoflow.app.presentation.theme.AutoFlowTheme

@Composable
fun AFNumberPad(
    onDigitClick: (Char) -> Unit,
    onDotClick: () -> Unit,
    onBackspaceClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val rows = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf(".", "0", "⌫")
    )

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)
    ) {
        for (row in rows) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)
            ) {
                for (key in row) {
                    AFNumberPadKey(
                        label = key,
                        onClick = {
                            when (key) {
                                "." -> onDotClick()
                                "⌫" -> onBackspaceClick()
                                else -> onDigitClick(key[0])
                            }
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun AFNumberPadKey(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = AutoFlowTheme.colors

    Surface(
        shape = RoundedCornerShape(AppRadius.md),
        color = colors.sheet,
        contentColor = colors.ink,
        modifier = modifier
            .defaultMinSize(minHeight = AppSpacing.minTapTarget)
            .height(56.dp)
            .clip(RoundedCornerShape(AppRadius.md))
            .clickable(
                role = Role.Button,
                onClick = onClick
            )
            .semantics {
                contentDescription = if (label == "⌫") "Backspace" else "Key $label"
            }
    ) {
        Box(
            modifier = Modifier.padding(AppSpacing.xs),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                style = AutoFlowTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = colors.ink
            )
        }
    }
}

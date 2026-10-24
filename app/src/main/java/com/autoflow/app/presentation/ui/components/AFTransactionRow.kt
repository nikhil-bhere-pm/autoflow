package com.autoflow.app.presentation.ui.components

import android.content.res.Configuration
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.autoflow.app.presentation.theme.AppSpacing
import com.autoflow.app.presentation.theme.AutoFlowTheme

@Composable
fun AFTransactionRow(
    title: String,
    subtitle: String,
    amountText: String,
    category: String,
    modifier: Modifier = Modifier,
    isPositive: Boolean = false,
    badge: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null
) {
    val colors = AutoFlowTheme.colors

    val clickableModifier = if (onClick != null) {
        Modifier.clickable(onClick = onClick)
    } else {
        Modifier
    }

    Surface(
        color = colors.sheet,
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 64.dp)
            .then(clickableModifier)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AppSpacing.md, vertical = AppSpacing.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AFCategoryTile(category = category)

            Spacer(modifier = Modifier.width(AppSpacing.md))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = title,
                    style = AutoFlowTheme.typography.titleSmall,
                    color = colors.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    style = AutoFlowTheme.typography.bodySmall,
                    color = colors.muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(AppSpacing.sm))

            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = amountText,
                    style = AutoFlowTheme.typography.titleMedium,
                    color = if (isPositive) colors.brand else colors.ink
                )
                if (badge != null) {
                    badge()
                }
            }
        }
    }
}

@Preview(name = "Light - AFTransactionRow")
@Composable
private fun AFTransactionRowLightPreview() {
    AutoFlowTheme(darkTheme = false) {
        Surface(color = AutoFlowTheme.colors.bg) {
            Column(
                modifier = Modifier.padding(vertical = AppSpacing.md),
                verticalArrangement = Arrangement.spacedBy(1.dp)
            ) {
                AFTransactionRow(
                    title = "Swiggy",
                    subtitle = "Today, 1:45 PM • UPI",
                    amountText = "- ₹450.00",
                    category = "Food",
                    badge = { AFBadge(text = "Confirmed", variant = AFBadgeVariant.CHECK) },
                    onClick = {}
                )
                AFTransactionRow(
                    title = "Amazon Refund",
                    subtitle = "Yesterday, 6:30 PM • Bank",
                    amountText = "+ ₹1,299.00",
                    category = "Shopping",
                    isPositive = true,
                    badge = { AFBadge(text = "Refund", variant = AFBadgeVariant.REFUND) },
                    onClick = {}
                )
            }
        }
    }
}

@Preview(name = "Dark - AFTransactionRow", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun AFTransactionRowDarkPreview() {
    AutoFlowTheme(darkTheme = true) {
        Surface(color = AutoFlowTheme.colors.bg) {
            Column(
                modifier = Modifier.padding(vertical = AppSpacing.md),
                verticalArrangement = Arrangement.spacedBy(1.dp)
            ) {
                AFTransactionRow(
                    title = "Swiggy",
                    subtitle = "Today, 1:45 PM • UPI",
                    amountText = "- ₹450.00",
                    category = "Food",
                    badge = { AFBadge(text = "Confirmed", variant = AFBadgeVariant.CHECK) },
                    onClick = {}
                )
                AFTransactionRow(
                    title = "Amazon Refund",
                    subtitle = "Yesterday, 6:30 PM • Bank",
                    amountText = "+ ₹1,299.00",
                    category = "Shopping",
                    isPositive = true,
                    badge = { AFBadge(text = "Refund", variant = AFBadgeVariant.REFUND) },
                    onClick = {}
                )
            }
        }
    }
}

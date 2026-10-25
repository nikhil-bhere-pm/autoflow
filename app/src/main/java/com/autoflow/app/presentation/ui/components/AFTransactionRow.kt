package com.autoflow.app.presentation.ui.components

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.autoflow.app.R
import com.autoflow.app.presentation.theme.AppRadius
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
    isNotCounted: Boolean = false,
    isFlat: Boolean = false,
    badge: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null
) {
    val colors = AutoFlowTheme.colors

    val clickableModifier = if (onClick != null) {
        Modifier.clickable(onClick = onClick)
    } else {
        Modifier
    }

    val stripeColor = colors.muted.copy(alpha = 0.08f)
    val backgroundModifier = if (isNotCounted) {
        Modifier.drawBehind {
            val stripeWidth = 2.dp.toPx()
            val gap = 10.dp.toPx()
            val step = stripeWidth + gap
            val total = size.width + size.height
            var x = 0f
            while (x < total) {
                drawLine(
                    color = stripeColor,
                    start = Offset(x, 0f),
                    end = Offset(x - size.height, size.height),
                    strokeWidth = stripeWidth
                )
                x += step
            }
        }
    } else {
        Modifier
    }

    Surface(
        color = if (isFlat) androidx.compose.ui.graphics.Color.Transparent else colors.sheet,
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 64.dp)
            .then(backgroundModifier)
            .then(clickableModifier)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = if (isFlat) 20.dp else AppSpacing.md,
                    vertical = AppSpacing.sm
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isNotCounted) {
                Box(
                    modifier = Modifier
                        .size(AppSpacing.minTapTarget)
                        .defaultMinSize(minWidth = AppSpacing.minTapTarget, minHeight = AppSpacing.minTapTarget)
                        .clip(RoundedCornerShape(AppRadius.md))
                        .background(colors.chip),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_ban),
                        contentDescription = "Not counted",
                        tint = colors.muted,
                        modifier = Modifier.size(20.dp)
                    )
                }
            } else {
                AFCategoryTile(category = category)
            }

            Spacer(modifier = Modifier.width(AppSpacing.md))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs)
                ) {
                    Text(
                        text = title,
                        style = AutoFlowTheme.typography.titleSmall,
                        color = if (isNotCounted) colors.muted else colors.ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (isNotCounted) {
                        AFBadge(text = "Not counted", variant = AFBadgeVariant.MUTED)
                    }
                }
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
                    textDecoration = if (isNotCounted) TextDecoration.LineThrough else null,
                    color = if (isNotCounted) colors.muted else if (isPositive) colors.brand else colors.ink
                )
                if (!isNotCounted && badge != null) {
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
                AFTransactionRow(
                    title = "Transfer to Friend",
                    subtitle = "Not counted · 2:15 PM",
                    amountText = "₹500.00",
                    category = "Others",
                    isNotCounted = true,
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
                    title = "Transfer to Friend",
                    subtitle = "Not counted · 2:15 PM",
                    amountText = "₹500.00",
                    category = "Others",
                    isNotCounted = true,
                    onClick = {}
                )
            }
        }
    }
}

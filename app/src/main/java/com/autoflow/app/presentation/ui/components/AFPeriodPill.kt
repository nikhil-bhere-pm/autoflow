package com.autoflow.app.presentation.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.autoflow.app.domain.stats.StatsPeriod
import com.autoflow.app.presentation.theme.AppRadius
import com.autoflow.app.presentation.theme.AppSpacing
import com.autoflow.app.presentation.theme.AutoFlowTheme
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun AFPeriodPill(
    pillLabel: String,
    onSelectPeriod: (StatsPeriod) -> Unit,
    modifier: Modifier = Modifier,
    onSelectMonth: ((YearMonth) -> Unit)? = null
) {
    val colors = AutoFlowTheme.colors
    var menuExpanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        Surface(
            shape = RoundedCornerShape(AppRadius.full),
            color = colors.sheet,
            border = androidx.compose.foundation.BorderStroke(1.dp, colors.line),
            modifier = Modifier
                .height(40.dp)
                .clickable { menuExpanded = true }
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.padding(horizontal = 16.dp)
            ) {
                Text(
                    text = pillLabel,
                    style = AutoFlowTheme.typography.labelLarge.copy(
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = colors.ink
                )
                Spacer(modifier = Modifier.width(AppSpacing.xs))
                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = "Select period",
                    tint = colors.muted,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        DropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = { menuExpanded = false },
            modifier = Modifier.background(colors.sheet)
        ) {
            DropdownMenuItem(
                text = { Text("Today", style = AutoFlowTheme.typography.bodyMedium, color = colors.ink) },
                onClick = {
                    onSelectPeriod(StatsPeriod.TODAY)
                    menuExpanded = false
                }
            )
            DropdownMenuItem(
                text = { Text("This week", style = AutoFlowTheme.typography.bodyMedium, color = colors.ink) },
                onClick = {
                    onSelectPeriod(StatsPeriod.THIS_WEEK)
                    menuExpanded = false
                }
            )
            DropdownMenuItem(
                text = { Text("This month", style = AutoFlowTheme.typography.bodyMedium, color = colors.ink) },
                onClick = {
                    onSelectPeriod(StatsPeriod.THIS_MONTH)
                    menuExpanded = false
                }
            )

            if (onSelectMonth != null) {
                HorizontalDivider(color = colors.line, thickness = 1.dp)

                val currentMonth = YearMonth.now(ZoneId.systemDefault())
                val formatter = DateTimeFormatter.ofPattern("MMM yyyy", Locale.US)
                val pastMonths = (1..5).map { currentMonth.minusMonths(it.toLong()) }

                for (month in pastMonths) {
                    val label = month.format(formatter)
                    DropdownMenuItem(
                        text = { Text(label, style = AutoFlowTheme.typography.bodyMedium, color = colors.ink) },
                        onClick = {
                            onSelectMonth(month)
                            menuExpanded = false
                        }
                    )
                }
            }
        }
    }
}

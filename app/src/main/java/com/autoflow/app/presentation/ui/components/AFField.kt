package com.autoflow.app.presentation.ui.components

import android.content.res.Configuration
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.autoflow.app.presentation.theme.AppRadius
import com.autoflow.app.presentation.theme.AppSpacing
import com.autoflow.app.presentation.theme.AutoFlowTheme

@Composable
fun AFField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    errorMessage: String? = null,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    enabled: Boolean = true,
    singleLine: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default
) {
    val colors = AutoFlowTheme.colors
    val isError = !errorMessage.isNullOrBlank()

    Column(modifier = modifier.fillMaxWidth()) {
        if (!label.isNullOrBlank()) {
            Text(
                text = label,
                style = AutoFlowTheme.typography.labelLarge,
                color = colors.ink,
                modifier = Modifier.padding(bottom = AppSpacing.xs)
            )
        }

        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = AppSpacing.minTapTarget),
            enabled = enabled,
            singleLine = singleLine,
            isError = isError,
            textStyle = AutoFlowTheme.typography.bodyLarge.copy(color = colors.ink),
            placeholder = if (!placeholder.isNullOrBlank()) {
                {
                    Text(
                        text = placeholder,
                        style = AutoFlowTheme.typography.bodyLarge,
                        color = colors.muted
                    )
                }
            } else null,
            leadingIcon = leadingIcon,
            trailingIcon = trailingIcon,
            shape = RoundedCornerShape(AppRadius.md),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = colors.field,
                unfocusedContainerColor = colors.field,
                disabledContainerColor = colors.chip,
                errorContainerColor = colors.field,
                focusedBorderColor = colors.brand,
                unfocusedBorderColor = colors.line,
                errorBorderColor = colors.red,
                cursorColor = colors.brand,
                errorCursorColor = colors.red
            ),
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions
        )

        if (isError) {
            Spacer(modifier = Modifier.height(AppSpacing.xs))
            Text(
                text = errorMessage ?: "",
                style = AutoFlowTheme.typography.bodySmall,
                color = colors.red,
                modifier = Modifier.padding(start = AppSpacing.xs)
            )
        }
    }
}

@Preview(name = "Light - AFField")
@Composable
private fun AFFieldLightPreview() {
    AutoFlowTheme(darkTheme = false) {
        Surface(color = AutoFlowTheme.colors.bg) {
            Column(
                modifier = Modifier.padding(AppSpacing.md),
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(AppSpacing.md)
            ) {
                AFField(
                    value = "Zomato",
                    onValueChange = {},
                    label = "Merchant Name",
                    placeholder = "Enter merchant"
                )
                AFField(
                    value = "",
                    onValueChange = {},
                    label = "Note",
                    placeholder = "Add an optional note",
                    errorMessage = "Note cannot exceed 200 characters"
                )
            }
        }
    }
}

@Preview(name = "Dark - AFField", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun AFFieldDarkPreview() {
    AutoFlowTheme(darkTheme = true) {
        Surface(color = AutoFlowTheme.colors.bg) {
            Column(
                modifier = Modifier.padding(AppSpacing.md),
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(AppSpacing.md)
            ) {
                AFField(
                    value = "Zomato",
                    onValueChange = {},
                    label = "Merchant Name",
                    placeholder = "Enter merchant"
                )
                AFField(
                    value = "",
                    onValueChange = {},
                    label = "Note",
                    placeholder = "Add an optional note",
                    errorMessage = "Note cannot exceed 200 characters"
                )
            }
        }
    }
}

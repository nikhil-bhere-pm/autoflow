package com.autoflow.app.presentation.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.autoflow.app.presentation.theme.AutoFlowTheme
import com.autoflow.app.presentation.viewmodel.AutopayUiState

@Composable
fun AutopayScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    uiState: AutopayUiState = AutopayUiState.Empty
) {
    val colors = AutoFlowTheme.colors

    BackHandler {
        onNavigateBack()
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = colors.bg
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        )
    }
}

package com.autoflow.app.presentation.viewmodel

sealed interface AutopayUiState {
    data object Empty : AutopayUiState
}

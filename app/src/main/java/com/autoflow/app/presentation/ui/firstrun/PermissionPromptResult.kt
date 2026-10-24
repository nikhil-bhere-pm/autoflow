package com.autoflow.app.presentation.ui.firstrun

import com.autoflow.app.presentation.ui.Screen

enum class PermissionPromptResult {
    Granted,
    Denied,
    BlockedOrRestricted
}

fun resolveNextScreenOnPermissionResult(
    result: PermissionPromptResult,
    fromSettings: Boolean
): Screen {
    return when (result) {
        PermissionPromptResult.Granted -> {
            if (fromSettings) Screen.Settings else Screen.Home
        }
        PermissionPromptResult.Denied -> {
            if (fromSettings) Screen.Settings else Screen.Home
        }
        PermissionPromptResult.BlockedOrRestricted -> {
            Screen.PermissionHelp(fromSettings = fromSettings)
        }
    }
}

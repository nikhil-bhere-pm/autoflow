package com.autoflow.app.presentation.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ============================================================================
// SPACING & RADII (ONE centralized design tokens file)
// ============================================================================

object AppSpacing {
    val xxs: Dp = 2.dp
    val xs: Dp = 4.dp
    val sm: Dp = 8.dp
    val md: Dp = 16.dp
    val lg: Dp = 24.dp
    val xl: Dp = 32.dp
    val xxl: Dp = 48.dp
    val minTapTarget: Dp = 48.dp
}

object AppRadius {
    val xs: Dp = 4.dp
    val sm: Dp = 8.dp
    val md: Dp = 12.dp
    val lg: Dp = 16.dp
    val xl: Dp = 24.dp
    val full: Dp = 999.dp
}

// ============================================================================
// COLORS (Neutral, clean, slate/cool-gray palette; NOT default purple)
// ============================================================================

private val NeutralSlate950 = Color(0xFF0B0F17)
private val NeutralSlate900 = Color(0xFF0F172A)
private val NeutralSlate850 = Color(0xFF131B2A)
private val NeutralSlate800 = Color(0xFF1E293B)
private val NeutralSlate700 = Color(0xFF334155)
private val NeutralSlate600 = Color(0xFF475569)
private val NeutralSlate500 = Color(0xFF64748B)
private val NeutralSlate400 = Color(0xFF94A3B8)
private val NeutralSlate300 = Color(0xFFCBD5E1)
private val NeutralSlate200 = Color(0xFFE2E8F0)
private val NeutralSlate100 = Color(0xFFF1F5F9)
private val NeutralSlate50  = Color(0xFFF8FAFC)
private val PureWhite       = Color(0xFFFFFFFF)

// Semantic accent & badge colors
private val Amber100        = Color(0xFFFEF3C7)
private val Amber200        = Color(0xFFFDE68A)
private val Amber900        = Color(0xFF92400E)
private val Amber950        = Color(0xFF451A03)

private val Emerald100      = Color(0xFFD1FAE5)
private val Emerald200      = Color(0xFFA7F3D0)
private val Emerald900      = Color(0xFF065F46)
private val Emerald950      = Color(0xFF022C22)

private val Crimson600      = Color(0xFFDC2626)
private val Crimson100      = Color(0xFFFEE2E2)
private val Crimson400      = Color(0xFFF87171)
private val Crimson950      = Color(0xFF450A0A)

@Immutable
data class AppBadgeColors(
    val reviewContainer: Color,
    val onReview: Color,
    val refundContainer: Color,
    val onRefund: Color
)

val LocalAppBadgeColors = staticCompositionLocalOf {
    AppBadgeColors(
        reviewContainer = Amber100,
        onReview = Amber900,
        refundContainer = Emerald100,
        onRefund = Emerald900
    )
}

private val LightColorScheme = lightColorScheme(
    primary = NeutralSlate900,
    onPrimary = PureWhite,
    primaryContainer = NeutralSlate100,
    onPrimaryContainer = NeutralSlate900,
    secondary = NeutralSlate600,
    onSecondary = PureWhite,
    secondaryContainer = NeutralSlate200,
    onSecondaryContainer = NeutralSlate800,
    background = NeutralSlate50,
    onBackground = NeutralSlate900,
    surface = PureWhite,
    onSurface = NeutralSlate900,
    surfaceVariant = NeutralSlate100,
    onSurfaceVariant = NeutralSlate500,
    outline = NeutralSlate300,
    outlineVariant = NeutralSlate200,
    error = Crimson600,
    onError = PureWhite,
    errorContainer = Crimson100,
    onErrorContainer = Crimson600
)

private val DarkColorScheme = darkColorScheme(
    primary = NeutralSlate100,
    onPrimary = NeutralSlate950,
    primaryContainer = NeutralSlate800,
    onPrimaryContainer = NeutralSlate100,
    secondary = NeutralSlate400,
    onSecondary = NeutralSlate950,
    secondaryContainer = NeutralSlate700,
    onSecondaryContainer = NeutralSlate200,
    background = NeutralSlate950,
    onBackground = NeutralSlate100,
    surface = NeutralSlate850,
    onSurface = NeutralSlate100,
    surfaceVariant = NeutralSlate800,
    onSurfaceVariant = NeutralSlate400,
    outline = NeutralSlate700,
    outlineVariant = NeutralSlate800,
    error = Crimson400,
    onError = NeutralSlate950,
    errorContainer = Crimson950,
    onErrorContainer = Crimson400
)

// ============================================================================
// TYPOGRAPHY
// ============================================================================

val AppTypography = Typography(
    headlineLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 34.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 24.sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 22.sp
    ),
    titleSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 14.sp
    )
)

// ============================================================================
// THEME COMPOSABLE
// ============================================================================

@Composable
fun AutoFlowTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val badgeColors = if (darkTheme) {
        AppBadgeColors(
            reviewContainer = Amber950,
            onReview = Amber200,
            refundContainer = Emerald950,
            onRefund = Emerald200
        )
    } else {
        AppBadgeColors(
            reviewContainer = Amber100,
            onReview = Amber900,
            refundContainer = Emerald100,
            onRefund = Emerald900
        )
    }

    CompositionLocalProvider(LocalAppBadgeColors provides badgeColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = AppTypography,
            content = content
        )
    }
}

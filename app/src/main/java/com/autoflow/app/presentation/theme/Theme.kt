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
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.autoflow.app.R

// ============================================================================
// SPACING & RADII (Centralized design tokens)
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
// COLOR TOKENS (PROMPT D1 APPROVED VALUES - THEME.KT ONLY)
// ============================================================================

// Light theme tokens
private val LightBg = Color(0xFFF9FAFB)
private val LightSheet = Color(0xFFFFFFFF)
private val LightInk = Color(0xFF0F172A)
private val LightMuted = Color(0xFF64748B)
private val LightLine = Color(0xFFE5E7EB)
private val LightBrand = Color(0xFF047857)
private val LightTint = Color(0xFFD1FAE5)
private val LightOnTint = Color(0xFF065F46)
private val LightButton = Color(0xFF047857)
private val LightButtonText = Color(0xFFFFFFFF)
private val LightAmberBg = Color(0xFFFEF3C7)
private val LightAmberFg = Color(0xFF78350F)
private val LightChip = Color(0xFFF1F5F9)
private val LightHero = Color(0xFFECFDF5)
private val LightField = Color(0xFFF8FAFC)
private val LightRed = Color(0xFFB91C1C)
private val LightBar = Color(0xFF0D9E71)
private val LightTrack = Color(0xFFE2E8F0)

// Light category tokens
private val LightCatFood = Color(0xFFB83A0B)
private val LightCatGroceries = Color(0xFF047857)
private val LightCatTransport = Color(0xFF1D4ED8)
private val LightCatShopping = Color(0xFFBE185D)
private val LightCatBills = Color(0xFF8F5B05)
private val LightCatEntertainment = Color(0xFF6D28D9)
private val LightCatHealth = Color(0xFFB91C1C)
private val LightCatTravel = Color(0xFF0F766E)
private val LightCatOthers = Color(0xFF475569)
private val LightCatUnknown = Color(0xFF475569)

// Dark theme tokens
private val DarkBg = Color(0xFF0B1412)
private val DarkSheet = Color(0xFF12201C)
private val DarkInk = Color(0xFFF1F5F9)
private val DarkMuted = Color(0xFF94A3B8)
private val DarkLine = Color(0xFF22332F)
private val DarkBrand = Color(0xFF34D399)
private val DarkTint = Color(0xFF0F3D2E)
private val DarkOnTint = Color(0xFFA7F3D0)
private val DarkButton = Color(0xFF34D399)
private val DarkButtonText = Color(0xFF052E22)
private val DarkAmberBg = Color(0xFF3A2E0A)
private val DarkAmberFg = Color(0xFFFDE68A)
private val DarkChip = Color(0xFF1B2D28)
private val DarkHero = Color(0xFF0F2A22)
private val DarkField = Color(0xFF0F1B18)
private val DarkRed = Color(0xFFFCA5A5)
private val DarkBar = Color(0xFF34D399)
private val DarkTrack = Color(0xFF22332F)

// Dark category tokens
private val DarkCatFood = Color(0xFFFDBA74)
private val DarkCatGroceries = Color(0xFF6EE7B7)
private val DarkCatTransport = Color(0xFF93C5FD)
private val DarkCatShopping = Color(0xFFF9A8D4)
private val DarkCatBills = Color(0xFFFDE047)
private val DarkCatEntertainment = Color(0xFFC4B5FD)
private val DarkCatHealth = Color(0xFFFCA5A5)
private val DarkCatTravel = Color(0xFF5EEAD4)
private val DarkCatOthers = Color(0xFFCBD5E1)
private val DarkCatUnknown = Color(0xFFCBD5E1)

@Immutable
data class AutoFlowColors(
    val bg: Color,
    val sheet: Color,
    val ink: Color,
    val muted: Color,
    val line: Color,
    val brand: Color,
    val tint: Color,
    val onTint: Color,
    val button: Color,
    val buttonText: Color,
    val amberBg: Color,
    val amberFg: Color,
    val chip: Color,
    val hero: Color,
    val field: Color,
    val red: Color,
    val bar: Color,
    val track: Color,
    val isDark: Boolean,
    val catFood: Color,
    val catGroceries: Color,
    val catTransport: Color,
    val catShopping: Color,
    val catBills: Color,
    val catEntertainment: Color,
    val catHealth: Color,
    val catTravel: Color,
    val catOthers: Color,
    val catUnknown: Color
) {
    fun forColorKey(colorKey: String?): Color = when (colorKey?.trim()?.uppercase()) {
        "ORANGE" -> catFood
        "GREEN" -> catGroceries
        "BLUE" -> catTransport
        "PINK" -> catShopping
        "GOLD" -> catBills
        "PURPLE" -> catEntertainment
        "TEAL" -> catTravel
        "RED" -> catHealth
        else -> catUnknown
    }

    fun forCategory(category: String?): Color {
        val norm = category?.trim()?.lowercase() ?: return catUnknown
        return when (norm) {
            "food" -> catFood
            "groceries" -> catGroceries
            "transport" -> catTransport
            "shopping" -> catShopping
            "bills" -> catBills
            "entertainment" -> catEntertainment
            "health" -> catHealth
            "travel" -> catTravel
            "others" -> catOthers
            else -> {
                val colorKey = com.autoflow.app.presentation.ui.components.CategoryThemeRegistry.getColorKey(norm)
                if (colorKey != null) {
                    forColorKey(colorKey)
                } else {
                    catUnknown
                }
            }
        }
    }

    fun categoryTileBackground(category: String?): Color {
        return forCategory(category).copy(alpha = 0.11f)
    }
}

val LightAutoFlowColors = AutoFlowColors(
    bg = LightBg,
    sheet = LightSheet,
    ink = LightInk,
    muted = LightMuted,
    line = LightLine,
    brand = LightBrand,
    tint = LightTint,
    onTint = LightOnTint,
    button = LightButton,
    buttonText = LightButtonText,
    amberBg = LightAmberBg,
    amberFg = LightAmberFg,
    chip = LightChip,
    hero = LightHero,
    field = LightField,
    red = LightRed,
    bar = LightBar,
    track = LightTrack,
    isDark = false,
    catFood = LightCatFood,
    catGroceries = LightCatGroceries,
    catTransport = LightCatTransport,
    catShopping = LightCatShopping,
    catBills = LightCatBills,
    catEntertainment = LightCatEntertainment,
    catHealth = LightCatHealth,
    catTravel = LightCatTravel,
    catOthers = LightCatOthers,
    catUnknown = LightCatUnknown
)

val DarkAutoFlowColors = AutoFlowColors(
    bg = DarkBg,
    sheet = DarkSheet,
    ink = DarkInk,
    muted = DarkMuted,
    line = DarkLine,
    brand = DarkBrand,
    tint = DarkTint,
    onTint = DarkOnTint,
    button = DarkButton,
    buttonText = DarkButtonText,
    amberBg = DarkAmberBg,
    amberFg = DarkAmberFg,
    chip = DarkChip,
    hero = DarkHero,
    field = DarkField,
    red = DarkRed,
    bar = DarkBar,
    track = DarkTrack,
    isDark = true,
    catFood = DarkCatFood,
    catGroceries = DarkCatGroceries,
    catTransport = DarkCatTransport,
    catShopping = DarkCatShopping,
    catBills = DarkCatBills,
    catEntertainment = DarkCatEntertainment,
    catHealth = DarkCatHealth,
    catTravel = DarkCatTravel,
    catOthers = DarkCatOthers,
    catUnknown = DarkCatUnknown
)

val LocalAutoFlowColors = staticCompositionLocalOf { LightAutoFlowColors }

@Immutable
data class AppBadgeColors(
    val reviewContainer: Color,
    val onReview: Color,
    val refundContainer: Color,
    val onRefund: Color
)

val LocalAppBadgeColors = staticCompositionLocalOf {
    AppBadgeColors(
        reviewContainer = LightAmberBg,
        onReview = LightAmberFg,
        refundContainer = LightTint,
        onRefund = LightOnTint
    )
}

// Material3 mappings for backwards compatibility with existing screens
private val MaterialLightColorScheme = lightColorScheme(
    primary = LightBrand,
    onPrimary = LightButtonText,
    primaryContainer = LightTint,
    onPrimaryContainer = LightOnTint,
    secondary = LightMuted,
    onSecondary = LightSheet,
    secondaryContainer = LightChip,
    onSecondaryContainer = LightInk,
    background = LightBg,
    onBackground = LightInk,
    surface = LightSheet,
    onSurface = LightInk,
    surfaceVariant = LightChip,
    onSurfaceVariant = LightMuted,
    outline = LightLine,
    outlineVariant = LightLine,
    error = LightRed,
    onError = LightSheet,
    errorContainer = LightAmberBg,
    onErrorContainer = LightAmberFg
)

private val MaterialDarkColorScheme = darkColorScheme(
    primary = DarkBrand,
    onPrimary = DarkButtonText,
    primaryContainer = DarkTint,
    onPrimaryContainer = DarkOnTint,
    secondary = DarkMuted,
    onSecondary = DarkSheet,
    secondaryContainer = DarkChip,
    onSecondaryContainer = DarkInk,
    background = DarkBg,
    onBackground = DarkInk,
    surface = DarkSheet,
    onSurface = DarkInk,
    surfaceVariant = DarkChip,
    onSurfaceVariant = DarkMuted,
    outline = DarkLine,
    outlineVariant = DarkLine,
    error = DarkRed,
    onError = DarkBg,
    errorContainer = DarkAmberBg,
    onErrorContainer = DarkAmberFg
)

// ============================================================================
// TYPOGRAPHY (Plus Jakarta Sans, bundled resources, weights 400..800)
// ============================================================================

@OptIn(ExperimentalTextApi::class)
val PlusJakartaSans = FontFamily(
    Font(
        R.font.plus_jakarta_sans,
        weight = FontWeight.W400,
        variationSettings = FontVariation.Settings(FontVariation.weight(400))
    ),
    Font(
        R.font.plus_jakarta_sans,
        weight = FontWeight.W500,
        variationSettings = FontVariation.Settings(FontVariation.weight(500))
    ),
    Font(
        R.font.plus_jakarta_sans,
        weight = FontWeight.W600,
        variationSettings = FontVariation.Settings(FontVariation.weight(600))
    ),
    Font(
        R.font.plus_jakarta_sans,
        weight = FontWeight.W700,
        variationSettings = FontVariation.Settings(FontVariation.weight(700))
    ),
    Font(
        R.font.plus_jakarta_sans,
        weight = FontWeight.W800,
        variationSettings = FontVariation.Settings(FontVariation.weight(800))
    )
)

val AppTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = PlusJakartaSans,
        fontWeight = FontWeight.W800, // amount large 800
        fontSize = 32.sp,
        lineHeight = 40.sp
    ),
    displayMedium = TextStyle(
        fontFamily = PlusJakartaSans,
        fontWeight = FontWeight.W800, // amount large 800
        fontSize = 28.sp,
        lineHeight = 36.sp
    ),
    headlineLarge = TextStyle(
        fontFamily = PlusJakartaSans,
        fontWeight = FontWeight.W700, // titles 700
        fontSize = 24.sp,
        lineHeight = 32.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = PlusJakartaSans,
        fontWeight = FontWeight.W700, // titles 700
        fontSize = 20.sp,
        lineHeight = 28.sp
    ),
    titleLarge = TextStyle(
        fontFamily = PlusJakartaSans,
        fontWeight = FontWeight.W700, // titles 700
        fontSize = 18.sp,
        lineHeight = 24.sp
    ),
    titleMedium = TextStyle(
        fontFamily = PlusJakartaSans,
        fontWeight = FontWeight.W700, // titles 700
        fontSize = 16.sp,
        lineHeight = 22.sp
    ),
    titleSmall = TextStyle(
        fontFamily = PlusJakartaSans,
        fontWeight = FontWeight.W700, // titles 700
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = PlusJakartaSans,
        fontWeight = FontWeight.W500, // body 500
        fontSize = 16.sp,
        lineHeight = 24.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = PlusJakartaSans,
        fontWeight = FontWeight.W400, // body 400
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    bodySmall = TextStyle(
        fontFamily = PlusJakartaSans,
        fontWeight = FontWeight.W400, // body 400
        fontSize = 12.sp,
        lineHeight = 16.sp
    ),
    labelLarge = TextStyle(
        fontFamily = PlusJakartaSans,
        fontWeight = FontWeight.W600, // labels 600
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    labelMedium = TextStyle(
        fontFamily = PlusJakartaSans,
        fontWeight = FontWeight.W600, // labels 600
        fontSize = 12.sp,
        lineHeight = 16.sp
    ),
    labelSmall = TextStyle(
        fontFamily = PlusJakartaSans,
        fontWeight = FontWeight.W600, // labels 600
        fontSize = 11.sp,
        lineHeight = 14.sp
    )
)

// ============================================================================
// THEME COMPOSABLE & ACCESSOR OBJECT
// ============================================================================

object AutoFlowTheme {
    val colors: AutoFlowColors
        @Composable
        get() = LocalAutoFlowColors.current

    val typography: Typography
        @Composable
        get() = MaterialTheme.typography

    val spacing: AppSpacing = AppSpacing
    val radius: AppRadius = AppRadius
}

@Composable
fun AutoFlowTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val autoFlowColors = if (darkTheme) DarkAutoFlowColors else LightAutoFlowColors
    val materialColorScheme = if (darkTheme) MaterialDarkColorScheme else MaterialLightColorScheme
    val badgeColors = if (darkTheme) {
        AppBadgeColors(
            reviewContainer = DarkAmberBg,
            onReview = DarkAmberFg,
            refundContainer = DarkTint,
            onRefund = DarkOnTint
        )
    } else {
        AppBadgeColors(
            reviewContainer = LightAmberBg,
            onReview = LightAmberFg,
            refundContainer = LightTint,
            onRefund = LightOnTint
        )
    }

    CompositionLocalProvider(
        LocalAutoFlowColors provides autoFlowColors,
        LocalAppBadgeColors provides badgeColors
    ) {
        MaterialTheme(
            colorScheme = materialColorScheme,
            typography = AppTypography,
            content = content
        )
    }
}

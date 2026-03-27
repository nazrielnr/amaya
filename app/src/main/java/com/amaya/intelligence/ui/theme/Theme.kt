package com.amaya.intelligence.ui.theme

import android.app.Activity
import android.graphics.drawable.ColorDrawable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

// --- Monochrome Neutral Palette ---
// Light mode: near-black primary, grey surfaces. Dark mode: near-white primary, dark surfaces.

// ─── Dark Mode Palette ────────────────────────────────────────────────────────
private val DarkPrimary              = Color(0xFFE0E0E0) // near-white — buttons, switches, FAB
private val DarkOnPrimary            = Color(0xFF181818) // dark text on primary
private val DarkPrimaryContainer     = Color(0xFF3A3A3A) // neutral dark container
private val DarkOnPrimaryContainer   = Color(0xFFE0E0E0)

private val DarkSecondary            = Color(0xFFBBBBBB)
private val DarkOnSecondary          = Color(0xFF181818)
private val DarkSecondaryContainer   = Color(0xFF2E2E2E)
private val DarkOnSecondaryContainer = Color(0xFFBBBBBB)

private val DarkTertiary             = Color(0xFF9E9E9E)
private val DarkOnTertiary           = Color(0xFF181818)
private val DarkTertiaryContainer    = Color(0xFF2A2A2A)
private val DarkOnTertiaryContainer  = Color(0xFF9E9E9E)

private val DarkError                = Color(0xFFF2B8B5)
private val DarkOnError              = Color(0xFF601410)
private val DarkErrorContainer       = Color(0xFF8C1D18)
private val DarkOnErrorContainer     = Color(0xFFF9DEDC)

private val DarkBackground           = Color(0xFF1E1E1E) // VS Code dark bg
private val DarkOnBackground         = Color(0xFFE0E0E0)
private val DarkSurface              = Color(0xFF252526) // VS Code dark surface
private val DarkOnSurface            = Color(0xFFE0E0E0)
private val DarkSurfaceVariant       = Color(0xFF2D2D2D) // slightly lighter than surface
private val DarkOnSurfaceVariant     = Color(0xFFAAAAAA)
private val DarkOutline                  = Color(0xFF6E6E6E)
private val DarkOutlineVariant           = Color(0xFF484848)
// surfaceContainer hierarchy — all neutral dark, no purple
private val DarkSurfaceContainerLowest   = Color(0xFF1A1A1A)
private val DarkSurfaceContainerLow      = Color(0xFF202020)
private val DarkSurfaceContainer         = Color(0xFF2A2A2A)
private val DarkSurfaceContainerHigh     = Color(0xFF303030)
private val DarkSurfaceContainerHighest  = Color(0xFF383838)

// ─── Light Mode Palette ───────────────────────────────────────────────────────
private val LightPrimary             = Color(0xFF1C1B1F) // near-black — buttons, switches, FAB
private val LightOnPrimary           = Color(0xFFFFFFFF)
private val LightPrimaryContainer    = Color(0xFFDEDEDE) // neutral grey container
private val LightOnPrimaryContainer  = Color(0xFF1C1B1F)

private val LightSecondary           = Color(0xFF5F5F5F) // medium grey
private val LightOnSecondary         = Color(0xFFFFFFFF)
private val LightSecondaryContainer  = Color(0xFFE5E5E5) // neutral grey container
private val LightOnSecondaryContainer = Color(0xFF1C1B1F)

private val LightTertiary            = Color(0xFF757575) // soft grey
private val LightOnTertiary          = Color(0xFFFFFFFF)
private val LightTertiaryContainer   = Color(0xFFE0E0E0) // neutral grey container
private val LightOnTertiaryContainer = Color(0xFF1C1B1F)

private val LightError               = Color(0xFFB3261E)
private val LightOnError             = Color(0xFFFFFFFF)
private val LightErrorContainer      = Color(0xFFF9DEDC)
private val LightOnErrorContainer    = Color(0xFF410E0B)

private val LightBackground          = Color(0xFFEEEEEE) // #EEEEEE — main background
private val LightOnBackground        = Color(0xFF1C1B1F)
private val LightSurface             = Color(0xFFEEEEEE) // #EEEEEE — same as background
private val LightOnSurface           = Color(0xFF1C1B1F)
private val LightSurfaceVariant      = Color(0xFFF9F9F9) // #F9F9F9 — card / pill surface
private val LightOnSurfaceVariant    = Color(0xFF5F5F5F) // #5F5F5F — icons, secondary text
private val LightOutline                  = Color(0xFFBDBDBD)
private val LightOutlineVariant           = Color(0xFFD8D8D8)
// surfaceContainer hierarchy — all neutral grey, no purple
private val LightSurfaceContainerLowest   = Color(0xFFEEEEEE) // same as background
private val LightSurfaceContainerLow      = Color(0xFFEBEBEB) // slightly darker than bg
private val LightSurfaceContainer         = Color(0xFFE8E8E8) // default container
private val LightSurfaceContainerHigh     = Color(0xFFE4E4E4) // slightly elevated
private val LightSurfaceContainerHighest  = Color(0xFFE0E0E0) // most elevated

private val LightColorScheme: ColorScheme = lightColorScheme(
    primary = LightPrimary,
    onPrimary = LightOnPrimary,
    primaryContainer = LightPrimaryContainer,
    onPrimaryContainer = LightOnPrimaryContainer,
    secondary = LightSecondary,
    onSecondary = LightOnSecondary,
    secondaryContainer = LightSecondaryContainer,
    onSecondaryContainer = LightOnSecondaryContainer,
    tertiary = LightTertiary,
    onTertiary = LightOnTertiary,
    tertiaryContainer = LightTertiaryContainer,
    onTertiaryContainer = LightOnTertiaryContainer,
    error = LightError,
    onError = LightOnError,
    errorContainer = LightErrorContainer,
    onErrorContainer = LightOnErrorContainer,
    background = LightBackground,
    onBackground = LightOnBackground,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    outline = LightOutline,
    outlineVariant = LightOutlineVariant,
    surfaceContainerLowest = LightSurfaceContainerLowest,
    surfaceContainerLow = LightSurfaceContainerLow,
    surfaceContainer = LightSurfaceContainer,
    surfaceContainerHigh = LightSurfaceContainerHigh,
    surfaceContainerHighest = LightSurfaceContainerHighest
)

private val DarkColorScheme: ColorScheme = darkColorScheme(
    primary = DarkPrimary,
    onPrimary = DarkOnPrimary,
    primaryContainer = DarkPrimaryContainer,
    onPrimaryContainer = DarkOnPrimaryContainer,
    secondary = DarkSecondary,
    onSecondary = DarkOnSecondary,
    secondaryContainer = DarkSecondaryContainer,
    onSecondaryContainer = DarkOnSecondaryContainer,
    tertiary = DarkTertiary,
    onTertiary = DarkOnTertiary,
    tertiaryContainer = DarkTertiaryContainer,
    onTertiaryContainer = DarkOnTertiaryContainer,
    error = DarkError,
    onError = DarkOnError,
    errorContainer = DarkErrorContainer,
    onErrorContainer = DarkOnErrorContainer,
    background = DarkBackground,
    onBackground = DarkOnBackground,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    outline = DarkOutline,
    outlineVariant = DarkOutlineVariant,
    surfaceContainerLowest = DarkSurfaceContainerLowest,
    surfaceContainerLow = DarkSurfaceContainerLow,
    surfaceContainer = DarkSurfaceContainer,
    surfaceContainerHigh = DarkSurfaceContainerHigh,
    surfaceContainerHighest = DarkSurfaceContainerHighest
)

// --- Premium Android 16 Shapes ---
// Heavily rounded corners for a luxurious, pill-like modern UI feel
val PremiumShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(24.dp), // Often used for cards
    large = RoundedCornerShape(32.dp),  // Expensive bottom sheets / modal drawers
    extraLarge = RoundedCornerShape(40.dp)
)

// Section and Card specific shapes for consistency
val SectionShape = RoundedCornerShape(14.dp)
val CardShape = RoundedCornerShape(40.dp)

// ─── Amaya Gradients & Design Tokens ──────────────────────────────────────────

@Immutable
class AmayaGradients(
    val iconPalettes: List<Brush>,
    val topScrim: Brush,
    val modalTopScrim: Brush,
    val bottomScrim: Brush
)

val LocalAmayaGradients = staticCompositionLocalOf<AmayaGradients> {
    error("No AmayaGradients provided")
}

@Composable
fun amayaGradients(darkTheme: Boolean): AmayaGradients {
    val bgColor = if (darkTheme) DarkBackground else LightBackground
    
    // 8 Premium Gradient Pairs for Icons
    val palettes = listOf(
        Brush.linearGradient(listOf(Color(0xFF007AFF), Color(0xFF00C7FF))), // Blue
        Brush.linearGradient(listOf(Color(0xFF34C759), Color(0xFF30D158))), // Green
        Brush.linearGradient(listOf(Color(0xFFFF3B30), Color(0xFFFF453A))), // Red
        Brush.linearGradient(listOf(Color(0xFFFF9500), Color(0xFFFFA00A))), // Orange
        Brush.linearGradient(listOf(Color(0xFFAF52DE), Color(0xFFBF5AF2))), // Purple
        Brush.linearGradient(listOf(Color(0xFF5856D6), Color(0xFF5E5CE6))), // Indigo
        Brush.linearGradient(listOf(Color(0xFFFF2D55), Color(0xFFFF375F))), // Pink
        Brush.linearGradient(listOf(Color(0xFF64D2FF), Color(0xFF70D7FF)))  // Cyan
    )

    return AmayaGradients(
        iconPalettes = palettes,
        topScrim = Brush.verticalGradient(
            0.0f to bgColor.copy(alpha = 1.00f),
            0.10f to bgColor.copy(alpha = 0.98f),
            0.20f to bgColor.copy(alpha = 0.95f),
            0.35f to bgColor.copy(alpha = 0.90f),
            0.50f to bgColor.copy(alpha = 0.80f),
            0.60f to bgColor.copy(alpha = 0.70f),
            0.70f to bgColor.copy(alpha = 0.55f),
            0.80f to bgColor.copy(alpha = 0.38f),
            0.88f to bgColor.copy(alpha = 0.25f),
            0.94f to bgColor.copy(alpha = 0.12f),
            0.98f to bgColor.copy(alpha = 0.05f),
            1.0f to Color.Transparent
        ),
        modalTopScrim = Brush.verticalGradient(
            0.0f to bgColor.copy(alpha = 1.00f),
            0.15f to bgColor.copy(alpha = 0.98f),
            0.30f to bgColor.copy(alpha = 0.96f),
            0.45f to bgColor.copy(alpha = 0.93f),
            0.60f to bgColor.copy(alpha = 0.85f),
            0.70f to bgColor.copy(alpha = 0.75f),
            0.80f to bgColor.copy(alpha = 0.60f),
            0.88f to bgColor.copy(alpha = 0.45f),
            0.94f to bgColor.copy(alpha = 0.25f),
            0.98f to bgColor.copy(alpha = 0.10f),
            1.0f to Color.Transparent
        ),
        bottomScrim = Brush.verticalGradient(
            0.0f to Color.Transparent,
            0.05f to bgColor.copy(alpha = 0.05f),
            0.10f to bgColor.copy(alpha = 0.10f),
            0.18f to bgColor.copy(alpha = 0.18f),
            0.28f to bgColor.copy(alpha = 0.30f),
            0.40f to bgColor.copy(alpha = 0.45f),
            0.52f to bgColor.copy(alpha = 0.60f),
            0.65f to bgColor.copy(alpha = 0.75f),
            0.78f to bgColor.copy(alpha = 0.88f),
            0.88f to bgColor.copy(alpha = 0.94f),
            0.95f to bgColor.copy(alpha = 0.97f),
            1.0f to bgColor.copy(alpha = 1.00f)
        )
    )
}

// --- Premium Typography Scale ---
val PremiumTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Light,
        fontSize = 57.sp,
        lineHeight = 64.sp,
        letterSpacing = (-0.25).sp
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp,
        lineHeight = 36.sp,
        letterSpacing = 0.sp
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp
    ),
    titleMedium = TextStyle( // Used prominently in premium lists/headers
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 18.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.15.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp
    ),
    bodyMedium = TextStyle( // Key for settings metadata / secondary text
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.ExtraLight,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.25.sp
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    )
)

@Composable
fun AmayaTheme(
    // darkTheme follows AppCompatDelegate.setDefaultNightMode() automatically via isSystemInDarkTheme()
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val gradients = amayaGradients(darkTheme)

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            // Keep the actual window background in sync with Compose so predictive back
            // and activity transitions never fall through to the system's default black.
            window.setBackgroundDrawable(ColorDrawable(colorScheme.background.toArgb()))
            // status bar is transparent — edge-to-edge
            window.statusBarColor = Color.Transparent.toArgb()
            // navigation bar is transparent — edge-to-edge
            window.navigationBarColor = Color.Transparent.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    CompositionLocalProvider(
        LocalAmayaGradients provides gradients
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = PremiumTypography,
            shapes = PremiumShapes,
            content = content
        )
    }
}

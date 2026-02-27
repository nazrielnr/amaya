package com.amaya.intelligence.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
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

// Dark Mode
private val DarkPrimary             = Color(0xFFE6E0E9) // near-white — buttons, switches, FAB
private val DarkOnPrimary           = Color(0xFF181818) // dark text on primary
private val DarkPrimaryContainer    = Color(0xFF3A3A3A) // elevated container
private val DarkOnPrimaryContainer  = Color(0xFFE6E0E9)

private val DarkSecondary           = Color(0xFFBBBBBB)
private val DarkOnSecondary         = Color(0xFF181818)
private val DarkSecondaryContainer  = Color(0xFF2E2E2E)
private val DarkOnSecondaryContainer= Color(0xFFBBBBBB)

private val DarkTertiary            = Color(0xFF9E9E9E)
private val DarkOnTertiary          = Color(0xFF181818)
private val DarkTertiaryContainer   = Color(0xFF2A2A2A)
private val DarkOnTertiaryContainer = Color(0xFF9E9E9E)

private val DarkError               = Color(0xFFF2B8B5)
private val DarkOnError             = Color(0xFF601410)
private val DarkErrorContainer      = Color(0xFF8C1D18)
private val DarkOnErrorContainer    = Color(0xFFF9DEDC)

private val DarkBackground          = Color(0xFF1E1E1E) // #1E1E1E — VS Code dark background
private val DarkOnBackground        = Color(0xFFE6E0E9)
private val DarkSurface             = Color(0xFF252526) // #252526 — VS Code dark surface (panels)
private val DarkOnSurface           = Color(0xFFE6E0E9)
private val DarkSurfaceVariant      = Color(0xFF2D2D2D) // slightly lighter than surface
private val DarkOnSurfaceVariant    = Color(0xFFCAC4D0)
private val DarkOutline             = Color(0xFF6E6E6E)

// Light Mode
private val LightPrimary            = Color(0xFF1C1B1F) // near-black — buttons, switches, FAB
private val LightOnPrimary          = Color(0xFFFFFFFF)
private val LightPrimaryContainer   = Color(0xFFE0E0E0) // neutral grey container
private val LightOnPrimaryContainer = Color(0xFF1C1B1F)

private val LightSecondary          = Color(0xFF5F5F5F)
private val LightOnSecondary        = Color(0xFFFFFFFF)
private val LightSecondaryContainer = Color(0xFFEEEEEE)
private val LightOnSecondaryContainer = Color(0xFF1C1B1F)

private val LightTertiary           = Color(0xFF757575)
private val LightOnTertiary         = Color(0xFFFFFFFF)
private val LightTertiaryContainer  = Color(0xFFE8E8E8)
private val LightOnTertiaryContainer= Color(0xFF1C1B1F)

private val LightError              = Color(0xFFB3261E)
private val LightOnError            = Color(0xFFFFFFFF)
private val LightErrorContainer     = Color(0xFFF9DEDC)
private val LightOnErrorContainer   = Color(0xFF410E0B)

private val LightBackground         = Color(0xFFFFFFFF) // #FFFFFF — pure white canvas
private val LightOnBackground       = Color(0xFF1C1B1F)
private val LightSurface            = Color(0xFFF3F3F3) // #F3F3F3 — cards, sheets, drawers
private val LightOnSurface          = Color(0xFF1C1B1F)
private val LightSurfaceVariant     = Color(0xFFEEEEEE) // #EEEEEE — pills, secondary containers
private val LightOnSurfaceVariant   = Color(0xFF5F5F5F) // #5F5F5F — leading icons
private val LightOutline            = Color(0xFFBDBDBD)

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
    outline = LightOutline
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
    outline = DarkOutline
)

// --- Premium Android 16 Shapes ---
// Heavily rounded corners for a luxurious, pill-like modern UI feel
val PremiumShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(24.dp), // Often used for cards
    large = RoundedCornerShape(32.dp),  // Expensive bottom sheets / modal drawers
    extraLarge = RoundedCornerShape(40.dp)
)

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
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
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
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = Color.Transparent.toArgb() // Let the TopAppBar draw edge-to-edge
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }
    
    MaterialTheme(
        colorScheme = colorScheme,
        typography = PremiumTypography,
        shapes = PremiumShapes,
        content = content
    )
}

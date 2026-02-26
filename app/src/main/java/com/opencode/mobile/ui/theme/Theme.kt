package com.opencode.mobile.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

// --- Premium Palettes ---
// Using deep, luxurious colors for Dark Mode and crisp, clean colors for Light Mode.
private val PremiumDarkPrimary = Color(0xFF6750A4)
private val PremiumDarkOnPrimary = Color(0xFFFFFFFF)
private val PremiumDarkPrimaryContainer = Color(0xFF4F378B)
private val PremiumDarkOnPrimaryContainer = Color(0xFFEADDFF)

private val PremiumDarkSecondary = Color(0xFFCCC2DC)
private val PremiumDarkOnSecondary = Color(0xFF332D41)
private val PremiumDarkSecondaryContainer = Color(0xFF4A4458)
private val PremiumDarkOnSecondaryContainer = Color(0xFFE8DEF8)

private val PremiumDarkTertiary = Color(0xFFEFB8C8)
private val PremiumDarkOnTertiary = Color(0xFF492532)
private val PremiumDarkTertiaryContainer = Color(0xFF633B48)
private val PremiumDarkOnTertiaryContainer = Color(0xFFFFD8E4)

private val PremiumDarkError = Color(0xFFF2B8B5)
private val PremiumDarkOnError = Color(0xFF601410)
private val PremiumDarkErrorContainer = Color(0xFF8C1D18)
private val PremiumDarkOnErrorContainer = Color(0xFFF9DEDC)

private val PremiumDarkBackground = Color(0xFF181818)
private val PremiumDarkOnBackground = Color(0xFFE6E0E9)
private val PremiumDarkSurface = Color(0xFF181818)
private val PremiumDarkOnSurface = Color(0xFFE6E0E9)
private val PremiumDarkSurfaceVariant = Color(0xFF2A2A2A) // Elevated neutral card feel
private val PremiumDarkOnSurfaceVariant = Color(0xFFCAC4D0)
private val PremiumDarkOutline = Color(0xFF938F99)

// Light Mode Premium palette
private val PremiumLightPrimary = Color(0xFF6750A4)
private val PremiumLightOnPrimary = Color(0xFFFFFFFF)
private val PremiumLightPrimaryContainer = Color(0xFFEADDFF)
private val PremiumLightOnPrimaryContainer = Color(0xFF21005D)

private val PremiumLightSecondary = Color(0xFF625B71)
private val PremiumLightOnSecondary = Color(0xFFFFFFFF)
private val PremiumLightSecondaryContainer = Color(0xFFE8DEF8)
private val PremiumLightOnSecondaryContainer = Color(0xFF1D192B)

private val PremiumLightTertiary = Color(0xFF7D5260)
private val PremiumLightOnTertiary = Color(0xFFFFFFFF)
private val PremiumLightTertiaryContainer = Color(0xFFFFD8E4)
private val PremiumLightOnTertiaryContainer = Color(0xFF31111D)

private val PremiumLightError = Color(0xFFB3261E)
private val PremiumLightOnError = Color(0xFFFFFFFF)
private val PremiumLightErrorContainer = Color(0xFFF9DEDC)
private val PremiumLightOnErrorContainer = Color(0xFF410E0B)

private val PremiumLightBackground = Color(0xFFFFFBFE)
private val PremiumLightOnBackground = Color(0xFF1C1B1F)
private val PremiumLightSurface = Color(0xFFFFFBFE)
private val PremiumLightOnSurface = Color(0xFF1C1B1F)
private val PremiumLightSurfaceVariant = Color(0xFFF0EBF4) // Soft clean elevated surface
private val PremiumLightOnSurfaceVariant = Color(0xFF49454F)
private val PremiumLightOutline = Color(0xFF79747E)


// --- Dynamic Accent Mapping (For Dark Mode '#181818' and Light Mode White) ---
private fun getAccentPrimary(accentName: String, isDark: Boolean): Color = when (accentName) {
    "Blue" -> if (isDark) Color(0xFF8ECAFF) else Color(0xFF0061A4)
    "Green" -> if (isDark) Color(0xFF78DB95) else Color(0xFF006D36)
    "Red" -> if (isDark) Color(0xFFFFB4AB) else Color(0xFFB3261E)
    "Amber" -> if (isDark) Color(0xFFFFBA24) else Color(0xFF7A5900)
    "Purple" -> if (isDark) Color(0xFFD0BCFF) else Color(0xFF6750A4)
    else -> if (isDark) Color(0xFFD0BCFF) else Color(0xFF6750A4)
}

private fun getAccentOnPrimary(accentName: String, isDark: Boolean): Color = when (accentName) {
    "Blue" -> if (isDark) Color(0xFF003258) else Color(0xFFFFFFFF)
    "Green" -> if (isDark) Color(0xFF00391A) else Color(0xFFFFFFFF)
    "Red" -> if (isDark) Color(0xFF690005) else Color(0xFFFFFFFF)
    "Amber" -> if (isDark) Color(0xFF402D00) else Color(0xFFFFFFFF)
    "Purple" -> if (isDark) Color(0xFF381E72) else Color(0xFFFFFFFF)
    else -> if (isDark) Color(0xFF381E72) else Color(0xFFFFFFFF)
}

private fun getAccentPrimaryContainer(accentName: String, isDark: Boolean): Color = when (accentName) {
    "Blue" -> if (isDark) Color(0xFF00497D) else Color(0xFFD1E4FF)
    "Green" -> if (isDark) Color(0xFF005228) else Color(0xFF94F8AE)
    "Red" -> if (isDark) Color(0xFF93000A) else Color(0xFFFFDAD6)
    "Amber" -> if (isDark) Color(0xFF5C4300) else Color(0xFFFFDEAD)
    "Purple" -> if (isDark) Color(0xFF4F378B) else Color(0xFFEADDFF)
    else -> if (isDark) Color(0xFF4F378B) else Color(0xFFEADDFF)
}

private fun getAccentOnPrimaryContainer(accentName: String, isDark: Boolean): Color = when (accentName) {
    "Blue" -> if (isDark) Color(0xFFD1E4FF) else Color(0xFF001D36)
    "Green" -> if (isDark) Color(0xFF94F8AE) else Color(0xFF00210C)
    "Red" -> if (isDark) Color(0xFFFFDAD6) else Color(0xFF410002)
    "Amber" -> if (isDark) Color(0xFFFFDEAD) else Color(0xFF261900)
    "Purple" -> if (isDark) Color(0xFFEADDFF) else Color(0xFF21005D)
    else -> if (isDark) Color(0xFFEADDFF) else Color(0xFF21005D)
}

private fun getDarkColorScheme(accentColor: String): ColorScheme = darkColorScheme(
    primary = getAccentPrimary(accentColor, true),
    onPrimary = getAccentOnPrimary(accentColor, true),
    primaryContainer = getAccentPrimaryContainer(accentColor, true),
    onPrimaryContainer = getAccentOnPrimaryContainer(accentColor, true),
    secondary = PremiumDarkSecondary,
    onSecondary = PremiumDarkOnSecondary,
    secondaryContainer = PremiumDarkSecondaryContainer,
    onSecondaryContainer = PremiumDarkOnSecondaryContainer,
    tertiary = PremiumDarkTertiary,
    onTertiary = PremiumDarkOnTertiary,
    tertiaryContainer = PremiumDarkTertiaryContainer,
    onTertiaryContainer = PremiumDarkOnTertiaryContainer,
    error = PremiumDarkError,
    onError = PremiumDarkOnError,
    errorContainer = PremiumDarkErrorContainer,
    onErrorContainer = PremiumDarkOnErrorContainer,
    background = PremiumDarkBackground,
    onBackground = PremiumDarkOnBackground,
    surface = PremiumDarkSurface,
    onSurface = PremiumDarkOnSurface,
    surfaceVariant = PremiumDarkSurfaceVariant,
    onSurfaceVariant = PremiumDarkOnSurfaceVariant,
    outline = PremiumDarkOutline
)

private fun getLightColorScheme(accentColor: String): ColorScheme = lightColorScheme(
    primary = getAccentPrimary(accentColor, false),
    onPrimary = getAccentOnPrimary(accentColor, false),
    primaryContainer = getAccentPrimaryContainer(accentColor, false),
    onPrimaryContainer = getAccentOnPrimaryContainer(accentColor, false),
    secondary = PremiumLightSecondary,
    onSecondary = PremiumLightOnSecondary,
    secondaryContainer = PremiumLightSecondaryContainer,
    onSecondaryContainer = PremiumLightOnSecondaryContainer,
    tertiary = PremiumLightTertiary,
    onTertiary = PremiumLightOnTertiary,
    tertiaryContainer = PremiumLightTertiaryContainer,
    onTertiaryContainer = PremiumLightOnTertiaryContainer,
    error = PremiumLightError,
    onError = PremiumLightOnError,
    errorContainer = PremiumLightErrorContainer,
    onErrorContainer = PremiumLightOnErrorContainer,
    background = PremiumLightBackground,
    onBackground = PremiumLightOnBackground,
    surface = PremiumLightSurface,
    onSurface = PremiumLightOnSurface,
    surfaceVariant = PremiumLightSurfaceVariant,
    onSurfaceVariant = PremiumLightOnSurfaceVariant,
    outline = PremiumLightOutline
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
fun OpenCodeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    accentColor: String = "Purple",
    dynamicColor: Boolean = false, // Disabled to enforce the premium #181818 palette
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context)
            else dynamicLightColorScheme(context)
        }
        darkTheme -> getDarkColorScheme(accentColor)
        else -> getLightColorScheme(accentColor)
    }
    
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

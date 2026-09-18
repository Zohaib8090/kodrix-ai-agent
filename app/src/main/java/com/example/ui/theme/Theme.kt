package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = LandingPeach,
    onPrimary = Color(0xFF4A1800),
    primaryContainer = Color(0xFF6B2605),
    onPrimaryContainer = Color(0xFFFFDBCF),
    secondary = LandingPeach,
    onSecondary = Color(0xFF1E1E1E),
    tertiary = TertiaryAmber,
    onTertiary = Color.Black,
    tertiaryContainer = Color(0xFF78350F),
    onTertiaryContainer = Color(0xFFFEF3C7),
    background = Color(0xFF161311),
    onBackground = Color(0xFFF7F2EE),
    surface = Color(0xFF211D1A),
    onSurface = Color(0xFFF7F2EE),
    surfaceVariant = Color(0xFF2C2723),
    onSurfaceVariant = Color(0xFFB5ADA6),
    outline = Color(0xFF3F3730),
    error = StatusFailed,
    onError = Color.White
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFFE8590C),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDBCF),
    onPrimaryContainer = LandingHeading,
    secondary = LandingPeach,
    onSecondary = LandingHeading,
    tertiary = TertiaryAmber,
    onTertiary = Color.Black,
    background = LandingBackground,
    onBackground = LandingHeading,
    surface = LandingCardBg,
    onSurface = LandingHeading,
    surfaceVariant = LandingCategoryBg,
    onSurfaceVariant = LandingSubtext,
    outline = LandingBorder,
    error = StatusFailed,
    onError = Color.White
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = false,
    accentColor: String = "Peach",
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val baseColorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val primaryColor = when (accentColor.lowercase()) {
        "blue" -> if (darkTheme) Color(0xFF3B82F6) else Color(0xFF2563EB)
        "purple" -> if (darkTheme) Color(0xFFA855F7) else Color(0xFF9333EA)
        "green" -> if (darkTheme) Color(0xFF10B981) else Color(0xFF059669)
        else -> if (darkTheme) LandingPeach else Color(0xFFE8590C) // Peach
    }
    
    val primaryContainerColor = when (accentColor.lowercase()) {
        "blue" -> if (darkTheme) Color(0xFF1E3A8A) else Color(0xFFDBEAFE)
        "purple" -> if (darkTheme) Color(0xFF581C87) else Color(0xFFF3E8FF)
        "green" -> if (darkTheme) Color(0xFF064E3B) else Color(0xFFD1FAE5)
        else -> if (darkTheme) Color(0xFF6B2605) else Color(0xFFFFDBCF) // Peach
    }

    val colorScheme = baseColorScheme.copy(
        primary = primaryColor,
        primaryContainer = primaryContainerColor
    )

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

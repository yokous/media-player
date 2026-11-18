package com.example.mediaplayer.presentation.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

val darkScheme = darkColorScheme(
    background = Color(0xFF121212),
    onBackground = Color(0xFFF2F2F2),
    surface = Color(0xFF1C1C1C),
    onSurface = Color(0xFFF2F2F2),
    surfaceVariant = Color(0xFF262626),
    onSurfaceVariant = Color(0xFFB3B3B3),
    outline = Color(0xFF3A3A3A),
    primary = Color(0xFFFF5500),
    onPrimary = Color(0xFF111111),
    error = Color(0xFFFF5252),
    onError = Color(0xFF111111)
)

val lightScheme = lightColorScheme(
    background = Color(0xFFF5F5F5),
    onBackground = Color(0xFF111111),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF111111),
    surfaceVariant = Color(0xFFEBEBEB),
    onSurfaceVariant = Color(0xFF555555),
    outline = Color(0xFFD0D0D0),
    primary = Color(0xFFC93C00),
    onPrimary = Color(0xFFFFFFFF),
    error = Color(0xFFC62828),
    onError = Color(0xFFFFFFFF)
)

@Immutable
data class AppColors(
    val success: Color,
    val warning: Color,
    val glassFill: Color,
    val glassStroke: Color
)

val darkAppColors = AppColors(
    success = Color(0xFF69F0AE),
    warning = Color(0xFFFFD740),
    glassFill = Color.White.copy(alpha = 0.10f),
    glassStroke = Color.White.copy(alpha = 0.18f)
)

val lightAppColors = AppColors(
    success = Color(0xFF1B7F3B),
    warning = Color(0xFF8A5A00),
    glassFill = Color.Black.copy(alpha = 0.06f),
    glassStroke = Color.Black.copy(alpha = 0.10f)
)

val LocalAppColors = staticCompositionLocalOf { darkAppColors }
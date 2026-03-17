package org.kryspetrie.fileimport.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.kryspetrie.fileimport.domain.model.AppTheme

private val LightColorScheme =
    lightColorScheme(
        primary = Color(0xFF4A6FA5),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFE8EEF4),
        onPrimaryContainer = Color(0xFF1A3A5C),
        secondary = Color(0xFF5A7A6B),
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFE0EBE5),
        onSecondaryContainer = Color(0xFF1A3D2B),
        tertiary = Color(0xFF7A6B5A),
        onTertiary = Color.White,
        background = Color(0xFFF5F5F7),
        surface = Color.White,
        surfaceVariant = Color(0xFFECECEE),
        onBackground = Color(0xFF1D1D1F),
        onSurface = Color(0xFF1D1D1F),
        onSurfaceVariant = Color(0xFF6E6E73),
        outline = Color(0xFFD1D1D6),
        outlineVariant = Color(0xFFE5E5EA),
        error = Color(0xFFCC3333),
        onError = Color.White)

private val DarkColorScheme =
    darkColorScheme(
        primary = Color(0xFF8AB4E8),
        onPrimary = Color(0xFF0A2A4A),
        primaryContainer = Color(0xFF2C3E50),
        onPrimaryContainer = Color(0xFFD0E4F7),
        secondary = Color(0xFF8BBAA5),
        onSecondary = Color(0xFF0A3020),
        secondaryContainer = Color(0xFF2A4A3A),
        onSecondaryContainer = Color(0xFFD0E4DA),
        tertiary = Color(0xFFA59880),
        onTertiary = Color(0xFF2A2010),
        background = Color(0xFF1E1E1E),
        surface = Color(0xFF2D2D2D),
        surfaceVariant = Color(0xFF3A3A3C),
        onBackground = Color(0xFFE5E5E7),
        onSurface = Color(0xFFE5E5E7),
        onSurfaceVariant = Color(0xFF98989D),
        outline = Color(0xFF48484A),
        outlineVariant = Color(0xFF3A3A3C),
        error = Color(0xFFFF6B6B),
        onError = Color(0xFF1E1E1E))

private val DesktopTypography =
    Typography(
        headlineLarge =
            TextStyle(
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = (-0.5).sp,
                lineHeight = 28.sp),
        headlineMedium =
            TextStyle(fontSize = 18.sp, fontWeight = FontWeight.SemiBold, lineHeight = 24.sp),
        headlineSmall =
            TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Medium, lineHeight = 22.sp),
        titleLarge =
            TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold, lineHeight = 20.sp),
        titleMedium =
            TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium, lineHeight = 18.sp),
        titleSmall =
            TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium, lineHeight = 17.sp),
        bodyLarge = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
        bodyMedium = TextStyle(fontSize = 13.sp, lineHeight = 18.sp),
        bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 16.sp),
        labelLarge =
            TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium, lineHeight = 16.sp),
        labelMedium =
            TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium, lineHeight = 14.sp),
        labelSmall = TextStyle(fontSize = 11.sp, lineHeight = 14.sp))

private val DesktopShapes =
    Shapes(
        extraSmall = RoundedCornerShape(3.dp),
        small = RoundedCornerShape(4.dp),
        medium = RoundedCornerShape(6.dp),
        large = RoundedCornerShape(8.dp),
        extraLarge = RoundedCornerShape(10.dp))

@Composable
fun PetrieTheme(appTheme: AppTheme = AppTheme.SYSTEM, content: @Composable () -> Unit) {
  val darkTheme =
      when (appTheme) {
        AppTheme.LIGHT -> false
        AppTheme.DARK -> true
        AppTheme.SYSTEM -> isSystemInDarkTheme()
      }
  MaterialTheme(
      colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
      typography = DesktopTypography,
      shapes = DesktopShapes,
      content = content)
}

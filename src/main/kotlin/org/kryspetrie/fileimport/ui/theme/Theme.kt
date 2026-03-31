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

/**
 * Light theme color scheme for the Petrie File Importer application.
 *
 * Defines a cohesive set of colors following Material Design 3 guidelines optimized for desktop
 * applications. The color scheme uses a blue primary color with complementary secondary and
 * tertiary colors for a professional, photography-focused aesthetic.
 *
 * ## Color Roles
 * - **Primary**: Main brand color (blue) used for primary actions, selected states
 * - **Secondary**: Supporting color (green) for secondary actions, accents
 * - **Tertiary**: Decorative color (brown) for special elements, highlights
 * - **Background/Surface**: Neutral colors for app background and content areas
 * - **Error**: Red color for error states and destructive actions
 *
 * ## Design Principles
 * - High contrast for readability
 * - Accessible color combinations (WCAG compliant)
 * - Consistent with photography/professional application aesthetics
 * - Subtle tonal variations for depth and hierarchy
 *
 * @see MaterialTheme Material Design theme system
 * @see darkColorScheme Dark theme counterpart
 * @see AppTheme Theme selection enum
 */
private val LightColorScheme =
    lightColorScheme(
        // Primary action color - blue for trust and professionalism
        primary = Color(0xFF4A6FA5),
        // Text/icons on primary background
        onPrimary = Color.White,
        // Light variant for backgrounds, containers
        primaryContainer = Color(0xFFE8EEF4),
        // Text on primary container
        onPrimaryContainer = Color(0xFF1A3A5C),

        // Secondary accent color - green for success, completion
        secondary = Color(0xFF5A7A6B),
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFE0EBE5),
        onSecondaryContainer = Color(0xFF1A3D2B),

        // Tertiary decorative color - brown/earth tone
        tertiary = Color(0xFF7A6B5A),
        onTertiary = Color.White,

        // Application background - light gray
        background = Color(0xFFF5F5F7),
        // Card/surface backgrounds - white
        surface = Color.White,
        // Variant surface for elevation
        surfaceVariant = Color(0xFFECECEE),

        // Text colors
        onBackground = Color(0xFF1D1D1F),
        onSurface = Color(0xFF1D1D1F),
        onSurfaceVariant = Color(0xFF6E6E73),

        // Borders and dividers
        outline = Color(0xFFD1D1D6),
        outlineVariant = Color(0xFFE5E5EA),

        // Error state color
        error = Color(0xFFCC3333),
        onError = Color.White)

/**
 * Dark theme color scheme for the Petrie File Importer application.
 *
 * Defines colors optimized for dark mode, reducing eye strain in low-light environments while
 * maintaining brand identity and accessibility. Dark theme uses lighter versions of the brand
 * colors to ensure sufficient contrast against dark backgrounds.
 *
 * ## Dark Mode Considerations
 * - Avoid pure black (#000000) to reduce OLED smearing and improve aesthetics
 * - Use dark gray (#1E1E1E) for backgrounds
 * - Desaturate colors slightly to reduce visual vibration
 * - Ensure text contrast meets WCAG AA standards
 * - Maintain color relationships from light theme
 *
 * ## Usage
 *
 * Automatically applied when:
 * - User selects dark theme in View menu
 * - System is in dark mode and user selected "System" theme
 *
 * @see LightColorScheme Light theme counterpart
 * @see isSystemInDarkTheme System theme detection
 * @see MaterialTheme.darkColorScheme Material Design dark theme
 */
private val DarkColorScheme =
    darkColorScheme(
        // Lighter blue for dark background contrast
        primary = Color(0xFF8AB4E8),
        onPrimary = Color(0xFF0A2A4A),
        primaryContainer = Color(0xFF2C3E50),
        onPrimaryContainer = Color(0xFFD0E4F7),

        // Lighter green accent
        secondary = Color(0xFF8BBAA5),
        onSecondary = Color(0xFF0A3020),
        secondaryContainer = Color(0xFF2A4A3A),
        onSecondaryContainer = Color(0xFFD0E4DA),

        // Lighter brown accent
        tertiary = Color(0xFFA59880),
        onTertiary = Color(0xFF2A2010),

        // Dark backgrounds
        background = Color(0xFF1E1E1E),
        surface = Color(0xFF2D2D2D),
        surfaceVariant = Color(0xFF3A3A3C),

        // Light text for dark backgrounds
        onBackground = Color(0xFFE5E5E7),
        onSurface = Color(0xFFE5E5E7),
        onSurfaceVariant = Color(0xFF98989D),

        // Subtle borders
        outline = Color(0xFF48484A),
        outlineVariant = Color(0xFF3A3A3C),

        // Error state - brighter red for visibility
        error = Color(0xFFFF6B6B),
        onError = Color(0xFF1E1E1E))

/**
 * Typography scale optimized for desktop applications.
 *
 * Defines text styles for all text elements in the application, from large headlines to small
 * labels. Desktop typography uses larger sizes and different line heights compared to mobile to
 * account for viewing distance and screen size.
 *
 * ## Typography Scale
 * - **Headline**: Large section titles (22sp, 18sp, 16sp)
 * - **Title**: Card/section headers (15sp, 14sp, 13sp)
 * - **Body**: Main content text (14sp, 13sp, 12sp)
 * - **Label**: UI labels, buttons, tabs (13sp, 12sp, 11sp)
 *
 * ## Design Choices
 * - Negative letter-spacing for headlines (tighter, more professional)
 * - Consistent line heights for vertical rhythm
 * - Medium/SemiBold weights for emphasis without bold
 * - Sizes optimized for 1080p+ displays
 *
 * ## Usage
 *
 * Access via `MaterialTheme.typography`:
 * ```kotlin
 * Text("Title", style = MaterialTheme.typography.titleLarge)
 * Text("Body", style = MaterialTheme.typography.bodyMedium)
 * ```
 *
 * @see Typography Material Design typography system
 * @see TextStyle Compose text style definition
 * @see FontWeight Font weight constants
 */
private val DesktopTypography =
    Typography(
        // Large section headers
        headlineLarge =
            TextStyle(
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = (-0.5).sp, // Tighter tracking for headlines
                lineHeight = 28.sp),
        // Medium section headers
        headlineMedium =
            TextStyle(fontSize = 18.sp, fontWeight = FontWeight.SemiBold, lineHeight = 24.sp),
        // Small section headers
        headlineSmall =
            TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Medium, lineHeight = 22.sp),
        // Card titles, dialog titles
        titleLarge =
            TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold, lineHeight = 20.sp),
        // Section titles
        titleMedium =
            TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium, lineHeight = 18.sp),
        // Small titles
        titleSmall =
            TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium, lineHeight = 17.sp),
        // Main content text
        bodyLarge = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
        // Standard body text
        bodyMedium = TextStyle(fontSize = 13.sp, lineHeight = 18.sp),
        // Small print, captions
        bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 16.sp),
        // Button labels, tab labels (large)
        labelLarge =
            TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium, lineHeight = 16.sp),
        // Standard labels
        labelMedium =
            TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium, lineHeight = 14.sp),
        // Small labels, hints
        labelSmall = TextStyle(fontSize = 11.sp, lineHeight = 14.sp))

/**
 * Shape definitions for UI components.
 *
 * Defines corner radius values for all rounded elements in the application, from buttons to cards
 * to dialogs. Uses a consistent scale based on Material Design 3 guidelines.
 *
 * ## Shape Scale
 * - **extraSmall** (3dp): Small chips, tags
 * - **small** (4dp): Buttons, text fields
 * - **medium** (6dp): Cards, dialogs
 * - **large** (8dp): Large cards, sheets
 * - **extraLarge** (10dp): Full-screen dialogs, modals
 *
 * ## Design Rationale
 * - Subtle rounding (not fully rounded like some mobile apps)
 * - Professional appearance appropriate for desktop
 * - Consistent with macOS/Windows design languages
 * - Improves visual hierarchy and grouping
 *
 * ## Usage
 *
 * Access via `MaterialTheme.shapes`:
 * ```kotlin
 * Surface(shape = MaterialTheme.shapes.medium) { }
 * Button(shape = MaterialTheme.shapes.small) { }
 * ```
 *
 * @see Shapes Material Design shape system
 * @see RoundedCornerShape Compose shape definition
 */
private val DesktopShapes =
    Shapes(
        extraSmall = RoundedCornerShape(3.dp),
        small = RoundedCornerShape(4.dp),
        medium = RoundedCornerShape(6.dp),
        large = RoundedCornerShape(8.dp),
        extraLarge = RoundedCornerShape(10.dp))

/**
 * Application theme composable that applies the Petrie File Importer theme.
 *
 * This is the root theme wrapper that should surround all UI content. It applies:
 * - Color scheme (light or dark based on user preference)
 * - Typography scale (desktop-optimized text styles)
 * - Shape definitions (consistent corner radius)
 * - Material Design 3 design tokens
 *
 * ## Theme Selection
 *
 * Supports three theme modes via [AppTheme]:
 * - **LIGHT**: Always use light color scheme
 * - **DARK**: Always use dark color scheme
 * - **SYSTEM**: Follow operating system theme setting
 *
 * ## Usage
 *
 * Wrap your entire UI tree with this composable:
 * ```kotlin
 * @Composable
 * fun App() {
 *     PetrieTheme(AppTheme.DARK) {
 *         // All UI content here
 *         Surface {
 *             Text("Themed content")
 *         }
 *     }
 * }
 * ```
 *
 * ## Recomposition
 *
 * When the theme changes, all composables within this theme will recompose with the new colors,
 * typography, and shapes. This is automatic - no manual refresh needed.
 *
 * ## Material Theme Access
 *
 * Child composables can access theme values via:
 * - `MaterialTheme.colorScheme.primary` - Colors
 * - `MaterialTheme.typography.bodyLarge` - Text styles
 * - `MaterialTheme.shapes.medium` - Shapes
 *
 * @param appTheme Theme mode selection (light/dark/system). Changes to this parameter trigger theme
 *   recomposition.
 * @param content The UI content to which the theme applies. Should contain the entire application
 *   UI tree.
 * @see AppTheme Theme selection enum
 * @see LightColorScheme Light theme colors
 * @see DarkColorScheme Dark theme colors
 * @see MaterialTheme Material Design theme provider
 * @see isSystemInDarkTheme System theme detection
 */
@Composable
fun PetrieTheme(
    /**
     * Theme preference setting.
     *
     * Determines which color scheme to apply:
     * - [AppTheme.LIGHT] → [LightColorScheme]
     * - [AppTheme.DARK] → [DarkColorScheme]
     * - [AppTheme.SYSTEM] → Based on OS setting via [isSystemInDarkTheme]
     *
     * Typically comes from persisted user settings.
     */
    appTheme: AppTheme = AppTheme.SYSTEM,

    /**
     * UI content to theme.
     *
     * This lambda contains all UI composables that should receive the theme. Typically includes the
     * entire application UI tree.
     *
     * Example:
     * ```kotlin
     * PetrieTheme {
     *     Surface {
     *         Column {
     *             Text("Themed content")
     *             Button { }
     *         }
     *     }
     * }
     * ```
     */
    content: @Composable () -> Unit
) {
  // Determine if dark theme should be used
  // Respects user preference and system theme
  val darkTheme =
      when (appTheme) {
        AppTheme.LIGHT -> false // Force light theme
        AppTheme.DARK -> true // Force dark theme
        AppTheme.SYSTEM -> isSystemInDarkTheme() // Follow OS setting
      }

  // Apply Material Theme with our customizations
  // All child composables can access colors, typography, shapes via MaterialTheme
  MaterialTheme(
      // Color scheme based on theme mode
      colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
      // Desktop-optimized typography
      typography = DesktopTypography,
      // Consistent shape definitions
      shapes = DesktopShapes,
      // Content that receives the theme
      content = content)
}

package org.kryspetrie.fileimport.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color

/**
 * Semantic color tokens for the PhotoImporter application.
 *
 * Theme-aware getters read from [MaterialTheme.colorScheme] where a matching role exists.
 * App-specific tokens (warning, success, modified indicator, auto-orient) keep fixed values.
 *
 * For static contexts (non-Composable), use [Light] or [Dark].
 */
object DefaultColors {

    // ── Brand Colors ──────────────────────────────────────────

    /** Primary brand color for main actions, selected states. */
    val primary: Color
        @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.primary

    /** Text/icons on primary background. */
    val onPrimary: Color
        @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.onPrimary

    /** Light variant for primary containers/backgrounds. */
    val primaryContainer: Color
        @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.primaryContainer

    /** Text on primary container. */
    val onPrimaryContainer: Color
        @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.onPrimaryContainer

    /** Secondary accent color for completion, success states. */
    val secondary: Color
        @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.secondary

    /** Tertiary decorative color for highlights. */
    val tertiary: Color
        @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.tertiary

    // ── Status Colors ─────────────────────────────────────────

    /** Error color for destructive actions, validation errors. */
    val error: Color
        @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.error

    /** Warning color for caution states. */
    val warning: Color
        @Composable @ReadOnlyComposable get() = Color(0xFFCC9933)

    /** Success color for completed actions. */
    val success: Color
        @Composable @ReadOnlyComposable get() = Color(0xFF5A7A6B)

    // ── Surface Colors ────────────────────────────────────────

    /** Main app background. */
    val background: Color
        @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.background

    /** Card/surface background. */
    val cardBackground: Color
        @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.surface

    /** Elevated surface background. */
    val surfaceVariant: Color
        @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.surfaceVariant

    // ── Text Colors ────────────────────────────────────────────

    /** Primary text on surface. */
    val textPrimary: Color
        @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.onSurface

    /** Secondary/muted text. */
    val textSecondary: Color
        @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.onSurfaceVariant

    /** Text on primary background. */
    val textOnPrimary: Color
        @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.onPrimary

    // ── Border/Divider Colors ─────────────────────────────────

    /** Standard border color. */
    val border: Color
        @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.outline

    /** Light variant border color. */
    val borderLight: Color
        @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.outlineVariant

    // ── Modified Item Indicator ────────────────────────────────

    /** Indicator dot for modified-but-unsaved items. */
    val modifiedIndicator: Color
        @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.error

    // ── Auto-Orient Indicator ─────────────────────────────────

    /** Background color for auto-orient badge. */
    val autoOrientBackground: Color
        @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.primaryContainer

    // ── Static light/dark palettes for non-Composable contexts ─

    /** Static light mode colors for preview/testing. */
    object Light {
        val primary = Color(0xFF4A6FA5)
        val onPrimary = Color.White
        val primaryContainer = Color(0xFFE8EEF4)
        val onPrimaryContainer = Color(0xFF1A3A5C)
        val error = Color(0xFFCC3333)
        val warning = Color(0xFFCC9933)
        val success = Color(0xFF5A7A6B)
        val background = Color(0xFFF5F5F7)
        val cardBackground = Color.White
        val textPrimary = Color(0xFF1D1D1F)
        val textSecondary = Color(0xFF6E6E73)
    }

    /** Static dark mode colors for preview/testing. */
    object Dark {
        val primary = Color(0xFF8AB4E8)
        val onPrimary = Color(0xFF0A2A4A)
        val primaryContainer = Color(0xFF2C3E50)
        val onPrimaryContainer = Color(0xFFD0E4F7)
        val error = Color(0xFFFF6B6B)
        val warning = Color(0xFFFFCC66)
        val success = Color(0xFF8BBAA5)
        val background = Color(0xFF1E1E1E)
        val cardBackground = Color(0xFF2D2D2D)
        val textPrimary = Color(0xFFE5E5E7)
        val textSecondary = Color(0xFF98989D)
    }
}

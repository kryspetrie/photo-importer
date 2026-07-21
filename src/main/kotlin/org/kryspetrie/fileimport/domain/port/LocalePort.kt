package org.kryspetrie.fileimport.domain.port

import kotlinx.coroutines.flow.StateFlow
import org.kryspetrie.fileimport.domain.model.i18n.LocaleConfig
import org.kryspetrie.fileimport.domain.model.i18n.StringKey

/**
 * Port for localization/i18n string resolution.
 *
 * Provides translated strings for all user-facing text in the application. Locale files are loaded
 * from the classpath (`resources/i18n/`) and can be overridden by user files in
 * `~/.petrie-importer/i18n/`.
 *
 * ## Fallback Chain
 *
 * When a key is not found in the current locale, the fallback chain is:
 * 1. Requested locale (e.g., "de")
 * 2. Fallback locale ("en")
 * 3. Key name with underscores replaced by spaces (e.g., "IMPORT_TITLE" → "Import title")
 *
 * This ensures the app never crashes due to a missing translation.
 *
 * ## Parameter Substitution
 *
 * Strings can contain `{param}` placeholders that are replaced at runtime:
 * ```kotlin
 * localePort.t(StringKey.IMPORT_PROGRESS_IMPORTING, "current" to "5", "total" to "47")
 * // → "Importing 5 of 47..."
 * ```
 *
 * ## Usage in Composables
 *
 * ```kotlin
 * val s = strings()  // from LocalStrings composition local
 * Text(s.t(StringKey.IMPORT_TITLE))
 * Text(s.t(StringKey.IMPORT_PROGRESS_IMPORTING, "current" to "5", "total" to "47"))
 * ```
 */
interface LocalePort {
    /**
     * Returns the translated string for the given key, with parameter substitution.
     *
     * Parameters are provided as pairs of (name, value). The string `{name}` in the translation
     * will be replaced with `value`.
     *
     * Example: `t(StringKey.IMPORT_PROGRESS_IMPORTING, "current" to "5", "total" to "47")`
     */
    fun t(key: StringKey, vararg params: Pair<String, String>): String

    /** Returns the current locale configuration. */
    fun config(): LocaleConfig

    /** Returns all available locale codes (e.g., ["en", "de", "ja"]). */
    fun availableLocales(): Set<String>

    /**
     * Sets the current locale and loads its strings.
     *
     * If the locale file cannot be loaded, falls back to the current locale and returns false.
     */
    suspend fun setLocale(localeCode: String): Boolean

    /** Observes the current locale as a StateFlow for reactive UI updates. */
    fun observeLocale(): StateFlow<String>

    /** Returns the native display name for a locale code (e.g., "Deutsch" for "de"). */
    fun nativeLocaleName(localeCode: String): String
}
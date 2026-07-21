package org.kryspetrie.fileimport.domain.model.i18n

import kotlinx.serialization.Serializable

/**
 * Locale configuration for the application.
 *
 * @property currentLocale The active locale code (e.g., "en", "de", "ja").
 * @property fallbackLocale The locale to fall back to when a key is missing in the current locale.
 *   Always defaults to "en" since English translations are the canonical source.
 */
@Serializable
data class LocaleConfig(
    val currentLocale: String = "en",
    val fallbackLocale: String = "en",
)
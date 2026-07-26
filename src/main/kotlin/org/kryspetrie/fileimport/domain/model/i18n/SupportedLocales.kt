package org.kryspetrie.fileimport.domain.model.i18n

/** Bundled and RTL locale codes shipped with the application. */
object SupportedLocales {
    /** Locale files under `resources/i18n/{code}.json`. */
    val bundled: Set<String> =
        setOf(
            "en",
            "de",
            "zh",
            "ja",
            "es",
            "fr",
            "pt",
            "nl",
            "sv",
            "ru",
            "hi",
            "ar",
            "ko",
            "bn",
            "id",
            "ur",
            "tr",
            "vi",
            "it",
        )

    /** Locales that use right-to-left layout. */
    val rtl: Set<String> = setOf("ar", "ur")
}

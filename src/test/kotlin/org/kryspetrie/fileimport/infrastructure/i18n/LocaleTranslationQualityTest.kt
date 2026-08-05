package org.kryspetrie.fileimport.infrastructure.i18n

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.kryspetrie.fileimport.domain.model.i18n.SupportedLocales

@DisplayName("Locale translation quality")
class LocaleTranslationQualityTest {

    private val uiKeys =
        listOf(
            "SETTINGS_VERIFY_COPIES_DESC",
            "SETTINGS_DELETE_SOURCE_DESC",
            "APP_KS_TABS",
            "APP_KS_ENTER_SUBMIT",
            "APP_KS_WIZARD_HELP_HINT",
            "META_KS_EDITING",
            "META_KS_PREV_NEXT",
            "META_KS_SAVE",
            "META_KS_LOCATION",
            "META_KS_FACES",
            "META_KS_KEYWORDS",
            "META_KS_BROWSER",
            "META_KS_APPLY_MULTI",
        )

    @Test
    @DisplayName("Phase 3/4 UI keys are not English copy-paste in bundled locales")
    fun phase3And4KeysAreTranslated() {
        val en = loadLocale("en")
        val nonEnglish = SupportedLocales.bundled - "en"

        nonEnglish.forEach { locale ->
            val strings = loadLocale(locale)
            uiKeys.forEach { key ->
                assertThat(strings[key])
                    .describedAs("$locale translation for $key")
                    .isNotBlank()
                    .isNotEqualTo(en[key])
            }
        }
    }

    private fun loadLocale(locale: String): Map<String, String> {
        val resource = "/i18n/$locale.json"
        val stream =
            checkNotNull(javaClass.getResourceAsStream(resource)) {
                "Missing locale resource: $resource"
            }
        @Suppress("UNCHECKED_CAST")
        return kotlinx.serialization.json.Json.decodeFromString<Map<String, String>>(
            stream.bufferedReader().readText()
        )
    }
}

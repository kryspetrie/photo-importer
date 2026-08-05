package org.kryspetrie.fileimport.infrastructure.i18n

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.kryspetrie.fileimport.domain.model.i18n.StringKey
import org.kryspetrie.fileimport.domain.model.i18n.SupportedLocales
import org.kryspetrie.fileimport.domain.port.DispatcherProvider

@DisplayName("Locale completeness")
class LocaleCompletenessTest {

    private lateinit var adapter: JsonLocaleAdapter
    private val testDispatcher =
        object : DispatcherProvider {
            override val io = Dispatchers.Unconfined
            override val default = Dispatchers.Unconfined
        }

    @BeforeEach
    fun setup() {
        adapter = JsonLocaleAdapter(dispatchers = testDispatcher, appLogger = null)
    }

    @Test
    @DisplayName("every bundled locale resolves all StringKeys")
    fun everyStringKeyHasTranslations() = runTest {
        for (locale in SupportedLocales.bundled) {
            adapter.setLocale(locale)
            StringKey.entries.forEach { key ->
                val value = adapter.t(key)
                assertThat(value)
                    .describedAs("$locale translation for $key")
                    .isNotBlank()
                    .isNotEqualTo(key.name.lowercase().replace('_', ' '))
            }
        }
    }

    @Test
    @DisplayName("locale JSON files contain only known StringKey entries")
    fun localeFilesContainOnlyKnownKeys() {
        val enumKeys = StringKey.entries.map { it.name }.toSet()
        SupportedLocales.bundled.forEach { locale ->
            val jsonKeys = loadLocaleKeys(locale)
            assertThat(jsonKeys - enumKeys).describedAs("unknown keys in $locale.json").isEmpty()
            assertThat(enumKeys - jsonKeys).describedAs("missing keys in $locale.json").isEmpty()
        }
    }

    @Test
    @DisplayName("availableLocales includes all bundled locales")
    fun availableLocalesIncludesBundled() {
        assertThat(adapter.availableLocales()).containsAll(SupportedLocales.bundled)
    }

    private fun loadLocaleKeys(locale: String): Set<String> {
        val resource = "/i18n/$locale.json"
        val stream =
            checkNotNull(javaClass.getResourceAsStream(resource)) {
                "Missing locale resource: $resource"
            }
        val content = stream.bufferedReader().readText()
        return Regex("\"([A-Z0-9_]+)\"\\s*:").findAll(content).map { it.groupValues[1] }.toSet()
    }
}

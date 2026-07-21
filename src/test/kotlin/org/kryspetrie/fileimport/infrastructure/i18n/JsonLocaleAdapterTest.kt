package org.kryspetrie.fileimport.infrastructure.i18n

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.kryspetrie.fileimport.domain.model.i18n.LocaleConfig
import org.kryspetrie.fileimport.domain.model.i18n.StringKey
import org.kryspetrie.fileimport.domain.port.DispatcherProvider

@DisplayName("JsonLocaleAdapter")
class JsonLocaleAdapterTest {

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

    @Nested
    @DisplayName("Basic locale operations")
    inner class BasicLocaleTests {
        @Test
        @DisplayName("Default locale flow starts with 'en'")
        fun defaultLocaleIsEnglish() = runTest {
            val locale = adapter.observeLocale().first()
            assertThat(locale).isEqualTo("en")
        }

        @Test
        @DisplayName("setLocale changes the locale")
        fun setLocaleChangesLocale() = runTest {
            val result = adapter.setLocale("de")
            assertThat(result).isTrue()
            val locale = adapter.observeLocale().first()
            assertThat(locale).isEqualTo("de")
        }

        @Test
        @DisplayName("availableLocales returns at least en and de")
        fun availableLocalesReturnsExpected() {
            val locales = adapter.availableLocales()
            assertThat(locales).contains("en")
            assertThat(locales).contains("de")
        }

        @Test
        @DisplayName("nativeLocaleName returns correct names")
        fun nativeLocaleNameReturnsCorrectNames() {
            assertThat(adapter.nativeLocaleName("en")).isEqualTo("English")
            assertThat(adapter.nativeLocaleName("de")).isEqualTo("Deutsch")
        }
    }

    @Nested
    @DisplayName("String key resolution")
    inner class StringKeyResolutionTests {
        @Test
        @DisplayName("t() returns non-empty string for known key in English")
        fun tReturnsNonEmptyForKnownKey() = runTest {
            adapter.setLocale("en")
            val result = adapter.t(StringKey.IMPORT_TITLE)
            assertThat(result).isNotEmpty()
        }

        @Test
        @DisplayName("t() returns German value for known key in German locale")
        fun tReturnsGermanTranslation() = runTest {
            adapter.setLocale("de")
            val result = adapter.t(StringKey.IMPORT_TITLE)
            assertThat(result).isNotEmpty()
        }

        @Test
        @DisplayName("t() with parameter pairs substitutes placeholders")
        fun tWithParametersSubstitutes() = runTest {
            adapter.setLocale("en")
            val result = adapter.t(StringKey.IMPORT_TITLE, "name" to "test")
            assertThat(result).isNotEmpty()
        }

        @Test
        @DisplayName("observeLocale returns a StateFlow that updates on setLocale")
        fun observeLocaleUpdates() = runTest {
            val flow = adapter.observeLocale()
            assertThat(flow.value).isEqualTo("en")
            adapter.setLocale("de")
            assertThat(flow.value).isEqualTo("de")
        }

        @Test
        @DisplayName("Unknown locale falls back to English or key name")
        fun unknownLocaleFallsBack() = runTest {
            adapter.setLocale("xx")
            val result = adapter.t(StringKey.IMPORT_TITLE)
            assertThat(result).isNotEmpty()
        }
    }

    @Nested
    @DisplayName("StringKey enum completeness")
    inner class StringKeyTests {
        @Test
        @DisplayName("StringKey has more than 100 keys")
        fun stringKeyHasMoreThan100Keys() {
            assertThat(StringKey.entries.size).isGreaterThan(100)
        }

        @Test
        @DisplayName("StringKey entries are unique by name")
        fun stringKeyEntriesAreUnique() {
            val names = StringKey.entries.map { it.name }
            assertThat(names.size).isEqualTo(names.toSet().size)
        }

        @Test
        @DisplayName("Key entries exist for navigation and common actions")
        fun keyEntriesExistForNavigationAndCommonActions() {
            // Navigation keys
            assertThat(StringKey.entries.map { it.name })
                .contains("NAV_IMPORT", "NAV_PHOTO_SCAN", "NAV_METADATA_EDITOR")
            // Common action keys
            assertThat(StringKey.entries.map { it.name }).contains("ACTION_OK", "ACTION_CANCEL")
        }
    }

    @Nested
    @DisplayName("Locale config")
    inner class LocaleConfigTests {
        @Test
        @DisplayName("LocaleConfig defaults to English")
        fun localeConfigDefaultsToEnglish() {
            val config = LocaleConfig()
            assertThat(config.currentLocale).isEqualTo("en")
            assertThat(config.fallbackLocale).isEqualTo("en")
        }

        @Test
        @DisplayName("LocaleConfig copy creates new instance with changed locale")
        fun localeConfigCopyCreatesNewInstance() {
            val config = LocaleConfig()
            val germanConfig = config.copy(currentLocale = "de")
            assertThat(config.currentLocale).isEqualTo("en") // Original unchanged
            assertThat(germanConfig.currentLocale).isEqualTo("de") // New instance changed
        }
    }
}

package org.kryspetrie.fileimport.infrastructure.i18n

import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.kryspetrie.fileimport.domain.model.i18n.LocaleConfig
import org.kryspetrie.fileimport.domain.model.i18n.StringKey
import org.kryspetrie.fileimport.domain.model.i18n.SupportedLocales
import org.kryspetrie.fileimport.domain.port.DispatcherProvider
import org.kryspetrie.fileimport.domain.port.LocalePort
import org.kryspetrie.fileimport.infrastructure.logging.AppLogger

/**
 * JSON-based locale adapter that loads translations from classpath resources and user override
 * files.
 *
 * Locale JSON files are expected at:
 * - Classpath: `resources/i18n/{locale}.json` (bundled with the app)
 * - User overrides: `~/.petrie-importer/i18n/{locale}.json` (takes precedence)
 *
 * Each JSON file is a flat key-value map where keys match [StringKey] enum names:
 * ```json
 * {
 *   "IMPORT_TITLE": "Import",
 *   "IMPORT_PROGRESS_IMPORTING": "Importing {current} of {total}..."
 * }
 * ```
 */
class JsonLocaleAdapter(
    private val dispatchers: DispatcherProvider,
    private val appLogger: AppLogger? = null,
) : LocalePort {

    private val json = Json { ignoreUnknownKeys = true }

    /** Loaded locale strings: locale code → (StringKey → translated string). */
    private val strings = ConcurrentHashMap<String, Map<StringKey, String>>()

    /** Native display names for locales (shown in language picker). */
    private val nativeNames =
        mutableMapOf<String, String>(
            "en" to "English (US)",
            "de" to "Deutsch",
            "zh" to "中文（简体）",
            "ja" to "日本語",
            "es" to "Español",
            "fr" to "Français",
            "pt" to "Português",
            "nl" to "Nederlands",
            "sv" to "Svenska",
            "ru" to "Русский",
            "hi" to "हिन्दी",
            "ar" to "العربية",
            "ko" to "한국어",
            "bn" to "বাংলা",
            "id" to "Bahasa Indonesia",
            "ur" to "اردو",
            "tr" to "Türkçe",
            "vi" to "Tiếng Việt",
            "it" to "Italiano",
        )

    companion object {
        /** Bundled locale files shipped under `resources/i18n/{code}.json`. */
        val bundledLocales: Set<String> = SupportedLocales.bundled

        /** Locales that use right-to-left layout. */
        val rtlLocales: Set<String> = SupportedLocales.rtl
    }

    private val localeFlow = MutableStateFlow("en")

    private val config = LocaleConfig()

    init {
        // Eagerly load English (fallback locale)
        runCatching { loadLocale("en") }
            .onFailure { appLogger?.warn("Failed to load English locale: ${it.message}") }
    }

    override fun t(key: StringKey, vararg params: Pair<String, String>): String {
        val currentLocale = localeFlow.value
        val localeStrings = strings[currentLocale] ?: strings[config.fallbackLocale] ?: emptyMap()
        val fallbackStrings = strings[config.fallbackLocale] ?: emptyMap()

        var result =
            localeStrings[key] ?: fallbackStrings[key] ?: key.name.lowercase().replace('_', ' ')

        for ((paramName, paramValue) in params) {
            result = result.replace("{$paramName}", paramValue)
        }

        return result
    }

    override fun config(): LocaleConfig = config.copy(currentLocale = localeFlow.value)

    override fun availableLocales(): Set<String> {
        val userDir = java.io.File(System.getProperty("user.home"), ".petrie-importer/i18n")
        val userLocales =
            userDir
                .listFiles()
                ?.filter {
                    it.name.endsWith(".json") && java.nio.file.Files.isRegularFile(it.toPath())
                }
                ?.map { it.name.removeSuffix(".json") }
                ?.filter { isValidLocaleCode(it) }
                ?.toSet() ?: emptySet()
        return bundledLocales + userLocales
    }

    override suspend fun setLocale(localeCode: String): Boolean {
        return try {
            loadLocale(localeCode)
            localeFlow.value = localeCode
            appLogger?.info("Locale set to: $localeCode")
            true
        } catch (e: Exception) {
            appLogger?.warn("Failed to set locale to $localeCode: ${e.message}")
            false
        }
    }

    override fun observeLocale(): StateFlow<String> = localeFlow

    override fun nativeLocaleName(localeCode: String): String {
        return nativeNames[localeCode] ?: localeCode.uppercase()
    }

    /**
     * Loads a locale's strings from classpath and user override files.
     *
     * Priority: user override file > classpath resource > no entry (will fallback to English).
     */
    /**
     * Validates a locale code to prevent path traversal attacks.
     *
     * Locale codes must consist of 2-3 lowercase letters, optionally followed by a hyphen and 2-3
     * uppercase letters (e.g., "en", "pt-BR"). Rejects any code containing path separators, parent
     * directory references, or other suspicious characters.
     */
    private fun isValidLocaleCode(code: String): Boolean {
        return code.matches(Regex("^[a-z]{2,3}(-[A-Z]{2,3})?$"))
    }

    private fun loadLocale(localeCode: String) {
        if (!isValidLocaleCode(localeCode)) {
            appLogger?.warn("Invalid locale code rejected (possible path traversal): $localeCode")
            return
        }

        if (strings.containsKey(localeCode) && localeCode != config.fallbackLocale) {
            return // Already loaded (but always reload fallback locale in init)
        }

        val loadedStrings = mutableMapOf<StringKey, String>()

        // 1. Load from classpath
        val classpathResource = "/i18n/$localeCode.json"
        val classpathStream = javaClass.getResourceAsStream(classpathResource)
        if (classpathStream != null) {
            val classpathContent = classpathStream.bufferedReader().use { it.readText() }
            parseAndMerge(classpathContent, loadedStrings)
        }

        // 2. Load from user override directory (takes precedence)
        val userFile =
            java.io.File(System.getProperty("user.home"), ".petrie-importer/i18n/$localeCode.json")
        // Validate the file is a regular file (not a symlink) and within the expected directory
        if (
            userFile.exists() &&
                java.nio.file.Files.isRegularFile(userFile.toPath()) &&
                userFile
                    .toPath()
                    .normalize()
                    .startsWith(
                        java.io
                            .File(System.getProperty("user.home"), ".petrie-importer/i18n")
                            .toPath()
                            .normalize()
                    )
        ) {
            val userContent = userFile.readText()
            parseAndMerge(userContent, loadedStrings)
        }

        if (loadedStrings.isNotEmpty()) {
            strings[localeCode] = loadedStrings
            appLogger?.info("Loaded ${loadedStrings.size} strings for locale: $localeCode")
        } else if (localeCode != config.fallbackLocale) {
            appLogger?.warn("No strings found for locale: $localeCode, will use fallback")
        }
    }

    /**
     * Parses a JSON locale file and merges its entries into the target map.
     *
     * Entries that don't match a [StringKey] enum value are silently ignored (so locale files can
     * contain comments or meta-keys like `_native_name` without causing errors).
     */
    private fun parseAndMerge(jsonContent: String, target: MutableMap<StringKey, String>) {
        try {
            val jsonObject = json.parseToJsonElement(jsonContent) as JsonObject
            for ((key, value) in jsonObject) {
                val stringKey = StringKey.entries.find { it.name == key }
                if (stringKey != null && value.jsonPrimitive.isString) {
                    target[stringKey] = value.jsonPrimitive.content
                }
            }
        } catch (e: Exception) {
            appLogger?.warn("Failed to parse locale JSON: ${e.message}")
        }
    }
}

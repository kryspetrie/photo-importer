# Petrie Image Importer — Localization (i18n) Design

> **Status: implemented.** This is the original design proposal; the phased plan in section 3 has been
> carried out, so the per-file string counts there refer to files that have since been split or renamed
> (for example `EditSections.kt`). The architecture described in section 2 still matches the code.

## 1. Design Goals
- Support multiple languages via simple JSON text files that non-developers can create
- No code changes required to add a new language
- Fallback chain: requested locale → default language (English) → key name
- Hot-reloadable during development (swap locale files without rebuild)
- Format-number, date, and plural support
- Right-to-left (RTL) language support
- Minimum performance overhead (pre-loaded string maps, not runtime file reads)

## 2. Architecture

### 2.1 StringKey Enum
Each user-facing string gets a key in an enum. The enum is the single source of truth for what strings exist. Adding a new string means adding an enum entry + the English translation.

```kotlin
// domain/model/i18n/StringKey.kt
enum class StringKey {
    // -- App --
    APP_NAME,
    APP_SHORT_DESCRIPTION,
    
    // -- Navigation --
    NAV_IMPORT,
    NAV_PHOTO_SCAN,
    NAV_METADATA_EDITOR,
    NAV_REORGANIZE,
    NAV_DUPLICATES,
    
    // -- Import Screen --
    IMPORT_TITLE,
    IMPORT_SOURCE_LABEL,
    IMPORT_DESTINATION_LABEL,
    IMPORT_START_BUTTON,
    IMPORT_CANCEL_BUTTON,
    IMPORT_PROGRESS_SCANNING,
    IMPORT_PROGRESS_IMPORTING,
    IMPORT_PROGRESS_COMPLETE,
    IMPORT_SETTINGS_AUTO_ORIENT,
    IMPORT_SETTINGS_AUTO_ORIENT_DESC,
    
    // -- Photo Scan --
    SCAN_DETECTED_PHOTOS,
    SCAN_ROTATE_ALL_CW,
    SCAN_ROTATE_ALL_CCW,
    SCAN_RESET_ALL,
    SCAN_RESET_CONFIRM_TITLE,
    SCAN_RESET_CONFIRM_MESSAGE,
    SCAN_PHOTO_LABEL,
    SCAN_ASPECT_RATIO_LABEL,
    SCAN_CORRECTION_STRATEGY_LABEL,
    
    // -- Metadata Editor --
    META_ROTATE_LABEL,
    META_AUTO_DETECT_ROTATION,
    META_ROTATION_DEGREES,
    META_AUTO_ROTATE_DIALOG_TITLE,
    META_AUTO_ROTATE_DIALOG_DETECTED,
    META_AUTO_ROTATE_DIALOG_CORRECTION,
    META_AUTO_ROTATE_DIALOG_JPEG_WARNING,
    META_AUTO_ROTATE_DIALOG_APPLY,
    META_AUTO_ROTATE_REQUIRES_MODEL,
    
    // -- Rotation --
    ROTATION_BADGE_AUTO,
    ORIENTATION_INDICATOR_LABEL,
    
    // -- Common Actions --
    ACTION_OK,
    ACTION_CANCEL,
    ACTION_APPLY,
    ACTION_SAVE,
    ACTION_DELETE,
    ACTION_RESET,
    ACTION_CLOSE,
    
    // -- Errors --
    ERROR_ORIENT_DETECT_FAILED,
    ERROR_IMAGE_READ_FAILED,
    ERROR_GENERIC,
    
    // ... (continue for all 250+ strings)
    ;
}
```

### 2.2 Locale File Format
JSON files in `resources/i18n/` directory. Simple flat key-value format.

```json
// resources/i18n/en.json
{
  "APP_NAME": "Petrie Image Importer",
  "APP_SHORT_DESCRIPTION": "Import, organize, and manage your photos",
  "IMPORT_TITLE": "Import",
  "IMPORT_SOURCE_LABEL": "Source folder",
  "IMPORT_DESTINATION_LABEL": "Destination folder",
  "IMPORT_START_BUTTON": "Start Import",
  "IMPORT_CANCEL_BUTTON": "Cancel",
  "IMPORT_PROGRESS_SCANNING": "Scanning {count} files...",
  "IMPORT_PROGRESS_IMPORTING": "Importing {current} of {total}...",
  "IMPORT_PROGRESS_COMPLETE": "Import complete!",
  "IMPORT_SETTINGS_AUTO_ORIENT": "Auto-orient photos on import",
  "IMPORT_SETTINGS_AUTO_ORIENT_DESC": "Detect and correct rotation using ML (requires orientation model)",
  "ROTATION_BADGE_AUTO": "Auto",
  "ORIENTATION_INDICATOR_LABEL": "Auto-orient",
  
  "ACTION_OK": "OK",
  "ACTION_CANCEL": "Cancel",
  "ACTION_APPLY": "Apply",
  "ACTION_SAVE": "Save",
  "ACTION_DELETE": "Delete",
  "ACTION_RESET": "Reset",
  "ACTION_CLOSE": "Close"
}
```

```json
// resources/i18n/de.json
{
  "APP_NAME": "Petrie Bildimporte",
  "IMPORT_TITLE": "Importieren",
  "IMPORT_SOURCE_LABEL": "Quellordner",
  "IMPORT_DESTINATION_LABEL": "Zielordner",
  "IMPORT_START_BUTTON": "Import starten",
  "IMPORT_CANCEL_BUTTON": "Abbrechen",
  "IMPORT_PROGRESS_SCANNING": "Scanne {count} Dateien...",
  "IMPORT_PROGRESS_IMPORTING": "Importiere {current} von {total}...",
  "IMPORT_PROGRESS_COMPLETE": "Import abgeschlossen!",
  "IMPORT_SETTINGS_AUTO_ORIENT": "Fotos automatisch beim Import ausrichten",
  "IMPORT_SETTINGS_AUTO_ORIENT_DESC": "Drehung per ML erkennen und korrigieren (erfordert Orientierungsmodell)",
  "ROTATION_BADGE_AUTO": "Auto",
  "ORIENTATION_INDICATOR_LABEL": "Auto-Ausrichtung",
  
  "ACTION_OK": "OK",
  "ACTION_CANCEL": "Abbrechen",
  "ACTION_APPLY": "Anwenden",
  "ACTION_SAVE": "Speichern",
  "ACTION_DELETE": "Löschen",
  "ACTION_RESET": "Zurücksetzen",
  "ACTION_CLOSE": "Schließen"
}
```

### 2.3 Parameters and Plurals
Strings support `{param}` parameter substitution. For plurals, use explicit keys:

```json
{
  "IMPORT_PROGRESS_SCANNING": "Scanning {count} files...",
  "IMPORT_COUNT_ONE": "1 file",
  "IMPORT_COUNT_OTHER": "{count} files",
  "SCAN_DETECTED_PHOTOS_ONE": "1 photo detected",
  "SCAN_DETECTED_PHOTOS_OTHER": "{count} photos detected"
}
```

Plural key resolution at runtime:

```kotlin
fun pluralKey(baseKey: StringKey, count: Int): StringKey {
    val suffix = if (count == 1) "_ONE" else "_OTHER"
    return StringKey.valueOf(baseKey.name + suffix)
}

// Usage: s.t(pluralKey(StringKey.SCAN_DETECTED_PHOTOS, photoList.size), "count" to photoList.size.toString())
```

This explicit approach avoids ICU-style plural rules in JSON, keeping the file format simple and accessible to non-developers. Languages requiring additional plural forms (e.g., Slavic languages with `FEW` category) add `_FEW` suffixes as needed.

### 2.4 Core Classes

```kotlin
// domain/port/LocalePort.kt
interface LocalePort {
    /** Get translated string for key, with optional parameter substitution */
    fun t(key: StringKey, vararg params: Pair<String, String>): String
    
    /** Get current locale code (e.g., "en", "de", "ja") */
    fun currentLocale(): String
    
    /** Get all available locale codes */
    fun availableLocales(): Set<String>
    
    /** Set locale and reload strings */
    suspend fun setLocale(localeCode: String)
    
    /** Observe locale changes as StateFlow */
    fun observeLocale(): StateFlow<String>
}

// domain/model/i18n/LocaleConfig.kt
data class LocaleConfig(
    val currentLocale: String = "en",
    val availableLocales: Set<String> = setOf("en"),
    val fallbackLocale: String = "en"
)

// infrastructure/i18n/JsonLocaleAdapter.kt
class JsonLocaleAdapter(
    private val fileSystemPort: FileSystemPort,
    private val dispatchers: DispatcherProvider,
) : LocalePort {
    
    private val strings = ConcurrentHashMap<String, Map<StringKey, String>>()
    private val localeFlow = MutableStateFlow("en")
    
    override fun t(key: StringKey, vararg params: Pair<String, String>): String {
        val localeStrings = strings[localeFlow.value] ?: strings["en"] ?: emptyMap()
        var result = localeStrings[key] 
            ?: strings["en"]?.get(key) 
            ?: key.name.replace('_', ' ').lowercase()
        for ((paramName, paramValue) in params) {
            result = result.replace("{$paramName}", paramValue)
        }
        return result
    }
    
    override suspend fun setLocale(localeCode: String) {
        loadLocale(localeCode)
        localeFlow.value = localeCode
    }
    
    override fun currentLocale(): String = localeFlow.value
    
    override fun availableLocales(): Set<String> = strings.keys
    
    override fun observeLocale(): StateFlow<String> = localeFlow
    
    private suspend fun loadLocale(localeCode: String) {
        if (strings.containsKey(localeCode)) return
        
        withContext(dispatchers.io) {
            // Priority order:
            // 1. User override: ~/.petrie-importer/i18n/{locale}.json
            // 2. Classpath: resources/i18n/{locale}.json
            val userOverride = fileSystemPort.readText(
                Path("${System.getProperty("user.home")}/.petrie-importer/i18n/${localeCode}.json")
            )
            val classpathResource = this::class.java
                .getResourceAsStream("/i18n/${localeCode}.json")
                ?.bufferedReader()
                ?.readText()
            
            val rawJson = userOverride ?: classpathResource ?: return@withContext
            val parsed = json.decodeFromString<Map<String, String>>(rawJson)
            val mapped = parsed.mapKeys { (k, _) -> 
                try { StringKey.valueOf(k) } catch (_: IllegalArgumentException) { null } 
            }
                .filterKeys { it != null }
                .mapKeys { (k, _) -> k!! }
            
            strings[localeCode] = mapped
        }
    }
}
```

### 2.5 Composable Helper

```kotlin
// ui/i18n/LocalStrings.kt
val LocalStrings = compositionLocalOf<Strings> { Strings() }

@Composable
fun StringsProvider(content: @Composable () -> Unit) {
    val localePort: LocalePort = koinInject()
    val locale by localePort.observeLocale().collectAsState()
    val strings = remember(locale) { Strings(localePort) }
    CompositionLocalProvider(LocalStrings provides strings) {
        content()
    }
}

// Convenience accessor
@Composable
fun strings(): Strings = LocalStrings.current

class Strings(private val localePort: LocalePort? = null) {
    fun t(key: StringKey, vararg params: Pair<String, String>): String = 
        localePort?.t(key, *params) ?: key.name.replace('_', ' ').lowercase()
    
    // Common shorthand accessors
    val appName: String get() = t(StringKey.APP_NAME)
    val import: String get() = t(StringKey.IMPORT_TITLE)
    val ok: String get() = t(StringKey.ACTION_OK)
    val cancel: String get() = t(StringKey.ACTION_CANCEL)
    // ... (generated shorthand accessors for all keys)
}
```

### 2.6 Usage in Composables

Before:
```kotlin
Text("Auto-orient photos on import")
Text("Detect and correct rotation using ML (requires orientation model)")
```

After:
```kotlin
val s = strings()
Text(s.t(StringKey.IMPORT_SETTINGS_AUTO_ORIENT))
Text(s.t(StringKey.IMPORT_SETTINGS_AUTO_ORIENT_DESC))
```

With parameters:
```kotlin
val s = strings()
Text(s.t(StringKey.IMPORT_PROGRESS_IMPORTING, "current" to "12", "total" to "50"))
// Resolves to: "Importing 12 of 50..."
```

With plurals:
```kotlin
val s = strings()
val photoCount = photoList.size
val key = if (photoCount == 1) StringKey.SCAN_DETECTED_PHOTOS_ONE else StringKey.SCAN_DETECTED_PHOTOS_OTHER
Text(s.t(key, "count" to photoCount.toString()))
```

### 2.7 Adding a New Language
1. Create `resources/i18n/{locale}.json` (e.g., `fr.json`)
2. Translate all string values from `en.json`
3. The new locale appears automatically in settings

No code changes, no registration, no DI wiring. The `JsonLocaleAdapter` discovers locale files at runtime by scanning the `resources/i18n/` directory and the user override directory.

### 2.8 User Override Locales
Users can place custom locale files in `~/.petrie-importer/i18n/` to override bundled translations. Files in this directory take precedence over classpath resources.

Use cases include:
- Correcting a mistranslation without waiting for an upstream fix
- Customizing terminology for a specific workflow (e.g., "patient" instead of "photo")
- Creating an organization-specific locale that isn't publicly distributed

### 2.9 RTL Support

Compose handles RTL layout automatically when the locale is set. Additional considerations:

```kotlin
// In StringsProvider, detect RTL and apply layout direction
@Composable
fun StringsProvider(content: @Composable () -> Unit) {
    val localePort: LocalePort = koinInject()
    val locale by localePort.observeLocale().collectAsState()
    val strings = remember(locale) { Strings(localePort) }
    
    val isRtl = remember(locale) {
        locale in setOf("ar", "he", "fa", "ur")
    }
    
    CompositionLocalProvider(
        LocalStrings provides strings,
        LocalLayoutDirection provides if (isRtl) LayoutDirection.Rtl else LayoutDirection.Ltr
    ) {
        content()
    }
}
```

For manual layout adjustments:
```kotlin
// Flip icons/mirrors for RTL
Icon(
    imageVector = if (isRtl) Icons.Default.ArrowBack else Icons.Default.ArrowForward,
    contentDescription = s.t(StringKey.NAV_IMPORT)
)
```

## 3. Implementation Plan

### Phase 1: Infrastructure (1 week)
- Create `StringKey` enum with all ~250 current strings
- Create `en.json` with all translations
- Create `LocalePort` interface
- Create `JsonLocaleAdapter` (infrastructure)
- Create `Strings` class and `LocalStrings` composable
- Wire into `AppModule` DI
- Add `LocaleConfig` to domain model

### Phase 2: Migration (2 weeks)
- Replace all hardcoded strings in composables with `s.t(StringKey.XXX)`
- Start with the largest files first:
  - `ImagePreviewScreen`: 54 strings
  - `EditSections`: 38 strings
  - `MetadataEditorScreen`: 37 strings
  - `ImportScreen`: 28 strings
  - Remaining screens and dialogs
- Add `val s = strings()` at top of each `@Composable` function that uses strings
- Run full UI regression test after each screen migration

### Phase 3: Sample Locales (1 week)
Bundled locales (13 total):

| Code | Language | Notes |
|------|----------|-------|
| `en` | English (US) | Fallback locale |
| `de` | Deutsch | Hand-maintained |
| `zh` | 中文（简体） | Chinese (Simplified) |
| `ja` | 日本語 | Kanji, hiragana, katakana |
| `es` | Español | Spanish |
| `fr` | Français | French |
| `pt` | Português | Portuguese |
| `nl` | Nederlands | Dutch |
| `sv` | Svenska | Swedish |
| `ru` | Русский | Russian |
| `hi` | हिन्दी | Hindi (Devanagari) |
| `ar` | العربية | Arabic (RTL) |
| `ko` | 한국어 | Korean |
| `bn` | বাংলা | Bengali |
| `id` | Bahasa Indonesia | Indonesian |
| `ur` | اردو | Urdu (RTL) |
| `tr` | Türkçe | Turkish |
| `vi` | Tiếng Việt | Vietnamese |
| `it` | Italiano | Italian |

Regenerate machine-translated locales from `en.json`:

```bash
# One command: create venv, install deps, sync all locales
./scripts/sync-locales.sh

# Preview / verify (pass-through flags)
./scripts/sync-locales.sh --dry-run
./scripts/sync-locales.sh --check

# Or run the Python entry points directly:
pip install deep-translator
python3 scripts/sync_locales_from_en.py

# Full regenerate of one locale (overwrites existing translations)
python3 scripts/generate_locales.py --locale ja --force
```

Machine translations are a starting point — review changes before release, especially for hand-maintained locales (`de`, `zh`).

- Verify RTL handling with `ar.json` and `ur.json` (Arabic, Urdu)
- Verify CJK text rendering and line-breaking (`zh`, `ja`, `ko`)

### Phase 4: Settings UI (1 week)
- Add Language dropdown to `AppSettings`
- Show available locales with native names (e.g., "Deutsch" not "German")
- Persist locale preference in `AppSettings`
- Reload app UI on locale change
- Add locale-native-names mapping:

```json
// resources/i18n/locale-names.json
{
  "en": "English",
  "de": "Deutsch",
  "fr": "Français",
  "ja": "日本語",
  "es": "Español",
  "ar": "العربية"
}
```

## 4. File Structure
```
src/main/resources/i18n/
├── en.json              # English (US, base, always present)
├── de.json              # German
├── zh.json              # Chinese (Simplified)
├── ja.json              # Japanese
├── es.json              # Spanish
├── fr.json              # French
├── pt.json              # Portuguese
├── nl.json              # Dutch
├── sv.json              # Swedish
├── ru.json              # Russian
├── hi.json              # Hindi
├── ar.json              # Arabic (RTL)
├── ko.json              # Korean
├── bn.json              # Bengali
├── id.json              # Indonesian
├── ur.json              # Urdu (RTL)
├── tr.json              # Turkish
├── vi.json              # Vietnamese
└── it.json              # Italian

src/main/kotlin/.../domain/
├── model/i18n/
│   ├── StringKey.kt     # Enum of all string identifiers
│   └── LocaleConfig.kt # Configuration data class
└── port/
    └── LocalePort.kt    # Localization interface

src/main/kotlin/.../infrastructure/i18n/
└── JsonLocaleAdapter.kt # JSON-based LocalePort implementation

src/main/kotlin/.../ui/i18n/
├── LocalStrings.kt      # CompositionLocal + StringsProvider
└── Strings.kt           # Convenience wrapper class

~/.petrie-importer/i18n/
└── {locale}.json        # User override files (takes precedence)
```

## 5. Performance Considerations
- **Startup**: Locale strings loaded once at startup and cached in `ConcurrentHashMap`. No runtime file reads during UI rendering.
- **Lookup**: String lookups are O(1) via enum-based Map access. The `StringKey` enum maps to a hash in the `Map<StringKey, String>`.
- **Memory**: Only the current locale + fallback locale are held in memory. Switching locales triggers a one-time load of the new locale file.
- **Compose**: `remember(locale)` ensures `Strings` object is recreated only when locale changes, not on every recomposition.
- **Development mode**: Optional file watcher on `resources/i18n/` for hot-reload during development (disabled in production builds).

```kotlin
// Development-only hot reload (optional)
class DevLocaleWatcher(
    private val localeAdapter: JsonLocaleAdapter,
    private val watchPath: Path = Path("src/main/resources/i18n")
) {
    private var watcher: FileWatchService? = null
    
    fun start(scope: CoroutineScope) {
        if (!BuildConfig.DEBUG) return
        watcher = FileSystems.getDefault().newWatchService()
        watchPath.register(watcher, StandardWatchEventKinds.ENTRY_MODIFY)
        scope.launch(Dispatchers.IO) {
            while (true) {
                val key = watcher?.take() ?: break
                for (event in key.pollEvents()) {
                    val filename = event.context().toString()
                    if (filename.endsWith(".json")) {
                        val localeCode = filename.removeSuffix(".json")
                        localeAdapter.reloadLocale(localeCode)
                    }
                }
                key.reset()
            }
        }
    }
}
```

## 6. Testing

### 6.1 Unit Tests
- **StringKey enum completeness**: Every key in `StringKey` must have a corresponding entry in `en.json`
- **en.json completeness**: Every entry in `en.json` must have a matching `StringKey` enum value
- **UI text localization** (`UiLocalizationArchitectureTest`): No hardcoded user-facing strings in `ui/` composables or the desktop menu bar; use `StringKey` + locale JSON, or `@LocalizedExempt` for rare exceptions (e.g. native file-picker filter labels passed from callers)
- **Parameter substitution**: Verify `{count}` is correctly replaced with actual values
- **Fallback chain**: Missing key in locale → English fallback → key name (never crashes, never returns null)
- **Empty/missing locale file**: Gracefully falls back to English

```kotlin
class LocalePortTest {
    @Test
    fun `fallback chain returns key name when both locale and English are missing`() {
        val adapter = JsonLocaleAdapter(mockFileSystem, testDispatchers)
        // Fallback for unknown key
        assertEquals("app name", adapter.t(StringKey.APP_NAME))
    }
    
    @Test
    fun `parameter substitution replaces placeholders`() {
        val adapter = JsonLocaleAdapter(mockFileSystem, testDispatchers)
        adapter.loadLocale("en") // loads en.json with "IMPORT_PROGRESS_IMPORTING": "Importing {current} of {total}..."
        val result = adapter.t(StringKey.IMPORT_PROGRESS_IMPORTING, "current" to "5", "total" to "100")
        assertEquals("Importing 5 of 100...", result)
    }
    
    @Test
    fun `missing locale key falls back to English`() {
        val adapter = JsonLocaleAdapter(mockFileSystem, testDispatchers)
        adapter.loadLocale("de") // de.json missing IMPORT_TITLE
        assertEquals("Import", adapter.t(StringKey.IMPORT_TITLE)) // falls back to en.json
    }
}
```

### 6.2 Integration Tests
- Load all bundled locale files without errors
- Switch locale at runtime and verify all strings update
- User override file takes precedence over classpath resource

### 6.3 UI Tests
- Verify all composables that use `strings()` render without `StringKey` names showing
- Screenshot tests for each locale to catch layout overflow issues

## 7. Risks

| Risk | Mitigation |
|------|-----------|
| Missing translations at runtime | Fallback chain: locale → en → key name (never crashes) |
| Translation quality | Community contributions via GitHub PRs for locale files |
| RTL layout issues | Compose handles RTL automatically with `LocalLayoutDirection`; test with `ar.json` |
| String key explosion | Group keys by screen/purpose, not by arbitrary granularity; max ~300 keys |
| Performance with 250+ keys | `ConcurrentHashMap` lookup is O(1), negligible overhead |
| Stale locale files after adding new keys | CI check: `en.json` keys match `StringKey` enum entries; fail build on mismatch |
| Translator introduces invalid JSON | JSON parsing error logs warning and skips file; fallback to English |
| Parameter name mismatch | Unit test `{param}` substitution for all parameterized strings |

## 8. CI Checks

Add the following checks to CI pipeline:

```bash
# 1. Verify every StringKey enum entry exists in en.json
./gradlew checkStringKeys

# 2. Verify every en.json entry has a StringKey enum value
./gradlew checkLocaleFiles

# 3. Verify all locale files are valid JSON
./gradlew validateLocaleJson
```

The `checkStringKeys` task should:
- Parse `StringKey.kt` to extract all enum values
- Parse `en.json` to extract all keys
- Report any mismatches as build failures

The `validateLocaleJson` task should:
- Load each `*.json` file in `resources/i18n/`
- Verify it parses as valid JSON
- Verify all keys are strings (not nested objects or arrays)
- Report any invalid files

## 9. Glossary

| Term | Definition |
|------|-----------|
| **StringKey** | Enum entry identifying a single user-facing string |
| **Locale file** | JSON file mapping `StringKey` names to translated strings |
| **Fallback chain** | Lookup order: requested locale → English → key name |
| **User override** | Custom locale file in `~/.petrie-importer/i18n/` that supersedes bundled translations |
| **Plural key** | String key with `_ONE` / `_OTHER` / `_FEW` suffixes for pluralization |
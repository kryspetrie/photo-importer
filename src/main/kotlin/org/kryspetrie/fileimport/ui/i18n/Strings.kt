package org.kryspetrie.fileimport.ui.i18n

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.flow.StateFlow
import org.koin.compose.koinInject
import org.kryspetrie.fileimport.domain.model.i18n.LocaleConfig
import org.kryspetrie.fileimport.domain.model.i18n.StringKey
import org.kryspetrie.fileimport.domain.port.LocalePort

/**
 * Composition local for providing [Strings] throughout the composable tree.
 *
 * Use [strings] to access the current translation helper in any composable:
 * ```kotlin
 * val s = strings()
 * Text(s.t(StringKey.IMPORT_TITLE))
 * ```
 */
val LocalStrings = staticCompositionLocalOf { Strings() }

/**
 * Wraps content with a [Strings] provider that reacts to locale changes.
 *
 * Must be placed high in the composable tree (near the root). All descendants can then access
 * translations via [strings].
 */
@Composable
fun StringsProvider(content: @Composable () -> Unit) {
    val localePort: LocalePort = koinInject()
    val localeState = localePort.observeLocale().collectAsState()
    val strings = remember(localeState.value) { Strings(localePort) }
    CompositionLocalProvider(LocalStrings provides strings) { content() }
}

/**
 * Returns the current [Strings] translation helper from the composition local.
 *
 * Usage:
 * ```kotlin
 * @Composable
 * fun MyScreen() {
 *     val s = strings()
 *     Text(s.t(StringKey.IMPORT_TITLE))
 * }
 * ```
 */
@Composable fun strings(): Strings = LocalStrings.current

/**
 * Translation helper that delegates to [LocalePort] for string resolution.
 *
 * Provides both generic [t] lookup and convenient property accessors for common strings. Property
 * accessors avoid key lookups in common cases and improve readability:
 * ```kotlin
 * Text(s.ok)           // instead of s.t(StringKey.ACTION_OK)
 * Text(s.importTitle)   // instead of s.t(StringKey.IMPORT_TITLE)
 * ```
 */
class Strings(private val localePort: LocalePort = StubLocalePort()) {

    /** Generic translation lookup with parameter substitution. */
    fun t(key: StringKey, vararg params: Pair<String, String>): String = localePort.t(key, *params)

    // ── Convenience accessors for high-frequency strings ──────────────

    /** App name. */
    val appName: String
        get() = localePort.t(StringKey.APP_NAME)

    // ── Common actions ────────────────────────────────────────────────

    /** "OK". */
    val ok: String
        get() = localePort.t(StringKey.ACTION_OK)

    /** "Cancel". */
    val cancel: String
        get() = localePort.t(StringKey.ACTION_CANCEL)

    /** "Apply". */
    val apply: String
        get() = localePort.t(StringKey.ACTION_APPLY)

    /** "Save". */
    val save: String
        get() = localePort.t(StringKey.ACTION_SAVE)

    /** "Delete". */
    val delete: String
        get() = localePort.t(StringKey.ACTION_DELETE)

    /** "Reset". */
    val reset: String
        get() = localePort.t(StringKey.ACTION_RESET)

    /** "Close". */
    val close: String
        get() = localePort.t(StringKey.ACTION_CLOSE)

    /** "Back". */
    val back: String
        get() = localePort.t(StringKey.ACTION_BACK)

    /** "Next". */
    val next: String
        get() = localePort.t(StringKey.ACTION_NEXT)

    /** "Export". */
    val export: String
        get() = localePort.t(StringKey.ACTION_EXPORT)
}

/**
 * Stub locale port used when [Strings] is created outside a composable context (e.g., in previews
 * or tests). Returns key names as fallback strings.
 */
internal class StubLocalePort : LocalePort {
    override fun t(key: StringKey, vararg params: Pair<String, String>): String =
        key.name.lowercase().replace('_', ' ')

    override fun config() = LocaleConfig()

    override fun availableLocales(): Set<String> = setOf("en")

    override suspend fun setLocale(localeCode: String): Boolean = true

    override fun observeLocale(): StateFlow<String> = kotlinx.coroutines.flow.MutableStateFlow("en")

    override fun nativeLocaleName(localeCode: String): String = localeCode.uppercase()
}

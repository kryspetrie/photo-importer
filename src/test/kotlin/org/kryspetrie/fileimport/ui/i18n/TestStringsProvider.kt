package org.kryspetrie.fileimport.ui.i18n

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import kotlinx.coroutines.Dispatchers
import org.kryspetrie.fileimport.domain.port.DispatcherProvider
import org.kryspetrie.fileimport.infrastructure.i18n.JsonLocaleAdapter

/**
 * Test helper that provides English [Strings] without Koin.
 *
 * Wrap composable UI tests that call [strings] or use [StringKey] lookups.
 */
@Composable
fun TestStringsProvider(
    localeCode: String = "en",
    content: @Composable () -> Unit,
) {
    val localePort =
        remember {
            JsonLocaleAdapter(
                dispatchers =
                    object : DispatcherProvider {
                        override val io = Dispatchers.Unconfined
                        override val default = Dispatchers.Unconfined
                    },
                appLogger = null,
            )
        }
    remember(localeCode) { kotlinx.coroutines.runBlocking { localePort.setLocale(localeCode) } }
    val strings = remember(localeCode) { Strings(localePort) }
    CompositionLocalProvider(LocalStrings provides strings) { content() }
}

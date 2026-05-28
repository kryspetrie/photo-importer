package org.kryspetrie.fileimport.domain.port

import kotlinx.coroutines.CoroutineDispatcher

/**
 * Provides coroutine dispatchers, allowing tests to override dispatchers for deterministic testing.
 */
interface DispatcherProvider {
    val io: CoroutineDispatcher
    val default: CoroutineDispatcher
}

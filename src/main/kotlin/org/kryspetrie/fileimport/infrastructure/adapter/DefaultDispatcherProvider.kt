package org.kryspetrie.fileimport.infrastructure.adapter

import kotlinx.coroutines.Dispatchers
import org.kryspetrie.fileimport.domain.port.DispatcherProvider

/** Default [DispatcherProvider] implementation that delegates to Kotlin dispatchers. */
@Suppress(
    "InjectDispatcher"
) // This IS the injection point — provides real dispatchers for production
class DefaultDispatcherProvider : DispatcherProvider {
    override val io: kotlinx.coroutines.CoroutineDispatcher = Dispatchers.IO
    override val default: kotlinx.coroutines.CoroutineDispatcher = Dispatchers.Default
}

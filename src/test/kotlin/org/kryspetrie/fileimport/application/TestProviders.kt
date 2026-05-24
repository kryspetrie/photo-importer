package org.kryspetrie.fileimport.application

import kotlinx.coroutines.Dispatchers
import org.kryspetrie.fileimport.domain.port.DispatcherProvider
import org.kryspetrie.fileimport.domain.port.IdGenerator
import org.kryspetrie.fileimport.domain.port.TimeProvider

/** Test [TimeProvider] that returns a fixed or incrementing timestamp. */
class TestTimeProvider(private var time: Long = 1000L) : TimeProvider {
    override fun currentTimeMillis(): Long = time
    override fun formattedTimestamp(): String = "2026-01-01T00:00:00"
    override fun formatTimestamp(timestamp: Long): String = "2026-01-01 00:00:00"

    fun advanceMs(ms: Long) {
        time += ms
    }
}

/** Test [IdGenerator] that returns sequential IDs. */
class TestIdGenerator(private var counter: Int = 0) : IdGenerator {
    override fun generateId(): String = "test-id-${counter++}"
}

/** Test [DispatcherProvider] that delegates to standard Kotlin dispatchers. */
class TestDispatcherProvider : DispatcherProvider {
    override val io: kotlinx.coroutines.CoroutineDispatcher = Dispatchers.IO
    override val default: kotlinx.coroutines.CoroutineDispatcher = Dispatchers.Default
}

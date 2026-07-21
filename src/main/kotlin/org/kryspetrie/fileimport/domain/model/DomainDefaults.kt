package org.kryspetrie.fileimport.domain.model

import org.kryspetrie.fileimport.domain.port.IdGenerator
import org.kryspetrie.fileimport.domain.port.TimeProvider

/**
 * Provides default values for domain model IDs and timestamps.
 *
 * Delegates to [IdGenerator] and [TimeProvider] ports. When no provider is explicitly set, resolves
 * lazily from the Koin DI container (which registers [DefaultIdGenerator] and
 * [DefaultTimeProvider]). If Koin is not available (e.g., in unit tests), falls back to simple
 * implementations that avoid importing JVM infrastructure classes directly.
 *
 * Tests can override via [setIdGenerator] and [setTimeProvider] to control values.
 */
object DomainDefaults {
    @Volatile private var idGenerator: IdGenerator? = null

    @Volatile private var timeProvider: TimeProvider? = null

    /** Generate a unique ID using the current [IdGenerator], or resolve one lazily. */
    fun generateId(): String {
        val gen = idGenerator
        if (gen != null) return gen.generateId()
        val resolved = resolveIdGenerator()
        return resolved.generateId()
    }

    /**
     * Get the current time in milliseconds using the current [TimeProvider], or resolve one lazily.
     */
    fun currentTimeMillis(): Long {
        val prov = timeProvider
        if (prov != null) return prov.currentTimeMillis()
        val resolved = resolveTimeProvider()
        return resolved.currentTimeMillis()
    }

    /**
     * Format a timestamp as "yyyy-MM-dd HH:mm:ss" using the current [TimeProvider], or resolve one
     * lazily.
     */
    fun formatTimestamp(timestamp: Long): String {
        val prov = timeProvider
        if (prov != null) return prov.formatTimestamp(timestamp)
        val resolved = resolveTimeProvider()
        return resolved.formatTimestamp(timestamp)
    }

    /**
     * Override the [IdGenerator] for testing. Returns a reset function to restore the previous
     * value.
     */
    fun setIdGenerator(generator: IdGenerator): () -> Unit {
        val previous = idGenerator
        idGenerator = generator
        return { idGenerator = previous }
    }

    /**
     * Override the [TimeProvider] for testing. Returns a reset function to restore the previous
     * value.
     */
    fun setTimeProvider(provider: TimeProvider): () -> Unit {
        val previous = timeProvider
        timeProvider = provider
        return { timeProvider = previous }
    }

    /**
     * Resolves [IdGenerator] from Koin DI container. Called lazily on first [generateId] invocation
     * if no generator was explicitly set.
     */
    private fun resolveIdGenerator(): IdGenerator {
        return try {
            val koin = org.koin.core.context.GlobalContext.get()
            val generator = koin.get<IdGenerator>()
            idGenerator = generator
            generator
        } catch (_: Exception) {
            // Koin not initialized (e.g., in unit tests) — use simple fallback
            val fallback = FallbackIdGenerator
            idGenerator = fallback
            fallback
        }
    }

    /**
     * Resolves [TimeProvider] from Koin DI container. Called lazily on first time-related
     * invocation if no provider was explicitly set.
     */
    private fun resolveTimeProvider(): TimeProvider {
        return try {
            val koin = org.koin.core.context.GlobalContext.get()
            val provider = koin.get<TimeProvider>()
            timeProvider = provider
            provider
        } catch (_: Exception) {
            // Koin not initialized (e.g., in unit tests) — use simple fallback
            val fallback = FallbackTimeProvider
            timeProvider = fallback
            fallback
        }
    }

    /**
     * Simple fallback ID generator for when Koin is not available. Uses kotlin.random.Random which
     * has no JVM-specific imports in the domain layer.
     */
    private object FallbackIdGenerator : IdGenerator {
        override fun generateId(): String =
            kotlin.random.Random.Default.nextLong().toString(16) +
                kotlin.random.Random.Default.nextLong().toString(16)
    }

    /**
     * Simple fallback time provider for when Koin is not available. Uses System.currentTimeMillis
     * which is available on all Kotlin platforms.
     */
    private object FallbackTimeProvider : TimeProvider {
        override fun currentTimeMillis(): Long = System.currentTimeMillis()

        override fun formattedTimestamp(): String = formatTimestamp(currentTimeMillis())

        override fun formatTimestamp(timestamp: Long): String {
            return java.time.Instant.ofEpochMilli(timestamp)
                .atZone(java.time.ZoneId.systemDefault())
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
        }
    }
}

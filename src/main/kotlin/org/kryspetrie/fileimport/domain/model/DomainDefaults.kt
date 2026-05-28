package org.kryspetrie.fileimport.domain.model

import org.kryspetrie.fileimport.domain.port.IdGenerator
import org.kryspetrie.fileimport.domain.port.TimeProvider
import org.kryspetrie.fileimport.infrastructure.adapter.DefaultIdGenerator
import org.kryspetrie.fileimport.infrastructure.adapter.DefaultTimeProvider

/**
 * Provides default values for domain model IDs and timestamps.
 *
 * In production, these delegate to [DefaultIdGenerator] and [DefaultTimeProvider]. Tests can
 * override via [setIdGenerator] and [setTimeProvider] to control values.
 *
 * This replaces direct `java.util.UUID.randomUUID()` and `System.currentTimeMillis()` calls in
 * domain model default parameters, making domain models testable without hardcoding JVM
 * dependencies.
 */
object DomainDefaults {
    @Volatile private var idGenerator: IdGenerator = DefaultIdGenerator()

    @Volatile private var timeProvider: TimeProvider = DefaultTimeProvider()

    /** Generate a unique ID using the current [IdGenerator]. */
    fun generateId(): String = idGenerator.generateId()

    /** Get the current time in milliseconds using the current [TimeProvider]. */
    fun currentTimeMillis(): Long = timeProvider.currentTimeMillis()

    /**
     * Format a timestamp as a human-readable string using the current [TimeProvider]. Format:
     * "yyyy-MM-dd HH:mm:ss"
     */
    fun formatTimestamp(timestamp: Long): String = timeProvider.formatTimestamp(timestamp)

    /** Override the [IdGenerator] for testing. Returns a reset function to restore the default. */
    fun setIdGenerator(generator: IdGenerator): () -> Unit {
        val previous = idGenerator
        idGenerator = generator
        return { idGenerator = previous }
    }

    /** Override the [TimeProvider] for testing. Returns a reset function to restore the default. */
    fun setTimeProvider(provider: TimeProvider): () -> Unit {
        val previous = timeProvider
        timeProvider = provider
        return { timeProvider = previous }
    }
}

package org.kryspetrie.fileimport.domain.port

/** Provides the current time, allowing tests to control time. */
interface TimeProvider {
    fun currentTimeMillis(): Long
    fun formattedTimestamp(): String

    /** Format a specific timestamp as "yyyy-MM-dd HH:mm:ss". */
    fun formatTimestamp(timestamp: Long): String
}

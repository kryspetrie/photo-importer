package org.kryspetrie.fileimport.infrastructure.adapter

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import org.kryspetrie.fileimport.domain.port.TimeProvider

/** Default [TimeProvider] implementation that uses system clock and ISO formatting. */
@Suppress("InjectDispatcher") // This IS the injection point for time
class DefaultTimeProvider : TimeProvider {
    override fun currentTimeMillis(): Long = System.currentTimeMillis()

    override fun formattedTimestamp(): String =
        DateTimeFormatter.ISO_LOCAL_DATE_TIME.withZone(ZoneId.systemDefault())
            .format(Instant.ofEpochMilli(currentTimeMillis()))

    override fun formatTimestamp(timestamp: Long): String =
        java.text
            .SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US)
            .format(java.util.Date(timestamp))
}

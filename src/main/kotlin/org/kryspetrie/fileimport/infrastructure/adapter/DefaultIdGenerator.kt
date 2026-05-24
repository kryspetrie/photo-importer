package org.kryspetrie.fileimport.infrastructure.adapter

import java.util.UUID
import org.kryspetrie.fileimport.domain.port.IdGenerator

/** Default [IdGenerator] implementation that produces random UUIDs. */
class DefaultIdGenerator : IdGenerator {
    override fun generateId(): String = UUID.randomUUID().toString()
}

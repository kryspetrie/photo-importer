package org.kryspetrie.fileimport.domain.port

/** Generates unique identifiers, allowing tests to control ID generation. */
interface IdGenerator {
    fun generateId(): String
}

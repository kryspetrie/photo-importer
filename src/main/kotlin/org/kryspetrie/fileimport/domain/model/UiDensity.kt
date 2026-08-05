package org.kryspetrie.fileimport.domain.model

import kotlinx.serialization.Serializable

/** UI spacing and control sizing preference for desktop layouts. */
@Serializable
enum class UiDensity {
    COMPACT,
    COMFORTABLE,
    SPACIOUS,
}

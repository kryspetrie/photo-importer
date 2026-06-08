package org.kryspetrie.fileimport.infrastructure.wizard

import org.kryspetrie.fileimport.domain.model.PhotoScanConfiguration

/**
 * Type alias for backward compatibility — [PhotoConfiguration] in the wizard layer is now the same
 * class as [PhotoScanConfiguration] in the domain layer. The merge eliminated a ~30-field bridge
 * method ("toDomain()") and the duplicate data class that had nearly identical fields.
 */
typealias PhotoConfiguration = PhotoScanConfiguration

package org.kryspetrie.fileimport.domain.model

/**
 * Strategy for resolving filename conflicts at destination.
 * - **RENAME**: Add numeric suffix to avoid conflict (safest, recommended)
 * - **SKIP**: Don't import conflicting file (keep existing)
 * - **REPLACE**: Overwrite existing file (destructive!)
 * - **ASK_USER**: Prompt user for each conflict (slowest)
 *
 * @see ImportConfiguration.conflictResolution
 */
enum class ConflictResolution {
    RENAME,
    SKIP,
    REPLACE,
    ASK_USER,
}

package org.kryspetrie.fileimport.domain.model

import kotlinx.serialization.Serializable

/**
 * Represents a saved import configuration profile.
 *
 * Import profiles allow users to save complete import configurations for different cameras,
 * workflows, or use cases. Instead of reconfiguring import settings each time, users can select a
 * profile and instantly apply all saved preferences.
 *
 * ## Use Cases
 * 1. **Camera-Specific Profiles**: Different settings for each camera
 *     - "Canon R5 Profile": RAW+JPEG handling, specific naming pattern
 *     - "iPhone Profile": HEIC conversion, date-based organization
 * 2. **Workflow Profiles**: Different workflows for different scenarios
 *     - "Quick Import": Flat structure, original names, no verification
 *     - "Archive Import": Hierarchical folders, verified, backup enabled
 * 3. **Client/Project Profiles**: Separate configurations per client or project
 *     - "Client A": Specific folder structure, naming convention
 *     - "Personal": Different organization scheme
 *
 * ## Auto-Selection
 *
 * Profiles can be associated with a specific camera via [cameraName]. When that camera is
 * connected, the profile is automatically selected, streamlining the import workflow for users with
 * multiple cameras.
 *
 * ## Persistence
 *
 * Profiles are serialized to JSON and stored in `~/.petrie-importer/settings.json`. The [id] field
 * ensures profiles can be uniquely identified even if renamed.
 *
 * ## Example
 *
 * ```kotlin
 * val weddingProfile = ImportProfile(
 *     name = "Wedding Photography",
 *     description = "Dual card import with client naming",
 *     configuration = ImportConfiguration(
 *         folderPattern = "{yyyy}/{MM-dd}/{camera}",
 *         fileNamePattern = "{client}_{yyyy}{MM}{dd}_{counter}",
 *         preserveOriginalName = false,
 *         verifyAfterCopy = true
 *     ),
 *     cameraName = "Canon EOS R5"
 * )
 * ```
 */
@Serializable
data class ImportProfile(
    /** Auto-generated unique identifier. */
    val id: String = DomainDefaults.generateId(),

    /** Human-readable profile display name. */
    val name: String,

    /** Optional description of the profile's purpose. */
    val description: String = "",

    /** Complete import configuration for this profile. */
    val configuration: ImportConfiguration = ImportConfiguration(),

    /** Optional camera model name for auto-selection. Case-insensitive match. */
    val cameraName: String = "",

    /** Last used source directory path. */
    val lastSourcePath: String = "",

    /** Last used destination directory path. */
    val lastDestinationPath: String = "",

    /** Profile creation timestamp (milliseconds since epoch). */
    val createdAt: Long = DomainDefaults.currentTimeMillis(),

    /** Profile last modification timestamp (milliseconds since epoch). */
    val updatedAt: Long = DomainDefaults.currentTimeMillis(),
)

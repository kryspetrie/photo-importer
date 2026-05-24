package org.kryspetrie.fileimport.domain.model

import kotlinx.serialization.Serializable

/**
 * A named preset for folder or filename patterns.
 *
 * Presets allow users to save and reuse custom patterns. Built-in presets are provided by
 * [FolderPresets] and [FilenamePresets], and users can create their own custom presets.
 *
 * @property name Display name shown in preset selector dropdown
 * @property pattern The actual pattern string with placeholders
 * @property isBuiltIn Whether this is a built-in preset (true) or user-created (false)
 * @see FolderPresets Built-in folder pattern presets
 * @see FilenamePresets Built-in filename pattern presets
 */
@Serializable
data class PatternPreset(val name: String, val pattern: String, val isBuiltIn: Boolean = false)

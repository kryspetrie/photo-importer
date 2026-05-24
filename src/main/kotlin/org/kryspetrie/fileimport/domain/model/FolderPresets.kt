package org.kryspetrie.fileimport.domain.model

/**
 * Built-in folder pattern presets.
 *
 * Provides commonly-used folder organization patterns as ready-to-use presets.
 *
 * ## Included Patterns
 * - Flat date: `{yyyy-MM-dd}` → `2024-01-15/`
 * - Year-Month: `{yyyy-MM}` → `2024-01/`
 * - Year / Date: `{yyyy}/{yyyy-MM-dd}` → `2024/2024-01-15/`
 * - Year / Month: `{yyyy}/{MM}` → `2024/01/`
 * - Year / Month / Day: `{yyyy}/{MM}/{dd}` → `2024/01/15/`
 * - Camera / Date: `{camera}/{yyyy-MM-dd}` → `Canon EOS R5/2024-01-15/`
 * - Year / Type: `{yyyy}/{type}` → `2024/Photos/`
 *
 * @see PatternPreset Preset data structure
 * @see NamePlaceholders Available placeholders for patterns
 */
object FolderPresets {
    val builtIn =
        listOf(
            PatternPreset("Flat date", "{yyyy-MM-dd}", isBuiltIn = true),
            PatternPreset("Year-Month", "{yyyy-MM}", isBuiltIn = true),
            PatternPreset("Year / Date", "{yyyy}/{yyyy-MM-dd}", isBuiltIn = true),
            PatternPreset("Year / Month", "{yyyy}/{MM}", isBuiltIn = true),
            PatternPreset("Year / Month / Day", "{yyyy}/{MM}/{dd}", isBuiltIn = true),
            PatternPreset("Year / Month-Day", "{yyyy}/{MM}-{dd}", isBuiltIn = true),
            PatternPreset("Camera / Date", "{camera}/{yyyy-MM-dd}", isBuiltIn = true),
            PatternPreset("Year / Camera", "{yyyy}/{camera}", isBuiltIn = true),
            PatternPreset("Year / Type", "{yyyy}/{type}", isBuiltIn = true),
            PatternPreset("Year / Type / Date", "{yyyy}/{type}/{yyyy-MM-dd}", isBuiltIn = true),
        )

    /** Example outputs for each pattern. Map of pattern → example output path. */
    val examples =
        mapOf(
            "{yyyy-MM-dd}" to "dest/2024-01-15/IMG_0001.jpg",
            "{yyyy-MM}" to "dest/2024-01/IMG_0001.jpg",
            "{yyyy}/{yyyy-MM-dd}" to "dest/2024/2024-01-15/IMG_0001.jpg",
            "{yyyy}/{MM}" to "dest/2024/01/IMG_0001.jpg",
            "{yyyy}/{MM}/{dd}" to "dest/2024/01/15/IMG_0001.jpg",
            "{yyyy}/{MM}-{dd}" to "dest/2024/01-15/IMG_0001.jpg",
            "{camera}/{yyyy-MM-dd}" to "dest/Canon EOS R5/2024-01-15/IMG_0001.jpg",
            "{yyyy}/{camera}" to "dest/2024/Canon EOS R5/IMG_0001.jpg",
            "{yyyy}/{type}" to "dest/2024/Photos/ or dest/2024/Videos/",
        )
}

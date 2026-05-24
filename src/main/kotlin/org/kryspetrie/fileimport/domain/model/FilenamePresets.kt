package org.kryspetrie.fileimport.domain.model

/**
 * Built-in filename pattern presets.
 *
 * Provides commonly-used filename patterns as ready-to-use presets.
 *
 * ## Included Patterns
 * - **Original**: `{original}` → `IMG_1234.CR3`
 * - **Date + Original**: `{yyyy}{MM}{dd}_{original}` → `20240115_IMG_1234.CR3`
 * - **Date-Time**: `{yyyy}{MM}{dd}_{HH}{mm}{ss}` → `20240115_143022.CR3`
 * - **Date + Counter**: `{yyyy}{MM}{dd}_{counter}` → `20240115_0001.CR3`
 * - **Camera + Date**: `{camera}_{yyyy}{MM}{dd}_{counter}` → `CanonR5_20240115_0001.CR3`
 * - **Full EXIF**: `{yyyy}{MM}{dd}_{HH}{mm}{ss}_{camera}_ISO{iso}` →
 *   `20240115_143022_CanonR5_ISO400.CR3`
 *
 * @see PatternPreset Preset data structure
 * @see NamePlaceholders Available placeholders for patterns
 * @see FolderPresets Built-in folder pattern presets
 */
object FilenamePresets {
    val builtIn =
        listOf(
            PatternPreset("Original", "{original}", isBuiltIn = true),
            PatternPreset("Date + Original", "{yyyy}{MM}{dd}_{original}", isBuiltIn = true),
            PatternPreset("Date-Time", "{yyyy}{MM}{dd}_{HH}{mm}{ss}", isBuiltIn = true),
            PatternPreset("Date + Counter", "{yyyy}{MM}{dd}_{counter}", isBuiltIn = true),
            PatternPreset("Camera + Date", "{camera}_{yyyy}{MM}{dd}_{counter}", isBuiltIn = true),
            PatternPreset(
                "Full EXIF",
                "{yyyy}{MM}{dd}_{HH}{mm}{ss}_{camera}_ISO{iso}",
                isBuiltIn = true,
            ),
        )
}

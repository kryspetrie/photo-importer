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
 *
 * @property id Unique identifier for this profile. Auto-generated UUID ensures uniqueness even
 *   across different machines or sync scenarios.
 * @property name Human-readable name shown in the profile selector dropdown. Should be descriptive
 *   (e.g., "Canon R5 - RAW+JPEG").
 * @property description Optional detailed description of the profile's purpose. Shown as tooltip or
 *   in profile management UI.
 * @property configuration Complete import configuration including folder patterns, naming rules,
 *   duplicate detection settings, etc.
 * @property cameraName Optional camera model name for auto-selection. When a camera with matching
 *   name is connected, this profile is automatically selected. Match is case-insensitive.
 * @property lastSourcePath Last used source directory for this profile. Convenience feature to
 *   remember where user typically imports from.
 * @property lastDestinationPath Last used destination directory for this profile. Remembers user's
 *   preferred import location.
 * @property createdAt Unix timestamp (milliseconds) when profile was created. Used for sorting and
 *   audit purposes.
 * @property updatedAt Unix timestamp (milliseconds) when profile was last modified. Updated on
 *   every save to track changes.
 * @see ImportConfiguration Detailed import settings
 * @see AppSettings Collection of all profiles and application settings
 * @see Serializable Kotlin serialization for JSON persistence
 */
@Serializable
data class ImportProfile(
    /**
     * Unique profile identifier.
     *
     * Auto-generated UUID ensures:
     * - No collisions between profiles
     * - Safe to rename profiles
     * - Can be referenced by [AppSettings.activeProfileId]
     */
    val id: String = java.util.UUID.randomUUID().toString(),

    /**
     * Profile display name.
     *
     * Shown in UI dropdowns and selectors. Examples:
     * - "Canon R5 - Wedding"
     * - "iPhone - Quick Import"
     * - "Archive - Verified"
     */
    val name: String,

    /**
     * Optional profile description.
     *
     * Can include:
     * - Purpose of this profile
     * - Special handling notes
     * - Which camera/workflow it's for
     *
     * Displayed as tooltip or in profile management screen.
     */
    val description: String = "",

    /**
     * Complete import configuration for this profile.
     *
     * Contains all import settings:
     * - Folder organization pattern
     * - Filename generation rules
     * - Duplicate detection strategy
     * - Verification and deletion options
     * - RAW+JPEG handling
     *
     * Defaults to [ImportConfiguration] defaults if not specified.
     */
    val configuration: ImportConfiguration = ImportConfiguration(),

    /**
     * Associated camera model name.
     *
     * When set, enables auto-selection:
     * 1. User connects camera
     * 2. App detects camera model
     * 3. Finds profile with matching [cameraName]
     * 4. Automatically selects that profile
     *
     * Match is case-insensitive and checks both [CameraDevice.name] and [CameraDevice.displayName].
     *
     * Leave blank for profiles not tied to specific camera.
     */
    val cameraName: String = "",

    /**
     * Last used source directory path.
     *
     * Convenience feature that remembers:
     * - Last camera mount point used
     * - Last folder selected for import
     *
     * Pre-populated when profile is selected to save user time.
     */
    val lastSourcePath: String = "",

    /**
     * Last used destination directory path.
     *
     * Remembers user's preferred import location for this profile. Pre-populated when profile is
     * selected.
     */
    val lastDestinationPath: String = "",

    /**
     * Profile creation timestamp.
     *
     * Unix timestamp in milliseconds since epoch. Used for:
     * - Sorting profiles by creation date
     * - Audit trail
     * - Sync conflict resolution
     */
    val createdAt: Long = System.currentTimeMillis(),

    /**
     * Profile last modification timestamp.
     *
     * Updated every time profile is saved. Used for:
     * - Showing "recently modified" indicator
     * - Sort by last used
     * - Sync and backup decisions
     */
    val updatedAt: Long = System.currentTimeMillis()
)

/**
 * Application-wide settings and preferences.
 *
 * This is the root settings object that contains all persisted application configuration. It's
 * loaded on startup and saved whenever settings change.
 *
 * ## Persistence
 *
 * Serialized to JSON and stored in `~/.petrie-importer/settings.json`. Uses Kotlinx Serialization
 * for efficient, type-safe JSON handling.
 *
 * ## Settings Categories
 * 1. **Profiles**: Saved import configurations
 * 2. **Active Profile**: Currently selected profile
 * 3. **Window State**: Window size and position
 * 4. **Theme**: Light/dark/system theme preference
 * 5. **Presets**: Custom folder/filename pattern presets
 *
 * ## State Management
 *
 * In Compose UI, this object is held in mutable state:
 * ```kotlin
 * var settings by remember { mutableStateOf(loadSettings()) }
 *
 * // Update settings
 * settings = settings.copy(theme = AppTheme.DARK)
 * saveSettings(settings)
 * ```
 *
 * @property profiles List of all saved import profiles. Users can create multiple profiles for
 *   different cameras or workflows.
 * @property activeProfileId ID of currently selected profile. Used to restore selection on app
 *   restart.
 * @property windowState Window dimensions and position. Restored on startup for consistent user
 *   experience.
 * @property theme Theme preference (light/dark/system). Applied to entire UI.
 * @property savedFolderPresets User-created folder pattern presets. Shown in pattern selector
 *   dropdown alongside built-ins.
 * @property savedFilenamePresets User-created filename pattern presets. Shown in pattern selector
 *   alongside built-ins.
 * @see ImportProfile Individual import configuration profile
 * @see WindowState Window dimensions and position
 * @see AppTheme Theme selection enum
 * @see PatternPreset Custom pattern preset
 */
@Serializable
data class AppSettings(
    /**
     * All saved import profiles.
     *
     * Users can create multiple profiles for:
     * - Different cameras (Canon, Nikon, Sony, etc.)
     * - Different workflows (quick import, archive, client work)
     * - Different organizational schemes
     *
     * Profiles are displayed in a dropdown selector in the Import screen.
     */
    val profiles: List<ImportProfile> = emptyList(),

    /**
     * ID of currently active/selected profile.
     *
     * Used to:
     * - Restore profile selection on app restart
     * - Quickly access profile via `profiles.find { it.id == activeProfileId }`
     * - Track which profile was last used
     *
     * Null if no profile is selected (using default configuration).
     */
    val activeProfileId: String? = null,

    /**
     * Window state (size and position).
     *
     * Persisted to provide consistent window size across sessions. Restored when application
     * starts.
     */
    val windowState: WindowState = WindowState(),

    /**
     * Theme preference.
     *
     * Controls application color scheme:
     * - [AppTheme.LIGHT]: Always use light theme
     * - [AppTheme.DARK]: Always use dark theme
     * - [AppTheme.SYSTEM]: Follow operating system theme
     *
     * Can be changed via View menu or settings screen.
     */
    val theme: AppTheme = AppTheme.SYSTEM,

    /**
     * User-saved folder pattern presets.
     *
     * Users can create custom folder patterns and save them as presets. These appear in the folder
     * pattern dropdown alongside built-in presets.
     *
     * Example: User creates "Client / Date" pattern and saves it for reuse.
     */
    val savedFolderPresets: List<PatternPreset> = emptyList(),

    /**
     * User-saved filename pattern presets.
     *
     * Similar to [savedFolderPresets] but for filename patterns. Allows users to save and reuse
     * custom naming conventions.
     */
    val savedFilenamePresets: List<PatternPreset> = emptyList()
)

/**
 * Window state information for persistence.
 *
 * Stores window dimensions and position to restore on application restart. This provides a
 * consistent user experience across sessions.
 *
 * ## Platform Considerations
 * - **macOS**: Window position may be managed by system
 * - **Windows**: Full position/size restoration supported
 * - **Linux**: Varies by window manager
 *
 * ## Usage
 *
 * ```kotlin
 * // Save window state
 * val state = WindowState(
 *     width = window.width.value.toInt(),
 *     height = window.height.value.toInt()
 * )
 * settings = settings.copy(windowState = state)
 *
 * // Restore on startup
 * Window(
 *     state = WindowState(
 *         width = settings.windowState.width.dp,
 *         height = settings.windowState.height.dp
 *     )
 * )
 * ```
 *
 * @property width Window width in pixels. Default 1200px provides good desktop experience without
 *   being too large.
 * @property height Window height in pixels. Default 800px fits most screens.
 * @property x Window X position on screen (horizontal). Null to let OS decide.
 * @property y Window Y position on screen (vertical). Null to let OS decide.
 * @property isMaximized Whether window was maximized when last closed. If true, restore to
 *   maximized state.
 * @see AppSettings Contains window state as part of application settings
 */
@Serializable
data class WindowState(
    /**
     * Window width in pixels.
     *
     * Default 1200px provides comfortable desktop workspace. Minimum should be ~800px for
     * usability.
     */
    val width: Int = 1200,

    /**
     * Window height in pixels.
     *
     * Default 800px fits most laptop and desktop screens. Minimum should be ~600px for usability.
     */
    val height: Int = 800,

    /**
     * Window horizontal position on screen.
     *
     * Pixels from left edge of screen. Null to let OS position window (usually centered or
     * cascaded).
     */
    val x: Int? = null,

    /**
     * Window vertical position on screen.
     *
     * Pixels from top edge of screen. Null to let OS position window.
     */
    val y: Int? = null,

    /**
     * Whether window was maximized when last closed.
     *
     * If true, window should be restored to maximized state on startup. Takes precedence over
     * width/height when restoring.
     */
    val isMaximized: Boolean = false
)

/**
 * Application theme selection.
 *
 * Determines the color scheme used throughout the application. Theme changes trigger recomposition
 * of all themed UI elements.
 *
 * ## Theme Options
 * - **LIGHT**: Light color scheme with dark text. Best for bright environments.
 * - **DARK**: Dark color scheme with light text. Reduces eye strain in low light.
 * - **SYSTEM**: Automatically follows operating system theme setting. Most convenient for users who
 *   switch themes during the day.
 *
 * ## Implementation
 *
 * Theme is applied via [PetrieTheme] composable:
 * ```kotlin
 * PetrieTheme(appTheme = settings.theme) {
 *     // All themed UI content
 * }
 * ```
 *
 * When theme changes, Compose automatically recomposes all UI with new colors.
 *
 * @see PetrieTheme Theme composable that applies theme
 * @see LightColorScheme Light theme colors
 * @see DarkColorScheme Dark theme colors
 */
enum class AppTheme {
  /**
   * Light theme - light backgrounds, dark text.
   *
   * Best for:
   * - Bright environments
   * - Daytime use
   * - Users who prefer traditional light UI
   */
  LIGHT,

  /**
   * Dark theme - dark backgrounds, light text.
   *
   * Best for:
   * - Low-light environments
   * - Nighttime use
   * - Reducing eye strain
   * - OLED displays (saves power)
   */
  DARK,

  /**
   * System theme - follows operating system setting.
   *
   * Automatically switches between light and dark based on:
   * - macOS: System Preferences → General → Appearance
   * - Windows: Settings → Personalization → Colors → Choose your mode
   * - Linux: Depends on desktop environment (GNOME, KDE, etc.)
   *
   * Most convenient option for most users.
   */
  SYSTEM
}

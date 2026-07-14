package org.kryspetrie.fileimport.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowState
import org.kryspetrie.fileimport.domain.model.AppSettings
import org.kryspetrie.fileimport.ui.screens.DuplicateScannerScreen
import org.kryspetrie.fileimport.ui.screens.MediaImportScreen
import org.kryspetrie.fileimport.ui.screens.ReorganizeScreen
import org.kryspetrie.fileimport.ui.screens.metadataeditor.MetadataEditorScreen
import org.kryspetrie.fileimport.ui.screens.wizard.WizardContainer
import org.kryspetrie.fileimport.ui.theme.PetrieTheme

/**
 * Defines the available navigation tabs in the Petrie File Importer application.
 *
 * This enumeration represents the main features/screens accessible through the bottom
 * navigation bar. Each tab corresponds to a distinct use case in the photo management workflow:
 * 1. **Media Import**: Import photos/videos from cameras, SD cards, or folders
 * 2. **Photo Scan Import**: Extract individual photos from scanned images
 * 3. **Reorganize**: Reorganize existing photo libraries with new naming/folder patterns
 * 4. **Library Duplicates**: Scan and resolve duplicate photos across the library
 * 5. **Metadata Editor**: Edit EXIF/IPTC/XMP metadata across multiple photos in bulk
 *
 * ## Navigation Pattern
 *
 * Uses a bottom navigation bar (Material Design [NavigationBar]) which is standard for desktop
 * applications with 3-5 main sections. Each tab has:
 * - A descriptive label for clarity
 * - An icon for quick visual recognition
 * - Exclusive selection (only one tab active at a time)
 *
 * ## Adding New Tabs
 *
 * To add a new screen:
 * 1. Add a new enum value with label and icon
 * 2. Create the screen composable in `screens/` package
 * 3. Add a branch in the `when` statement in [PetrieFileImporterApp]
 * 4. Ensure the screen follows the standard settings callback pattern
 *
 * @property label The display name shown to users in the navigation bar. Should be concise (1-2
 *   words) and clearly describe the feature.
 * @property icon The Material Design icon representing this tab. Uses [ImageVector] from
 *   compose.materialIcons for vector graphics.
 * @see PetrieFileImporterApp Main application composable that uses these tabs
 * @see NavigationBar Material Design bottom navigation component
 * @see NavigationBarItem Individual tab item in the navigation bar
 */
private enum class AppTab(val label: String, val icon: ImageVector) {
    /**
     * Media Import tab - standard file import workflow.
     *
     * Provides UI for:
     * - Selecting source (camera, folder, SD card)
     * - Choosing destination directory
     * - Configuring import settings (naming patterns, duplicate detection)
     * - Previewing changes before import
     * - Executing the import with progress tracking
     *
     * @see ImportScreen The composable that implements this tab
     */
    MEDIA_IMPORT("Media Import", Icons.Default.Download),

    /**
     * Photo Scan Import tab - scan printed photos from a single image.
     *
     * Enables users to:
     * - Import an image containing multiple printed photos
     * - Automatically detect photo boundaries using computer vision
     * - Manually refine detection corners for accuracy
     * - Apply perspective correction and rotation
     * - Export individual photos with proper aspect ratios
     *
     * This is useful for digitizing physical photo prints that were photographed together, such as
     * photos on a scanner bed or photos of a photo album page.
     *
     * @see WizardContainer The composable that implements this tab
     */
    PHOTO_SCAN("Photo Scan Import", Icons.Default.DocumentScanner),

    /**
     * Reorganize tab - library reorganization workflow.
     *
     * Allows users to:
     * - Select existing photo library folder
     * - Apply new folder/filename patterns retroactively
     * - Preview all changes before applying
     * - Execute reorganization with undo support
     * - Track reorganization history
     *
     * This is useful when users want to change their organization scheme after already importing
     * photos.
     *
     * @see ReorganizeScreen The composable that implements this tab
     */
    REORGANIZE("Reorganize", Icons.AutoMirrored.Filled.DriveFileMove),

    /**
     * Duplicate Scanner tab - library deduplication workflow.
     *
     * Enables users to:
     * - Scan entire photo library for duplicates
     * - Use multiple detection strategies (hash, EXIF, perceptual hash, SURF)
     * - Review duplicate groups with side-by-side previews
     * - Choose which copies to keep/delete
     * - Resolve duplicates safely with confirmation
     *
     * Helps reclaim storage space and organize photo collections.
     *
     * @see DuplicateScannerScreen The composable that implements this tab
     */
    DUPLICATES("Library Duplicates", Icons.Default.ContentCopy),

    /**
     * Bulk Metadata Editor tab - bulk metadata editing on individual files or folders.
     *
     * Enables users to:
     * - Open a folder of images or select individual files
     * - Edit EXIF metadata (date, camera info, location, subjects) on any photo
     * - Add back-of-photo images to any photo
     * - Save changes by overwriting originals or creating new files
     * - Navigate between images with Previous/Next and a scrollable thumbnail sidebar
     *
     * Unlike the Photo Scan wizard, this operates on existing individual image files rather than
     * photos detected within a scanned image.
     *
     * @see MetadataEditorScreen The composable that implements this tab
     */
    METADATA_EDITOR("Metadata Editor", Icons.Default.Edit),
}

/**
 * Main application composable that renders the Petrie File Importer user interface.
 *
 * This is the root composable for the entire application UI. It sets up the main application
 * structure including:
 * - Theme configuration (light/dark/system)
 * - Navigation bar with five tabs (Import, Photo Scan, Reorganize, Duplicates, Metadata Editor)
 * - Screen content based on selected tab
 * - Settings state management
 *
 * ## Architecture Role
 *
 * Acts as the composition root for the UI layer, similar to how a Spring Boot application's main
 * class acts as the application context root. It:
 * - Initializes the theme system
 * - Manages navigation state (which tab is selected)
 * - Delegates to feature-specific screen composables
 * - Propagates settings changes throughout the UI
 *
 * ## State Management
 *
 * Uses Compose's state management with `remember` and `mutableStateOf` to track the currently
 * selected tab. When the tab changes, Compose automatically recomposes only the affected parts of
 * the UI (the screen content).
 *
 * ## Layout Structure
 *
 * ```
 * ┌─────────────────────────────────────┐
 * │           (Top Bar - optional)      │
 * ├─────────────────────────────────────┤
 * │                                     │
 * │                                     │
 * │         Screen Content              │
 * │   (ImportScreen, etc.)              │
 * │                                     │
 * │                                     │
 * ├─────────────────────────────────────┤
 * │  [Import] [Reorganize] [Duplicates] │
 * │        Navigation Bar               │
 * └─────────────────────────────────────┘
 * ```
 *
 * ## Theming
 *
 * Wraps all content in [PetrieTheme] which applies:
 * - Color scheme (light or dark based on settings)
 * - Typography (font sizes, weights for desktop)
 * - Shapes (corner radius for buttons, cards)
 * - Material Design 3 design tokens
 *
 * ## Usage Example
 *
 * ```kotlin
 * @Composable
 * fun MainWindow() {
 *     val settings = loadSettings()
 *     val windowState = rememberWindowState()
 *
 *     PetrieFileImporterApp(
 *         settings = settings,
 *         onSettingsChange = { saveSettings(it) },
 *         windowState = windowState
 *     )
 * }
 * ```
 *
 * @param settings Current application settings including theme, profiles, and preferences. Changes
 *   to settings trigger UI updates through recomposition.
 * @param onSettingsChange Callback invoked when user modifies settings. Should persist the new
 *   settings and update state. Follows the unidirectional data flow pattern.
 * @param windowState State of the application window (size, position, etc.). Can be used for window
 *   management operations.
 * @param modifier Optional [Modifier] for customizing layout behavior. Defaults to [Modifier] (no
 *   modifications).
 * @see PetrieTheme Application theme configuration
 * @see ImportScreen Import workflow screen
 * @see ReorganizeScreen Library reorganization screen
 * @see DuplicateScannerScreen Duplicate detection screen
 * @see NavigationBar Material Design bottom navigation
 * @see Surface Material Design surface container with background color
 */
@Composable
fun PetrieFileImporterApp(
    /**
     * Current application settings.
     *
     * Contains:
     * - Theme preference (light/dark/system)
     * - Import profiles (saved configurations)
     * - Active profile ID
     * - Window state (dimensions)
     *
     * When this parameter changes, the theme and all screens receive updated settings.
     */
    settings: AppSettings,

    /**
     * Callback for settings changes.
     *
     * Invoked when:
     * - User switches theme (View menu)
     * - User creates/updates/deletes import profiles
     * - User changes any persisted preference
     *
     * Implementation should:
     * 1. Persist settings to disk (via SettingsAdapter)
     * 2. Update the state holder that provides this parameter
     * 3. Trigger recomposition with new settings
     *
     * @param newSettings The updated settings object
     */
    onSettingsChange: (AppSettings) -> Unit,

    /**
     * Window state for the application window.
     *
     * Provides:
     * - Window dimensions (width, height)
     * - Window position (x, y)
     * - Window state (minimized, maximized, fullscreen)
     *
     * Can be used for:
     * - Saving/restoring window position
     * - Responsive layout adjustments
     * - Window management operations
     */
    windowState: WindowState,

    /**
     * Modifier for layout customization.
     *
     * Allows callers to:
     * - Apply padding or margins
     * - Set size constraints
     * - Add click handlers or other interactions
     * - Chain layout modifications
     *
     * Defaults to [Modifier] (no modifications applied).
     */
    modifier: Modifier = Modifier,
) {
    // Apply application theme
    // Theme provides colors, typography, and shapes to all child composables
    // Theme changes trigger recomposition of entire UI tree
    PetrieTheme(settings.theme) {
        // State: Currently selected navigation tab
        // remember{} ensures state survives recomposition
        // mutableStateOf{} makes it observable - changes trigger recomposition
        var currentTab by remember { mutableStateOf(AppTab.MEDIA_IMPORT) }

        // Surface: Material Design container with background color
        // Provides consistent background across the application
        // Fills entire window area
        Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            // Column: Vertical layout container
            // Arranges navigation bar and screen content vertically
            Column(modifier = Modifier.fillMaxSize()) {

                // NavigationBar: Bottom navigation bar with tabs
                // tonalElevation adds subtle shadow for depth
                // Standard Material Design navigation pattern
                NavigationBar(tonalElevation = 1.dp) {
                    // Create navigation item for each tab
                    AppTab.entries.forEach { tab ->
                        NavigationBarItem(
                            // Icon: Visual representation of the tab
                            // Uses 20.dp size for consistency
                            icon = {
                                Icon(
                                    tab.icon,
                                    contentDescription = tab.label,
                                    modifier = Modifier.size(20.dp),
                                )
                            },
                            // Label: Text shown below icon
                            // Uses Material Theme typography for consistency
                            label = {
                                Text(tab.label, style = MaterialTheme.typography.labelSmall)
                            },
                            // Selection state: Highlight when tab is active
                            selected = currentTab == tab,
                            // Click handler: Switch to this tab
                            onClick = { currentTab = tab },
                        )
                    }
                }

                // Box: Container for screen content
                // Fills remaining space after navigation bar
                // Adds 12dp padding around content for breathing room
                Box(modifier = Modifier.fillMaxSize().padding(12.dp)) {
                    // Conditional rendering based on selected tab
                    // Only the active screen is composed (performance optimization)
                    // Each screen receives settings and callback for consistency
                    when (currentTab) {
                        // Import tab: Main photo import workflow
                        AppTab.MEDIA_IMPORT ->
                            MediaImportScreen(
                                settings = settings,
                                onSettingsChange = onSettingsChange,
                            )

                        // Reorganize tab: Library reorganization workflow
                        AppTab.REORGANIZE ->
                            ReorganizeScreen(
                                settings = settings,
                                onSettingsChange = onSettingsChange,
                            )

                        // Duplicates tab: Duplicate detection and resolution
                        AppTab.DUPLICATES ->
                            DuplicateScannerScreen(
                                settings = settings,
                                onSettingsChange = onSettingsChange,
                            )

                        // Photo Scan tab: Multi-photo scan wizard
                        AppTab.PHOTO_SCAN ->
                            WizardContainer(
                                onComplete = { processedPhotos ->
                                    // Log results for debugging
                                    println(
                                        "Photo Scan Complete: ${processedPhotos.size} photos exported"
                                    )
                                    processedPhotos.forEach { photo ->
                                        println(
                                            "  - ${photo.outputPath}" +
                                                " (${photo.dimensions.first}x${photo.dimensions.second})"
                                        )
                                    }
                                    // Switch back to Import tab after completion
                                    currentTab = AppTab.MEDIA_IMPORT
                                },
                                onCancel = { currentTab = AppTab.MEDIA_IMPORT },
                            )

                        // Metadata Editor tab: bulk edit metadata on individual images
                        AppTab.METADATA_EDITOR ->
                            MetadataEditorScreen(
                                settings = settings,
                                onSettingsChange = onSettingsChange,
                            )
                    }
                }
            }
        }
    }
}

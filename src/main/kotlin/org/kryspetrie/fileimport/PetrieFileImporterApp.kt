package org.kryspetrie.fileimport

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.MenuBar
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application
import java.awt.Dimension
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.core.context.startKoin
import org.kryspetrie.fileimport.di.appModule
import org.kryspetrie.fileimport.domain.model.AppTheme
import org.kryspetrie.fileimport.infrastructure.adapter.SettingsAdapter
import org.kryspetrie.fileimport.infrastructure.logging.AppLogger
import org.kryspetrie.fileimport.ui.PetrieFileImporterApp
import org.kryspetrie.fileimport.ui.createAppIcon

/**
 * Main entry point for the Petrie Image Importer desktop application.
 *
 * This is the application bootstrap that initializes the dependency injection container, loads
 * persisted settings, and launches the Compose Desktop window. It serves as the composition root
 * for the entire application, similar to a Spring Boot application's main method.
 *
 * ## Application Lifecycle
 * 1. **CLI Mode Check**: If launched with `--cli` argument, runs command-line interface instead
 * 2. **Koin Initialization**: Starts the Koin dependency injection container with [appModule]
 * 3. **Settings Load**: Retrieves persisted application settings from disk
 * 4. **Window Creation**: Creates the main application window with menu bar
 * 5. **UI Composition**: Renders the [PetrieFileImporterApp] composable
 *
 * ## Architecture Role
 *
 * This file represents the outermost layer of the hexagonal architecture:
 * - Initializes infrastructure (DI container, settings storage)
 * - Creates the UI composition root
 * - Handles application-level concerns (window management, menu bar)
 *
 * ## Threading Model
 *
 * Runs on the main UI thread. Settings are loaded using [runBlocking] to ensure they're available
 * before the UI is composed. After initialization, all UI operations run in the Compose runtime
 * with coroutine support for async operations.
 *
 * @see PetrieFileImporterApp The main composable that renders the UI
 * @see org.kryspetrie.fileimport.cli.main CLI entry point for command-line mode
 * @see org.kryspetrie.fileimport.di.appModule Dependency injection configuration
 */
private const val APP_TITLE = "Petrie Image Importer"

/**
 * Application entry point.
 *
 * Starts the Compose Desktop application lifecycle. This function is called by the JVM when the
 * application launches and handles both GUI and CLI modes.
 *
 * ## Usage
 *
 * ```bash
 * # Launch GUI mode (default)
 * ./gradlew run
 *
 * # Launch CLI mode
 * ./gradlew run --args="--cli import /source /destination"
 * ```
 *
 * ## CLI Mode
 *
 * When the first argument is `--cli`, delegates to the command-line interface implemented in
 * [org.kryspetrie.fileimport.cli.PhotoImportCli]. This allows automation and scripting without
 * launching the GUI.
 *
 * ## GUI Mode
 *
 * In GUI mode:
 * 1. Initializes Koin DI container
 * 2. Loads persisted settings (theme, profiles, window state)
 * 3. Creates application icon from embedded resources
 * 4. Launches Compose application with menu bar and main window
 * 5. Sets up state management for settings changes
 *
 * @param args Command-line arguments. Use `--cli` as first argument for CLI mode.
 * @see org.kryspetrie.fileimport.cli.main CLI implementation
 * @see startKoin Koin dependency injection initialization
 * @see application Compose Desktop application lifecycle
 */
fun main(args: Array<String>) {
    // Check for CLI mode - allows running headless for automation
    if (args.isNotEmpty() && args[0] == "--cli") {
        org.kryspetrie.fileimport.cli.main(args.drop(1).toTypedArray())
        return
    }

    // Initialize dependency injection container
    // This is equivalent to Spring's ApplicationContext initialization
    startKoin { modules(appModule) }

    // Load persisted application settings
    // Settings include: theme preference, import profiles, window dimensions
    val settingsAdapter = SettingsAdapter()
    val settings = runBlocking { settingsAdapter.loadSettings() }

    // Initialize window state with persisted dimensions
    // Uses Compose's dp (density-independent pixels) for consistent sizing across displays
    val windowState =
        WindowState(
            width = settings.windowState.width.dp,
            height = settings.windowState.height.dp,
            placement =
                if (settings.windowState.isMaximized) WindowPlacement.Maximized
                else WindowPlacement.Floating,
        )

    // Generate application icon from embedded PNG resource
    // Icon is used in window title bar, taskbar, and application switcher
    val appIcon = BitmapPainter(createAppIcon(512).toComposeImageBitmap())

    // Start Compose Desktop application lifecycle
    // This creates the composition root and enters the UI event loop
    application {
        // Mutable state holder for current settings
        // Changes to this state trigger recomposition of dependent UI
        val currentSettings = mutableStateOf(settings)

        // Get the application logger
        val appLogger: AppLogger = org.koin.core.context.GlobalContext.get().get()

        // IO scope for async settings persistence — avoids runBlocking() on the AWT thread,
        // which blocks MonotonicFrameClock frame delivery and crashes Compose animations.
        val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        // Callback to persist settings changes
        // Called when user changes theme or other persisted settings.
        // Saves settings asynchronously (not on AWT thread) and updates state immediately.
        val onSettingsChange = { newSettings: org.kryspetrie.fileimport.domain.model.AppSettings ->
            ioScope.launch { settingsAdapter.saveSettings(newSettings) }
            currentSettings.value = newSettings
        }

        // Create main application window
        // Window is the top-level container for all Compose UI content
        Window(
            // Callback when user clicks window close button
            onCloseRequest = {
                // Persist window placement before exiting so we remember maximized state
                val isMax = windowState.placement == WindowPlacement.Maximized
                val updatedSettings =
                    currentSettings.value.copy(
                        windowState =
                            org.kryspetrie.fileimport.domain.model.WindowState(
                                width = windowState.size.width.value.toInt(),
                                height = windowState.size.height.value.toInt(),
                                x = windowState.position.x.value.toInt(),
                                y = windowState.position.y.value.toInt(),
                                isMaximized = isMax,
                            )
                    )
                ioScope.launch { settingsAdapter.saveSettings(updatedSettings) }
                exitApplication()
            },
            // Window dimensions and position
            state = windowState,
            // Window title shown in title bar
            title = APP_TITLE,
            // Application icon for window decorations
            icon = appIcon,
        ) {
            // Enforce minimum window size for proper desktop layout
            window.minimumSize = Dimension(1024, 768)
            // Create application menu bar (File, View, Help)
            // Standard desktop application menu pattern
            MenuBar {
                Menu("File") { Item("Quit", onClick = ::exitApplication) }
                Menu("View") {
                    // Theme switching menu items
                    // Updates persisted settings and triggers UI recomposition
                    Item(
                        "Light Theme",
                        onClick = {
                            onSettingsChange(currentSettings.value.copy(theme = AppTheme.LIGHT))
                        },
                    )
                    Item(
                        "Dark Theme",
                        onClick = {
                            onSettingsChange(currentSettings.value.copy(theme = AppTheme.DARK))
                        },
                    )
                    Item(
                        "System Theme",
                        onClick = {
                            onSettingsChange(currentSettings.value.copy(theme = AppTheme.SYSTEM))
                        },
                    )
                    Separator()
                    Item(
                        "Clear Metadata History",
                        onClick = {
                            onSettingsChange(
                                currentSettings.value.copy(
                                    metadataHistory =
                                        org.kryspetrie.fileimport.domain.model.MetadataHistory()
                                )
                            )
                        },
                    )
                }
                Menu("Help") {
                    Item("View Log File") { appLogger.openLogFileWithSystemViewer() }
                    Item("About $APP_TITLE", onClick = {})
                }
            }

            // Render the main application UI
            // This composable contains all screens, navigation, and state management
            PetrieFileImporterApp(
                // Current application settings (theme, profiles, etc.)
                settings = currentSettings.value,
                // Callback to update and persist settings
                onSettingsChange = onSettingsChange,
                // Window state for potential window management
                windowState = windowState,
            )
        }
    }
}

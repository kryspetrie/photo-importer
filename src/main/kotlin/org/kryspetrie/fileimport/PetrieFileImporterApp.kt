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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.koin.core.context.startKoin
import org.kryspetrie.fileimport.di.appModule
import org.kryspetrie.fileimport.domain.model.AppTheme
import org.kryspetrie.fileimport.domain.model.i18n.StringKey
import org.kryspetrie.fileimport.infrastructure.adapter.SettingsAdapter
import org.kryspetrie.fileimport.domain.port.LocalePort
import org.kryspetrie.fileimport.infrastructure.logging.AppLogger
import org.kryspetrie.fileimport.ui.PetrieFileImporterApp
import org.kryspetrie.fileimport.ui.createAppIcon

/**
 * Main entry point for the PhotoImporter desktop application.
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
private const val APP_TITLE = "PhotoImporter"

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
    // Smart CLI dispatch: detect CLI subcommands and delegate to CLI mode
    // This allows `photo-import scan ./photos/` to just work without --cli prefix
    val cliCommands =
        setOf(
            "import",
            "check-duplicates",
            "reorganize",
            "undo",
            "check-journals",
            "scan",
            "watch",
            "--version",
            "-V",
            "--help",
            "-h",
        )
    if (args.isNotEmpty() && (args[0] in cliCommands || args[0] == "--cli")) {
        // Initialize Koin before CLI invocation so all services are injectable
        startKoin { modules(appModule) }
        val cliArgs = if (args[0] == "--cli") args.drop(1).toTypedArray() else args
        org.kryspetrie.fileimport.cli.main(cliArgs)
        return
    }

    // Initialize dependency injection container
    // This is equivalent to Spring's ApplicationContext initialization
    startKoin { modules(appModule) }

    // Auto-start watch folders that have autoStart enabled
    try {
        val watchManager: org.kryspetrie.fileimport.application.WatchFolderManager =
            org.koin.core.context.GlobalContext.get().get()
        watchManager.startAllAutoStart()
    } catch (_: Exception) {
        // Watch folder auto-start is best-effort; don't block app launch
    }

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
            val localePort: LocalePort = org.koin.core.context.GlobalContext.get().get()
            // Enforce minimum window size for proper desktop layout
            window.minimumSize = Dimension(1024, 768)
            // Create application menu bar (File, View, Help)
            // Standard desktop application menu pattern
            MenuBar {
                Menu(localePort.t(StringKey.MENU_FILE)) {
                    Item(localePort.t(StringKey.MENU_QUIT), onClick = ::exitApplication)
                }
                Menu(localePort.t(StringKey.MENU_VIEW)) {
                    Item(
                        localePort.t(StringKey.MENU_LIGHT_THEME),
                        onClick = {
                            onSettingsChange(currentSettings.value.copy(theme = AppTheme.LIGHT))
                        },
                    )
                    Item(
                        localePort.t(StringKey.MENU_DARK_THEME),
                        onClick = {
                            onSettingsChange(currentSettings.value.copy(theme = AppTheme.DARK))
                        },
                    )
                    Item(
                        localePort.t(StringKey.MENU_SYSTEM_THEME),
                        onClick = {
                            onSettingsChange(currentSettings.value.copy(theme = AppTheme.SYSTEM))
                        },
                    )
                    Separator()
                    Menu(localePort.t(StringKey.MENU_LANGUAGE)) {
                        localePort
                            .availableLocales()
                            .sortedWith(compareBy({ it != "en" }, { it }))
                            .forEach { code ->
                                val selected = currentSettings.value.locale == code
                                Item(
                                    text =
                                        buildString {
                                            append(localePort.nativeLocaleName(code))
                                            if (selected) append(" ✓")
                                        },
                                    onClick = {
                                        onSettingsChange(currentSettings.value.withLocale(code))
                                    },
                                )
                            }
                    }
                    Separator()
                    Item(
                        localePort.t(StringKey.MENU_CLEAR_METADATA_HISTORY),
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
                Menu(localePort.t(StringKey.MENU_HELP)) {
                    Item(localePort.t(StringKey.MENU_VIEW_LOG)) { appLogger.openLogFileWithSystemViewer() }
                    Item(
                        localePort.t(StringKey.MENU_ABOUT, "app" to APP_TITLE),
                        onClick = {},
                    )
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

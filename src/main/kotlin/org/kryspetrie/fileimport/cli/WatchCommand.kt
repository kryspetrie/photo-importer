package org.kryspetrie.fileimport.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.file
import kotlinx.coroutines.runBlocking
import org.kryspetrie.fileimport.application.WatchFolderManager
import org.kryspetrie.fileimport.domain.model.ImportConfiguration
import org.kryspetrie.fileimport.domain.model.WatchFolderConfig

/**
 * CLI command for headless watch folder mode.
 *
 * Monitors a source directory for new/modified image files and automatically imports them to a
 * destination directory. Prints status updates to stderr.
 *
 * Usage:
 * ```
 * photo-import watch ~/Pictures/Incoming ~/Library/Photos
 * photo-import watch ~/Incoming ~/Library --cooldown 3000 --no-recursive
 * photo-import watch ~/Incoming ~/Library --delete-after-import
 * ```
 */
class WatchCommand(private val watchFolderManager: WatchFolderManager) :
    CliktCommand(
        name = "watch",
        help = "Watch a folder for new images and auto-import them (headless mode)",
    ) {

    private val source by
        argument(help = "Source folder to watch for new images")
            .file(mustExist = true, canBeDir = true)

    private val destination by
        argument(help = "Destination folder for imported images").file(canBeDir = true)

    private val cooldown by
        option(
                "--cooldown",
                help = "Cooldown in milliseconds between import batches (default: 5000)",
            )
            .default("5000")

    private val noRecursive by
        option("--no-recursive", help = "Do not watch subdirectories").flag(default = false)

    private val noVerifyHash by
        option("--no-verify-hash", help = "Skip file verification after copy").flag(default = false)

    private val deleteAfterImport by
        option("--delete-after-import", help = "Delete source files after successful import")
            .flag(default = false)

    private val profileName by
        option("--profile", help = "Import profile name (uses default if not specified)")
            .default("")

    override fun run() {
        val cooldownMs = cooldown.toLongOrNull() ?: 5000L

        echo("═══════════════════════════════════════", err = true)
        echo("PhotoImporter - Watch Folder (headless)", err = true)
        echo("═══════════════════════════════════════", err = true)
        echo("Source:      ${source.absolutePath}", err = true)
        echo("Destination: ${destination.absolutePath}", err = true)
        echo("Cooldown:    ${cooldownMs}ms", err = true)
        echo("Recursive:   ${!noRecursive}", err = true)
        echo("Verify:      ${!noVerifyHash}", err = true)
        echo("", err = true)

        val config =
            WatchFolderConfig(
                watchPath = source.absolutePath,
                destinationPath = destination.absolutePath,
                profileName = profileName,
                configuration =
                    ImportConfiguration(
                        verifyAfterCopy = !noVerifyHash,
                        deleteAfterImport = deleteAfterImport,
                    ),
                cooldownMs = cooldownMs,
                recursive = !noRecursive,
                enabled = true,
                autoStart = true,
            )

        // Add config to persistence and start watching
        watchFolderManager.addConfig(config)
        watchFolderManager.startWatching(config)

        echo("Watching for new images... (Ctrl+C to stop)", err = true)
        echo("", err = true)

        // Register shutdown hook for cleanup
        Runtime.getRuntime()
            .addShutdownHook(
                Thread {
                    echo("", err = true)
                    echo("Stopping watch folder...", err = true)
                    watchFolderManager.stopWatching(config.id)
                    watchFolderManager.removeConfig(config.id)
                    echo("Watch folder stopped.", err = true)
                }
            )

        // Block and print status updates until interrupted
        try {
            runBlocking {
                var lastImportCount = 0
                watchFolderManager.statuses.collect { statuses ->
                    val status = statuses[config.id] ?: return@collect
                    if (status.importCount != lastImportCount && status.importCount > 0) {
                        echo(
                            "Imported ${status.lastImportFileCount} file(s) " +
                                "(total: ${status.importCount}) — " +
                                "last: ${formatTime(status.lastImportTime)}",
                            err = true,
                        )
                        lastImportCount = status.importCount
                    }
                    if (status.lastError != null) {
                        echo("Error: ${status.lastError}", err = true)
                    }
                }
            }
        } catch (_: Exception) {
            // Interrupted or cancelled
        }
    }

    private fun formatTime(epochMs: Long): String {
        if (epochMs == 0L) return "never"
        val secondsAgo = (System.currentTimeMillis() - epochMs) / 1000
        return when {
            secondsAgo < 60 -> "${secondsAgo}s ago"
            secondsAgo < 3600 -> "${secondsAgo / 60}m ago"
            else -> "${secondsAgo / 3600}h ago"
        }
    }
}

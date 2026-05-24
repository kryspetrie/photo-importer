package org.kryspetrie.fileimport.infrastructure.adapter

/**
 * Cross-platform utility for OS detection and platform-specific operations.
 *
 * Centralizes all OS-conditional logic so it can be tested and maintained in one place. Every other
 * file should use [Platform] instead of calling `System.getProperty("os.name")` directly.
 *
 * Detection logic matches the conventions used by Apache Commons Lang and JetBrains:
 * - macOS: `os.name` contains "mac" or "darwin"
 * - Windows: `os.name` contains "win"
 * - Linux: everything else that isn't macOS or Windows
 */
object Platform {

    /** Lowercased OS name, cached at class load for performance. */
    val osName: String = System.getProperty("os.name").lowercase()

    /** True when running on macOS (includes Darwin kernel check for macOS on ARM). */
    val isMac: Boolean = osName.contains("mac") || osName.contains("darwin")

    /** True when running on Windows. */
    val isWindows: Boolean = osName.contains("win")

    /** True when running on Linux or other Unix-like systems that aren't macOS. */
    val isLinux: Boolean = !isMac && !isWindows

    // ── Cross-platform file opening ──────────────────────────────────

    /**
     * Opens a file or directory with the system's default application.
     *
     * Platform behavior:
     * - **macOS**: Uses `open` command (covers all file types and directories)
     * - **Windows**: Uses `cmd /c start ""` (handles spaces in paths via empty title arg)
     * - **Linux**: Uses `xdg-open` (freedesktop standard, works on GNOME/KDE/XFCE/etc.)
     *
     * Falls back to [java.awt.Desktop.open] if the platform command fails or is unavailable.
     *
     * @param file The file or directory to open
     * @return `true` if the file was opened successfully, `false` otherwise
     */
    @Suppress("SpreadOperator", "ReturnCount")
    fun openWithSystemViewer(file: java.io.File): Boolean {
        if (!file.exists()) return false

        // Try platform-specific command first (more reliable than Desktop.open)
        val command =
            when {
                isMac -> arrayOf("open", file.absolutePath)
                isWindows -> arrayOf("cmd", "/c", "start", "", file.absolutePath)
                isLinux -> arrayOf("xdg-open", file.absolutePath)
                else -> null
            }

        if (command != null) {
            try {
                val process = ProcessBuilder(*command).redirectErrorStream(true).start()
                process.inputStream.bufferedReader().readText() // drain output to prevent deadlock
                val finished = process.waitFor(5, java.util.concurrent.TimeUnit.SECONDS)
                if (finished && process.exitValue() == 0) return true
                if (!finished) process.destroyForcibly()
            } catch (_: Exception) {
                // Fall through to Desktop.open
            }
        }

        // Fallback: Desktop.open (works on macOS and Windows, may not work on headless Linux)
        return try {
            if (java.awt.Desktop.isDesktopSupported()) {
                java.awt.Desktop.getDesktop().open(file)
                true
            } else {
                false
            }
        } catch (_: Exception) {
            false
        }
    }

    // ── Cross-platform directory discovery ────────────────────────────

    /**
     * Returns platform-appropriate application data directory.
     * - macOS: `~/Library/Application Support/petrie-file-importer/`
     * - Windows: `%APPDATA%/petrie-file-importer/`
     * - Linux: `~/.config/petrie-file-importer/`
     */
    val appDataDir: java.io.File by lazy {
        val dir =
            when {
                isMac ->
                    java.io.File(
                        System.getProperty("user.home"),
                        "Library/Application Support/petrie-file-importer",
                    )
                isWindows ->
                    java.io.File(
                        System.getenv("APPDATA") ?: System.getProperty("user.home"),
                        "petrie-file-importer",
                    )
                else ->
                    java.io.File(System.getProperty("user.home"), ".config/petrie-file-importer")
            }
        dir.apply { mkdirs() }
    }

    /**
     * Returns platform-appropriate log directory.
     * - macOS: `~/Library/Logs/PetrieImageImporter/`
     * - Windows: `%APPDATA%/PetrieImageImporter/logs/`
     * - Linux: `~/.local/share/PetrieImageImporter/logs/`
     */
    val logDir: java.io.File by lazy {
        val dir =
            when {
                isMac ->
                    java.io.File(
                        System.getProperty("user.home"),
                        "Library/Logs/PetrieImageImporter",
                    )
                isWindows ->
                    java.io.File(
                        System.getenv("APPDATA") ?: System.getProperty("user.home"),
                        "PetrieImageImporter/logs",
                    )
                else ->
                    java.io.File(
                        System.getProperty("user.home"),
                        ".local/share/PetrieImageImporter/logs",
                    )
            }
        dir.apply { mkdirs() }
    }

    /**
     * Returns platform-appropriate cache directory.
     * - macOS: `~/Library/Caches/petrie-file-importer/`
     * - Windows: `%LOCALAPPDATA%/petrie-file-importer/cache/`
     * - Linux: `~/.cache/petrie-file-importer/`
     */
    val cacheDir: java.io.File by lazy {
        val dir =
            when {
                isMac ->
                    java.io.File(
                        System.getProperty("user.home"),
                        "Library/Caches/petrie-file-importer",
                    )
                isWindows ->
                    java.io.File(
                        System.getenv("LOCALAPPDATA")
                            ?: System.getenv("APPDATA")
                            ?: System.getProperty("user.home"),
                        "petrie-file-importer/cache",
                    )
                else -> java.io.File(System.getProperty("user.home"), ".cache/petrie-file-importer")
            }
        dir.apply { mkdirs() }
    }

    /**
     * Returns the platform-appropriate FFmpeg binary name.
     * - Windows: `ffmpeg.exe`
     * - macOS/Linux: `ffmpeg`
     */
    val ffmpegBinaryName: String
        get() = if (isWindows) "ffmpeg.exe" else "ffmpeg"

    /**
     * Resolves the FFmpeg executable path.
     *
     * Search order:
     * 1. Bundled binary in app directory (alongside the JAR)
     * 2. System PATH (via `which`/`where`)
     * 3. null (FFmpeg unavailable)
     */
    @Suppress("SpreadOperator", "ReturnCount")
    fun resolveFfmpegPath(): String? {
        val binaryName = ffmpegBinaryName

        // 1. Check for bundled binary alongside the app
        val appDir = appDataDir.parentFile
        val bundled = java.io.File(appDir, binaryName)
        if (bundled.exists() && bundled.canExecute()) return bundled.absolutePath

        // 2. Check the directory the JAR is running from
        val localBin = java.io.File(".", binaryName)
        if (localBin.exists() && localBin.canExecute()) return localBin.absolutePath

        // 3. Platform-specific locations
        when {
            isWindows -> {
                // Check common Windows locations
                val programFiles =
                    listOf(
                        System.getenv("ProgramFiles") ?: "C:\\Program Files",
                        System.getenv("ProgramFiles(x86)") ?: "C:\\Program Files (x86)",
                    )
                for (pf in programFiles) {
                    val ffmpeg = java.io.File(pf, "FFmpeg/$binaryName")
                    if (ffmpeg.exists()) return ffmpeg.absolutePath
                }
            }
            isMac -> {
                // Check Homebrew locations
                val brew = java.io.File("/opt/homebrew/bin/ffmpeg")
                if (brew.exists()) return brew.absolutePath
                val brewIntel = java.io.File("/usr/local/bin/ffmpeg")
                if (brewIntel.exists()) return brewIntel.absolutePath
            }
        }

        // 4. Try system PATH
        return try {
            val findCmd =
                if (isWindows) arrayOf("where", binaryName) else arrayOf("which", binaryName)
            val process = ProcessBuilder(*findCmd).redirectErrorStream(true).start()
            val result = process.inputStream.bufferedReader().readText().trim()
            process.waitFor(3, java.util.concurrent.TimeUnit.SECONDS)
            if (result.isNotBlank() && java.io.File(result).exists()) result else null
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Ejects/unmounts a device at the given mount point.
     *
     * Platform behavior:
     * - **macOS**: Uses `diskutil eject`
     * - **Linux**: Uses `udisksctl unmount` then `udisksctl power-off`
     * - **Windows**: Uses PowerShell to invoke the shell "Eject" verb
     *
     * @param mountPoint The device mount path (e.g., `/Volumes/SD_CARD`, `E:\`)
     * @return `true` if the ejection command succeeded, `false` otherwise
     */
    @Suppress("SpreadOperator", "ReturnCount")
    fun ejectDevice(mountPoint: String): Boolean {
        return try {
            val cmd =
                when {
                    isMac -> arrayOf("diskutil", "eject", mountPoint)
                    isLinux -> arrayOf("udisksctl", "unmount", "-b", mountPoint)
                    isWindows ->
                        arrayOf(
                            "powershell",
                            "-command",
                            "\$shell = New-Object -ComObject Shell.Application; " +
                                "\$drive = \$shell.NameSpace(17).ParseName((Resolve-Path '$mountPoint').Drive.Name); " +
                                "\$drive.InvokeVerb('Eject')",
                        )
                    else -> return false
                }
            val process = ProcessBuilder(*cmd).redirectErrorStream(true).start()
            process.inputStream.bufferedReader().readText() // drain output
            val finished = process.waitFor(10, java.util.concurrent.TimeUnit.SECONDS)
            if (!finished) {
                process.destroyForcibly()
                return false
            }
            process.exitValue() == 0
        } catch (_: Exception) {
            false
        }
    }
}

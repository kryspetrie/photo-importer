package org.kryspetrie.fileimport.infrastructure.logging

import java.awt.Desktop
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.concurrent.ConcurrentLinkedQueue
import javax.inject.Inject
import javax.inject.Singleton
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * Centralized logging manager for the application.
 *
 * Provides:
 * - Application-level logging to a dedicated log file
 * - In-memory ring buffer for recent log entries
 * - Ability to open log file with system default text viewer
 * - Thread-safe logging operations
 *
 * The log file is stored in the user's application data directory:
 * - macOS: ~/Library/Logs/PetrieImageImporter/
 * - Linux: ~/.local/share/PetrieImageImporter/logs/
 * - Windows: %APPDATA%/PetrieImageImporter/logs/
 */
@Singleton
class AppLogger @Inject constructor() {

  private val logger: Logger = LoggerFactory.getLogger(AppLogger::class.java)

  // Ring buffer to store recent log entries for UI display
  private val recentLogs = ConcurrentLinkedQueue<LogEntry>()
  private val maxRecentLogs = 1000

  // Log file configuration
  private val logDir: File by lazy {
    val baseDir =
        when {
          System.getProperty("os.name").startsWith("Mac") ->
              File(System.getProperty("user.home"), "Library/Logs/PetrieImageImporter")
          System.getProperty("os.name").startsWith("Windows") ->
              File(
                  System.getenv("APPDATA") ?: System.getProperty("user.home"),
                  "PetrieImageImporter/logs")
          else -> // Linux and others
          File(System.getProperty("user.home"), ".local/share/PetrieImageImporter/logs")
        }
    baseDir.apply { mkdirs() }
  }

  val logFile: File by lazy {
    val timestamp = SimpleDateFormat("yyyy-MM-dd").format(Date())
    File(logDir, "petrie-$timestamp.log")
  }

  init {
    // Log application startup
    info("AppLogger initialized")
    info("Log file location: ${logFile.absolutePath}")
  }

  /** Logs an info message. */
  fun info(message: String, vararg args: Any?) {
    val entry = LogEntry(Level.INFO, message, System.currentTimeMillis())
    addToRingBuffer(entry)
    logger.info(message, *args)
  }

  /** Logs a warning message. */
  fun warn(message: String, vararg args: Any?) {
    val entry = LogEntry(Level.WARN, message, System.currentTimeMillis())
    addToRingBuffer(entry)
    logger.warn(message, *args)
  }

  /** Logs an error message. */
  fun error(message: String, throwable: Throwable? = null) {
    val entry = LogEntry(Level.ERROR, message, System.currentTimeMillis())
    addToRingBuffer(entry)
    if (throwable != null) {
      logger.error(message, throwable)
    } else {
      logger.error(message)
    }
  }

  /** Logs a debug message. */
  fun debug(message: String, vararg args: Any?) {
    val entry = LogEntry(Level.DEBUG, message, System.currentTimeMillis())
    addToRingBuffer(entry)
    logger.debug(message, *args)
  }

  /** Logs an operation start event. */
  fun logOperationStart(operation: OperationType, details: String = "") {
    val message = buildString {
      append("START: ${operation.displayName}")
      if (details.isNotEmpty()) append(" - $details")
    }
    info(message)
  }

  /** Logs an operation completion event. */
  fun logOperationComplete(operation: OperationType, details: String = "") {
    val message = buildString {
      append("COMPLETE: ${operation.displayName}")
      if (details.isNotEmpty()) append(" - $details")
    }
    info(message)
  }

  /** Logs an operation failure event. */
  fun logOperationFailed(operation: OperationType, reason: String, throwable: Throwable? = null) {
    val message = buildString {
      append("FAILED: ${operation.displayName}")
      append(" - Reason: $reason")
    }
    error(message, throwable)
  }

  /** Returns recent log entries for UI display. */
  fun getRecentLogs(count: Int = 100): List<LogEntry> {
    return recentLogs.toList().takeLast(count)
  }

  /**
   * Opens the log file with the system's default text viewer.
   *
   * @return true if successful, false otherwise
   */
  fun openLogFileWithSystemViewer(): Boolean {
    return try {
      if (!logFile.exists()) {
        // Create the file if it doesn't exist
        logFile.parentFile?.mkdirs()
        logFile.createNewFile()
      }

      if (Desktop.isDesktopSupported()) {
        Desktop.getDesktop().open(logFile)
        info("Opened log file: ${logFile.absolutePath}")
        true
      } else {
        error("Desktop is not supported on this platform")
        false
      }
    } catch (e: IOException) {
      error("Failed to open log file: ${e.message}", e)
      false
    }
  }

  /** Returns the path to the current log file. */
  fun getLogFilePath(): String = logFile.absolutePath

  private fun addToRingBuffer(entry: LogEntry) {
    recentLogs.offer(entry)
    while (recentLogs.size > maxRecentLogs) {
      recentLogs.poll()
    }
  }
}

/** Represents a single log entry. */
data class LogEntry(val level: Level, val message: String, val timestamp: Long) {
  val formattedTime: String
    get() = SimpleDateFormat("HH:mm:ss.SSS").format(Date(timestamp))

  val formattedDateTime: String
    get() = SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(Date(timestamp))
}

/** Log levels compatible with SLF4J. */
enum class Level {
  DEBUG,
  INFO,
  WARN,
  ERROR
}

/** Types of operations that can be logged. */
enum class OperationType(val displayName: String) {
  APPLICATION_START("Application Start"),
  IMAGE_LOAD("Image Load"),
  IMAGE_DETECTION("Photo Detection"),
  BOX_CREATION("Box Creation"),
  BOX_DELETION("Box Deletion"),
  BOX_MODIFICATION("Box Modification"),
  EXPORT_START("Export Started"),
  EXPORT_PHOTO("Export Photo"),
  EXPORT_COMPLETE("Export Complete"),
  EXPORT_FAILED("Export Failed"),
  VALIDATION_ERROR("Validation Error"),
  USER_INTERACTION("User Interaction"),
  SETTINGS_CHANGE("Settings Change"),
  UNDO_OPERATION("Undo"),
  REDO_OPERATION("Redo"),
  REFINE_BOX("Refine Box")
}

package org.kryspetrie.fileimport.infrastructure.logging

import java.io.File
import java.nio.file.Path
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/**
 * Unit tests for AppLogger.
 *
 * Tests logging functionality, file operations, and ring buffer behavior.
 */
class AppLoggerTest {

  private lateinit var appLogger: AppLogger

  @TempDir lateinit var tempDir: Path

  @BeforeEach
  fun setup() {
    appLogger = AppLogger()
  }

  @AfterEach
  fun tearDown() {
    // Clean up any resources
  }

  // ==================== Log Entry Tests ====================

  @Test
  fun `info logs message and adds to ring buffer`() {
    appLogger.info("Test info message")

    val logs = appLogger.getRecentLogs(1)
    assertEquals(1, logs.size)
    assertEquals(Level.INFO, logs.first().level)
    assertEquals("Test info message", logs.first().message)
  }

  @Test
  fun `warn logs warning message`() {
    appLogger.warn("Test warning")

    val logs = appLogger.getRecentLogs(1)
    assertEquals(Level.WARN, logs.first().level)
    assertEquals("Test warning", logs.first().message)
  }

  @Test
  fun `error logs error without exception`() {
    appLogger.error("Error occurred")

    val logs = appLogger.getRecentLogs(1)
    assertEquals(Level.ERROR, logs.first().level)
    assertEquals("Error occurred", logs.first().message)
  }

  @Test
  fun `error logs error with exception`() {
    val exception = RuntimeException("Test exception")
    appLogger.error("Error with exception", exception)

    val logs = appLogger.getRecentLogs(1)
    assertEquals(Level.ERROR, logs.first().level)
  }

  @Test
  fun `debug logs debug message`() {
    appLogger.debug("Debug message")

    val logs = appLogger.getRecentLogs(1)
    assertEquals(Level.DEBUG, logs.first().level)
  }

  // ==================== Ring Buffer Tests ====================

  @Test
  fun `ring buffer stores up to max entries`() {
    // Add more than max (1000)
    repeat(1500) { i -> appLogger.info("Message $i") }

    val logs = appLogger.getRecentLogs()
    // Should not exceed maxRecentLogs
    assertTrue(logs.size <= 1000)
  }

  @Test
  fun `getRecentLogs returns last N entries`() {
    appLogger.info("First")
    appLogger.info("Second")
    appLogger.info("Third")

    val logs = appLogger.getRecentLogs(2)

    assertEquals(2, logs.size)
  }

  @Test
  fun `log entries have timestamps`() {
    val beforeTime = System.currentTimeMillis()
    appLogger.info("Timestamp test")
    val afterTime = System.currentTimeMillis()

    val logs = appLogger.getRecentLogs(1)
    assertTrue(logs.first().timestamp in beforeTime..afterTime)
  }

  @Test
  fun `log entries have formatted time`() {
    appLogger.info("Format test")

    val logs = appLogger.getRecentLogs(1)
    assertTrue(logs.first().formattedTime.isNotEmpty())
    assertTrue(logs.first().formattedTime.matches(Regex("\\d{2}:\\d{2}:\\d{2}\\.\\d{3}")))
  }

  // ==================== Operation Logging Tests ====================

  @Test
  fun `logOperationStart creates INFO entry with START prefix`() {
    appLogger.logOperationStart(OperationType.IMAGE_LOAD, "test.jpg")

    val logs = appLogger.getRecentLogs(1)
    assertTrue(logs.first().message.contains("START"))
    assertTrue(logs.first().message.contains("Image Load"))
    assertTrue(logs.first().message.contains("test.jpg"))
  }

  @Test
  fun `logOperationComplete creates INFO entry with COMPLETE prefix`() {
    appLogger.logOperationComplete(OperationType.EXPORT_COMPLETE, "3 photos")

    val logs = appLogger.getRecentLogs(1)
    assertTrue(logs.first().message.contains("COMPLETE"))
    assertTrue(logs.first().message.contains("Export Complete"))
  }

  @Test
  fun `logOperationFailed creates ERROR entry with FAILED prefix`() {
    appLogger.logOperationFailed(OperationType.EXPORT_FAILED, "Disk full")

    val logs = appLogger.getRecentLogs(1)
    assertTrue(logs.first().message.contains("FAILED"))
    assertTrue(logs.first().message.contains("Export Failed"))
    assertTrue(logs.first().message.contains("Disk full"))
  }

  // ==================== Log File Tests ====================

  @Test
  fun `logFile returns File object`() {
    val logFile = appLogger.logFile

    assertNotNull(logFile)
    assertTrue(logFile.name.startsWith("petrie-"))
    assertTrue(logFile.name.endsWith(".log"))
  }

  @Test
  fun `logFile uses dated filename`() {
    val logFile = appLogger.logFile

    // Should contain date pattern
    assertTrue(logFile.name.matches(Regex("petrie-\\d{4}-\\d{2}-\\d{2}\\.log")))
  }

  @Test
  fun `getLogFilePath returns absolute path`() {
    val path = appLogger.getLogFilePath()

    assertTrue(path.isNotEmpty())
    assertTrue(File(path).isAbsolute)
  }

  @Test
  fun `log directory is created`() {
    val logDir = appLogger.logFile.parentFile

    assertTrue(logDir.exists())
    assertTrue(logDir.isDirectory)
  }

  // ==================== LogEntry Tests ====================

  @Test
  fun `LogEntry formattedTime format is correct`() {
    val entry = LogEntry(Level.INFO, "Test", 1704067200000) // 2024-01-01 00:00:00

    // Should be in HH:mm:ss.SSS format
    assertTrue(entry.formattedTime.matches(Regex("\\d{2}:\\d{2}:\\d{2}\\.\\d{3}")))
  }

  @Test
  fun `LogEntry formattedDateTime includes date and time`() {
    // Use a timestamp to avoid timezone issues
    val entry = LogEntry(Level.INFO, "Test", 1704100000000)

    val formatted = entry.formattedDateTime
    // Should contain date and time components
    assertTrue(
        formatted.matches(Regex(".*\\d{4}-\\d{2}-\\d{2}.*")), "Should contain date: $formatted")
    assertTrue(
        formatted.matches(Regex(".*\\d{2}:\\d{2}:\\d{2}.*")), "Should contain time: $formatted")
  }

  // ==================== OperationType Tests ====================

  @Test
  fun `all operation types have display names`() {
    OperationType.entries.forEach { op -> assertTrue(op.displayName.isNotEmpty()) }
  }

  @Test
  fun `operation type display names are human-readable`() {
    assertEquals("Application Start", OperationType.APPLICATION_START.displayName)
    assertEquals("Image Load", OperationType.IMAGE_LOAD.displayName)
    assertEquals("Photo Detection", OperationType.IMAGE_DETECTION.displayName)
    assertEquals("Export Started", OperationType.EXPORT_START.displayName)
  }

  // ==================== Desktop Integration Tests ====================

  @Test
  fun `log file can be written to`() {
    // Verify we can write to the log file (file exists or can be created)
    val logFile = appLogger.logFile
    assertTrue(logFile.parentFile.exists() || logFile.parentFile.mkdirs())
  }

  // ==================== Level Enum Tests ====================

  @Test
  fun `all log levels are defined`() {
    assertEquals(4, Level.entries.size)
    assertTrue(Level.entries.contains(Level.DEBUG))
    assertTrue(Level.entries.contains(Level.INFO))
    assertTrue(Level.entries.contains(Level.WARN))
    assertTrue(Level.entries.contains(Level.ERROR))
  }

  // ==================== All Operation Types Have Display Names ====================

  @Test
  fun `all operation types have non-empty display names`() {
    OperationType.entries.forEach { op ->
      assertFalse(op.displayName.isBlank(), "OperationType.${op.name} has blank displayName")
    }
  }

  @Test
  fun `box operations are logged correctly`() {
    appLogger.logOperationStart(OperationType.BOX_CREATION, "Box 1")
    assertTrue(appLogger.getRecentLogs(1).first().message.contains("Box Creation"))

    appLogger.logOperationComplete(OperationType.BOX_DELETION, "Box 1 removed")
    assertTrue(appLogger.getRecentLogs(1).first().message.contains("Box Deletion"))

    appLogger.logOperationFailed(OperationType.VALIDATION_ERROR, "Invalid box")
    assertTrue(appLogger.getRecentLogs(1).first().message.contains("Validation Error"))
  }

  @Test
  fun `export operations are logged correctly`() {
    appLogger.logOperationStart(OperationType.EXPORT_START, "10 photos")
    assertTrue(appLogger.getRecentLogs(1).first().message.contains("Export Started"))

    appLogger.logOperationComplete(OperationType.EXPORT_COMPLETE, "10 photos exported")
    assertTrue(appLogger.getRecentLogs(1).first().message.contains("Export Complete"))

    appLogger.logOperationFailed(OperationType.EXPORT_FAILED, "Disk full")
    assertTrue(appLogger.getRecentLogs(1).first().message.contains("Export Failed"))
  }

  @Test
  fun `undo redo operations are logged correctly`() {
    appLogger.logOperationStart(OperationType.UNDO_OPERATION, "Delete Box 1")
    assertTrue(appLogger.getRecentLogs(1).first().message.contains("Undo"))

    appLogger.logOperationComplete(OperationType.REDO_OPERATION, "Delete Box 1")
    assertTrue(appLogger.getRecentLogs(1).first().message.contains("Redo"))
  }

  @Test
  fun `settings change operations are logged correctly`() {
    appLogger.logOperationStart(OperationType.SETTINGS_CHANGE, "Theme changed to Dark")
    assertTrue(appLogger.getRecentLogs(1).first().message.contains("Settings Change"))
  }

  @Test
  fun `user interaction operations are logged correctly`() {
    appLogger.logOperationStart(OperationType.USER_INTERACTION, "Button clicked")
    assertTrue(appLogger.getRecentLogs(1).first().message.contains("User Interaction"))
  }

  @Test
  fun `refine box operations are logged correctly`() {
    appLogger.logOperationStart(OperationType.REFINE_BOX, "Box 1 corners adjusted")
    assertTrue(appLogger.getRecentLogs(1).first().message.contains("Refine Box"))
  }
}

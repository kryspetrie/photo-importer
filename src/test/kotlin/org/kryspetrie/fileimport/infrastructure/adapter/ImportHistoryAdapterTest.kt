package org.kryspetrie.fileimport.infrastructure.adapter

import java.io.File
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.kryspetrie.fileimport.application.TestDispatcherProvider
import org.kryspetrie.fileimport.domain.model.ImportHistoryEntry

@DisplayName("ImportHistoryAdapter")
class ImportHistoryAdapterTest {
    private lateinit var adapter: ImportHistoryAdapter

    @TempDir lateinit var tempDir: File

    @BeforeEach
    fun setup() {
        adapter = ImportHistoryAdapter(tempDir, TestDispatcherProvider())
    }

    @Nested
    @DisplayName("loadHistory")
    inner class LoadHistory {
        @Test
        @DisplayName("should return empty list when no history file exists")
        fun shouldReturnEmptyForNoFile() = runTest {
            val result = adapter.loadHistory()
            assertThat(result).isEmpty()
        }

        @Test
        @DisplayName("should return empty list for corrupt history file")
        fun shouldReturnEmptyForCorruptFile() = runTest {
            File(tempDir, "import_history.json").writeText("not valid json")
            val result = adapter.loadHistory()
            assertThat(result).isEmpty()
        }
    }

    @Nested
    @DisplayName("addEntry")
    inner class AddEntry {
        @Test
        @DisplayName("should add entry to history")
        fun shouldAddEntry() = runTest {
            val entry =
                ImportHistoryEntry(
                    sourcePath = "/source",
                    destinationPath = "/dest",
                    totalFiles = 10,
                    successCount = 9,
                    errorCount = 1,
                    skippedCount = 0,
                    duplicateCount = 0,
                )

            adapter.addEntry(entry)

            val history = adapter.loadHistory()
            assertThat(history).hasSize(1)
            assertThat(history[0].sourcePath).isEqualTo("/source")
            assertThat(history[0].totalFiles).isEqualTo(10)
        }

        @Test
        @DisplayName("should add entries in reverse chronological order")
        fun shouldAddInReverseOrder() = runTest {
            adapter.addEntry(
                ImportHistoryEntry(
                    sourcePath = "/first",
                    destinationPath = "/dest",
                    totalFiles = 1,
                    successCount = 1,
                    errorCount = 0,
                    skippedCount = 0,
                    duplicateCount = 0,
                )
            )
            adapter.addEntry(
                ImportHistoryEntry(
                    sourcePath = "/second",
                    destinationPath = "/dest",
                    totalFiles = 2,
                    successCount = 2,
                    errorCount = 0,
                    skippedCount = 0,
                    duplicateCount = 0,
                )
            )

            val history = adapter.loadHistory()
            assertThat(history).hasSize(2)
            assertThat(history[0].sourcePath).isEqualTo("/second")
            assertThat(history[1].sourcePath).isEqualTo("/first")
        }

        @Test
        @DisplayName("should trim history to 500 entries")
        fun shouldTrimTo500() = runTest {
            repeat(510) { i ->
                adapter.addEntry(
                    ImportHistoryEntry(
                        sourcePath = "/source_$i",
                        destinationPath = "/dest",
                        totalFiles = 1,
                        successCount = 1,
                        errorCount = 0,
                        skippedCount = 0,
                        duplicateCount = 0,
                    )
                )
            }

            val history = adapter.loadHistory()
            assertThat(history.size).isLessThanOrEqualTo(500)
        }
    }

    @Nested
    @DisplayName("clearHistory")
    inner class ClearHistory {
        @Test
        @DisplayName("should clear all history")
        fun shouldClearAllHistory() = runTest {
            adapter.addEntry(
                ImportHistoryEntry(
                    sourcePath = "/source",
                    destinationPath = "/dest",
                    totalFiles = 1,
                    successCount = 1,
                    errorCount = 0,
                    skippedCount = 0,
                    duplicateCount = 0,
                )
            )

            adapter.clearHistory()

            val history = adapter.loadHistory()
            assertThat(history).isEmpty()
        }
    }

    @Nested
    @DisplayName("isSourceFullyImported")
    inner class IsSourceFullyImported {
        @Test
        @DisplayName("should return true for fully imported source")
        fun shouldReturnTrueForFullyImported() = runTest {
            adapter.addEntry(
                ImportHistoryEntry(
                    sourcePath = "/camera",
                    destinationPath = "/library",
                    totalFiles = 50,
                    successCount = 50,
                    errorCount = 0,
                    skippedCount = 0,
                    duplicateCount = 0,
                )
            )

            assertThat(adapter.isSourceFullyImported("/camera", "/library")).isTrue()
        }

        @Test
        @DisplayName("should return false for partially imported source")
        fun shouldReturnFalseForPartialImport() = runTest {
            adapter.addEntry(
                ImportHistoryEntry(
                    sourcePath = "/camera",
                    destinationPath = "/library",
                    totalFiles = 50,
                    successCount = 45,
                    errorCount = 5,
                    skippedCount = 0,
                    duplicateCount = 0,
                )
            )

            assertThat(adapter.isSourceFullyImported("/camera", "/library")).isFalse()
        }

        @Test
        @DisplayName("should return false for unknown source")
        fun shouldReturnFalseForUnknownSource() = runTest {
            assertThat(adapter.isSourceFullyImported("/unknown", "/dest")).isFalse()
        }
    }
}

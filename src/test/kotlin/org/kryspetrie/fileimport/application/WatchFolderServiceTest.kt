package org.kryspetrie.fileimport.application

import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.kryspetrie.fileimport.domain.model.ImportConfiguration
import org.kryspetrie.fileimport.domain.model.WatchFolderConfig
import org.kryspetrie.fileimport.domain.model.WatchFolderStatus
import org.kryspetrie.fileimport.domain.port.DeduplicationPort
import org.kryspetrie.fileimport.domain.port.ImageRepositoryPort
import org.kryspetrie.fileimport.domain.port.NamingPort
import org.mockito.Mockito.mock

@DisplayName("WatchFolderService")
class WatchFolderServiceTest {
    private lateinit var importService: ImportService
    private lateinit var service: WatchFolderService

    @TempDir lateinit var tempDir: File

    @BeforeEach
    fun setup() {
        val imageRepository = mock(ImageRepositoryPort::class.java)
        val deduplicationPort = mock(DeduplicationPort::class.java)
        val namingPort = mock(NamingPort::class.java)
        val importScanner = ImportScanner(imageRepository, null, TestDispatcherProvider())
        val importExecutor = ImportExecutor(imageRepository, namingPort, TestTimeProvider(), TestFileSystemAdapter())
        importService = ImportService(importScanner, importExecutor, deduplicationPort, namingPort)
        service = WatchFolderService(importService, TestTimeProvider(), TestDispatcherProvider())
    }

    @AfterEach
    fun teardown() {
        service.stopWatching()
    }

    @Test
    @DisplayName("should report not watching initially")
    fun shouldNotBeWatchingInitially() {
        assertThat(service.isWatching()).isFalse()
        assertThat(service.status.value.isWatching).isFalse()
    }

    @Test
    @DisplayName("should start watching a valid directory")
    fun shouldStartWatching() = runBlocking {
        val watchDir = File(tempDir, "watch")
        watchDir.mkdirs()
        val destDir = File(tempDir, "dest")
        destDir.mkdirs()

        val config =
            WatchFolderConfig(
                watchPath = watchDir.absolutePath,
                destinationPath = destDir.absolutePath,
                cooldownMs = 100,
            )

        val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        service.startWatching(config, scope)

        delay(500)

        assertThat(service.isWatching()).isTrue()
        assertThat(service.status.value.watchPath).isEqualTo(watchDir.absolutePath)

        service.stopWatching()
        scope.cancel()
    }

    @Test
    @DisplayName("should stop watching and reset status")
    fun shouldStopWatching() = runBlocking {
        val watchDir = File(tempDir, "watch2")
        watchDir.mkdirs()
        val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

        service.startWatching(
            WatchFolderConfig(
                watchPath = watchDir.absolutePath,
                destinationPath = tempDir.absolutePath,
            ),
            scope,
        )

        delay(300)
        service.stopWatching()

        assertThat(service.isWatching()).isFalse()
        assertThat(service.status.value.watchPath).isEmpty()

        scope.cancel()
    }

    @Test
    @DisplayName("should report error for non-existent watch path")
    fun shouldReportErrorForMissingPath() = runBlocking {
        val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        service.startWatching(
            WatchFolderConfig(
                watchPath = "/nonexistent/path/watch",
                destinationPath = tempDir.absolutePath,
            ),
            scope,
        )

        delay(1000)

        assertThat(service.status.value.isWatching).isFalse()
        assertThat(service.status.value.lastError).isNotNull()

        scope.cancel()
    }

    @Test
    @DisplayName("should have default WatchFolderConfig values")
    fun shouldHaveDefaultConfigValues() {
        val config = WatchFolderConfig(watchPath = "/tmp", destinationPath = "/dest")
        assertThat(config.cooldownMs).isEqualTo(5000)
        assertThat(config.recursive).isTrue()
        assertThat(config.configuration).isEqualTo(ImportConfiguration())
    }

    @Test
    @DisplayName("should have default WatchFolderStatus values")
    fun shouldHaveDefaultStatusValues() {
        val status = WatchFolderStatus()
        assertThat(status.isWatching).isFalse()
        assertThat(status.watchPath).isEmpty()
        assertThat(status.filesDetected).isEqualTo(0)
        assertThat(status.autoImportsPending).isEqualTo(0)
        assertThat(status.lastError).isNull()
    }
}

package org.kryspetrie.fileimport.application

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import org.kryspetrie.fileimport.domain.model.WatchFolderConfig
import org.kryspetrie.fileimport.domain.model.WatchFolderStatus
import org.kryspetrie.fileimport.domain.port.DispatcherProvider
import org.kryspetrie.fileimport.domain.port.FileSystemPort
import org.kryspetrie.fileimport.domain.port.SettingsPort
import org.kryspetrie.fileimport.domain.port.TimeProvider

/**
 * Manages multiple [WatchFolderService] instances for simultaneous folder watching.
 *
 * Provides CRUD operations on watch configs, lifecycle management (start/stop individual
 * watches or all at once), and automatic startup of configs marked [WatchFolderConfig.autoStart].
 * Config changes are persisted through [SettingsPort].
 *
 * @see WatchFolderConfig
 * @see WatchFolderService
 */
class WatchFolderManager(
    private val importService: ImportService,
    private val timeProvider: TimeProvider,
    private val dispatcherProvider: DispatcherProvider,
    private val fileSystem: FileSystemPort,
    private val settingsPort: SettingsPort,
) {
    private val services = ConcurrentHashMap<String, WatchFolderService>()
    private val scopeJobs = ConcurrentHashMap<String, Job>()

    private val _statuses = MutableStateFlow<Map<String, WatchFolderStatus>>(emptyMap())
    val statuses: StateFlow<Map<String, WatchFolderStatus>> = _statuses

    private val managerScope = CoroutineScope(SupervisorJob() + dispatcherProvider.default)

    /**
     * Starts watching a folder using the given config.
     */
    fun startWatching(config: WatchFolderConfig) {
        stopWatching(config.id)

        val service = WatchFolderService(importService, timeProvider, dispatcherProvider, fileSystem)
        services[config.id] = service
        val job = SupervisorJob()
        scopeJobs[config.id] = job
        val scope = CoroutineScope(job + dispatcherProvider.default)

        // Observe status changes from the service
        managerScope.launch {
            service.status.collect { status ->
                _statuses.update { it + (config.id to status) }
            }
        }

        service.startWatching(config, scope)
    }

    /**
     * Stops watching a folder by config ID.
     */
    fun stopWatching(configId: String) {
        services[configId]?.stopWatching()
        services.remove(configId)
        scopeJobs[configId]?.cancel()
        scopeJobs.remove(configId)
        _statuses.update { it - configId }
    }

    /** Stops all active watches. */
    fun stopAll() {
        services.keys.toList().forEach { stopWatching(it) }
    }

    /** Stops all watches and cancels the manager scope — call once on shutdown. */
    fun shutdown() {
        stopAll()
        managerScope.cancel()
    }

    /**
     * Starts all watches that have [WatchFolderConfig.autoStart] and [WatchFolderConfig.enabled].
     */
    fun startAllAutoStart() {
        managerScope.launch {
            val settings = settingsPort.observeSettings().first()
            settings.watchConfigs
                .filter { it.autoStart && it.enabled }
                .forEach { config -> startWatching(config) }
        }
    }

    /**
     * Updates a watch config. Restarts the watch if currently active.
     */
    fun updateConfig(config: WatchFolderConfig) {
        val wasWatching = services.containsKey(config.id)
        if (wasWatching) {
            startWatching(config)
        }
        persistConfigUpdate(config)
    }

    /** Returns the current status for a specific watch, or null if not active. */
    fun getStatus(configId: String): WatchFolderStatus? = _statuses.value[configId]

    /** Adds a new watch config and persists it. Does not start watching. */
    fun addConfig(config: WatchFolderConfig) {
        persistConfigUpdate(config)
    }

    /** Removes a watch config and stops watching if active. */
    fun removeConfig(configId: String) {
        stopWatching(configId)
        managerScope.launch {
            val settings = settingsPort.observeSettings().first()
            settingsPort.saveSettings(settings.withoutWatchConfig(configId))
        }
    }

    /** Returns all persisted watch configs. */
    suspend fun getConfigs(): List<WatchFolderConfig> {
        return settingsPort.observeSettings().first().watchConfigs
    }

    private fun persistConfigUpdate(config: WatchFolderConfig) {
        managerScope.launch {
            val settings = settingsPort.observeSettings().first()
            settingsPort.saveSettings(settings.withWatchConfig(config))
        }
    }
}
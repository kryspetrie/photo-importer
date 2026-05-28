package org.kryspetrie.fileimport.infrastructure.adapter

import java.io.File
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import org.kryspetrie.fileimport.domain.model.CameraDevice
import org.kryspetrie.fileimport.domain.model.DeviceType
import org.kryspetrie.fileimport.domain.port.DeviceEvent
import org.kryspetrie.fileimport.domain.port.DevicePort
import org.kryspetrie.fileimport.domain.port.DispatcherProvider

class DeviceAdapter(private val dispatcherProvider: DispatcherProvider) : DevicePort {

    override suspend fun detectDevices(): List<CameraDevice> =
        withContext(dispatcherProvider.io) {
            val roots = discoverMountPoints()
            roots.mapNotNull { root ->
                val dcim = File(root, "DCIM")
                if (!dcim.exists() || !dcim.isDirectory) return@mapNotNull null

                val volumeName = root.name
                val deviceType = inferDeviceType(root)
                val totalSpace = root.totalSpace
                val freeSpace = root.usableSpace

                CameraDevice(
                    id = root.absolutePath,
                    name = volumeName,
                    manufacturer = null,
                    model = volumeName,
                    serialNumber = null,
                    mountPoint = root.absolutePath,
                    storageCapacity = totalSpace,
                    availableSpace = freeSpace,
                    isConnected = true,
                    deviceType = deviceType,
                )
            }
        }

    override fun observeDeviceChanges(): Flow<DeviceEvent> = flow {
        var knownIds = emptySet<String>()
        while (true) {
            val current = detectDevices()
            val currentIds = current.map { it.id }.toSet()

            current.filter { it.id !in knownIds }.forEach { emit(DeviceEvent.Connected(it)) }
            (knownIds - currentIds).forEach { emit(DeviceEvent.Disconnected(it)) }

            knownIds = currentIds
            delay(3000)
        }
    }

    override suspend fun isRemovableDevice(path: String): Boolean =
        withContext(dispatcherProvider.io) {
            val file = File(path)
            if (!file.exists()) return@withContext false
            val mountPoints = discoverMountPoints()
            mountPoints.any { path.startsWith(it.absolutePath) }
        }

    override suspend fun getMountPoints(): List<String> =
        withContext(dispatcherProvider.io) { discoverMountPoints().map { it.absolutePath } }

    override suspend fun ejectDevice(device: CameraDevice): Boolean =
        withContext(dispatcherProvider.io) {
            val mountPoint = device.mountPoint ?: return@withContext false
            Platform.ejectDevice(mountPoint)
        }

    /**
     * Discovers mounted volumes that may contain photos.
     *
     * Platform behavior:
     * - **macOS**: Lists `/Volumes/` (all entries) excluding "Macintosh HD"
     * - **Linux**: Checks `/media/$USER`, `/run/media/$USER`, and `/mnt`
     * - **Windows**: Lists all drive roots except C:\, filtering to removable/media drives
     */
    private fun discoverMountPoints(): List<File> {
        return when {
            Platform.isMac -> {
                val volumes = File("/Volumes")
                volumes
                    .listFiles()
                    ?.filter { it.isDirectory && it.name != "Macintosh HD" }
                    ?.toList() ?: emptyList()
            }
            Platform.isLinux -> {
                val user = System.getProperty("user.name")
                val candidates =
                    listOf(File("/media/$user"), File("/run/media/$user"), File("/mnt"))
                candidates.flatMap {
                    it.listFiles()?.filter { d -> d.isDirectory }?.toList() ?: emptyList()
                }
            }
            Platform.isWindows -> {
                File.listRoots()
                    .filter { root ->
                        root.absolutePath != "C:\\" && root.exists() && root.totalSpace > 0
                    }
                    .toList()
            }
            else -> emptyList()
        }
    }

    private fun inferDeviceType(mountPoint: File): DeviceType {
        val name = mountPoint.name.lowercase()
        return when {
            name.contains("camera") || name.contains("dcim") -> DeviceType.CAMERA
            name.contains("sd") || name.contains("card") || name.contains("eos") ->
                DeviceType.SD_CARD
            name.contains("usb") -> DeviceType.USB_DRIVE
            File(mountPoint, "DCIM").exists() -> DeviceType.CAMERA
            else -> DeviceType.UNKNOWN
        }
    }
}

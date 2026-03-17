package org.kryspetrie.fileimport.infrastructure.adapter

import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import org.kryspetrie.fileimport.domain.model.CameraDevice
import org.kryspetrie.fileimport.domain.model.DeviceType
import org.kryspetrie.fileimport.domain.port.DeviceEvent
import org.kryspetrie.fileimport.domain.port.DevicePort

class DeviceAdapter : DevicePort {

  override suspend fun detectDevices(): List<CameraDevice> =
      withContext(Dispatchers.IO) {
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
              deviceType = deviceType)
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
      withContext(Dispatchers.IO) {
        val file = File(path)
        if (!file.exists()) return@withContext false
        val mountPoints = discoverMountPoints()
        mountPoints.any { path.startsWith(it.absolutePath) }
      }

  override suspend fun getMountPoints(): List<String> =
      withContext(Dispatchers.IO) { discoverMountPoints().map { it.absolutePath } }

  override suspend fun ejectDevice(device: CameraDevice): Boolean =
      withContext(Dispatchers.IO) {
        val mountPoint = device.mountPoint ?: return@withContext false
        try {
          val os = System.getProperty("os.name").lowercase()
          val cmd =
              when {
                os.contains("mac") -> arrayOf("diskutil", "eject", mountPoint)
                os.contains("linux") -> arrayOf("udisksctl", "unmount", "-b", mountPoint)
                else -> return@withContext false
              }
          Runtime.getRuntime().exec(cmd).waitFor() == 0
        } catch (_: Exception) {
          false
        }
      }

  private fun discoverMountPoints(): List<File> {
    val os = System.getProperty("os.name").lowercase()
    return when {
      os.contains("mac") -> {
        val volumes = File("/Volumes")
        volumes.listFiles()?.filter { it.isDirectory && it.name != "Macintosh HD" }?.toList()
            ?: emptyList()
      }
      os.contains("linux") -> {
        val user = System.getProperty("user.name")
        val candidates = listOf(File("/media/$user"), File("/run/media/$user"), File("/mnt"))
        candidates.flatMap {
          it.listFiles()?.filter { d -> d.isDirectory }?.toList() ?: emptyList()
        }
      }
      os.contains("win") -> {
        File.listRoots()
            .filter { root -> root.absolutePath != "C:\\" && root.exists() && root.totalSpace > 0 }
            .toList()
      }
      else -> emptyList()
    }
  }

  private fun inferDeviceType(mountPoint: File): DeviceType {
    val name = mountPoint.name.lowercase()
    return when {
      name.contains("camera") || name.contains("dcim") -> DeviceType.CAMERA
      name.contains("sd") || name.contains("card") || name.contains("eos") -> DeviceType.SD_CARD
      name.contains("usb") -> DeviceType.USB_DRIVE
      File(mountPoint, "DCIM").exists() -> DeviceType.CAMERA
      else -> DeviceType.UNKNOWN
    }
  }
}

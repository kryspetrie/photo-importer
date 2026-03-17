package org.kryspetrie.fileimport.domain.model

data class CameraDevice(
    val id: String,
    val name: String,
    val manufacturer: String?,
    val model: String?,
    val serialNumber: String?,
    val mountPoint: String?,
    val storageCapacity: Long?,
    val availableSpace: Long?,
    val isConnected: Boolean = true,
    val deviceType: DeviceType = DeviceType.CAMERA,
    val lastSeen: Long = System.currentTimeMillis()
) {
  val displayName: String
    get() = name.ifBlank { model ?: "Unknown Device" }

  val formattedCapacity: String
    get() = storageCapacity?.let { formatBytes(it) } ?: "Unknown"

  val formattedAvailableSpace: String
    get() = availableSpace?.let { formatBytes(it) } ?: "Unknown"

  private fun formatBytes(bytes: Long): String {
    return when {
      bytes >= 1_000_000_000 -> String.format("%.1f GB", bytes / 1_000_000_000.0)
      bytes >= 1_000_000 -> String.format("%.1f MB", bytes / 1_000_000.0)
      bytes >= 1_000 -> String.format("%.1f KB", bytes / 1_000.0)
      else -> "$bytes bytes"
    }
  }
}

enum class DeviceType {
  CAMERA,
  SD_CARD,
  USB_DRIVE,
  HARD_DRIVE,
  UNKNOWN
}

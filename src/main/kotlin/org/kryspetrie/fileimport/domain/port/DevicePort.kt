package org.kryspetrie.fileimport.domain.port

import kotlinx.coroutines.flow.Flow
import org.kryspetrie.fileimport.domain.model.CameraDevice

interface DevicePort {
    suspend fun detectDevices(): List<CameraDevice>

    fun observeDeviceChanges(): Flow<DeviceEvent>

    suspend fun isRemovableDevice(path: String): Boolean

    suspend fun getMountPoints(): List<String>

    suspend fun ejectDevice(device: CameraDevice): Boolean
}

sealed class DeviceEvent {
    data class Connected(val device: CameraDevice) : DeviceEvent()

    data class Disconnected(val deviceId: String) : DeviceEvent()

    data class MountChanged(val path: String, val isMounted: Boolean) : DeviceEvent()
}

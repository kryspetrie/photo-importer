# Camera Device Import Plan

> **Status:** Partially Implemented (domain ports & model exist; UI not yet connected)
> **Created:** 2026-05-17  
> **Updated:** 2026-07-14  
> **Scope:** petrie-file-importer — Import from PTP/MTP camera devices that don't mount as USB mass storage (cross-platform: macOS, Linux, Windows)  
> **Related:** DevicePort, DeviceAdapter, MediaImportScreen, PhotoScanImportScreen, [CAMERA_DEVICE_IMPORT_TEST_PLAN.md](./CAMERA_DEVICE_IMPORT_TEST_PLAN.md)

---

## Table of Contents

1. [Executive Summary](#1-executive-summary)
2. [Problem Statement](#2-problem-statement)
3. [Technical Background](#3-technical-background)
4. [Architecture — Cross-Platform Bridge Pattern](#4-architecture--cross-platform-bridge-pattern)
5. [No-Staging Design: Stream to Destination](#5-no-staging-design-stream-to-destination)
6. [Domain Model Changes](#6-domain-model-changes)
7. [Port & Adapter Changes](#7-port--adapter-changes)
8. [Platform Bridges](#8-platform-bridges)
9. [Import Service Changes](#9-import-service-changes)
10. [UI Changes](#10-ui-changes)
11. [Integration with Photo Scan Wizard](#11-integration-with-photo-scan-wizard)
12. [Implementation Phases](#12-implementation-phases)
13. [Validation Strategy](#13-validation-strategy)
14. [Risks & Mitigations](#14-risks--mitigations)
15. [Appendix A: ImageCaptureCore API Reference (macOS)](#appendix-a-imagecapturecore-api-reference-macos)
16. [Appendix B: gphoto2 CLI Reference (Linux)](#appendix-b-gphoto2-cli-reference-linux)
17. [Appendix C: WPD COM API Reference (Windows)](#appendix-c-wpd-com-api-reference-windows)
18. [Appendix D: Bridge Protocol Specification](#appendix-d-bridge-protocol-specification)

---

## 1. Executive Summary

Many cameras (Canon, Fujifilm, Nikon, Sony) do not appear as USB mass storage drives. They use **PTP (Picture Transfer Protocol)** or **MTP (Media Transfer Protocol)**, which requires platform-specific APIs to access. This plan adds **camera device import** to petrie-file-importer, allowing users to select a folder or a connected camera as the import source — on **macOS, Linux, and Windows**.

### Key Capabilities

1. **Cross-platform camera detection** — macOS (ImageCaptureCore), Linux (gphoto2), Windows (WPD/COM)
2. **No staging copies** — Files stream directly from camera to final destination, never doubling disk usage
3. **Camera file browsing** — List files on camera with metadata before importing
4. **Selective import** — Import all, import new, or select individual files
5. **Unified source selector** — Single UI with "Folder" / "Camera" toggle
6. **Hot-plug detection** — Cameras appear/disappear in real time
7. **Delete after import** — Optionally remove files from camera after verification
8. **Photo Scan integration** — Camera files can feed the photo scan wizard

### Design Principles

- **Platform bridges, not JNA** — Each platform has a native bridge tool (Swift CLI / gphoto2 CLI / PowerShell) that the Kotlin app spawns and communicates with via JSON-over-stdin/stdout. This avoids ObjC runtime, JNI, or COM complexity in Java.
- **Stream to destination, never stage** — Downloads go directly to the final import destination. No temp staging directory that doubles disk usage. For Photo Scan, files download one-at-a-time to the destination, not all at once.
- **Existing ports preserved** — `DevicePort` gains PTP devices via composition; `ImageRepositoryPort` gains a streaming download method; a new `CameraImportService` application service provides the camera import entry point.
- **Graceful platform fallback** — On platforms where no bridge is available, the camera source option is hidden. Existing mass-storage detection continues working everywhere.

---

## 2. Problem Statement

### Current Behavior

1. `DeviceAdapter.detectDevices()` scans `/Volumes/` (macOS), `/media/` (Linux), or drive roots (Windows) for directories containing `DCIM/`
2. PTP cameras (Fujifilm, Canon, etc.) **don't mount as filesystem volumes** — they're invisible
3. Users must use OS-specific apps (macOS Image Capture, Linux gphoto2, Windows Explorer) to download files, then manually import the folder

### What Doesn't Work

| Approach | Why It Fails |
|----------|-------------|
| Filesystem paths only | PTP cameras have no filesystem path |
| JNA → ImageCaptureCore | ObjC runtime `objc_msgSend` not natively accessible from JNA |
| JNA → libgphoto2 | C library, requires JNI or JNA, complex build, GPL licensing |
| JNA → WPD COM | COM requires Windows-specific threading (STA), incompatible with JVM threading |
| Staging downloads | Doubles disk usage; large imports could fill the disk |

### What Works

**Platform-specific CLI bridge tools** that:
- Use native OS APIs (ImageCaptureCore / gphoto2 / WPD)
- Communicate via JSON-over-stdin/stdout with the Kotlin app
- Download files directly to the final destination path (no staging)
- Are bundled with the app or discovered on PATH

---

## 3. Technical Background

### PTP/MTP Protocol

- **PTP (ISO 15740)** — Standard protocol for camera-to-computer file transfer
- **MTP** — Extended PTP used by Android and some cameras
- All three platforms include native PTP implementations accessible via platform APIs

### Platform API Comparison

| Capability | macOS (ImageCaptureCore) | Linux (gphoto2) | Windows (WPD/COM) |
|-----------|--------------------------|-----------------|-------------------|
| Detect cameras | `ICDeviceBrowser` | `gphoto2 --auto-detect` | `IPortableDeviceManager` |
| Browse files | `ICCameraDevice.mediaFiles` | `gphoto2 --list-files` | `IPortableDeviceContent.Enum()` |
| Download file | `requestDownload(:options:)` | `gphoto2 --get-file` | `IPortableDeviceResources.GetStream()` |
| Thumbnail | `requestThumbnailData()` | `gphoto2 --get-thumbnail` | WPD resource `STGDFM_THUMBNAIL` |
| Delete file | `requestDeleteFiles()` | `gphoto2 --delete-file` | `IPortableDeviceContent.Delete()` |
| Eject device | `requestEject()` | `gphoto2 --reset` | Device disconnect |
| Hot-plug events | `ICDeviceBrowserDelegate` | `gphoto2 --wait-event` | `WM_DEVICECHANGE` |
| License | Apple framework (proprietary) | GPL-2.0 | Windows API (proprietary) |

---

## 4. Architecture — Cross-Platform Bridge Pattern

```
┌──────────────────────────────────────────────────────────────────────┐
│  UI Layer (Compose)                                                  │
│                                                                      │
│  MediaImportScreen                                                   │
│    Source: [Import from Folder] | [Import from Camera]               │
│    ┌─ Folder: sourcePath + Browse button                             │
│    └─ Camera: detected devices → select → browse → import            │
│                                                                      │
├──────────────────────────────────────────────────────────────────────┤
│  Application Layer                                                    │
│                                                                      │
│  ImportService (folder — existing)                                   │
│    scanSource(path) → List<ImageFile>        ← existing (folder)    │
│    executeImport(...) → ImportResult         ← existing (folder)    │
│                                                                      │
│  CameraImportService (camera — new)                                  │
│    detectCameras() → List<PtpCameraDevice>  ← delegates to port    │
│    browseDevice(id) → List<CameraFile>       ← delegates to port    │
│    importFromCamera(...) → ImportResult      ← stream to dest       │
│    downloadSingleFile(...) → File            ← Photo Scan use case  │
│                                                                      │
├──────────────────────────────────────────────────────────────────────┤
│  Domain Layer                                                         │
│                                                                      │
│  CameraDevicePort (interface)                                        │
│    detectPtpDevices() → List<PtpCameraDevice>                        │
│    browseDevice(id) → List<CameraFile>                                │
│    downloadFile(id, fileId, destination) → File  ← streams to dest   │
│    downloadFiles(id, files, destDir, onProgress) → List<File>        │
│    deleteFiles(id, fileIds) → Boolean                                │
│    ejectDevice(id) → Boolean                                         │
│    observeDeviceChanges() → Flow<PtpDeviceEvent>                     │
│    isAvailable() → Boolean                                           │
│                                                                      │
│  DevicePort (interface — unchanged)                                   │
│    detectDevices() → List<CameraDevice>  ← now includes PTP devices  │
│                                                                      │
├──────────────────────────────────────────────────────────────────────┤
│  Infrastructure Layer — Platform Bridges                              │
│                                                                      │
│  ┌─────────────────────┬────────────────────┬────────────────────┐ │
│  │  macOS               │  Linux               │  Windows           │ │
│  │  ImageCaptureCore    │  gphoto2 CLI         │  WPD/PowerShell    │ │
│  │  BridgeAdapter       │  BridgeAdapter        │  BridgeAdapter    │ │
│  │  (Swift CLI tool)    │  (subprocess)         │  (PS script)     │ │
│  └─────────────────────┴────────────────────┴────────────────────┘ │
│                                                                      │
│  CompositeDeviceAdapter                                               │
│    merges mass-storage devices + PTP devices                         │
│                                                                      │
│  DeviceAdapter (existing — mass storage only)                         │
└──────────────────────────────────────────────────────────────────────┘
```

### Bridge Selection Logic

```kotlin
// At app startup, select the appropriate bridge
fun createCameraDevicePort(): CameraDevicePort {
    val os = System.getProperty("os.name").lowercase()
    return when {
        os.contains("mac") || os.contains("darwin") -> {
            // Prefer ImageCaptureCore bridge (bundled binary)
            // Fall back to gphoto2 if available on PATH
            val bridge = ImageCaptureCoreBridgeAdapter()
            if (bridge.isAvailable()) bridge
            else Gphoto2BridgeAdapter().takeIf { it.isAvailable() }
                ?: NoOpCameraDevicePort()
        }
        os.contains("linux") -> {
            Gphoto2BridgeAdapter().takeIf { it.isAvailable() }
                ?: NoOpCameraDevicePort()
        }
        os.contains("win") -> {
            WpdBridgeAdapter().takeIf { it.isAvailable() }
                ?: NoOpCameraDevicePort()
        }
        else -> NoOpCameraDevicePort()
    }
}
```

---

## 5. No-Staging Design: Stream to Destination

### Problem with Staging

If we download camera files to a temp directory, then copy to the final destination:
- **Disk usage doubles** during import (staging copy + destination copy)
- Large imports (50+ GB from a 128 GB SD card) could fill the disk
- Cleanup complexity (what if the app crashes mid-import?)

### Solution: Stream Directly to Destination

The import pipeline **downloads each camera file directly to its final destination path**. The naming and folder resolution is computed *before* download, so the bridge tool receives the exact destination path.

```
Traditional (with staging):
  Camera → /tmp/staging/DSCF1234.RAF → /dest/2026/05/DSCF1234.RAF
  (2× disk usage)

Stream-to-destination (this plan):
  Camera → /dest/2026/05/DSCF1234.RAF
  (1× disk usage)
```

### How It Works

1. **Browse phase**: `browseDevice()` returns `List<CameraFile>` with metadata (name, size, date, type)
2. **Planning phase**: `CameraImportService` computes the destination path for each file *before* downloading, using `NamingPort.generateFolderPath()` and `generateFileName()`
3. **Download phase**: For each file, call `downloadFile(deviceId, fileId, destinationPath=computedDestPath)` — the bridge streams directly to that path
4. **Verification phase**: If `verifyAfterCopy` is enabled, compute hash of the destination file and compare against the camera-reported file size
5. **Delete phase**: If `deleteAfterImport` is enabled and all files succeeded, call `deleteFiles()` on the camera

### Photo Scan: One-at-a-Time Download

For Photo Scan, the wizard needs a `BufferedImage` loaded from a local file. Since we can't load from a PTP device path directly:

1. User selects a camera and a file on the camera
2. `downloadFile()` streams that single file directly to the **destination directory** (user's configured output folder)
3. The wizard loads the downloaded file with `ImageIO.read(file)`
4. When the user advances to the next batch image, the next file downloads

This means the destination directory accumulates files as the user works through them, but never doubles. If the user cancels mid-wizard, the partially-imported files remain in the destination (which is fine — they're valid files that can be cleaned up or kept).

### Disk Space Check Before Import

Before starting a camera import, `CameraImportService` checks available disk space:

```kotlin
suspend fun importFromCamera(
    device: PtpCameraDevice,
    selectedFiles: List<CameraFile>,
    destinationPath: String,
    configuration: ImportConfiguration,
    onProgress: (CameraImportProgress) -> Unit = {}
): ImportResult {
    val totalBytes = selectedFiles.sumOf { it.size }
    val availableBytes = File(destinationPath).usableSpace
    val safetyMargin = 1.05 // 5% safety margin for filesystem overhead, metadata, sidecars
    
    if (availableBytes < totalBytes * safetyMargin) {
        throw InsufficientDiskSpaceException(
            "Need ${formatBytes(totalBytes)} but only ${formatBytes(availableBytes)} available " +
            "at $destinationPath (including 5% safety margin)"
        )
    }
    // ... proceed with download-to-destination ...
}
```

---

## 6. Domain Model Changes

### New Models

#### `PtpCameraDevice`

```kotlin
// domain/model/PtpCameraDevice.kt
@Serializable
data class PtpCameraDevice(
    val id: String,                    // Unique device ID (platform-specific)
    val name: String,                   // e.g., "Fujifilm X-T5"
    val manufacturer: String?,          // e.g., "Fujifilm"
    val model: String?,                 // e.g., "X-T5"
    val serialNumber: String?,          // Device serial if available
    val transport: String,              // "USB", "MassStorage", "FireWire", "Bluetooth"
    val fileCount: Int = 0,            // Number of files on device (0 until browse)
    val capacity: Long? = null,         // Total storage in bytes
    val available: Long? = null,        // Available storage in bytes
    val capabilities: Set<CameraCapability> = emptySet(),
    val isConnected: Boolean = true,
    val platform: String = "",          // "macos", "linux", "windows"
    val bridgeType: String = "",        // "imagecapturecore", "gphoto2", "wpd"
    val lastSeen: Long = System.currentTimeMillis()
) {
    val displayName: String
        get() = buildString {
            append(name.ifBlank { model ?: "Unknown Camera" })
            manufacturer?.let { append(" ($it)") }
        }

    val formattedCapacity: String
        get() = capacity?.let { formatBytes(it) } ?: "Unknown"

    val formattedAvailable: String
        get() = available?.let { formatBytes(it) } ?: "Unknown"

    val supportsDelete: Boolean
        get() = CameraCapability.DELETE_ONE in capabilities || CameraCapability.DELETE_ALL in capabilities

    private fun formatBytes(bytes: Long): String = when {
        bytes >= 1_000_000_000 -> String.format("%.1f GB", bytes / 1_000_000_000.0)
        bytes >= 1_000_000 -> String.format("%.1f MB", bytes / 1_000_000.0)
        bytes >= 1_000 -> String.format("%.1f KB", bytes / 1_000.0)
        else -> "$bytes bytes"
    }
}

@Serializable
enum class CameraCapability {
    DELETE_ONE,        // Can delete individual files
    DELETE_ALL,        // Can delete all files (bulk)
    TAKE_PICTURE,      // Remote shutter release
    PTP_COMMANDS,      // Raw PTP command support
    HEIF_SUPPORT,      // Supports HEIF format
    RECEIVE_FILE,       // Can receive files (upload to camera)
    THUMBNAIL,          // Can provide thumbnails
    METADATA,           // Can provide EXIF/metadata without download
    PARTIAL_READ         // Can read arbitrary byte ranges
}
```

#### `CameraFile`

```kotlin
// domain/model/CameraFile.kt
@Serializable
data class CameraFile(
    val id: String,                     // Platform-specific file ID
    val name: String,                    // e.g., "DSCF1234.RAF"
    val folder: String,                 // e.g., "/DCIM/100FUJI/"
    val size: Long,                      // File size in bytes
    val creationDate: String?,           // ISO 8601 date string
    val modificationDate: String?,       // ISO 8601 date string
    val fileType: CameraFileType,        // File type classification
    val width: Int? = null,              // Image width in pixels
    val height: Int? = null,             // Image height in pixels
    val hasThumbnail: Boolean = false,
    val isLocked: Boolean = false,       // Protected on camera
    val sidecarFiles: List<CameraFile> = emptyList()  // Associated sidecars (XMP, etc.)
) {
    val isRaw: Boolean get() = fileType == CameraFileType.RAW
    val isJpeg: Boolean get() = fileType == CameraFileType.JPEG
    val isVideo: Boolean get() = fileType == CameraFileType.VIDEO
    val isSidecar: Boolean get() = fileType == CameraFileType.SIDECAR
    val fileExtension: String get() = name.substringAfterLast('.', "")
    
    /** Convert to ImageFile for the import pipeline. */
    fun toImageFile(destFile: java.io.File): ImageFile = ImageFile(
        file = destFile,
        fileName = destFile.name,
        filePath = destFile.absolutePath,
        fileSize = size,
        fileType = when (fileType) {
            CameraFileType.RAW -> ImageFileType.RAW
            CameraFileType.JPEG -> ImageFileType.JPEG
            CameraFileType.VIDEO -> ImageFileType.VIDEO
            else -> ImageFileType.OTHER
        },
        importStatus = ImportStatus.PENDING
    )
}

@Serializable
enum class CameraFileType {
    RAW, JPEG, VIDEO, SIDECAR, OTHER;

    companion object {
        fun fromExtension(ext: String): CameraFileType = when (ext.lowercase()) {
            "raf", "cr2", "cr3", "nef", "arw", "dng", "orf", "rw2", "pef", "srw", "x3f", "3fr", "iiq", "kdc", "mef", "mos", "mrw", "nrw", "ptx", "pxn", "r3d", "raw", "rwl", "srf", "sr2" -> RAW
            "jpg", "jpeg", "jpe" -> JPEG
            "mp4", "mov", "avi", "mkv", "m4v", "3gp", "wmv", "flv" -> VIDEO
            "xmp", "aae", "xml" -> SIDECAR
            else -> OTHER
        }
    }
}
```

#### `CameraImportProgress`

```kotlin
// domain/model/CameraImportProgress.kt
data class CameraImportProgress(
    val phase: ImportPhase,
    val currentFile: Int,
    val totalFiles: Int,
    val fileName: String,
    val bytesDownloaded: Long,
    val totalBytes: Long,
    val bytesCopied: Long = 0           // For verification phase
) {
    enum class ImportPhase {
        PLANNING,        // Computing destination paths
        DOWNLOADING,     // Streaming from camera to destination
        VERIFYING,       // Hash verification
        DELETING,        // Deleting from camera (if enabled)
        COMPLETE         // All done
    }

    val downloadPercentage: Float
        get() = if (totalBytes > 0) bytesDownloaded.toFloat() / totalBytes else 0f

    val overallPercentage: Float
        get() = if (totalFiles > 0) currentFile.toFloat() / totalFiles else 0f
}
```

#### `ImportSource`

```kotlin
// ui/screens/model/ImportSource.kt
enum class ImportSource {
    FOLDER,  // Traditional filesystem folder
    CAMERA   // PTP/MTP camera device
}
```

> **Design note:** `ImportSource` is a UI-only display state, not a persisted domain concept. It determines which source panel is shown on the import screen. It is NOT saved to settings — the source is always re-evaluated on startup (auto-selecting camera if one is plugged in). Contrast with `ImportMode` (ALL/NEW/SELECT) which is a persistent user preference.

### Updated Existing Models

#### `CameraDevice` — Extended for Device Unification

The existing `CameraDevice` model remains as-is. PTP-specific information lives in the separate `PtpCameraDevice` model. The UI and application layer unify them through composition, not by polluting `CameraDevice`:

```kotlin
// domain/model/CameraDevice.kt — NO CHANGES to existing model
// PtpCameraDevice is a separate model, not a subtype
// The application layer maps between PtpCameraDevice → display info
// The CameraDevice model stays focused on mass-storage concerns (mountPoint, etc.)
```

> **Architecture note:** Why not add `isPtpDevice` and `ptpDeviceId` to `CameraDevice`? In hexagonal architecture, domain models should express the core business concept they represent. `CameraDevice` represents a mounted filesystem volume — that's its bounded context. PTP cameras are a different bounded context (devices accessed via protocol, not filesystem). Adding PTP fields to `CameraDevice` would mean every folder-import code path sees `null` PTP fields forever, violating the Single Responsibility Principle and coupling two bounded contexts in one model.

#### `ImageFile` — No Camera Fields Added

The core `ImageFile` model is NOT extended with camera-specific fields (`cameraSource`, `cameraFile`). Camera import produces `ImageFile` instances the same way folder import does — files end up at a destination path with metadata. The camera origin is a one-time concern that doesn't need to be persisted in the domain model.

```kotlin
// domain/model/ImageFile.kt — NO CHANGES
// Camera import creates ImageFile instances pointing at the destination path,
// just like folder import creates them pointing at the source path.
// The fact that a file came from a camera vs. a folder is an import-time concern,
// not a persistent domain property of the image.
```

> **Why not add `cameraSource: PtpCameraDevice?` to `ImageFile`?** `ImageFile` is the central domain object used by every import flow. Adding a nullable camera reference means every `ImageFile` created from a folder scan carries a permanent `null` for that field. Camera download progress tracking belongs in the application service (`CameraImportService`) and `CameraImportProgress`, not in the persistent domain model. Once a file is downloaded from the camera to disk, it's just a file — identical to one copied from a folder.

---

## 7. Port & Adapter Changes

### New Port: `CameraDevicePort`

```kotlin
// domain/port/CameraDevicePort.kt
interface CameraDevicePort {
    /** Detect connected PTP camera devices. */
    suspend fun detectPtpDevices(): List<PtpCameraDevice>

    /** Observe real-time device connect/disconnect events. */
    fun observePtpDeviceChanges(): Flow<PtpDeviceEvent>

    /** Browse all files on a camera device. Opens a session if needed. */
    suspend fun browseDevice(deviceId: String): List<CameraFile>

    /**
     * Download a single file from camera directly to destination path.
     * NO STAGING — streams directly to destination.
     *
     * @param deviceId Camera device ID
     * @param fileId File ID on the camera
     * @param destinationPath Absolute path where the file should be saved
     * @return The downloaded File if successful
     */
    suspend fun downloadFile(deviceId: String, fileId: String, destinationPath: String): File

    /**
     * Download multiple files directly to a destination directory.
     * NO STAGING — each file streams directly to destDir/fileName.
     *
     * @param deviceId Camera device ID
     * @param files List of (fileId, fileName) pairs
     * @param destDir Destination directory (must exist)
     * @param onProgress Progress callback with per-file and overall progress
     * @return List of downloaded files
     */
    suspend fun downloadFiles(
        deviceId: String,
        files: List<Pair<String, String>>,
        destDir: String,
        onProgress: (CameraImportProgress) -> Unit = {}
    ): List<File>

    /** Download a thumbnail for a camera file. Returns local file path or null. */
    suspend fun downloadThumbnail(
        deviceId: String,
        fileId: String,
        width: Int = 256,
        height: Int = 256
    ): File?

    /** Delete files from the camera. Requires DELETE capability. */
    suspend fun deleteFiles(deviceId: String, fileIds: List<String>): Boolean

    /** Eject/safely disconnect the camera. */
    suspend fun ejectDevice(deviceId: String): Boolean

    /** Check if the bridge is available and functional on this platform. */
    fun isAvailable(): Boolean
}

sealed class PtpDeviceEvent {
    data class Connected(val device: PtpCameraDevice) : PtpDeviceEvent()
    data class Disconnected(val deviceId: String) : PtpDeviceEvent()
}
```

### Base Class: `CameraBridgeAdapter`

Common logic for all platform bridges (process spawning, JSON protocol, error handling):

```kotlin
// infrastructure/adapter/camera/CameraBridgeAdapter.kt
abstract class CameraBridgeAdapter : CameraDevicePort {
    protected val logger = AppLogger()

    /**
     * Subclasses provide the bridge binary path or command.
     * Returns null if the bridge is not available on this platform.
     */
    protected abstract fun getBridgeCommand(): List<String>?

    /** Parse a JSON response from the bridge into a domain object. */
    protected abstract fun parseDevices(json: JsonObject): List<PtpCameraDevice>

    /** Parse a JSON response from the bridge into a list of camera files. */
    protected abstract fun parseFiles(json: JsonObject): List<CameraFile>

    /** Run a command and return its stdout as a JsonObject. */
    protected suspend fun runCommand(vararg args: String): JsonObject =
        withContext(Dispatchers.IO) {
            val command = getBridgeCommand() ?: throw CameraDeviceException("Bridge not available")
            val processBuilder = ProcessBuilder(command + args)
                .redirectErrorStream(true)
            val process = processBuilder.start()
            val output = process.inputStream.bufferedReader().readText()
            process.waitFor(30, TimeUnit.SECONDS)

            if (process.exitValue() != 0) {
                throw CameraDeviceException("Bridge command failed: $output")
            }

            gson.fromJson(output, JsonObject::class.java)
                ?: throw CameraDeviceException("Empty response from bridge")
        }

    // ... standard implementations of detectPtpDevices, browseDevice, downloadFile, etc.
    // that call the bridge and parse JSON responses
}
```

### macOS Adapter: `ImageCaptureCoreBridgeAdapter`

Uses the bundled Swift CLI tool (`petrie-camera-bridge`):

```kotlin
// infrastructure/adapter/camera/ImageCaptureCoreBridgeAdapter.kt
class ImageCaptureCoreBridgeAdapter : CameraBridgeAdapter() {

    override fun getBridgeCommand(): List<String>? {
        val os = System.getProperty("os.name").lowercase()
        if (!os.contains("mac")) return null

        // Try alongside app first, then classpath extraction
        val localBridge = File(appDir, "petrie-camera-bridge")
        if (localBridge.exists() && localBridge.canExecute()) return listOf(localBridge.absolutePath)

        val classpathBridge = extractFromClasspath()
        if (classpathBridge != null && classpathBridge.canExecute()) return listOf(classpathBridge.absolutePath)

        return null
    }

    override fun isAvailable(): Boolean = getBridgeCommand() != null

    // macOS-specific long-running bridge for device events
    private var bridgeProcess: Process? = null
    private var bridgeInput: BufferedWriter? = null
    private var bridgeOutput: BufferedReader? = null

    override fun observePtpDeviceChanges(): Flow<PtpDeviceEvent> = flow {
        ensureBridgeRunning()
        bridgeOutput?.let { reader ->
            while (true) {
                val line = withContext(Dispatchers.IO) { reader.readLine() } ?: break
                val event = parseBridgeEvent(line)
                event?.let { emit(it) }
            }
        }
    }

    // macOS bridge uses long-running process with JSON-stdin/stdout protocol
    // (See Section 8.1 for Swift bridge specification)
}
```

### Linux Adapter: `Gphoto2BridgeAdapter`

Uses the `gphoto2` CLI tool (must be installed separately or bundled):

```kotlin
// infrastructure/adapter/camera/Gphoto2BridgeAdapter.kt
class Gphoto2BridgeAdapter : CameraBridgeAdapter() {

    override fun getBridgeCommand(): List<String>? {
        val os = System.getProperty("os.name").lowercase()
        if (!os.contains("linux") && !os.contains("mac")) return null

        // Check if gphoto2 is on PATH
        return try {
            val which = ProcessBuilder("which", "gphoto2")
                .redirectErrorStream(true).start()
            val path = which.inputStream.bufferedReader().readLine()?.trim()
            which.waitFor(5, TimeUnit.SECONDS)
            if (which.exitValue() == 0 && path != null) listOf("gphoto2") else null
        } catch (_: Exception) { null }
    }

    override fun isAvailable(): Boolean = getBridgeCommand() != null

    override suspend fun detectPtpDevices(): List<PtpCameraDevice> = withContext(Dispatchers.IO) {
        val result = runCommand("--auto-detect")
        // Parse gphoto2 --auto-detect output
        // Format: lines of "Model    Port"
        parseGphoto2Devices(result)
    }

    override suspend fun browseDevice(deviceId: String): List<CameraFile> = withContext(Dispatchers.IO) {
        // gphoto2 --list-files --port <port>
        val result = runCommand("--list-files", "--port", deviceId)
        parseGphoto2Files(result)
    }

    override suspend fun downloadFile(
        deviceId: String,
        fileId: String,
        destinationPath: String
    ): File = withContext(Dispatchers.IO) {
        // gphoto2 --get-file <n> --filename <dest> --port <port>
        // --filename allows specifying exact destination path
        val destDir = File(destinationPath).parentFile
        destDir.mkdirs()
        runCommand("--get-file", fileId, "--filename", destinationPath, "--port", deviceId)
        File(destinationPath)
    }

    // gphoto2 uses individual CLI invocations (no long-running process)
    // Each command spawns gphoto2, which opens camera, does operation, closes
}
```

### Windows Adapter: `WpdBridgeAdapter`

Uses a PowerShell script or small .NET exe wrapping WPD COM:

```kotlin
// infrastructure/adapter/camera/WpdBridgeAdapter.kt
class WpdBridgeAdapter : CameraBridgeAdapter() {

    override fun getBridgeCommand(): List<String>? {
        val os = System.getProperty("os.name").lowercase()
        if (!os.contains("win")) return null

        // Try bundled exe first, then PowerShell script
        val localExe = File(appDir, "petrie-wpd-bridge.exe")
        if (localExe.exists()) return listOf(localExe.absolutePath)

        val psScript = extractFromClasspath("bridge/petrie-wpd-bridge.ps1")
        if (psScript != null) return listOf("powershell", "-ExecutionPolicy", "Bypass", "-File", psScript.absolutePath)

        return null
    }

    override fun isAvailable(): Boolean = getBridgeCommand() != null
}
```

### `NoOpCameraDevicePort`

Fallback for unsupported platforms:

```kotlin
// infrastructure/adapter/camera/NoOpCameraDevicePort.kt
class NoOpCameraDevicePort : CameraDevicePort {
    override suspend fun detectPtpDevices() = emptyList<PtpCameraDevice>()
    override fun observePtpDeviceChanges() = emptyFlow<PtpDeviceEvent>()
    override suspend fun browseDevice(deviceId: String) = emptyList<CameraFile>()
    override suspend fun downloadFile(deviceId: String, fileId: String, destinationPath: String) =
        throw CameraDeviceException("Camera device access not available on this platform")
    override suspend fun downloadFiles(deviceId: String, files: List<Pair<String, String>>, destDir: String, onProgress: (CameraImportProgress) -> Unit) =
        throw CameraDeviceException("Camera device access not available on this platform")
    override suspend fun downloadThumbnail(deviceId: String, fileId: String, width: Int, height: Int) = null
    override suspend fun deleteFiles(deviceId: String, fileIds: List<String>) = false
    override suspend fun ejectDevice(deviceId: String) = false
    override fun isAvailable() = false
}
```

### `CompositeDeviceAdapter` — Merge Mass Storage + PTP

```kotlin
// infrastructure/adapter/CompositeDeviceAdapter.kt
class CompositeDeviceAdapter(
    private val storageDetector: DevicePort,       // Existing DeviceAdapter
    private val cameraDetector: CameraDevicePort   // ImageCaptureCoreBridge, Gphoto2, or NoOp
) : DevicePort by storageDetector {

    override suspend fun detectDevices(): List<CameraDevice> {
        val storageDevices = storageDetector.detectDevices()
        val ptpDevices = try {
            cameraDetector.detectPtpDevices().map { it.toCameraDevice() }
        } catch (_: Exception) { emptyList() }
        return storageDevices + ptpDevices
    }

    override fun observeDeviceChanges(): Flow<DeviceEvent> {
        return merge(
            storageDetector.observeDeviceChanges(),
            cameraDetector.observePtpDeviceChanges().map { it.toDeviceEvent() }
        )
    }

    override suspend fun ejectDevice(device: CameraDevice): Boolean {
        return if (device.isPtpDevice && device.ptpDeviceId != null) {
            cameraDetector.ejectDevice(device.ptpDeviceId)
        } else {
            storageDetector.ejectDevice(device)
        }
    }
}
```

### DI Registration

```kotlin
// di/AppModule.kt — additions

single<CameraDevicePort> { createCameraDevicePort() }

single<DevicePort> {
    CompositeDeviceAdapter(
        storageDetector = DeviceAdapter(),    // existing
        cameraDetector = get<CameraDevicePort>()
    )
}

single { CameraImportService(cameraDevicePort = get(), namingPort = get(), historyAdapter = get()) }

fun createCameraDevicePort(): CameraDevicePort {
    val os = System.getProperty("os.name").lowercase()
    return when {
        os.contains("mac") || os.contains("darwin") -> {
            val icBridge = ImageCaptureCoreBridgeAdapter()
            if (icBridge.isAvailable()) icBridge
            else Gphoto2BridgeAdapter().takeIf { it.isAvailable() } ?: NoOpCameraDevicePort()
        }
        os.contains("linux") -> {
            Gphoto2BridgeAdapter().takeIf { it.isAvailable() } ?: NoOpCameraDevicePort()
        }
        os.contains("win") -> {
            WpdBridgeAdapter().takeIf { it.isAvailable() } ?: NoOpCameraDevicePort()
        }
        else -> NoOpCameraDevicePort()
    }
}
```

> **Dependency graph (hexagonal architecture):**
> ```
> UI Layer
>   ├── "Import from Folder" → ImportService → ImageRepositoryPort → ImageRepositoryAdapter
>   │                                                → NamingPort → NamingAdapter
>   │                                                → HashCachePort → HashCacheAdapter
>   │                                                → DeduplicationPort → DeduplicationAdapter
>   │                                                → DevicePort → CompositeDeviceAdapter
>   └── "Import from Camera" → CameraImportService → CameraDevicePort → ImageCaptureCore/Gphoto2/Wpd
>                                                    → NamingPort → NamingAdapter
>                                                    → ImportHistoryAdapter → ImportHistoryAdapter
> ```
>
> `CameraImportService` is the **application service** that mediates between the UI and the `CameraDevicePort`. The UI **never** calls `CameraDevicePort` directly — it always goes through `CameraImportService`. This is the same pattern as `ImportService` mediating between the UI and `ImageRepositoryPort`.

---

## 8. Platform Bridges

### 8.1 macOS: Swift CLI (`petrie-camera-bridge`)

**Long-running process** — Started once, communicates via JSON-over-stdin/stdout. Stays alive for the session to support hot-plug events.

**Command Protocol** (full specification in Appendix D):

| Command | Request | Response |
|---------|---------|----------|
| `detect` | `{"cmd":"detect"}` | `{"devices":[...]}` |
| `browse` | `{"cmd":"browse","deviceId":"..."}` | `{"files":[...]}` |
| `download` | `{"cmd":"download","deviceId":"...","fileId":"...","dest":"/path/to/dest.RAF"}` | `{"success":true,"path":"/path/to/dest.RAF","size":52428800}` |
| `download-batch` | `{"cmd":"download-batch","deviceId":"...","files":[{"id":"...","name":"DSCF1234.RAF"}],"destDir":"/path/to/dest"}` | Progress lines then `{"complete":{...}}` |
| `thumbnail` | `{"cmd":"thumbnail","deviceId":"...","fileId":"...","width":256,"height":256}` | `{"success":true,"path":"/tmp/thumb.jpg"}` |
| `delete` | `{"cmd":"delete","deviceId":"...","fileIds":["..."]}` | `{"success":true,"deleted":["..."]}` |
| `eject` | `{"cmd":"eject","deviceId":"..."}` | `{"success":true}` |
| `close` | `{"cmd":"close"}` | (exits) |

**Key implementation details:**
- Uses `ICDeviceBrowser` with delegate callbacks for hot-plug
- `ICCameraDevice.requestDownload(:options:completion:)` downloads directly to destination URL
- Runs on its own `NSRunLoop` for delegate callbacks
- Bundled in `src/main/resources/bridge/petrie-camera-bridge` (universal binary)

### 8.2 Linux: gphoto2 CLI

**Individual invocations** — Each operation spawns `gphoto2` as a subprocess. No long-running process needed. gphoto2 handles camera session internally.

**Key commands:**

| Operation | Command | Notes |
|-----------|---------|-------|
| Detect | `gphoto2 --auto-detect` | Returns camera model and port |
| List files | `gphoto2 --list-files --port <port>` | Returns file listing |
| Download | `gphoto2 --get-file <n> --filename <dest> --port <port>` | `--filename` specifies exact destination |
| Thumbnail | `gphoto2 --get-thumbnail <n> --filename <dest> --port <port>` | Small JPEG thumbnail |
| Delete | `gphoto2 --delete-file <n> --port <port>` | Deletes from camera |
| Summary | `gphoto2 --summary --port <port>` | Camera info (storage, battery) |

**Device ID format:** `usb:001,004` (USB bus,device) or `usb:04cb:0c8d` (USB vendor:product)

**File ID format:** Numeric index from `--list-files` output

**Prerequisite:** gphoto2/libgphoto2 must be installed (`apt install gphoto2` or `brew install gphoto2`). The adapter checks availability at startup and hides the camera option if unavailable.

### 8.3 Windows: WPD PowerShell Bridge

**PowerShell script** — Wraps Windows Portable Devices COM API. Similar JSON-stdin/stdout protocol.

**Key WPD COM interfaces:**

| Interface | Purpose |
|-----------|---------|
| `IPortableDeviceManager` | Device enumeration |
| `IPortableDevice` | Open session |
| `IPortableDeviceContent` | Browse files, transfer |
| `IPortableDeviceResources` | Stream file data (thumbnails, full files) |

**Download streaming:** Uses `IPortableDeviceResources.GetStream()` which provides an `IStream` that can be written directly to a destination file — no staging.

**Device ID format:** WPD PnP device ID string (long alphanumeric identifier)

**Prerequisite:** None — WPD is built into Windows. The bridge is a PowerShell script bundled with the app.

---

## 9. Application Service Changes

### New Application Service: `CameraImportService`

A dedicated application service for camera imports, following the same pattern as `ImportService` for folder imports. The UI calls this service, not the `CameraDevicePort` directly. This maintains hexagonal architecture: **UI → application service → domain port → infrastructure adapter**.

```kotlin
// application/CameraImportService.kt (new)

class CameraImportService(
    /** Camera device port — the primary driven port for PTP camera operations. */
    private val cameraDevicePort: CameraDevicePort,

    /** Naming port — for computing destination paths before download. */
    private val namingPort: NamingPort,

    /** Import history — for recording camera import results. */
    private val historyAdapter: ImportHistoryAdapter
) {
    private val logger = AppLogger()

    /** Detect PTP camera devices. UI calls this, not CameraDevicePort directly. */
    suspend fun detectCameras(): List<PtpCameraDevice> {
        if (!cameraDevicePort.isAvailable()) return emptyList()
        return try { cameraDevicePort.detectPtpDevices() } catch (_: Exception) { emptyList() }
    }

    /** Check if camera import is available on this platform. */
    fun isCameraImportAvailable(): Boolean = cameraDevicePort.isAvailable()

    /** Browse files on a camera device. */
    suspend fun browseDevice(deviceId: String): List<CameraFile> {
        return cameraDevicePort.browseDevice(deviceId)
    }

    /** Observe camera hot-plug events. */
    fun observeCameraEvents(): Flow<PtpDeviceEvent> {
        return cameraDevicePort.observePtpDeviceChanges()
    }

    /** Download a thumbnail for preview. */
    suspend fun getThumbnail(deviceId: String, fileId: String, width: Int = 256, height: Int = 256): File? {
        return cameraDevicePort.downloadThumbnail(deviceId, fileId, width, height)
    }

    /** Eject a camera device. */
    suspend fun ejectCamera(deviceId: String): Boolean {
        return cameraDevicePort.ejectDevice(deviceId)
    }

    /**
     * Download a single file from camera to a specific destination.
     * Used by Photo Scan wizard to download one file at a time.
     */
    suspend fun downloadSingleFile(deviceId: String, fileId: String, destinationPath: String): File {
        return cameraDevicePort.downloadFile(deviceId, fileId, destinationPath)
    }

    /**
     * Import files from a PTP camera with stream-to-destination.
     * Computes destination paths BEFORE downloading, then streams each file
     * directly to its final location — NO staging, NO temp copies.
     */
    suspend fun importFromCamera(
        device: PtpCameraDevice,
        selectedFiles: List<CameraFile>,
        destinationPath: String,
        configuration: ImportConfiguration,
        onProgress: (CameraImportProgress) -> Unit = {}
    ): ImportResult {
        val totalBytes = selectedFiles.sumOf { it.size }

    // ── Phase 1: Disk space check ──
    val availableBytes = File(destinationPath).usableSpace
    val safetyMargin = 1.05
    if (availableBytes < totalBytes * safetyMargin) {
        throw InsufficientDiskSpaceException(
            "Need ${formatBytes(totalBytes)} but only ${formatBytes(availableBytes)} " +
            "available at $destinationPath (5% safety margin)"
        )
    }

    val startTime = System.currentTimeMillis()
    val results = mutableListOf<File>()
    val errors = mutableListOf<ImportError>()
    var successCount = 0
    var skippedCount = 0
    var deletedCount = 0

    // ── Phase 2: Compute destination paths ──
    // For each camera file, determine where it should end up on disk
    val plannedImports = selectedFiles.mapIndexed { index, camFile ->
        val tempName = camFile.name  // Use original filename for naming
        val imageFile = camFile.toImageFile(File(destinationPath, tempName))
        val destFolder = namingPort.generateFolderPath(imageFile, destinationPath, configuration)
        val destFileName = namingPort.generateFileName(imageFile, configuration, index + 1)
        PlannedImport(camFile, File(destFolder, destFileName))
    }

    // ── Phase 3: Download each file directly to destination ──
    onProgress(CameraImportProgress(PLANNING, 0, selectedFiles.size, "", 0, totalBytes))

    for ((index, planned) in plannedImports.withIndex()) {
        onProgress(CameraImportProgress(
            DOWNLOADING, index + 1, selectedFiles.size,
            planned.cameraFile.name, 0, totalBytes
        ))

        try {
            // Create destination directory
            planned.destination.parentFile?.mkdirs()

            // Stream directly from camera to final destination — NO STAGING
            val downloaded = cameraDevicePort.downloadFile(
                deviceId = device.id,
                fileId = planned.cameraFile.id,
                destinationPath = planned.destination.absolutePath
            )

            results.add(downloaded)
            successCount++
        } catch (e: Exception) {
            logger.error("Failed to download ${planned.cameraFile.name}: ${e.message}")
            errors.add(ImportError(
                planned.cameraFile.toImageFile(planned.destination),
                ErrorType.UNKNOWN,
                "Download failed: ${e.message}"
            ))
        }
    }

    // ── Phase 4: Verify downloads (if configured) ──
    if (configuration.verifyAfterCopy) {
        for ((index, planned) in plannedImports.withIndex()) {
            if (!planned.destination.exists()) continue

            onProgress(CameraImportProgress(
                VERIFYING, index + 1, selectedFiles.size,
                planned.cameraFile.name, totalBytes, totalBytes
            ))

            val cameraSize = planned.cameraFile.size
            val destSize = planned.destination.length()
            if (cameraSize != destSize) {
                logger.error("Size mismatch for ${planned.cameraFile.name}: " +
                    "expected $cameraSize, got $destSize")
                planned.destination.delete()
                errors.add(ImportError(
                    planned.cameraFile.toImageFile(planned.destination),
                    ErrorType.HASH_MISMATCH,
                    "Downloaded file size ($destSize) doesn't match camera file ($cameraSize)"
                ))
                successCount--
            }
        }
    }

    // ── Phase 5: Delete from camera (if configured and all succeeded) ──
    if (configuration.deleteAfterImport && errors.isEmpty()) {
        onProgress(CameraImportProgress(
            DELETING, 0, selectedFiles.size, "", totalBytes, totalBytes
        ))

        val fileIds = selectedFiles.map { it.id }
        val deleted = cameraDevicePort.deleteFiles(device.id, fileIds)
        if (deleted) deletedCount = selectedFiles.size
    }

    // ── Phase 6: Build result ──
    onProgress(CameraImportProgress(COMPLETE, selectedFiles.size, selectedFiles.size, "", totalBytes, totalBytes))

    return ImportResult(
        totalFiles = selectedFiles.size,
        successCount = successCount,
        errorCount = errors.size,
        duplicateCount = 0,
        skippedCount = skippedCount,
        deletedSourceCount = deletedCount,
        endTime = System.currentTimeMillis()
    )
}

private data class PlannedImport(
    val cameraFile: CameraFile,
    val destination: File
)
```

### Updated `ImportConfiguration`

Add `deleteAfterImport` field (already exists — just note it applies to camera imports too):

```kotlin
data class ImportConfiguration(
    // ... existing fields ...
    val deleteAfterImport: Boolean = false  // Delete from source after successful import
                                            // For folders: delete from filesystem
                                            // For cameras: delete from camera device
)
```

---

## 10. UI Changes

### 10.1 Dual Entry Buttons

Replace the current source path field with two distinct entry buttons at the top of the import screen. Each button launches a different import flow—this is *not* a mode toggle, but two separate action paths:

```kotlin
// In MediaImportScreen.kt
val cameraImportService = koinInject<CameraImportService>()
val cameraAvailable by remember { mutableStateOf(cameraImportService.isCameraImportAvailable()) }
var selectedPtpDevice by remember { mutableStateOf<PtpCameraDevice?>(null) }
var importSource by remember { mutableStateOf(ImportSource.FOLDER) }

// ── Source selection: two distinct entry buttons ──
Row(
    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    horizontalArrangement = Arrangement.spacedBy(12.dp)
) {
    // "Import from Folder" — existing folder-based flow
    OutlinedButton(
        onClick = { importSource = ImportSource.FOLDER },
        modifier = Modifier.weight(1f).height(56.dp),
        colors = if (importSource == ImportSource.FOLDER) {
            ButtonDefaults.outlinedButtonColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        } else ButtonDefaults.outlinedButtonColors()
    ) {
        Icon(Icons.Default.FolderOpen, null, Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text("Import from Folder", style = MaterialTheme.typography.titleSmall)
    }

    // "Import from Camera" — PTP camera flow (disabled if no bridge available)
    OutlinedButton(
        onClick = { importSource = ImportSource.CAMERA },
        enabled = cameraAvailable,
        modifier = Modifier.weight(1f).height(56.dp),
        colors = if (importSource == ImportSource.CAMERA) {
            ButtonDefaults.outlinedButtonColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        } else ButtonDefaults.outlinedButtonColors()
    ) {
        Icon(Icons.Default.CameraAlt, null, Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text("Import from Camera", style = MaterialTheme.typography.titleSmall)
    }
}

// Content switches based on selected entry
when (importSource) {
    ImportSource.FOLDER -> {
        // Existing folder source path field + Browse button (unchanged)
    }
    ImportSource.CAMERA -> {
        CameraDeviceList(
            devices = detectedDevices.filter { it.isPtpDevice },
            onSelect = { device -> /* browse device */ },
            onImport = { device, mode -> /* start import */ }
        )
    }
}
```

**Why two buttons instead of a toggle?**
- **Discoverability**: Each button is a clear call-to-action — users immediately see both options.
- **No hidden state**: A segmented toggle hides the inactive choice; buttons keep both paths visible.
- **Simpler state model**: `ImportSource` is just a UI display state, not a persistent domain enum. It determines which panel is shown, not a persisted preference.
- **Consistent with current UI**: The existing screen already uses action buttons (Import All, Import New, Select & Import). Two source entry buttons fit the same visual language.

### 10.2 Auto-Select Camera on Startup

When the application starts, automatically switch to camera import if a PTP camera is detected. This makes the common workflow (plug in camera → open app → import) require zero clicks:

```kotlin
// In MediaImportScreen.kt — LaunchedEffect for camera detection
LaunchedEffect(Unit) {
    // Detect mass-storage devices (existing behavior)
    val initialDevices = try { devicePort.detectDevices() } catch (_: Exception) { emptyList() }
    detectedDevices = initialDevices

    // Detect PTP cameras (new)
    // Camera detection goes through CameraImportService, not the port directly
        val ptpDevices = try { cameraImportService.detectCameras() } catch (_: Exception) { emptyList() }
        ptpDetectedDevices = ptpDevices

        // AUTO-SELECT: If a camera is plugged in at startup, switch to camera source
        if (ptpDevices.isNotEmpty()) {
            importSource = ImportSource.CAMERA
            selectedPtpDevice = ptpDevices.first()
        }
    }

    // Monitor for hot-plug events (existing + PTP)
    devicePort.observeDeviceChanges().collect { event ->
        when (event) {
            is DeviceEvent.Connected -> {
                detectedDevices = detectedDevices.filter { it.id != event.device.id } + event.device
            }
            is DeviceEvent.Disconnected -> {
                detectedDevices = detectedDevices.filter { it.id != event.deviceId }
            }
            is DeviceEvent.MountChanged -> {}
        }
    }
}

// Separate LaunchedEffect for PTP device hot-plug
LaunchedEffect(Unit) {
    if (!cameraImportService.isCameraImportAvailable()) return@LaunchedEffect

    cameraImportService.observeCameraEvents().collect { event ->
        when (event) {
            is PtpDeviceEvent.Connected -> {
                ptpDetectedDevices = ptpDetectedDevices.filter { it.id != event.device.id } + event.device
                // AUTO-SELECT: Switch to camera when a camera is plugged in while app is running
                importSource = ImportSource.CAMERA
                selectedPtpDevice = event.device
                // Show brief notification
                scope.launch { snackbarHostState.showSnackbar("Camera connected: ${event.device.displayName}") }
            }
            is PtpDeviceEvent.Disconnected -> {
                ptpDetectedDevices = ptpDetectedDevices.filter { it.id != event.deviceId }
                selectedPtpDevice = null
                // If no cameras remain, switch back to folder
                if (ptpDetectedDevices.isEmpty()) {
                    importSource = ImportSource.FOLDER
                }
            }
        }
    }
}
```

**Behavior rules:**
| Condition | Action |
|-----------|--------|
| Camera detected at startup | Auto-select `ImportSource.CAMERA`, select first camera |
| No camera at startup | Default to `ImportSource.FOLDER` (current behavior) |
| Camera plugged in while app running | Auto-switch to `ImportSource.CAMERA`, show snackbar |
| Camera unplugged | If no other cameras, switch back to `ImportSource.FOLDER` |
| Multiple cameras at startup | Auto-select first camera; user can pick from CameraDeviceList |
| Camera button disabled when no bridge | Button is visible but `enabled = false`; tooltip shows "Camera bridge not available" |

### 10.3 Camera Device Card

```kotlin
@Composable
fun CameraDeviceCard(
    device: CameraDevice,
    ptpDevice: PtpCameraDevice?,
    onImport: (ImportMode) -> Unit
) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.CameraAlt, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text(device.displayName, style = MaterialTheme.typography.titleMedium)
                if (device.isPtpDevice) {
                    Spacer(Modifier.width(8.dp))
                    Badge { Text("PTP") }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                ptpDevice?.formattedCapacity?.let { Text("💾 $it") }
                ptpDevice?.let { Text("📷 ${it.fileCount} files") }
                Text(device.transport, style = MaterialTheme.typography.bodySmall)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { onImport(ImportMode.ALL) }) { Text("Import All") }
                Button(onClick = { onImport(ImportMode.NEW) }, colors = ...) { Text("Import New") }
                Button(onClick = { onImport(ImportMode.SELECT) }, colors = ...) { Text("Select & Import") }
            }
            if (!ptpDevice?.supportsDelete!!) {
                Text("⚠ File deletion not supported by this camera",
                     style = MaterialTheme.typography.labelSmall,
                     color = MaterialTheme.colorScheme.error)
            }
        }
    }
}
```

### 10.4 Camera Import Progress

Two-phase progress display for camera imports:

```kotlin
when (flowStep) {
    FlowStep.CAMERA_BROWSING -> ProgressCard("Browsing camera files...", 0, 0, "")
    FlowStep.CAMERA_DOWNLOADING -> ProgressCard(
        "Downloading from camera: ${cameraProgress.fileName}",
        cameraProgress.currentFile,
        cameraProgress.totalFiles,
        "${cameraProgress.bytesDownloaded}/${cameraProgress.totalBytes} bytes"
    )
    FlowStep.CAMERA_VERIFYING -> ProgressCard(
        "Verifying downloads...",
        cameraProgress.currentFile,
        cameraProgress.totalFiles,
        cameraProgress.fileName
    )
    // ... existing steps ...
}
```

### 10.5 Disk Space Warning

Before starting a camera import, check available disk space:

```kotlin
val totalImportSize = selectedFiles.sumOf { it.size }
val availableSpace = File(destinationPath).usableSpace
val needsWarning = availableSpace < totalImportSize * 1.05

if (needsWarning) {
    AlertDialog(
        onDismissRequest = { /* cancel */ },
        title = { Text("Insufficient Disk Space") },
        text = {
            Text("The selected files total ${formatBytes(totalImportSize)}, " +
                 "but only ${formatBytes(availableSpace)} is available at $destinationPath.\n\n" +
                 "Please free up space or choose a different destination.")
        },
        confirmButton = { TextButton(onClick = { /* cancel */ }) { Text("OK") } }
    )
}
```

---

## 11. Integration with Photo Scan Wizard

When a user selects **Camera** as the source for photo scan, the workflow is:

1. User selects a camera device
2. User browses files on the camera
3. User selects one (or more) files for scanning
4. Each selected file downloads **directly to the destination directory** (not a staging area)
5. `onImageSelected(downloadedFile, allDownloadedFiles)` is called — the wizard takes over from there
6. The wizard loads the file with `ImageIO.read(file)` as normal
7. If the user cancels, downloaded files remain in the destination directory (valid files, not temp)

This means Photo Scan from a camera **never creates a staging copy**. The destination is the user's configured output directory, and files arrive there directly.

### Changes to `PhotoScanImportScreen`

Photo Scan uses a similar dual-button pattern, but simpler — just "Select File" / "Select Folder" buttons alongside a "Select from Camera" button:

```kotlin
// In PhotoScanImportScreen.kt
var importSource by remember { mutableStateOf(ImportSource.FOLDER) }
val cameraImportService = koinInject<CameraImportService>()
val cameraAvailable by remember { mutableStateOf(cameraImportService.isCameraImportAvailable()) }

// ── Source selection buttons ──
Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(8.dp)
) {
    // "Select File" — existing file picker
    OutlinedButton(onClick = { pickFile("Select Image File")?.let { sourcePath = it } }) {
        Icon(Icons.Default.Image, null, Modifier.size(18.dp))
        Spacer(Modifier.width(4.dp))
        Text("Select File")
    }
    // "Select Folder" — existing folder picker
    OutlinedButton(onClick = { pickFolder("Select Folder")?.let { sourcePath = it } }) {
        Icon(Icons.Default.Folder, null, Modifier.size(18.dp))
        Spacer(Modifier.width(4.dp))
        Text("Select Folder")
    }
    // "Select from Camera" — PTP camera picker (new)
    OutlinedButton(
        onClick = { importSource = ImportSource.CAMERA },
        enabled = cameraAvailable
    ) {
        Icon(Icons.Default.CameraAlt, null, Modifier.size(18.dp))
        Spacer(Modifier.width(4.dp))
        Text("Select from Camera")
    }
}

// Camera file selection dialog (shown when "Select from Camera" is clicked)
if (importSource == ImportSource.CAMERA) {
    CameraFilePickerDialog(
        onDismiss = { importSource = ImportSource.FOLDER },
        onSelect = { device, cameraFile ->
            scope.launch {
                // Download file directly to destination — NO STAGING
                val destDir = File(settings.photoScanImportTabSettings.destinationPath)
                val destFile = File(destDir, cameraFile.name)
                destDir.mkdirs()
                cameraImportService.downloadSingleFile(device.id, cameraFile.id, destFile.absolutePath)
                importSource = ImportSource.FOLDER  // reset after selection
                onImageSelected(destFile, listOf(destFile))
            }
        }
    )
}
```

---

## 12. Implementation Phases

### Phase 1: Domain Models & Port

| Step | What | Files | Size |
|------|------|-------|------|
| 1.1 | Create `PtpCameraDevice`, `CameraCapability` | `domain/model/PtpCameraDevice.kt` (new) | Small |
| 1.2 | Create `CameraFile`, `CameraFileType` | `domain/model/CameraFile.kt` (new) | Small |
| 1.3 | Create `CameraImportProgress` | `domain/model/CameraImportProgress.kt` (new) | Small |
| 1.4 | Create `ImportSource` enum | `ui/screens/model/ImportSource.kt` (new) | Small |
| 1.5 | Create `CameraDevicePort` interface | `domain/port/CameraDevicePort.kt` (new) | Medium |
| 1.6 | Add PTP fields to `CameraDevice` | `domain/model/CameraDevice.kt` (modify) | Small |
| 1.7 | Add camera fields to `ImageFile` | `domain/model/ImageFile.kt` (modify) | Small |

### Phase 2: macOS Bridge (Primary Platform)

| Step | What | Files | Size |
|------|------|-------|------|
| 2.1 | Create Swift Package project | `bridge/Package.swift` (new) | Small |
| 2.2 | Implement `detect` command | `bridge/Sources/CameraBridge/main.swift` | Medium |
| 2.3 | Implement `browse` command | same | Medium |
| 2.4 | Implement `download` command (stream to destination) | same | Medium |
| 2.5 | Implement `download-batch` with progress | same | Medium |
| 2.6 | Implement `thumbnail` command | same | Small |
| 2.7 | Implement `delete` command | same | Small |
| 2.8 | Implement `eject` command | same | Small |
| 2.9 | Add JSON protocol, error handling | same | Medium |
| 2.10 | Build script for universal binary | `bridge/build.sh` (new) | Small |
| 2.11 | Bundle binary in resources | `src/main/resources/bridge/` | - |

### Phase 3: Infrastructure Adapters

| Step | What | Files | Size |
|------|------|-------|------|
| 3.1 | Create `CameraBridgeAdapter` base class | `infrastructure/adapter/camera/CameraBridgeAdapter.kt` (new) | Medium |
| 3.2 | Create `ImageCaptureCoreBridgeAdapter` | `infrastructure/adapter/camera/ImageCaptureCoreBridgeAdapter.kt` (new) | Large |
| 3.3 | Create `Gphoto2BridgeAdapter` | `infrastructure/adapter/camera/Gphoto2BridgeAdapter.kt` (new) | Medium |
| 3.4 | Create `WpdBridgeAdapter` (stub) | `infrastructure/adapter/camera/WpdBridgeAdapter.kt` (new) | Medium |
| 3.5 | Create `NoOpCameraDevicePort` | `infrastructure/adapter/camera/NoOpCameraDevicePort.kt` (new) | Small |
| 3.6 | Create `CompositeDeviceAdapter` | `infrastructure/adapter/camera/CompositeDeviceAdapter.kt` (new) | Medium |
| 3.7 | Update DI registration | `di/AppModule.kt` (modify) | Small |
| 3.8 | Add `InsufficientDiskSpaceException` | `domain/model/Exceptions.kt` (modify) | Small |

### Phase 4: Application Service — CameraImportService

| Step | What | Files | Size |
|------|------|-------|------|
| 4.1 | Create `CameraImportService` application service | `application/CameraImportService.kt` (new) | Large |
| 4.2 | Register `CameraImportService` in DI | `di/AppModule.kt` (modify) | Small |
| 4.3 | Add `InsufficientDiskSpaceException` | `domain/model/Exceptions.kt` (modify) | Small |

### Phase 5: UI — Dual Entry Buttons & Auto-Select

| Step | What | Files | Size |
|------|------|-------|------|
| 5.1 | Create `ImportSource` enum and add to `MediaImportScreen` | `ui/screens/model/ImportSource.kt` (new), `ui/screens/MediaImportScreen.kt` (modify) | Small |
| 5.2 | Add "Import from Folder" / "Import from Camera" dual buttons | `ui/screens/MediaImportScreen.kt` (modify) | Medium |
| 5.3 | Add startup auto-select: detect PTP cameras → switch to `ImportSource.CAMERA` | `ui/screens/MediaImportScreen.kt` (modify) | Medium |
| 5.4 | Add hot-plug auto-switch: camera connected → `ImportSource.CAMERA`, disconnected → fallback to `FOLDER` | `ui/screens/MediaImportScreen.kt` (modify) | Medium |
| 5.5 | Create `CameraDeviceCard` composable | `ui/screens/components/CameraDeviceCard.kt` (new) | Medium |
| 5.6 | Create `CameraDeviceList` composable | `ui/screens/components/CameraDeviceList.kt` (new) | Small |
| 5.7 | Add `CAMERA_BROWSING`, `CAMERA_DOWNLOADING`, `CAMERA_VERIFYING` to `FlowStep` | `ui/screens/MediaImportScreen.kt` (modify) | Small |
| 5.8 | Wire camera import flow (browse → download → import) | same | Medium |
| 5.9 | Add disk space warning dialog | same | Small |

### Phase 6: Camera File Browser

| Step | What | Files | Size |
|------|------|-------|------|
| 6.1 | Create `CameraFileBrowserDialog` composable | `ui/screens/CameraFileBrowserDialog.kt` (new) | Large |
| 6.2 | Add thumbnail loading from `CameraDevicePort` | same | Medium |
| 6.3 | Add select all/none/filter by type | same | Medium |

### Phase 7: Photo Scan Integration

| Step | What | Files | Size |
|------|------|-------|------|
| 7.1 | Add camera source button to `PhotoScanImportScreen` ("Select from Camera") | `ui/screens/wizard/PhotoScanImportScreen.kt` (modify) | Medium |
| 7.2 | Wire camera file selection → download → `onImageSelected()` | same | Medium |

### Phase 8: Linux gphoto2 Adapter

| Step | What | Files | Size |
|------|------|-------|------|
| 8.1 | Implement gphoto2 output parsing (detect, browse) | `Gphoto2BridgeAdapter.kt` (update) | Medium |
| 8.2 | Implement gphoto2 download-to-destination | same | Medium |
| 8.3 | Implement gphoto2 delete and eject | same | Small |
| 8.4 | Test with physical cameras on Linux | Manual testing | - |

### Phase 9: Windows WPD Adapter

| Step | What | Files | Size |
|------|------|-------|------|
| 9.1 | Create PowerShell WPD bridge script | `src/main/resources/bridge/petrie-wpd-bridge.ps1` (new) | Large |
| 9.2 | Implement WPD device detection | same | Medium |
| 9.3 | Implement WPD file listing | same | Medium |
| 9.4 | Implement WPD download-to-destination | same | Medium |
| 9.5 | Implement WPD delete and eject | same | Small |

### Phase 10: Testing & Polish

| Step | What | Files | Size |
|------|------|-------|------|
| 10.1 | Unit tests for all domain models | New test files | Medium |
| 10.2 | Unit tests for adapter JSON parsing | New test files | Medium |
| 10.3 | Integration test with mock bridge | New test files | Large |
| 10.4 | Hot-plug notification (camera connected/disconnected toast + auto-switch to camera) | `MediaImportScreen.kt` (modify) | Medium |
| 10.5 | Camera eject button in UI | `CameraDeviceCard.kt` (modify) | Small |
| 10.6 | Error handling for camera disconnect during download | `CameraImportService.kt` (modify) | Small |

---

## 13. Validation Strategy

### Unit Tests

| Test | What It Validates |
|------|-------------------|
| `PtpCameraDeviceTest` | Data class construction, formatting, capability checks |
| `CameraFileTest` | File type detection, extension parsing, `toImageFile()` conversion |
| `CameraImportProgressTest` | Progress percentage calculations |
| `ImportSourceTest` | Enum values (FOLDER, CAMERA) |
| `CameraFileTypeTest` | `fromExtension()` for all known RAW/JPEG/Video extensions |
| `Gphoto2BridgeAdapterTest` | Output parsing (detect, list-files) with mock process output |
| `ImageCaptureCoreBridgeAdapterTest` | JSON protocol parsing with mock process |
| `CompositeDeviceAdapterTest` | Merging mass-storage + PTP devices |
| `NoOpCameraDevicePortTest` | All methods return empty/false/throw |
| `CameraImportServiceTest` | `importFromCamera()` disk space check, path planning |

### Integration Tests

| Test | What It Validates |
|------|-------------------|
| `SwiftBridgeIntegrationTest` | Spawn bridge, detect cameras, browse files (requires camera) |
| `Gphoto2IntegrationTest` | gphoto2 detect, browse, download on Linux (requires camera) |
| `CameraImportFlowTest` | Full flow: browse → select → download → verify → check files exist at destination |
| `CameraAutoSelectTest` | Startup with fake camera → `ImportSource` auto-set to CAMERA; no camera → defaults to FOLDER |
| `CameraHotPlugTest` | PtpDeviceEvent.Connected → `ImportSource` switches to CAMERA; Disconnected → fallback to FOLDER |
| `DiskSpaceCheckTest` | Verify InsufficientDiskSpaceException when disk is too full |
| `CameraEjectTest` | Eject camera after import |

### Cross-Platform Matrix

| Platform | Bridge | Priority | Status |
|----------|--------|----------|--------|
| macOS (ARM64) | ImageCaptureCore Swift CLI | P0 | Phase 2 |
| macOS (x86_64) | ImageCaptureCore Swift CLI (universal binary) | P0 | Phase 2 |
| Linux (x86_64) | gphoto2 CLI | P1 | Phase 8 |
| Windows (x86_64) | WPD/PowerShell | P2 | Phase 9 |

### Manual Test Scenarios

| Scenario | Platform | Steps |
|----------|----------|-------|
| Fujifilm PTP camera import | macOS | Connect X-T5 → detect → browse → import all → verify files in destination |
| **Auto-select camera at startup** | macOS | Plug in camera → launch app → verify "Import from Camera" is auto-selected and camera device is shown |
| **No camera at startup** | Any | Launch app with no camera → verify "Import from Folder" is selected, camera button disabled or absent |
| **Hot-plug auto-switch** | macOS | App running in folder mode → plug in camera → verify auto-switches to camera, snackbar appears |
| **Camera unplug fallback** | macOS | In camera mode → unplug camera → verify switches back to folder mode |
| Disk space warning | All | Set destination to nearly-full drive → attempt import → see warning |
| Camera disconnect during download | macOS | Start download → unplug camera → verify graceful error |
| Mixed sources (mass storage + PTP) | macOS | SD card in reader + PTP camera → both appear in device list |
| gphoto2 camera import | Linux | Connect camera → detect via gphoto2 → browse → import |
| Delete from camera after import | macOS | Import with delete enabled → verify files removed from camera |
| Photo Scan from camera | macOS | Click "Select from Camera" → pick file → download to destination → run through wizard |
| No bridge available | Any | Remove bridge binary → "Import from Camera" button disabled → folder import works |
| **Dual buttons visible** | Any | Verify both "Import from Folder" and "Import from Camera" buttons are always visible (not hidden by a toggle) |

---

## 14. Risks & Mitigations

| Risk | Likelihood | Impact | Mitigation |
|------|-----------|--------|------------|
| **Disk full during download** | Medium | High | Pre-flight disk space check with 5% safety margin; clean up partial downloads on error |
| Camera disconnect mid-download | Medium | Medium | Catch IOException; report partial file; mark as error; allow retry |
| Camera locked by another app | High | Medium | Detect `device_busy` error; show message: "Close Image Capture / gphoto2 / other apps using this camera" |
| Swift binary not codesigned | Medium | High | Ad-hoc sign; notarize for distribution; document Gatekeeper exception |
| gphoto2 not installed on Linux | High | Medium | Check at startup; show "Install gphoto2" message; hide camera option if unavailable |
| gphoto2 GPL licensing conflict | Low | Medium | gphoto2 CLI is GPL-2.0, but we call it as a subprocess (not linked). Similar to how Darktable uses gphoto2. No license conflict for subprocess invocation. |
| Bridge binary architecture mismatch | Low | Medium | Build universal binary (ARM64 + x86_64); detect architecture at runtime |
| ImageCaptureCore API deprecation | Low | Low | Stable since macOS 10.4; no deprecation signs; Apple's own apps use it |
| PTP protocol differences across cameras | Medium | Medium | Test with multiple camera brands (Fujifilm, Canon, Nikon, Sony); gphoto2 has 2000+ camera drivers |
| Large camera (10k+ files) performance | Low | Medium | Lazy thumbnail loading (viewport-based); background metadata fetch; paginated file listing |
| macOS sandbox restrictions | Low | High | Not sandboxed (desktop app); document that App Sandbox would block camera access |
| **Auto-select false positives** | Medium | Low | Android phones in PTP mode, webcams, or non-camera PTP devices may trigger auto-select. Mitigation: filter `ICDevice` by `deviceType == .camera` (ImageCaptureCore) and `--camera` flag (gphoto2); show device name in snackbar so user can notice and switch back to folder. |
| Windows WPD COM threading issues | Medium | Medium | PowerShell bridge runs in separate process, isolating COM threading from JVM |

---

## Appendix A: ImageCaptureCore API Reference (macOS)

### Key Classes

**ICDeviceBrowser** — Detect connected cameras and scanners.
```swift
class ICDeviceBrowser: NSObject {
    var delegate: ICDeviceBrowserDelegate?
    var isBrowsing: Bool { get }
    func start()
    func stop()
}

protocol ICDeviceBrowserDelegate: AnyObject {
    func deviceBrowser(_ browser: ICDeviceBrowser, didAdd device: ICDevice, moreComing: Bool)
    func deviceBrowser(_ browser: ICDeviceBrowser, didRemove device: ICDevice, moreGoing: Bool)
}
```

**ICCameraDevice** — Camera device with file access.
```swift
class ICCameraDevice: ICDevice {
    var mediaFiles: [ICCameraItem]? { get }
    var capabilities: Set<String> { get }
    func requestContents(completion: @escaping ([ICCameraItem]?, Error?) -> Void)
    func requestDownload(_ file: ICCameraFile, options: [AnyHashable: Any] = [:],
                         completion: @escaping (URL?, Error?) -> Void)
    func requestDeleteFiles(_ files: [ICCameraFile],
                            deleteFailed: ((ICCameraFile, Error?) -> Void)? = nil,
                            completion: @escaping (Bool, Error?) -> Void)
}

// Download options:
// ICDownloadsDirectoryURL - destination directory URL
// ICSaveAsFilename - override filename
// ICOverwrite - overwrite existing file
// ICDeleteAfterSuccessfulDownload - delete from camera after download
```

**ICCameraFile** — Individual file on camera.
```swift
class ICCameraFile: ICCameraItem {
    var name: String { get }
    var size: Int { get }
    var createdDate: Date? { get }
    var modificationDate: Date? { get }
    var isRaw: Bool { get }
    var fileFormat: ICFileFormat? { get }
    func requestThumbnailData(options: [AnyHashable: Any] = [:],
                              completion: @escaping (Data?, Error?) -> Void)
    func requestMetadataDictionary(options: [AnyHashable: Any] = [:],
                                   completion: @escaping ([String: Any]?, Error?) -> Void)
}
```

---

## Appendix B: gphoto2 CLI Reference (Linux)

### Command Structure

```bash
# Detect cameras
gphoto2 --auto-detect

# List all files
gphoto2 --list-files --port usb:001,004

# Download a specific file directly to destination
gphoto2 --get-file 42 --filename /dest/path/DSCF1234.RAF --port usb:001,004

# Download all files
gphoto2 --get-all-files --filename /dest/path/ --port usb:001,004

# Get thumbnail
gphoto2 --get-thumbnail 42 --filename /tmp/thumb.jpg --port usb:001,004

# Delete file from camera
gphoto2 --delete-file 42 --port usb:001,004

# Get camera summary
gphoto2 --summary --port usb:001,004
```

### Output Parsing

**`--auto-detect` output:**
```
Model                          Port
------------------------------------------------------------
Fujifilm X-T5                  usb:001,004
```

**`--list-files` output:**
```
Capability summary:
...
Content of Directory /DCIM/100FUJI/:
  -rw-r--r--  52M DSCF1234.RAF  2026-05-15 14:30
  -rw-r--r-- 8.2M DSCF1235.JPG  2026-05-15 14:32
...
```

Note: gphoto2 requires no staging. The `--filename` flag directs downloads straight to the destination path.

---

## Appendix C: WPD COM API Reference (Windows)

### Key Interfaces

```csharp
// Device enumeration
IPortableDeviceManager mgr = new PortableDeviceManagerClass();
string[] deviceIds = mgr.GetDevices();

// Open device
IPortableDevice device = new PortableDeviceClass();
device.Open(deviceId, new PortableDeviceValues());

// Browse content
IPortableDeviceContent content;
device.Content(out content);
IPortableDeviceProperties properties;
content.Properties(out properties);

// Enumerate files
IEnumPortableDeviceObjectIDs enumerator;
content.EnumObjects(0, "DEVICE", null, out enumerator);

// Transfer file (streaming - no staging!)
IPortableDeviceResources resources;
content.Transfer(objectId, out resources);
IStream stream;
resources.GetStream(ref resourceId, ref stream);
// Stream directly to destination file
```

### PowerShell Bridge

The Windows bridge will be a PowerShell script using WPD COM via `New-Object -ComObject`:

```powershell
# Detect devices
$manager = New-Object -ComObject PortableDeviceManager
$devices = $manager.GetDevices()

# Browse files
$device = New-Object -ComObject PortableDevice
$device.Open($deviceId, $null)
# ... enumerate content ...

# Download file directly to destination
# Uses WPD streaming API - no staging
$content.Transfer($objectId, [ref]$resources)
$resources.GetStream([ref]$stream)
# Write $stream to destination file
```

---

## Appendix D: Bridge Protocol Specification

### JSON Command Format (stdin / CLI args)

**macOS (long-running process):** Single-line JSON on stdin.

**Linux (per-invocation):** CLI arguments.

**Windows (PowerShell):** CLI arguments.

All three produce single-line JSON on stdout.

### Common JSON Response Format

```json
{"status": "ok", "data": { ... }}
```

```json
{"status": "error", "error": {"code": "<error_code>", "message": "<description>"}}
```

### Error Codes

| Code | Meaning |
|------|---------|
| `device_not_found` | Device disappeared between detect and browse |
| `session_failed` | Could not open session with device |
| `download_failed` | Download failed (disk full, camera disconnected) |
| `disk_full` | Not enough disk space at destination |
| `delete_failed` | Delete failed (read-only, not supported) |
| `device_busy` | Camera is being accessed by another app |
| `not_supported` | Operation not supported by this device |
| `bridge_error` | Internal bridge error |
| `permission_denied` | User denied camera access permission |

### Device JSON Schema (common across platforms)

```json
{
    "id": "string (platform-specific)",
    "name": "Fujifilm X-T5",
    "manufacturer": "Fujifilm",
    "model": "X-T5",
    "serialNumber": "string or null",
    "transport": "USB | MassStorage | FireWire | Bluetooth",
    "fileCount": 2847,
    "capacity": 64200000000,
    "available": 12800000000,
    "capabilities": ["DELETE_ONE", "DELETE_ALL", "THUMBNAIL", "METADATA"],
    "platform": "macos | linux | windows",
    "bridgeType": "imagecapturecore | gphoto2 | wpd"
}
```

### Camera File JSON Schema

```json
{
    "id": "string (platform-specific file ID)",
    "name": "DSCF1234.RAF",
    "folder": "/DCIM/100FUJI/",
    "size": 52428800,
    "creationDate": "2026-05-15T14:30:22Z",
    "modificationDate": "2026-05-15T14:30:22Z",
    "fileType": "raw | jpeg | video | sidecar | other",
    "width": 6240,
    "height": 4160,
    "hasThumbnail": true,
    "isLocked": false,
    "sidecarFiles": []
}
```
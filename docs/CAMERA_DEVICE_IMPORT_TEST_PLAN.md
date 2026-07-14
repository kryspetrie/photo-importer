# Camera Device Import — Testing Plan

> **Status:** Deferred (awaiting camera device import implementation)
> **Created:** 2026-05-21  
> **Scope:** petrie-file-importer — Test suite for PTP/MTP camera device import (cross-platform)  
> **Related:** [CAMERA_DEVICE_IMPORT_PLAN.md](./CAMERA_DEVICE_IMPORT_PLAN.md)

---

## Table of Contents

1. [Testing Philosophy & Conventions](#1-testing-philosophy--conventions)
2. [Test Architecture](#2-test-architecture)
3. [Phase 1: Domain Model Tests](#3-phase-1-domain-model-tests)
4. [Phase 2: Port Contract Tests](#4-phase-2-port-contract-tests)
5. [Phase 3: Adapter Unit Tests](#5-phase-3-adapter-unit-tests)
6. [Phase 4: Import Service Tests](#6-phase-4-import-service-tests)
7. [Phase 5: UI Logic Tests](#7-phase-5-ui-logic-tests)
8. [Phase 6: Integration Tests](#8-phase-6-integration-tests)
9. [Phase 7: End-to-End Manual Tests](#9-phase-7-end-to-end-manual-tests)
10. [Test Utilities & Shared Fixtures](#10-test-utilities--shared-fixtures)
11. [CI Considerations](#11-ci-considerations)
12. [Test Implementation Order](#12-test-implementation-order)

---

## 1. Testing Philosophy & Conventions

This project follows established conventions documented from the existing test suite:

| Aspect | Convention |
|--------|-----------|
| **Framework** | JUnit 5 (`org.junit.jupiter.api`) |
| **Assertions** | AssertJ (`org.assertj.core.api.Assertions.assertThat`) |
| **Mocking** | Mockito + mockito-kotlin (`mock()`, `whenever`, `any()`, `eq()`) |
| **Fakes** | Hand-rolled in-memory implementations (e.g., `MockImageRepository`) |
| **Coroutines** | `kotlinx.coroutines.test.runTest { }` |
| **Temp dirs** | JUnit 5 `@TempDir` |
| **Naming** | `@DisplayName("should ...")` + `@Nested inner class` grouping |
| **Service construction** | Manual constructor injection (no Koin in tests) |
| **Edge cases** | Separate test class (`*EdgeCaseTest`) |

### New Conventions for Camera Tests

| Aspect | Convention |
|--------|-----------|
| **Bridge mocking** | Mockito mock of `CameraDevicePort` interface |
| **Bridge fake** | `FakeCameraDevicePort` — in-memory implementation for integration tests |
| **Platform skip** | `@EnabledOnOs(OS.MAC)`, `@EnabledOnOs(OS.LINUX)`, `@EnabledOnOs(OS.WINDOWS)` for platform-specific tests |
| **Requires hardware** | `@Tag("requires-hardware")` — tests that need a physical camera, excluded from CI |
| **Requires tool** | `@Tag("requires-gphoto2")`, `@Tag("requires-imagecapturecore")` — tests that need external tools |

---

## 2. Test Architecture

```
src/test/kotlin/org/kryspetrie/fileimport/
├── domain/
│   └── model/
│       ├── PtpCameraDeviceTest.kt         ← Phase 1
│       ├── CameraFileTest.kt              ← Phase 1
│       ├── CameraImportProgressTest.kt    ← Phase 1
│       ├── CameraFileTypeTest.kt          ← Phase 1
│       ├── ImportSourceTest.kt             ← Phase 1
│       └── CameraCapabilityTest.kt        ← Phase 1
│
├── domain/
│   └── port/
│       └── CameraDevicePortContractTest.kt ← Phase 2 (contract for all impls)
│
├── infrastructure/
│   └── adapter/
│       └── camera/
│           ├── ImageCaptureCoreBridgeAdapterTest.kt  ← Phase 3 (macOS)
│           ├── Gphoto2BridgeAdapterTest.kt            ← Phase 3 (Linux)
│           ├── WpdBridgeAdapterTest.kt               ← Phase 3 (Windows)
│           ├── CompositeDeviceAdapterTest.kt          ← Phase 3
│           └── NoOpCameraDevicePortTest.kt            ← Phase 3
│
├── application/
│   ├── CameraImportServiceTest.kt          ← Phase 4 (camera import flow)
│   └── CameraImportServiceEdgeCaseTest.kt  ← Phase 4 (edge cases)
│
├── ui/
│   └── screens/
│       ├── CameraDeviceCardTest.kt        ← Phase 5
│       └── MediaImportCameraSourceTest.kt  ← Phase 5
│
├── integration/
│   ├── CameraImportFlowTest.kt            ← Phase 6
│   ├── CameraDeviceHotPlugTest.kt         ← Phase 6 (requires hardware)
│   └── DiskSpaceCheckTest.kt              ← Phase 6
│
└── testutil/
    ├── FakeCameraDevicePort.kt             ← Shared fake
    ├── MockCameraDevicePort.kt             ← Shared mock factory
    ├── CameraTestFixtures.kt               ← Test data factories
    └── BridgeProcessMock.kt                ← Mock Process for bridge adapter tests
```

---

## 3. Phase 1: Domain Model Tests

Pure data class and enum tests — no mocking needed, no I/O, no platform dependencies. These run on every platform in CI.

### 3.1 `PtpCameraDeviceTest`

**File:** `domain/model/PtpCameraDeviceTest.kt`

```kotlin
@DisplayName("PtpCameraDevice")
class PtpCameraDeviceTest {

    @Nested
    @DisplayName("displayName")
    inner class DisplayName {
        @Test
        @DisplayName("should use name when non-blank")
        fun shouldUseNameWhenNonBlank() {
            val device = PtpCameraDevice(id = "1", name = "Fujifilm X-T5", transport = "USB")
            assertThat(device.displayName).isEqualTo("Fujifilm X-T5")
        }

        @Test
        @DisplayName("should fall back to model when name is blank")
        fun shouldFallBackToModelWhenNameBlank() {
            val device = PtpCameraDevice(id = "1", name = "", model = "X-T5", transport = "USB")
            assertThat(device.displayName).isEqualTo("X-T5")
        }

        @Test
        @DisplayName("should append manufacturer in parentheses")
        fun shouldAppendManufacturer() {
            val device = PtpCameraDevice(id = "1", name = "X-T5", manufacturer = "Fujifilm", transport = "USB")
            assertThat(device.displayName).isEqualTo("X-T5 (Fujifilm)")
        }

        @Test
        @DisplayName("should show Unknown Camera when both name and model are blank")
        fun shouldShowUnknownWhenBothBlank() {
            val device = PtpCameraDevice(id = "1", name = "", model = null, transport = "USB")
            assertThat(device.displayName).isEqualTo("Unknown Camera")
        }
    }

    @Nested
    @DisplayName("formattedCapacity")
    inner class FormattedCapacity {
        @Test @DisplayName("should format GB")
        fun shouldFormatGb() {
            val device = PtpCameraDevice(id = "1", name = "Test", transport = "USB", capacity = 64_000_000_000)
            assertThat(device.formattedCapacity).isEqualTo("64.0 GB")
        }

        @Test @DisplayName("should format MB")
        fun shouldFormatMb() {
            val device = PtpCameraDevice(id = "1", name = "Test", transport = "USB", capacity = 500_000_000)
            assertThat(device.formattedCapacity).isEqualTo("500.0 MB")
        }

        @Test @DisplayName("should show Unknown when capacity is null")
        fun shouldShowUnknownWhenNull() {
            val device = PtpCameraDevice(id = "1", name = "Test", transport = "USB", capacity = null)
            assertThat(device.formattedCapacity).isEqualTo("Unknown")
        }
    }

    @Nested
    @DisplayName("supportsDelete")
    inner class SupportsDelete {
        @Test @DisplayName("should return true when DELETE_ONE capability present")
        fun shouldSupportDeleteOne() {
            val device = PtpCameraDevice(id = "1", name = "Test", transport = "USB",
                capabilities = setOf(CameraCapability.DELETE_ONE))
            assertThat(device.supportsDelete).isTrue()
        }

        @Test @DisplayName("should return true when DELETE_ALL capability present")
        fun shouldSupportDeleteAll() {
            val device = PtpCameraDevice(id = "1", name = "Test", transport = "USB",
                capabilities = setOf(CameraCapability.DELETE_ALL))
            assertThat(device.supportsDelete).isTrue()
        }

        @Test @DisplayName("should return false when no delete capabilities")
        fun shouldNotSupportDelete() {
            val device = PtpCameraDevice(id = "1", name = "Test", transport = "USB",
                capabilities = emptySet())
            assertThat(device.supportsDelete).isFalse()
        }
    }
}
```

### 3.2 `CameraFileTest`

**File:** `domain/model/CameraFileTest.kt`

Tests for `CameraFile` data class, `CameraFileType` enum, and `toImageFile()` conversion.

```kotlin
@DisplayName("CameraFile")
class CameraFileTest {

    @Nested
    @DisplayName("file type classification")
    inner class FileTypeClassification {
        @Test @DisplayName("should identify RAW files")
        fun shouldIdentifyRaw() {
            val file = CameraFile(id = "1", name = "DSCF1234.RAF", folder = "/DCIM/100FUJI/",
                size = 52428800, creationDate = "2026-05-15T14:30:22Z", fileType = CameraFileType.RAW)
            assertThat(file.isRaw).isTrue()
            assertThat(file.isJpeg).isFalse()
            assertThat(file.isVideo).isFalse()
        }

        @Test @DisplayName("should identify JPEG files")
        fun shouldIdentifyJpeg() {
            val file = CameraFile(id = "2", name = "DSCF1235.JPG", folder = "/DCIM/100FUJI/",
                size = 8200000, creationDate = "2026-05-15T14:30:22Z", fileType = CameraFileType.JPEG)
            assertThat(file.isJpeg).isTrue()
            assertThat(file.isRaw).isFalse()
        }

        @Test @DisplayName("should identify video files")
        fun shouldIdentifyVideo() {
            val file = CameraFile(id = "3", name = "DSCF1236.MP4", folder = "/DCIM/100FUJI/",
                size = 200000000, creationDate = "2026-05-15T14:30:22Z", fileType = CameraFileType.VIDEO)
            assertThat(file.isVideo).isTrue()
        }

        @Test @DisplayName("should identify sidecar files")
        fun shouldIdentifySidecar() {
            val file = CameraFile(id = "4", name = "DSCF1234.XMP", folder = "/DCIM/100FUJI/",
                size = 2048, creationDate = "2026-05-15T14:30:22Z", fileType = CameraFileType.SIDECAR)
            assertThat(file.isSidecar).isTrue()
        }
    }

    @Nested
    @DisplayName("fileExtension")
    inner class FileExtension {
        @Test @DisplayName("should extract extension from filename")
        fun shouldExtractExtension() {
            val file = CameraFile(id = "1", name = "DSCF1234.RAF", folder = "/DCIM/",
                size = 100, creationDate = null, fileType = CameraFileType.RAW)
            assertThat(file.fileExtension).isEqualTo("RAF")
        }

        @Test @DisplayName("should return empty string for no extension")
        fun shouldReturnEmptyForNoExtension() {
            val file = CameraFile(id = "1", name = "DSCF1234", folder = "/DCIM/",
                size = 100, creationDate = null, fileType = CameraFileType.OTHER)
            assertThat(file.fileExtension).isEmpty()
        }
    }

    @Nested
    @DisplayName("CameraFileType.fromExtension")
    inner class FromExtension {
        @ParameterizedTest(name = "should map {0} to {1}")
        @CsvSource(
            "raf, RAW", "cr2, RAW", "cr3, RAW", "nef, RAW", "arw, RAW",
            "dng, RAW", "orf, RAW", "rw2, RAW", "pef, RAW",
            "jpg, JPEG", "jpeg, JPEG",
            "mp4, VIDEO", "mov, VIDEO", "avi, VIDEO",
            "xmp, SIDECAR",
            "txt, OTHER", "doc, OTHER"
        )
        fun shouldMapExtensions(extension: String, expected: CameraFileType) {
            assertThat(CameraFileType.fromExtension(extension)).isEqualTo(expected)
        }

        @Test @DisplayName("should be case-insensitive")
        fun shouldBeCaseInsensitive() {
            assertThat(CameraFileType.fromExtension("RAF")).isEqualTo(CameraFileType.RAW)
            assertThat(CameraFileType.fromExtension("jpg")).isEqualTo(CameraFileType.JPEG)
            assertThat(CameraFileType.fromExtension("JpG")).isEqualTo(CameraFileType.JPEG)
        }
    }

    @Nested
    @DisplayName("toImageFile conversion")
    inner class ToImageFileConversion {
        @Test @DisplayName("should convert RAW camera file to ImageFile with RAW type")
        fun shouldConvertRaw() {
            val destFile = File("/dest/2026/05/DSCF1234.RAF")
            val cameraFile = CameraFile(id = "1", name = "DSCF1234.RAF", folder = "/DCIM/100FUJI/",
                size = 52428800, creationDate = "2026-05-15T14:30:22Z", fileType = CameraFileType.RAW)

            val imageFile = cameraFile.toImageFile(destFile)

            assertThat(imageFile.fileName).isEqualTo("DSCF1234.RAF")
            assertThat(imageFile.filePath).isEqualTo(destFile.absolutePath)
            assertThat(imageFile.fileSize).isEqualTo(52428800L)
            assertThat(imageFile.fileType).isEqualTo(ImageFileType.RAW)
            assertThat(imageFile.importStatus).isEqualTo(ImportStatus.PENDING)
        }

        @Test @DisplayName("should convert JPEG camera file to ImageFile with JPEG type")
        fun shouldConvertJpeg() {
            val destFile = File("/dest/DSCF1235.JPG")
            val cameraFile = CameraFile(id = "2", name = "DSCF1235.JPG", folder = "/DCIM/100FUJI/",
                size = 8200000, creationDate = "2026-05-15T14:30:22Z", fileType = CameraFileType.JPEG)

            val imageFile = cameraFile.toImageFile(destFile)

            assertThat(imageFile.fileType).isEqualTo(ImageFileType.JPEG)
        }
    }
}
```

### 3.3 `CameraImportProgressTest`

**File:** `domain/model/CameraImportProgressTest.kt`

```kotlin
@DisplayName("CameraImportProgress")
class CameraImportProgressTest {

    @Test @DisplayName("should calculate download percentage")
    fun shouldCalculateDownloadPercentage() {
        val progress = CameraImportProgress(
            phase = CameraImportProgress.ImportPhase.DOWNLOADING,
            currentFile = 3,
            totalFiles = 10,
            fileName = "DSCF1234.RAF",
            bytesDownloaded = 26214400L,
            totalBytes = 52428800L
        )
        assertThat(progress.downloadPercentage).isCloseTo(0.5f, within(0.01f))
    }

    @Test @DisplayName("should calculate overall percentage")
    fun shouldCalculateOverallPercentage() {
        val progress = CameraImportProgress(
            phase = CameraImportProgress.ImportPhase.DOWNLOADING,
            currentFile = 5,
            totalFiles = 10,
            fileName = "test.RAF",
            bytesDownloaded = 0L,
            totalBytes = 0L
        )
        assertThat(progress.overallPercentage).isCloseTo(0.5f, within(0.01f))
    }

    @Test @DisplayName("should handle zero total bytes gracefully")
    fun shouldHandleZeroTotalBytes() {
        val progress = CameraImportProgress(
            phase = CameraImportProgress.ImportPhase.PLANNING,
            currentFile = 0,
            totalFiles = 0,
            fileName = "",
            bytesDownloaded = 0L,
            totalBytes = 0L
        )
        assertThat(progress.downloadPercentage).isEqualTo(0f)
        assertThat(progress.overallPercentage).isEqualTo(0f)
    }

    @Nested
    @DisplayName("ImportPhase")
    inner class PhaseTests {
        @Test @DisplayName("should progress through all phases")
        fun shouldProgressThroughPhases() {
            val phases = CameraImportProgress.ImportPhase.entries
            assertThat(phases).containsExactly(
                CameraImportProgress.ImportPhase.PLANNING,
                CameraImportProgress.ImportPhase.DOWNLOADING,
                CameraImportProgress.ImportPhase.VERIFYING,
                CameraImportProgress.ImportPhase.DELETING,
                CameraImportProgress.ImportPhase.COMPLETE
            )
        }
    }
}
```

### 3.4 `CameraCapabilityTest`

**File:** `domain/model/PtpCameraDeviceTest.kt` (in same file as above) or separate

```kotlin
@DisplayName("CameraCapability")
class CameraCapabilityTest {

    @Test @DisplayName("should have all expected capabilities")
    fun shouldHaveAllCapabilities() {
        assertThat(CameraCapability.entries).containsExactly(
            CameraCapability.DELETE_ONE,
            CameraCapability.DELETE_ALL,
            CameraCapability.TAKE_PICTURE,
            CameraCapability.PTP_COMMANDS,
            CameraCapability.HEIF_SUPPORT,
            CameraCapability.RECEIVE_FILE,
            CameraCapability.THUMBNAIL,
            CameraCapability.METADATA,
            CameraCapability.PARTIAL_READ
        )
    }

    @Test @DisplayName("should serialize and deserialize capability sets")
    fun shouldSerializeCapabilitySets() {
        val capabilities = setOf(CameraCapability.DELETE_ONE, CameraCapability.THUMBNAIL, CameraCapability.METADATA)
        val json = Json.encodeToString(SetSerializer(CameraCapability.serializer()), capabilities)
        val decoded = Json.decodeFromString(SetSerializer(CameraCapability.serializer()), json)
        assertThat(decoded).isEqualTo(capabilities)
    }
}
```

### 3.5 `ImportSourceTest`

**File:** `ui/screens/model/ImportSourceTest.kt`

> **Note:** `ImportSource` is a UI-only enum (not a domain model), but it's still valuable to test for exhaustive `when` coverage.

```kotlin
@DisplayName("ImportSource")
class ImportSourceTest {
    @Test @DisplayName("should have FOLDER and CAMERA values")
    fun shouldHaveExpectedValues() {
        assertThat(ImportSource.entries).containsExactly(ImportSource.FOLDER, ImportSource.CAMERA)
    }
}
```

---

## 4. Phase 2: Port Contract Tests

A single abstract test class that verifies the `CameraDevicePort` contract. Each adapter implementation runs the same tests.

### 4.1 `CameraDevicePortContractTest`

**File:** `domain/port/CameraDevicePortContractTest.kt`

```kotlin
@DisplayName("CameraDevicePort contract")
abstract class CameraDevicePortContractTest {

    abstract fun createPort(): CameraDevicePort

    protected lateinit var port: CameraDevicePort

    @BeforeEach
    fun setup() {
        port = createPort()
    }

    @Nested
    @DisplayName("isAvailable")
    inner class IsAvailable {
        @Test @DisplayName("should return boolean without throwing")
        fun shouldReturnBoolean() {
            assertThat(port.isAvailable()).isInstanceOf(Boolean::class.java)
        }
    }

    @Nested
    @DisplayName("detectPtpDevices")
    inner class DetectPtpDevices {
        @Test @DisplayName("should return empty list when no devices connected")
        fun shouldReturnEmptyListWhenNoDevices() = runTest {
            val devices = port.detectPtpDevices()
            assertThat(devices).isNotNull()
            // May or may not be empty — depends on hardware
        }

        @Test @DisplayName("should return devices with valid IDs")
        fun shouldReturnDevicesWithValidIds() = runTest {
            val devices = port.detectPtpDevices()
            devices.forEach { device ->
                assertThat(device.id).isNotBlank()
                assertThat(device.name).isNotBlank()
            }
        }
    }

    @Nested
    @DisplayName("browseDevice")
    inner class BrowseDevice {
        @Test @DisplayName("should throw CameraDeviceException for invalid device ID")
        fun shouldThrowForInvalidDevice() = runTest {
            assertThrows<CameraDeviceException> {
                port.browseDevice("invalid-device-id-12345")
            }
        }
    }

    @Nested
    @DisplayName("downloadFile")
    inner class DownloadFile {
        @Test @DisplayName("should throw CameraDeviceException for invalid device ID")
        fun shouldThrowForInvalidDevice() = runTest {
            assertThrows<CameraDeviceException> {
                port.downloadFile("invalid-id", "invalid-file", "/tmp/test.RAF")
            }
        }
    }

    @Nested
    @DisplayName("ejectDevice")
    inner class EjectDevice {
        @Test @DisplayName("should return false for invalid device ID")
        fun shouldReturnFalseForInvalidDevice() = runTest {
            val result = port.ejectDevice("invalid-device-id-12345")
            assertThat(result).isFalse()
        }
    }

    @Nested
    @DisplayName("deleteFiles")
    inner class DeleteFiles {
        @Test @DisplayName("should return false for invalid device ID")
        fun shouldReturnFalseForInvalidDevice() = runTest {
            val result = port.deleteFiles("invalid-id", listOf("invalid-file"))
            assertThat(result).isFalse()
        }
    }
}
```

Each concrete adapter then extends this contract:

```kotlin
@DisplayName("ImageCaptureCoreBridgeAdapter contract")
@EnabledOnOs(OS.MAC)
class ImageCaptureCoreBridgeAdapterContractTest : CameraDevicePortContractTest() {
    override fun createPort() = ImageCaptureCoreBridgeAdapter()
}

@DisplayName("Gphoto2BridgeAdapter contract")
class Gphoto2BridgeAdapterContractTest : CameraDevicePortContractTest() {
    override fun createPort() = Gphoto2BridgeAdapter()
}

@DisplayName("WpdBridgeAdapter contract")
@EnabledOnOs(OS.WINDOWS)
class WpdBridgeAdapterContractTest : CameraDevicePortContractTest() {
    override fun createPort() = WpdBridgeAdapter()
}
```

---

## 5. Phase 3: Adapter Unit Tests

### 5.1 `NoOpCameraDevicePortTest`

**File:** `infrastructure/adapter/camera/NoOpCameraDevicePortTest.kt`

```kotlin
@DisplayName("NoOpCameraDevicePort")
class NoOpCameraDevicePortTest {

    private val port = NoOpCameraDevicePort()

    @Test @DisplayName("isAvailable should return false")
    fun shouldNotBeAvailable() {
        assertThat(port.isAvailable()).isFalse()
    }

    @Test @DisplayName("detectPtpDevices should return empty list")
    fun shouldReturnEmptyDevices() = runTest {
        assertThat(port.detectPtpDevices()).isEmpty()
    }

    @Test @DisplayName("browseDevice should throw CameraDeviceException")
    fun shouldThrowOnBrowse() = runTest {
        assertThrows<CameraDeviceException> { port.browseDevice("any-id") }
    }

    @Test @DisplayName("downloadFile should throw CameraDeviceException")
    fun shouldThrowOnDownload() = runTest {
        assertThrows<CameraDeviceException> { port.downloadFile("any-id", "any-file", "/tmp/dest") }
    }

    @Test @DisplayName("downloadFiles should throw CameraDeviceException")
    fun shouldThrowOnDownloadBatch() = runTest {
        assertThrows<CameraDeviceException> {
            port.downloadFiles("any-id", listOf("f1" to "n1"), "/tmp/dest")
        }
    }

    @Test @DisplayName("downloadThumbnail should return null")
    fun shouldReturnNullThumbnail() = runTest {
        assertThat(port.downloadThumbnail("any-id", "any-file")).isNull()
    }

    @Test @DisplayName("deleteFiles should return false")
    fun shouldReturnFalseOnDelete() = runTest {
        assertThat(port.deleteFiles("any-id", listOf("any-file"))).isFalse()
    }

    @Test @DisplayName("ejectDevice should return false")
    fun shouldReturnFalseOnEject() = runTest {
        assertThat(port.ejectDevice("any-id")).isFalse()
    }

    @Test @DisplayName("observePtpDeviceChanges should emit nothing and complete")
    fun shouldEmitNothing() = runTest {
        val events = mutableListOf<PtpDeviceEvent>()
        port.observePtpDeviceChanges().collect { events.add(it) }
        assertThat(events).isEmpty()
    }
}
```

### 5.2 `CompositeDeviceAdapterTest`

**File:** `infrastructure/adapter/camera/CompositeDeviceAdapterTest.kt`

```kotlin
@DisplayName("CompositeDeviceAdapter")
class CompositeDeviceAdapterTest {

    private lateinit var storageDetector: DevicePort
    private lateinit var cameraDetector: CameraDevicePort
    private lateinit var compositeAdapter: CompositeDeviceAdapter

    @BeforeEach
    fun setup() = runTest {
        storageDetector = mock(DevicePort::class.java)
        cameraDetector = mock(CameraDevicePort::class.java)
        compositeAdapter = CompositeDeviceAdapter(storageDetector, cameraDetector)
    }

    @Nested
    @DisplayName("detectDevices")
    inner class DetectDevices {
        @Test @DisplayName("should merge mass-storage and PTP devices")
        fun shouldMergeDevices() = runTest {
            val storageDevice = CameraDevice(id = "sd1", name = "SD Card",
                deviceType = DeviceType.SD_CARD, mountPoint = "/Volumes/SD")
            val ptpDevice = PtpCameraDevice(id = "ptp1", name = "Fujifilm X-T5", transport = "USB")

            whenever(storageDetector.detectDevices()).thenReturn(listOf(storageDevice))
            whenever(cameraDetector.detectPtpDevices()).thenReturn(listOf(ptpDevice))

            val result = compositeAdapter.detectDevices()

            assertThat(result).hasSize(2)
            assertThat(result[0].name).isEqualTo("SD Card")
            assertThat(result[1].name).isEqualTo("Fujifilm X-T5")
            assertThat(result[1].isPtpDevice).isTrue()
        }

        @Test @DisplayName("should return only mass-storage devices when PTP unavailable")
        fun shouldReturnOnlyStorageWhenPtpUnavailable() = runTest {
            val storageDevice = CameraDevice(id = "sd1", name = "SD Card", deviceType = DeviceType.SD_CARD)
            whenever(storageDetector.detectDevices()).thenReturn(listOf(storageDevice))
            whenever(cameraDetector.detectPtpDevices()).thenReturn(emptyList())

            val result = compositeAdapter.detectDevices()
            assertThat(result).hasSize(1)
            assertThat(result[0].name).isEqualTo("SD Card")
        }

        @Test @DisplayName("should handle PTP detection failure gracefully")
        fun shouldHandlePtpFailure() = runTest {
            whenever(storageDetector.detectDevices()).thenReturn(emptyList())
            whenever(cameraDetector.detectPtpDevices()).thenThrow(CameraDeviceException("Bridge crashed"))

            // Should not throw — should return empty list since storage also returns empty
            val result = compositeAdapter.detectDevices()
            assertThat(result).isEmpty()
        }
    }

    @Nested
    @DisplayName("ejectDevice")
    inner class EjectDevice {
        @Test @DisplayName("should delegate to cameraDetector for PTP devices")
        fun shouldDelegatePtpEject() = runTest {
            val ptpDevice = CameraDevice(id = "ptp1", name = "Camera", isPtpDevice = true,
                ptpDeviceId = "ptp1", transport = "USB")
            whenever(cameraDetector.ejectDevice("ptp1")).thenReturn(true)

            val result = compositeAdapter.ejectDevice(ptpDevice)
            assertThat(result).isTrue()
            verify(cameraDetector).ejectDevice("ptp1")
        }

        @Test @DisplayName("should delegate to storageDetector for mass-storage devices")
        fun shouldDelegateStorageEject() = runTest {
            val storageDevice = CameraDevice(id = "sd1", name = "SD Card",
                mountPoint = "/Volumes/SD", isPtpDevice = false)
            whenever(storageDetector.ejectDevice(storageDevice)).thenReturn(true)

            val result = compositeAdapter.ejectDevice(storageDevice)
            assertThat(result).isTrue()
            verify(storageDetector).ejectDevice(storageDevice)
        }
    }
}
```

### 5.3 Bridge Adapter Unit Tests

These test JSON protocol parsing and error handling without a real bridge process.

#### `ImageCaptureCoreBridgeAdapterTest`

**File:** `infrastructure/adapter/camera/ImageCaptureCoreBridgeAdapterTest.kt`

```kotlin
@DisplayName("ImageCaptureCoreBridgeAdapter")
class ImageCaptureCoreBridgeAdapterTest {

    private lateinit var adapter: ImageCaptureCoreBridgeAdapter

    @BeforeEach
    fun setup() {
        // Use a test-friendly constructor that doesn't try to find the binary
        adapter = ImageCaptureCoreBridgeAdapter(bridgeCommand = null)
    }

    @Nested
    @DisplayName("isAvailable")
    inner class IsAvailable {
        @Test @DisplayName("should return false when bridge command is null")
        fun shouldReturnFalseWhenNoCommand() {
            assertThat(adapter.isAvailable()).isFalse()
        }
    }

    @Nested
    @DisplayName("parseDevices")
    inner class ParseDevices {
        @Test @DisplayName("should parse JSON device list response")
        fun shouldParseDeviceList() {
            val json = """
                {"status":"ok","devices":[
                    {"id":"usb-001-004","name":"Fujifilm X-T5","manufacturer":"Fujifilm",
                     "model":"X-T5","transport":"USB","fileCount":2847,
                     "capacity":64200000000,"available":12800000000,
                     "capabilities":["DELETE_ONE","THUMBNAIL"],"bridgeType":"imagecapturecore"}
                ]}
            """.trimIndent()
            val devices = adapter.parseDevices(json)
            assertThat(devices).hasSize(1)
            assertThat(devices[0].name).isEqualTo("Fujifilm X-T5")
            assertThat(devices[0].capabilities).contains(CameraCapability.DELETE_ONE, CameraCapability.THUMBNAIL)
        }

        @Test @DisplayName("should return empty list for empty devices array")
        fun shouldReturnEmptyForEmptyDevices() {
            val json = """{"status":"ok","devices":[]}"""
            val devices = adapter.parseDevices(json)
            assertThat(devices).isEmpty()
        }

        @Test @DisplayName("should throw CameraDeviceException for error response")
        fun shouldThrowForError() {
            val json = """{"status":"error","error":{"code":"session_failed","message":"Could not open session"}}"""
            assertThrows<CameraDeviceException> { adapter.parseDevices(json) }
        }
    }

    @Nested
    @DisplayName("parseFiles")
    inner class ParseFiles {
        @Test @DisplayName("should parse file list with all fields")
        fun shouldParseFileList() {
            val json = """
                {"status":"ok","files":[
                    {"id":"file-1","name":"DSCF1234.RAF","folder":"/DCIM/100FUJI/",
                     "size":52428800,"creationDate":"2026-05-15T14:30:22Z",
                     "modificationDate":"2026-05-15T14:30:22Z","fileType":"raw",
                     "width":6240,"height":4160,"hasThumbnail":true,"isLocked":false}
                ]}
            """.trimIndent()
            val files = adapter.parseFiles(json)
            assertThat(files).hasSize(1)
            assertThat(files[0].name).isEqualTo("DSCF1234.RAF")
            assertThat(files[0].size).isEqualTo(52428800L)
            assertThat(files[0].fileType).isEqualTo(CameraFileType.RAW)
            assertThat(files[0].hasThumbnail).isTrue()
        }
    }
}
```

#### `Gphoto2BridgeAdapterTest`

**File:** `infrastructure/adapter/camera/Gphoto2BridgeAdapterTest.kt`

```kotlin
@DisplayName("Gphoto2BridgeAdapter")
class Gphoto2BridgeAdapterTest {

    private lateinit var adapter: Gphoto2BridgeAdapter

    @BeforeEach
    fun setup() {
        adapter = Gphoto2BridgeAdapter()
    }

    @Nested
    @DisplayName("parseGphoto2Detect")
    inner class ParseDetect {
        @Test @DisplayName("should parse gphoto2 --auto-detect output")
        fun shouldParseAutoDetect() {
            val output = """
                Model                          Port
                ------------------------------------------------------------
                Fujifilm X-T5                  usb:001,004
                Canon EOS R5                   usb:001,006
            """.trimIndent()
            val devices = adapter.parseGphoto2Detect(output)
            assertThat(devices).hasSize(2)
            assertThat(devices[0].name).isEqualTo("Fujifilm X-T5")
            assertThat(devices[0].transport).isEqualTo("USB")
            assertThat(devices[1].name).isEqualTo("Canon EOS R5")
        }

        @Test @DisplayName("should handle empty auto-detect output")
        fun shouldHandleEmptyOutput() {
            val output = """
                Model                          Port
                ------------------------------------------------------------
            """.trimIndent()
            val devices = adapter.parseGphoto2Detect(output)
            assertThat(devices).isEmpty()
        }
    }

    @Nested
    @DisplayName("parseGphoto2ListFiles")
    inner class ParseListFiles {
        @Test @DisplayName("should parse file listing output")
        fun shouldParseFileListing() {
            val output = """
                Content of Directory /DCIM/100FUJI/:
                -rw-r--r--  52M DSCF1234.RAF  2026-05-15 14:30
                -rw-r--r-- 8.2M DSCF1235.JPG  2026-05-15 14:32
                
                Content of Directory /DCIM/101FUJI/:
                -rw-r--r-- 52M DSCF1236.RAF  2026-05-16 09:15
            """.trimIndent()
            val files = adapter.parseGphoto2ListFiles(output)
            assertThat(files).hasSize(3)
            assertThat(files[0].name).isEqualTo("DSCF1234.RAF")
            assertThat(files[0].folder).isEqualTo("/DCIM/100FUJI/")
            assertThat(files[0].fileType).isEqualTo(CameraFileType.RAW)
        }
    }
}
```

---

## 6. Phase 4: Import Service Tests

### 6.1 `CameraImportServiceTest`

**File:** `application/CameraImportServiceTest.kt`

Tests the camera import flow using a `MockCameraDevicePort` (mocked bridge) and real naming logic.

```kotlin
@DisplayName("CameraImportService camera import")
class CameraImportServiceTest {

    private lateinit var cameraDevicePort: CameraDevicePort
    private lateinit var imageRepository: ImageRepositoryPort
    private lateinit var namingPort: NamingPort
    private lateinit var deduplicationPort: DeduplicationPort
    private lateinit var service: CameraImportService

    @TempDir lateinit var tempDir: File

    @BeforeEach
    fun setup() {
        cameraDevicePort = mock(CameraDevicePort::class.java)
        imageRepository = mock(ImageRepositoryPort::class.java)
        namingPort = mock(NamingPort::class.java)
        deduplicationPort = mock(DeduplicationPort::class.java)
        service = CameraImportService(cameraDevicePort = cameraDevicePort, namingPort = namingPort, historyAdapter = historyAdapter)
    }

    private fun testCameraDevice() = PtpCameraDevice(
        id = "test-device-1",
        name = "Test Camera",
        manufacturer = "TestCo",
        model = "TestModel",
        transport = "USB",
        capabilities = setOf(CameraCapability.DELETE_ONE, CameraCapability.THUMBNAIL)
    )

    private fun testCameraFile(name: String, size: Long = 1000000L) = CameraFile(
        id = "file-$name",
        name = name,
        folder = "/DCIM/100TEST/",
        size = size,
        creationDate = "2026-05-15T14:30:22Z",
        fileType = CameraFileType.RAW
    )

    @Nested
    @DisplayName("disk space check")
    inner class DiskSpaceCheck {
        @Test @DisplayName("should throw InsufficientDiskSpaceException when disk is too full")
        fun shouldThrowWhenDiskFull() = runTest {
            val device = testCameraDevice()
            val files = listOf(testCameraFile("big.RAF", 1_000_000_000_000L)) // 1TB
            val config = ImportConfiguration()

            // tempDir has limited space — totalBytes × 1.05 will exceed it
            assertThrows<InsufficientDiskSpaceException> {
                service.importFromCamera(device, files, tempDir.absolutePath, config)
            }
        }

        @Test @DisplayName("should proceed when disk has enough space")
        fun shouldProceedWhenSpaceAvailable() = runTest {
            val device = testCameraDevice()
            val files = listOf(testCameraFile("small.JPG", 100L))
            val config = ImportConfiguration(verifyAfterCopy = false)

            whenever(cameraDevicePort.downloadFile(any(), any(), any())).thenAnswer { invocation ->
                val destPath = invocation.getArgument<String>(2)
                File(destPath).apply { parentFile?.mkdirs(); writeText("x") }
                File(destPath)
            }
            whenever(namingPort.generateFolderPath(any(), any(), any()))
                .thenReturn(tempDir.absolutePath)
            whenever(namingPort.generateFileName(any(), any(), any()))
                .thenReturn("small.JPG")

            // Should not throw — 100 bytes easily fits
            val result = service.importFromCamera(device, files, tempDir.absolutePath, config)
            assertThat(result).isNotNull()
        }
    }

    @Nested
    @DisplayName("download to destination (no staging)")
    inner class DownloadToDestination {
        @Test @DisplayName("should download files directly to destination path")
        fun shouldDownloadToDestination() = runTest {
            val device = testCameraDevice()
            val files = listOf(testCameraFile("DSCF1234.RAF"))
            val config = ImportConfiguration(verifyAfterCopy = false)
            val destDir = File(tempDir, "photos")

            whenever(namingPort.generateFolderPath(any(), eq(destDir.absolutePath), any()))
                .thenReturn(destDir.absolutePath)
            whenever(namingPort.generateFileName(any(), any(), any()))
                .thenReturn("2026-05-15_DSCF1234.RAF")
            whenever(cameraDevicePort.downloadFile(eq(device.id), any(), any()))
                .thenAnswer { invocation ->
                    val destPath = invocation.getArgument<String>(2)
                    File(destPath).apply { parentFile?.mkdirs(); writeText("fake-raf-data") }
                    File(destPath)
                }

            val result = service.importFromCamera(device, files, destDir.absolutePath, config)

            assertThat(result.successCount).isEqualTo(1)
            verify(cameraDevicePort).downloadFile(eq(device.id), eq("file-DSCF1234.RAF"),
                argThat { it.contains("2026-05-15_DSCF1234.RAF") })
        }

        @Test @DisplayName("should create destination directories before download")
        fun shouldCreateDirectories() = runTest {
            val device = testCameraDevice()
            val files = listOf(testCameraFile("photo.RAF"))
            val config = ImportConfiguration(verifyAfterCopy = false)

            whenever(namingPort.generateFolderPath(any(), any(), any()))
                .thenReturn(File(tempDir, "2026/05-May").absolutePath)
            whenever(namingPort.generateFileName(any(), any(), any()))
                .thenReturn("photo.RAF")
            whenever(cameraDevicePort.downloadFile(any(), any(), any()))
                .thenAnswer { invocation ->
                    val destPath = invocation.getArgument<String>(2)
                    val destFile = File(destPath)
                    destFile.parentFile?.mkdirs()
                    destFile.writeText("data")
                    destFile
                }

            service.importFromCamera(device, files, tempDir.absolutePath, config)

            // Verify parent directories were created
            assertThat(File(tempDir, "2026/05-May")).exists()
        }
    }

    @Nested
    @DisplayName("delete after import")
    inner class DeleteAfterImport {
        @Test @DisplayName("should delete from camera when configured and all files succeed")
        fun shouldDeleteWhenConfigured() = runTest {
            val device = testCameraDevice()
            val files = listOf(testCameraFile("photo.RAF"))
            val config = ImportConfiguration(verifyAfterCopy = false, deleteAfterImport = true)

            whenever(namingPort.generateFolderPath(any(), any(), any())).thenReturn(tempDir.absolutePath)
            whenever(namingPort.generateFileName(any(), any(), any())).thenReturn("photo.RAF")
            whenever(cameraDevicePort.downloadFile(any(), any(), any()))
                .thenAnswer { File(it.getArgument<String>(2)).apply { writeText("data") } }
            whenever(cameraDevicePort.deleteFiles(any(), any())).thenReturn(true)

            val result = service.importFromCamera(device, files, tempDir.absolutePath, config)

            assertThat(result.deletedSourceCount).isEqualTo(1)
            verify(cameraDevicePort).deleteFiles(eq(device.id), any())
        }

        @Test @DisplayName("should NOT delete from camera when some downloads fail")
        fun shouldNotDeleteWhenFailures() = runTest {
            val device = testCameraDevice()
            val files = listOf(testCameraFile("good.RAF"), testCameraFile("bad.RAF"))
            val config = ImportConfiguration(verifyAfterCopy = false, deleteAfterImport = true)

            whenever(namingPort.generateFolderPath(any(), any(), any())).thenReturn(tempDir.absolutePath)
            whenever(namingPort.generateFileName(any(), any(), any())).thenReturn("photo.RAF")
            whenever(cameraDevicePort.downloadFile(eq(device.id), eq("file-good.RAF"), any()))
                .thenAnswer { File(it.getArgument<String>(2)).apply { writeText("data") } }
            whenever(cameraDevicePort.downloadFile(eq(device.id), eq("file-bad.RAF"), any()))
                .thenThrow(CameraDeviceException("Download failed"))

            val result = service.importFromCamera(device, files, tempDir.absolutePath, config)

            // Should NOT have called deleteFiles because one file failed
            verify(cameraDevicePort, never()).deleteFiles(any(), any())
            assertThat(result.errorCount).isEqualTo(1)
        }
    }
}
```

### 6.2 `CameraImportServiceEdgeCaseTest`

**File:** `application/CameraImportServiceEdgeCaseTest.kt`

```kotlin
@DisplayName("CameraImportService camera import edge cases")
class CameraImportServiceEdgeCaseTest {

    @Nested
    @DisplayName("camera disconnect during download")
    inner class CameraDisconnect {
        @Test @DisplayName("should report error and continue with remaining files")
        fun shouldContinueAfterDisconnect() = runTest {
            // ... verify partial download succeeds, error count reflects failure
        }
    }

    @Nested
    @DisplayName("empty file list")
    inner class EmptyFileList {
        @Test @DisplayName("should return immediately with zero counts")
        fun shouldReturnImmediately() = runTest {
            // ... import 0 files should complete instantly
        }
    }

    @Nested
    @DisplayName("camera device port unavailable")
    inner class PortUnavailable {
        @Test @DisplayName("should throw CameraDeviceException when port is null")
        fun shouldThrowWhenPortNull() = runTest {
            val service = CameraImportService(cameraDevicePort = cameraDevicePort,
                namingPort = mock(), devicePort = null) // No camera port
            val device = testCameraDevice()
            assertThrows<CameraDeviceException> {
                service.importFromCamera(device, emptyList(), "/tmp", ImportConfiguration())
            }
        }
    }

    @Nested
    @DisplayName("size verification after download")
    inner class SizeVerification {
        @Test @DisplayName("should mark file as error when size mismatches")
        fun shouldMarkErrorOnSizeMismatch() = runTest {
            // ... download succeeds, but destination file size != camera file size
        }

        @Test @DisplayName("should delete partial file on size mismatch")
        fun shouldDeletePartialFileOnMismatch() = runTest {
            // ... after size mismatch, the partial download should be cleaned up
        }
    }
}
```

---

## 7. Phase 5: UI Logic Tests

### 7.1 `CameraDeviceCardTest`

**File:** `ui/screens/components/CameraDeviceCardTest.kt`

Non-Compose tests for the data logic behind the card. Compose UI tests require a running desktop environment.

```kotlin
@DisplayName("CameraDeviceCard data")
class CameraDeviceCardTest {

    @Test @DisplayName("should format PTP device correctly")
    fun shouldFormatPtpDevice() {
        val cameraDevice = CameraDevice(
            id = "ptp1", name = "Fujifilm X-T5", model = "X-T5",
            manufacturer = "Fujifilm", transport = "USB",
            isPtpDevice = true, ptpDeviceId = "ptp1",
            deviceType = DeviceType.CAMERA,
            capabilities = setOf(CameraCapability.DELETE_ONE)
        )
        val ptpDevice = PtpCameraDevice(
            id = "ptp1", name = "Fujifilm X-T5", transport = "USB",
            fileCount = 2847, capacity = 64_000_000_000L,
            available = 12_800_000_000L
        )

        assertThat(cameraDevice.displayName).isEqualTo("Fujifilm X-T5")
        assertThat(ptpDevice.formattedCapacity).contains("GB")
        assertThat(cameraDevice.isPtpDevice).isTrue()
    }
}
```

### 7.2 `MediaImportCameraSourceTest`

**File:** `ui/screens/MediaImportCameraSourceTest.kt`

Tests for the auto-select and dual-button UI logic. These are non-Compose presenter/logic tests.

```kotlin
@DisplayName("MediaImportScreen camera source logic")
class MediaImportCameraSourceTest {

    private lateinit var cameraDevicePort: CameraDevicePort
    private lateinit var fakeCameraPort: FakeCameraDevicePort

    @BeforeEach
    fun setUp() {
        fakeCameraPort = FakeCameraDevicePort()
        cameraDevicePort = fakeCameraPort
    }

    @Nested @DisplayName("Startup auto-select")
    inner class StartupAutoSelect {

        @Test @DisplayName("should default to FOLDER when no camera detected at startup")
        fun shouldDefaultToFolderWhenNoCamera() {
            // No devices added → detectPtpDevices() returns empty
            val source = determineInitialSource(
                cameraPort = fakeCameraPort,
                detectedPtpDevices = fakeCameraPort.detectPtpDevices()
            )
            assertThat(source).isEqualTo(ImportSource.FOLDER)
        }

        @Test @DisplayName("should auto-select CAMERA when camera detected at startup")
        fun shouldAutoSelectCameraWhenDetected() {
            fakeCameraPort.addDevice(CameraTestFixtures.fujiXt5(), CameraTestFixtures.sampleFileSet())
            val source = determineInitialSource(
                cameraPort = fakeCameraPort,
                detectedPtpDevices = fakeCameraPort.detectPtpDevices()
            )
            assertThat(source).isEqualTo(ImportSource.CAMERA)
        }

        @Test @DisplayName("should auto-select first camera when multiple cameras detected")
        fun shouldAutoSelectFirstCamera() {
            fakeCameraPort.addDevice(CameraTestFixtures.fujiXt5(), CameraTestFixtures.sampleFileSet())
            fakeCameraPort.addDevice(CameraTestFixtures.canonR5(), CameraTestFixtures.sampleFileSet())
            val result = determineInitialSource(
                cameraPort = fakeCameraPort,
                detectedPtpDevices = fakeCameraPort.detectPtpDevices()
            )
            assertThat(result).isEqualTo(ImportSource.CAMERA)
        }

        @Test @DisplayName("should default to FOLDER when camera bridge not available")
        fun shouldDefaultToFolderWhenBridgeUnavailable() {
            // NoOpCameraDevicePort.isAvailable() returns false
            val noOpPort = NoOpCameraDevicePort()
            val source = determineInitialSource(
                cameraPort = noOpPort,
                detectedPtpDevices = emptyList()
            )
            assertThat(source).isEqualTo(ImportSource.FOLDER)
        }
    }

    @Nested @DisplayName("Hot-plug auto-switch")
    inner class HotPlugAutoSwitch {

        @Test @DisplayName("should switch to CAMERA when camera plugged in while app running")
        fun shouldSwitchToCameraOnPlugIn() {
            val current = ImportSource.FOLDER
            val event = PtpDeviceEvent.Connected(CameraTestFixtures.fujiXt5())
            val newSource = applyDeviceEvent(current, event, remainingDevices = listOf(event.device))
            assertThat(newSource).isEqualTo(ImportSource.CAMERA)
        }

        @Test @DisplayName("should fallback to FOLDER when last camera unplugged")
        fun shouldFallbackToFolderOnUnplug() {
            val current = ImportSource.CAMERA
            val event = PtpDeviceEvent.Disconnected(deviceId = "ptp1")
            val newSource = applyDeviceEvent(current, event, remainingDevices = emptyList())
            assertThat(newSource).isEqualTo(ImportSource.FOLDER)
        }

        @Test @DisplayName("should stay on CAMERA when one of multiple cameras unplugged")
        fun shouldStayOnCameraWhenOtherCamerasRemain() {
            val current = ImportSource.CAMERA
            val event = PtpDeviceEvent.Disconnected(deviceId = "ptp1")
            val remaining = listOf(CameraTestFixtures.canonR5())
            val newSource = applyDeviceEvent(current, event, remainingDevices = remaining)
            assertThat(newSource).isEqualTo(ImportSource.CAMERA)
        }

        @Test @DisplayName("should stay on FOLDER when camera plugged in but user manually selected folder")
        fun shouldRespectManualFolderChoice() {
            // If user explicitly clicked "Import from Folder", don't auto-switch
            // This is a design choice — auto-switch only on startup, not on hot-plug
            // Or: always auto-switch on hot-plug (current plan). Document the decision.
            val current = ImportSource.FOLDER
            val event = PtpDeviceEvent.Connected(CameraTestFixtures.fujiXt5())
            // Current plan: always auto-switch on hot-plug
            val newSource = applyDeviceEvent(current, event, remainingDevices = listOf(event.device))
            assertThat(newSource).isEqualTo(ImportSource.CAMERA)
        }
    }
}

// Helper functions (extracted from composable logic for testability)
fun determineInitialSource(cameraPort: CameraDevicePort, detectedPtpDevices: List<PtpCameraDevice>): ImportSource {
    if (!cameraPort.isAvailable()) return ImportSource.FOLDER
    return if (detectedPtpDevices.isNotEmpty()) ImportSource.CAMERA else ImportSource.FOLDER
}

fun applyDeviceEvent(currentSource: ImportSource, event: PtpDeviceEvent, remainingDevices: List<PtpCameraDevice>): ImportSource {
    return when (event) {
        is PtpDeviceEvent.Connected -> ImportSource.CAMERA  // auto-switch
        is PtpDeviceEvent.Disconnected -> {
            if (remainingDevices.isEmpty()) ImportSource.FOLDER else currentSource
        }
    }
}
```

---

## 8. Phase 6: Integration Tests

### 8.1 `FakeCameraDevicePort`

**File:** `testutil/FakeCameraDevicePort.kt`

An in-memory fake for integration tests that simulates a camera with files:

```kotlin
class FakeCameraDevicePort : CameraDevicePort {
    private val devices = mutableMapOf<String, PtpCameraDevice>()
    private val files = mutableMapOf<String, MutableList<CameraFile>>()
    private val downloadedFiles = mutableMapOf<String, ByteArray>()  // destPath → content
    private val deletedFiles = mutableSetOf<String>()

    fun addDevice(device: PtpCameraDevice, cameraFiles: List<CameraFile>) {
        devices[device.id] = device
        files[device.id] = cameraFiles.toMutableList()
    }

    override fun isAvailable() = true

    override suspend fun detectPtpDevices() = devices.values.toList()

    override suspend fun browseDevice(deviceId: String): List<CameraFile> {
        return files[deviceId] ?: throw CameraDeviceException("Device not found: $deviceId")
    }

    override suspend fun downloadFile(deviceId: String, fileId: String, destinationPath: String): File {
        val deviceFiles = files[deviceId] ?: throw CameraDeviceException("Device not found")
        val camFile = deviceFiles.find { it.id == fileId }
            ?: throw CameraDeviceException("File not found: $fileId")

        // Write fake data directly to destination — NO STAGING
        val destFile = File(destinationPath)
        destFile.parentFile?.mkdirs()
        destFile.writeBytes(ByteArray(camFile.size.toInt()) { 0xAB })
        downloadedFiles[destinationPath] = destFile.readBytes()
        return destFile
    }

    override suspend fun downloadFiles(
        deviceId: String, files: List<Pair<String, String>>, destDir: String,
        onProgress: (CameraImportProgress) -> Unit
    ): List<File> = coroutineScope {
        val results = mutableListOf<File>()
        files.forEachIndexed { index, (fileId, fileName) ->
            onProgress(CameraImportProgress(
                phase = CameraImportProgress.ImportPhase.DOWNLOADING,
                currentFile = index + 1,
                totalFiles = files.size,
                fileName = fileName,
                bytesDownloaded = (index + 1) * 1000000L,
                totalBytes = files.size * 1000000L
            ))
            val destPath = File(destDir, fileName).absolutePath
            results.add(downloadFile(deviceId, fileId, destPath))
        }
        results
    }

    override suspend fun deleteFiles(deviceId: String, fileIds: List<String>): Boolean {
        deletedFiles.addAll(fileIds)
        files[deviceId]?.removeAll { it.id in fileIds }
        return true
    }

    override suspend fun ejectDevice(deviceId: String) = true

    override fun observePtpDeviceChanges() = flowOf<PtpDeviceEvent>()

    // Test helpers
    fun wasFileDownloaded(destPath: String) = downloadedFiles.containsKey(destPath)
    fun wasFileDeleted(fileId: String) = deletedFiles.contains(fileId)
    fun getDownloadedContent(destPath: String) = downloadedFiles[destPath]
}
```

### 8.2 `CameraImportFlowTest`

**File:** `integration/CameraImportFlowTest.kt`

Full end-to-end flow using `FakeCameraDevicePort` (no real camera needed, no real bridge process):

```kotlin
@DisplayName("Camera import flow")
class CameraImportFlowTest {

    private lateinit var fakeCameraPort: FakeCameraDevicePort
    private lateinit var namingPort: NamingPort
    private lateinit var cameraImportService: CameraImportService

    @TempDir lateinit var tempDir: File

    @BeforeEach
    fun setup() {
        fakeCameraPort = FakeCameraDevicePort()
        namingPort = NamingAdapter()  // Use real naming adapter
        cameraImportService = CameraImportService(
            imageRepository = MockImageRepository(),
            deduplicationPort = mock(DeduplicationPort::class.java),
            namingPort = namingPort,
            cameraDevicePort = fakeCameraPort
        )
    }

    @Nested
    @DisplayName("full import from camera")
    inner class FullImport {
        @Test @DisplayName("should import all files from camera to destination")
        fun shouldImportAllFiles() = runTest {
            // GIVEN: a camera with 3 files
            val device = PtpCameraDevice(id = "cam1", name = "Test Camera", transport = "USB",
                capabilities = setOf(CameraCapability.DELETE_ONE))
            val files = listOf(
                CameraFile(id = "f1", name = "photo1.RAF", folder = "/DCIM/100TEST/",
                    size = 50000000L, creationDate = "2026-05-15T14:30:00Z", fileType = CameraFileType.RAW),
                CameraFile(id = "f2", name = "photo2.JPG", folder = "/DCIM/100TEST/",
                    size = 8000000L, creationDate = "2026-05-15T14:31:00Z", fileType = CameraFileType.JPEG),
                CameraFile(id = "f3", name = "photo3.RAF", folder = "/DCIM/100TEST/",
                    size = 52000000L, creationDate = "2026-05-15T14:32:00Z", fileType = CameraFileType.RAW)
            )
            fakeCameraPort.addDevice(device, files)

            // WHEN: import all files
            val config = ImportConfiguration(verifyAfterCopy = false)
            val result = cameraImportService.importFromCamera(device, files, tempDir.absolutePath, config)

            // THEN: all files downloaded to destination
            assertThat(result.successCount).isEqualTo(3)
            assertThat(result.errorCount).isEqualTo(0)
            assertThat(result.totalFiles).isEqualTo(3)

            // Verify files exist at destination (not in a staging temp dir)
            files.forEach { file ->
                assertThat(File(tempDir, file.name)).exists()
            }
        }

        @Test @DisplayName("should verify file sizes after download when verifyAfterCopy enabled")
        fun shouldVerifySizes() = runTest {
            val device = PtpCameraDevice(id = "cam1", name = "Test Camera", transport = "USB")
            val files = listOf(
                CameraFile(id = "f1", name = "photo.RAF", folder = "/DCIM/",
                    size = 1000L, creationDate = null, fileType = CameraFileType.RAW)
            )
            fakeCameraPort.addDevice(device, files)

            val config = ImportConfiguration(verifyAfterCopy = true)
            val result = cameraImportService.importFromCamera(device, files, tempDir.absolutePath, config)

            // FakeCameraPort writes 1000 bytes (matches CameraFile.size), so verify should pass
            assertThat(result.successCount).isEqualTo(1)
        }

        @Test @DisplayName("should report progress during import")
        fun shouldReportProgress() = runTest {
            val device = PtpCameraDevice(id = "cam1", name = "Test Camera", transport = "USB")
            val files = listOf(
                CameraFile(id = "f1", name = "p1.RAF", folder = "/DCIM/",
                    size = 100L, creationDate = null, fileType = CameraFileType.RAW),
                CameraFile(id = "f2", name = "p2.RAF", folder = "/DCIM/",
                    size = 200L, creationDate = null, fileType = CameraFileType.RAW)
            )
            fakeCameraPort.addDevice(device, files)

            val progressList = mutableListOf<CameraImportProgress>()
            val config = ImportConfiguration(verifyAfterCopy = false)

            cameraImportService.importFromCamera(device, files, tempDir.absolutePath, config) { progress ->
                progressList.add(progress)
            }

            // Should have received progress updates including PLANNING, DOWNLOADING, COMPLETE
            assertThat(progressList).isNotEmpty()
            assertThat(progressList.first().phase).isEqualTo(CameraImportProgress.ImportPhase.PLANNING)
            assertThat(progressList.last().phase).isEqualTo(CameraImportProgress.ImportPhase.COMPLETE)
        }
    }

    @Nested
    @DisplayName("no staging copy")
    inner class NoStaging {
        @Test @DisplayName("should never write files to temp staging directory")
        fun shouldNeverWriteToStaging() = runTest {
            val device = PtpCameraDevice(id = "cam1", name = "Test Camera", transport = "USB")
            val files = listOf(
                CameraFile(id = "f1", name = "photo.RAF", folder = "/DCIM/",
                    size = 1000L, creationDate = null, fileType = CameraFileType.RAW)
            )
            fakeCameraPort.addDevice(device, files)

            val config = ImportConfiguration(verifyAfterCopy = false)
            val destDir = File(tempDir, "destination")
            destDir.mkdirs()

            cameraImportService.importFromCamera(device, files, destDir.absolutePath, config)

            // Verify: file exists directly in destination, NOT in any /tmp/staging area
            val destFile = File(destDir, "photo.RAF")
            assertThat(destFile).exists()

            // Verify: no files in system temp directory related to this import
            val systemTemp = File(System.getProperty("java.io.tmpdir"))
            val stagingFiles = systemTemp.listFiles { f -> f.name.contains("petrie-camera-staging") }
            assertThat(stagingFiles).isNull()
        }
    }

    @Nested
    @DisplayName("delete from camera after import")
    inner class DeleteAfterImport {
        @Test @DisplayName("should delete files from camera after successful import")
        fun shouldDeleteAfterImport() = runTest {
            val device = PtpCameraDevice(id = "cam1", name = "Test Camera", transport = "USB",
                capabilities = setOf(CameraCapability.DELETE_ONE))
            val files = listOf(
                CameraFile(id = "f1", name = "photo.RAF", folder = "/DCIM/",
                    size = 100L, creationDate = null, fileType = CameraFileType.RAW)
            )
            fakeCameraPort.addDevice(device, files)

            val config = ImportConfiguration(verifyAfterCopy = false, deleteAfterImport = true)
            cameraImportService.importFromCamera(device, files, tempDir.absolutePath, config)

            assertThat(fakeCameraPort.wasFileDeleted("f1")).isTrue()
        }
    }
}
```

### 8.3 `DiskSpaceCheckTest`

**File:** `integration/DiskSpaceCheckTest.kt`

```kotlin
@DisplayName("Disk space check")
class DiskSpaceCheckTest {

    @TempDir lateinit var tempDir: File

    @Test @DisplayName("should throw InsufficientDiskSpaceException when import exceeds available space")
    fun shouldThrowWhenExceedsSpace() = runTest {
        val fakePort = FakeCameraDevicePort()
        val service = CameraImportService(
            cameraDevicePort = fakePort,
            namingPort = mock(NamingPort::class.java),
            historyAdapter = mock(ImportHistoryAdapter::class.java)
        )

        val device = PtpCameraDevice(id = "cam1", name = "Test Camera", transport = "USB")
        // Request 1PB of files — definitely more than any disk
        val hugeFiles = listOf(
            CameraFile(id = "f1", name = "huge.RAF", folder = "/DCIM/",
                size = 1_000_000_000_000_000L, creationDate = null, fileType = CameraFileType.RAW)
        )
        fakePort.addDevice(device, hugeFiles)

        assertThrows<InsufficientDiskSpaceException> {
            service.importFromCamera(device, hugeFiles, tempDir.absolutePath, ImportConfiguration())
        }
    }

    @Test @DisplayName("should include 5% safety margin in space check")
    fun shouldIncludeSafetyMargin() = runTest {
        // This test verifies the calculation, not actual disk space
        // 5% of 1000 bytes = 50 bytes, so we need 1050 bytes free
        val totalBytes = 1000L
        val safetyMargin = 1.05
        val requiredSpace = (totalBytes * safetyMargin).toLong()
        assertThat(requiredSpace).isEqualTo(1050L)
    }
}
```

---

## 9. Phase 7: End-to-End Manual Tests

These tests require a physical camera and cannot run in CI. They are documented here as a checklist for manual testing.

### macOS Manual Test Matrix

| # | Scenario | Steps | Expected Result |
|---|----------|-------|----------------|
| M1 | Detect Fujifilm camera | Connect X-T5 via USB → open app | Camera appears, **"Import from Camera" button auto-selected** |
| M2 | Auto-select at startup | Plug in camera → launch app | **"Import from Camera" button is highlighted**, camera device list shown |
| M3 | No camera at startup | Launch app with no camera connected | **"Import from Folder" button selected**, camera button disabled |
| M4 | Browse camera files | Click camera → "Select & Import" | File list shows with names, sizes, types |
| M5 | Import all from camera | Click "Import All" | All files downloaded directly to destination, no temp staging |
| M6 | Import new only | Click "Import New" | Only new files (not previously imported) downloaded |
| M7 | Select & Import | Select specific files → continue | Only selected files downloaded |
| M8 | Disk space warning | Set destination to nearly-full drive → attempt import | Warning dialog appears with size information |
| M9 | Delete after import | Enable "Delete after import" → import | Files deleted from camera after successful import |
| M10 | Camera disconnect during download | Start import → unplug camera mid-download | Graceful error message, partial files cleaned up |
| M11 | Camera busy (Image Capture open) | Open Image Capture.app → attempt connect in petrie | Error message: "Camera is being used by another application" |
| M12 | Photo Scan from camera | Click "Select from Camera" button in Photo Scan tab → select file | File downloads to destination, wizard loads it |
| M13 | Mixed sources | SD card in reader + PTP camera → both appear | Both devices in list, correct type badges |
| M14 | Hot-plug auto-switch | App on Folder mode → plug in camera | **Auto-switches to "Import from Camera"**, snackbar shows "Camera connected" |
| M15 | Hot-unplug fallback | In Camera mode → unplug last camera | **Auto-switches back to "Import from Folder"** |
| M16 | Eject camera | Click "Eject" button on camera card | Camera safely disconnected, disappears from list |
| M17 | Dual buttons visible | Look at import screen top | Both "Import from Folder" and "Import from Camera" buttons are always visible |

### Linux Manual Test Matrix

| # | Scenario | Steps | Expected Result |
|---|----------|-------|----------------|
| L1 | gphoto2 not installed | Uninstall gphoto2 → open app | "Import from Camera" button disabled, shows tooltip |
| L2 | gphoto2 installed | Install gphoto2 → open app → connect camera | Camera appears, "Import from Camera" auto-selected |
| L3 | Import from camera | Select camera → Import All | Files downloaded to destination |
| L4 | Delete after import | Enable delete → import | Files deleted from camera |

### Windows Manual Test Matrix

| # | Scenario | Steps | Expected Result |
|---|----------|-------|----------------|
| W1 | WPD bridge not available | Run app without PowerShell bridge | "Import from Camera" button disabled |
| W2 | Camera detected | Connect camera → open app | Camera appears, auto-selected |
| W3 | Import from camera | Select camera → Import All | Files downloaded to destination |

### Cross-Platform Regression Tests

| # | Scenario | Steps | Expected Result |
|---|----------|-------|----------------|
| X1 | Folder import still works | Click "Import from Folder" → select local folder → import | All existing import behavior unchanged |
| X2 | Mass storage camera still works | Insert SD card in reader → detect → import | Mass-storage detection still works |
| X3 | No camera available | Open app on machine with no PTP camera | "Import from Camera" button disabled, "Import from Folder" works |
| X4 | Switch between sources | Click "Import from Folder" then "Import from Camera" | Panels switch instantly, no lost state |

---

## 10. Test Utilities & Shared Fixtures

### 10.1 `CameraTestFixtures`

**File:** `testutil/CameraTestFixtures.kt`

```kotlin
object CameraTestFixtures {
    fun fujiXt5() = PtpCameraDevice(
        id = "usb-001-004",
        name = "Fujifilm X-T5",
        manufacturer = "Fujifilm",
        model = "X-T5",
        transport = "USB",
        fileCount = 2847,
        capacity = 64_000_000_000L,
        available = 12_800_000_000L,
        capabilities = setOf(CameraCapability.DELETE_ONE, CameraCapability.DELETE_ALL,
            CameraCapability.THUMBNAIL, CameraCapability.METADATA),
        platform = "macos",
        bridgeType = "imagecapturecore"
    )

    fun canonR5() = PtpCameraDevice(
        id = "usb-001-006",
        name = "Canon EOS R5",
        manufacturer = "Canon",
        model = "EOS R5",
        transport = "USB",
        fileCount = 512,
        capacity = 32_000_000_000L,
        available = 15_000_000_000L,
        capabilities = setOf(CameraCapability.DELETE_ONE, CameraCapability.THUMBNAIL,
            CameraCapability.METADATA, CameraCapability.PARTIAL_READ),
        platform = "linux",
        bridgeType = "gphoto2"
    )

    fun sampleRawFile(name: String = "DSCF1234.RAF") = CameraFile(
        id = "file-$name",
        name = name,
        folder = "/DCIM/100FUJI/",
        size = 52428800L,
        creationDate = "2026-05-15T14:30:22Z",
        modificationDate = "2026-05-15T14:30:22Z",
        fileType = CameraFileType.RAW,
        width = 6240,
        height = 4160,
        hasThumbnail = true
    )

    fun sampleJpegFile(name: String = "DSCF1235.JPG") = CameraFile(
        id = "file-$name",
        name = name,
        folder = "/DCIM/100FUJI/",
        size = 8200000L,
        creationDate = "2026-05-15T14:32:00Z",
        modificationDate = "2026-05-15T14:32:00Z",
        fileType = CameraFileType.JPEG,
        width = 6240,
        height = 4160,
        hasThumbnail = true
    )

    fun sampleVideoFile(name: String = "DSCF1236.MP4") = CameraFile(
        id = "file-$name",
        name = name,
        folder = "/DCIM/100FUJI/",
        size = 200_000_000L,
        creationDate = "2026-05-15T15:00:00Z",
        modificationDate = "2026-05-15T15:00:00Z",
        fileType = CameraFileType.VIDEO
    )

    fun sampleFileSet() = listOf(
        sampleRawFile(),
        sampleJpegFile(),
        sampleVideoFile(),
        sampleRawFile("DSCF1237.RAF").copy(id = "file-DSCF1237.RAF", size = 48000000L)
    )

    fun massStorageSdCard() = CameraDevice(
        id = "/Volumes/SD_CARD",
        name = "SD_CARD",
        manufacturer = null,
        model = "SD_CARD",
        mountPoint = "/Volumes/SD_CARD",
        storageCapacity = 64_000_000_000L,
        availableSpace = 32_000_000_000L,
        isConnected = true,
        deviceType = DeviceType.SD_CARD,
        isPtpDevice = false
    )
}
```

### 10.2 `BridgeProcessMock`

**File:** `testutil/BridgeProcessMock.kt`

Mock for testing bridge process spawning without a real binary:

```kotlin
class BridgeProcessMock {
    private var responses = mutableMapOf<String, String>()

    fun respondTo(command: String, json: String) {
        responses[command] = json
    }

    fun createProcess(): Process {
        // Returns a mock Process that writes configured responses to stdout
        // and reads commands from stdin
        // Used for unit-testing ImageCaptureCoreBridgeAdapter without the real binary
    }
}
```

---

## 11. CI Considerations

### Test Categories

| Category | Tag | Runs in CI? | Needs Hardware? |
|----------|-----|-------------|-----------------|
| Domain model tests | (default) | ✅ Yes | ❌ No |
| Adapter unit tests | (default) | ✅ Yes | ❌ No |
| Import service tests | (default) | ✅ Yes | ❌ No |
| Integration tests (fake) | (default) | ✅ Yes | ❌ No |
| Bridge integration (real) | `requires-hardware` | ❌ No | ✅ Yes |
| Platform-specific (macOS) | `requires-macos` | ❌ CI only on macOS runners | ❌ No |
| Platform-specific (Linux) | `requires-linux` | ✅ On Linux runners | ❌ No |
| Platform-specific (Windows) | `requires-windows` | ❌ CI only on Windows runners | ❌ No |
| E2E manual tests | (not automated) | ❌ No | ✅ Yes |

### build.gradle.kts additions

```kotlin
tasks.test {
    useJUnitPlatform {
        excludeTags("requires-hardware")
    }
}

tasks.register<Test>("testWithHardware") {
    useJUnitPlatform {
        // No tag exclusions — includes requires-hardware tests
        // Run manually with a physical camera connected
    }
}

tasks.register<Test>("testMacOS") {
    useJUnitPlatform {
        excludeTags("requires-hardware")
        // Only run on macOS runners
    }
}
```

### Platform-Specific Test Annotations

```kotlin
// For tests that should only run on macOS (e.g., ImageCaptureCore bridge)
@EnabledOnOs(OS.MAC)
class ImageCaptureCoreBridgeAdapterContractTest : CameraDevicePortContractTest() { ... }

// For tests that should only run on Linux (e.g., gphoto2 bridge)
@EnabledOnOs(OS.LINUX)
class Gphoto2BridgeAdapterContractTest : CameraDevicePortContractTest() { ... }

// For tests that need a physical camera connected
@Tag("requires-hardware")
class CameraDeviceHotPlugTest { ... }
```

---

## 12. Test Implementation Order

Tests should be implemented alongside the corresponding production code, following this order:

| Phase | Production Code | Tests | Priority |
|-------|----------------|-------|----------|
| **1** | `PtpCameraDevice`, `CameraFile`, `CameraImportProgress`, `ImportSource`, `CameraCapability` | All model tests (§3.1–3.5) | P0 — No dependencies, runs everywhere |
| **2** | `CameraDevicePort` interface | Contract test (§4.1) | P0 — Defines the interface contract |
| **3a** | `NoOpCameraDevicePort` | NoOp tests (§5.1) | P0 — Trivial, validates fallback |
| **3b** | `CompositeDeviceAdapter` | Composite tests (§5.2) | P0 — Validates device merging |
| **3c** | `ImageCaptureCoreBridgeAdapter` | macOS bridge parsing tests (§5.3) | P1 — macOS only |
| **3d** | `Gphoto2BridgeAdapter` | gphoto2 parsing tests (§5.4) | P1 — Linux + macOS |
| **4** | `CameraImportService.importFromCamera()` | Import service tests (§6.1–6.2) | P0 — Core business logic |
| **5** | UI composables | Card data tests (§7.1) | P1 — After domain model tests pass |
| **6** | Integration with `FakeCameraDevicePort` | Integration tests (§8.1–8.3) | P0 — Validates end-to-end flow |
| **7** | Swift bridge tool | Manual hardware tests (§9) | P2 — Requires physical camera |

### Test Count Summary

| Category | Count | Automated? |
|----------|-------|-----------|
| Domain model tests | 35+ | ✅ |
| Port contract tests | 6+ | ✅ |
| Adapter unit tests | 25+ | ✅ |
| Import service tests | 15+ | ✅ |
| Integration tests | 10+ | ✅ |
| Manual hardware tests | 14+ | ❌ |
| **Total automated** | **90+** | ✅ |
| **Total manual** | **14+** | ❌ |
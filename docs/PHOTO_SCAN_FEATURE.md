# Photo Scan Feature Guide

## Overview

The **Photo Scan** feature allows you to import physical photographs that have been photographed on a solid background. The feature detects individual photographs in scanned images, allows corner adjustment, metadata editing, and exports individual photos with perspective correction.

## How It Works

```
┌─────────────────────────────────────────────────────────────────┐
│                      Photo Scan Workflow                         │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  1. DETECTING     →  2. CORNER_EDITING  →  3. METADATA_EDITING │
│  ┌─────────────┐      ┌─────────────┐      ┌─────────────┐    │
│  │ Scan image  │      │ Adjust box  │      │ Set date,   │    │
│  │ for photos  │ ──▶  │ corners     │ ──▶  │ tags, notes │    │
│  └─────────────┘      └─────────────┘      └─────────────┘    │
│                                                                    │
│  4. EXPORTING     →  5. COMPLETE                                   │
│  ┌─────────────┐      ┌─────────────┐                            │
│  │ Save photos  │ ──▶  │ Review      │                            │
│  │ to folder    │      │ summary     │                            │
│  └─────────────┘      └─────────────┘                            │
│                                                                    │
└─────────────────────────────────────────────────────────────────┘
```

## Features

### Automatic Detection
- Detects rectangular regions in scanned images
- Uses edge detection to find photo boundaries
- Filters by area and aspect ratio
- Removes overlapping detections

### Corner Editing
- Visual preview with draggable corner handles
- Tap to select photos
- Drag corners to adjust boundaries
- Add/remove photo bounding boxes

### Metadata Override
- **Date**: Year, month, and day override
- **Tags**: Custom tags for organization
- **Notes**: Free-form notes for each photo
- Apply metadata to all photos in a scan

### Perspective Correction
- Corrects trapezoidal distortion from angled photos
- Uses bilinear interpolation for high-quality output
- Produces rectangular output images

### Export
- JPEG output with configurable quality
- Duplicate filename handling with incrementing (`photo_1.jpg`, `photo_2.jpg`)
- Batch export of multiple photos

## Architecture

### Domain Models

```
PhotoScanState
├── images: List<ScannedImage>
│   ├── id: String
│   ├── file: File
│   ├── image: BufferedImage?
│   └── detectedPhotos: List<DetectedPhoto>
│       ├── id: String
│       ├── topLeft, topRight, bottomLeft, bottomRight: PhotoCorner
│       └── configuration: PhotoScanConfiguration
│
├── step: Step (DETECTING, CORNER_EDITING, METADATA_EDITING, EXPORTING, COMPLETE)
├── currentIndex: Int
├── selectedPhotoId: String?
├── selectedCorner: CornerType? (TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT)
└── exportProgress: ExportProgress
```

### Key Services

| Service | Responsibility |
|---------|----------------|
| `PhotoScanDetectorService` | Edge detection to find photo boundaries |
| `PerspectiveCorrectionService` | Bilinear interpolation for perspective transform |
| `PhotoScanExportService` | JPEG writing with metadata handling |
| `PhotoScanState` | Workflow state management with StateFlow |

### UI Screens

| Screen | Purpose |
|--------|---------|
| `PhotoScanScreen.kt` | Main orchestration and workflow |
| `PhotoScanPreviewScreen.kt` | Canvas with corner drag handles |
| `PhotoScanMetadataScreen.kt` | Metadata editing form |

## Usage

### From Import Screen

1. Select **Import Photos** → **Import Scans** toggle
2. Drag and drop or browse to scan image files
3. Review detected photos in each image
4. Adjust corners as needed
5. Edit metadata (date, tags, notes)
6. Export to destination folder

### Programmatic Usage

```kotlin
// 1. Initialize state with images
val state = PhotoScanState()
state.initialize(listOf(File("scan1.jpg"), File("scan2.jpg")))

// 2. Detect photos in current image
val detector = PhotoScanDetectorService()
val image = ImageIO.read(currentImage.file)
val photos = detector.detectPhotos(image)
state.setCurrentImageDetected(image, photos)

// 3. Adjust corners
state.updatePhotoCorner(photoId, CornerType.TOP_LEFT, 100f, 50f)

// 4. Update metadata
val config = PhotoScanConfiguration(dateYear = 2024, dateMonth = 3)
state.updatePhotoConfiguration(photoId, config)

// 5. Export
val exporter = PhotoScanExportService(perspectiveService)
val result = exporter.exportPhotos(
    sourceFile,
    image,
    detectedPhotos,
    destPath,
    baseFileName
)
```

## Configuration

### Detection Parameters

```kotlin
PhotoScanDetectorService(
    minArea = 10000,           // Minimum pixel area for detection
    edgeThreshold = 100,       // Sobel edge detection threshold
    minAspectRatio = 0.3,      // Minimum width/height ratio
    maxAspectRatio = 3.0       // Maximum width/height ratio
)
```

### Export Parameters

```kotlin
val service = PhotoScanExportService(perspectiveService)
service.jpegQuality = 0.95f  // JPEG quality (0.0 - 1.0)
```

### Metadata Configuration

```kotlin
PhotoScanConfiguration(
    dateYear: Int? = null,      // Override year
    dateMonth: Int? = null,     // Override month (1-12)
    dateDay: Int? = null,       // Override day (1-31)
    tags: List<String> = emptyList(),
    notes: String? = null
)
```

## Testing

Run photo scan tests:
```bash
./gradlew test --tests "*PhotoScan*"
```

Run service tests:
```bash
./gradlew test --tests "*PhotoScanDetectorServiceTest*"
./gradlew test --tests "*PerspectiveCorrectionServiceTest*"
./gradlew test --tests "*PhotoScanExportServiceTest*"
./gradlew test --tests "*PhotoScanStateTest*"
```

## File Structure

```
src/main/kotlin/org/kryspetrie/fileimport/
├── application/
│   ├── PhotoScanDetectorService.kt      # Edge detection for photos
│   ├── PerspectiveCorrectionService.kt  # Perspective transform
│   └── PhotoScanExportService.kt        # Export with metadata
├── domain/model/
│   ├── PhotoScanModels.kt               # DetectedPhoto, PhotoCorner, PhotoBounds
│   ├── PhotoScanConfiguration.kt         # Metadata config
│   └── PhotoScanState.kt                # Workflow state
└── ui/screens/
    ├── PhotoScanScreen.kt               # Main orchestration
    ├── PhotoScanPreviewScreen.kt        # Corner editing canvas
    └── PhotoScanMetadataScreen.kt       # Metadata form
```

## Tips for Best Results

1. **Solid Background**: Works best when photos are photographed against a solid color (e.g., white desk, black velvet)

2. **Good Lighting**: Avoid shadows that create edge artifacts

3. **Clear Separation**: Ensure photos don't overlap in the scan

4. **Corner Adjustment**: Always verify detected corners are accurate before exporting

5. **Test Images**: Use high-resolution scans for better detection accuracy

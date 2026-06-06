# Source Face Region Import Plan

## Goal
Read face region data (MWG-RS) from the original source image's XMP metadata and transform those coordinates to match each cropped output photo, accounting for perspective correction (warp), crop, and rotation.

## Coordinate Transformation Pipeline

A face region in the source image has normalized coordinates (0-1):
- `x, y` = center of face region (as fraction of source width/height)
- `w, h` = size of face region (as fraction of source width/height)

### Step-by-step transformation for each detected photo:

1. **Source-normalized → Source-pixel**: Multiply by source image dimensions
2. **Test containment**: Check if the face center falls within the detected photo's bounding box (with some tolerance for faces that cross boundaries)
3. **Source-pixel → Cropped-pixel via forward homography**: Apply the forward perspective transform (inverse of the backward mapping used for image warp)
4. **Cropped-pixel → Output-pixel via rotation**: Apply 90°/180°/270° rotation
5. **Output-pixel → Output-normalized**: Divide by output image dimensions

## Implementation

### 1. `FaceRegionTransformer` (new, in application layer)
- Parses XMP from source image to extract MWG-RS face regions
- Transforms coordinates through the full pipeline
- Called from `PhotoScanExportService` during export

### 2. XMP Face Region Reader
- Uses `Imaging.getXmpXml()` to read XMP from source file
- Parses MWG-RS Region `rdf:Description` elements
- Extracts name, type, x, y, w, h from each region

### 3. Coordinate Transformation
- Uses BoofCV homography (forward mapping: src→dst)
- Applies rotation transform to coordinates
- Handles both perspective correction and simple crop cases

### 4. Integration Points
- `PhotoScanExportService.writeImageWithMetadata()` — read source face regions and transform them
- `PhotoScanWizardState` — store source face regions for UI display
- UI shows inherited face regions with a visual indicator (future)

## Test Plan
- Unit test: XMP parsing of MWG-RS face regions
- Unit test: Coordinate transformation (perspective, crop, rotation)
- Unit test: Containment check
- Integration test: Source XMP → transformed face regions in exported output
- Edge case: Face region at boundary of crop
- Edge case: No perspective correction (simple crop)
- Edge case: 90°/180°/270° rotation
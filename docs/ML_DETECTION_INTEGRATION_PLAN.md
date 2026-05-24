# ML Detection Integration Plan

> **Status:** Planning  
> **Created:** 2026-05-08  
> **Updated:** 2026-05-17  
> **Scope:** petrie-file-importer — ONNX-based photo detection with CV refinement, settings system, training data export  
> **Related:** photo-pose-detector (Python CLI prototype)  
> **Supersedes:** DETECTION_MODES_PLAN.md (this plan absorbs and extends it)

---

## Table of Contents

1. [Executive Summary](#1-executive-summary)
2. [Architecture Overview](#2-architecture-overview)
3. [Detection Settings — Full Taxonomy](#3-detection-settings--full-taxonomy)
4. [Ports & Adapters Design](#4-ports--adapters-design)
5. [Pipeline Stages & Reusable Components](#5-pipeline-stages--reusable-components)
6. [UI Layout & Interaction Design](#6-ui-layout--interaction-design)
7. [Training Data Export Mode](#7-training-data-export-mode)
8. [Live Settings Propagation](#8-live-settings-propagation)
9. [Implementation Phases](#9-implementation-phases)
10. [Validation Strategy](#10-validation-strategy)
11. [Integration Points](#11-integration-points)
12. [Risks & Mitigations](#12-risks--mitigations)
13. [Appendix A: Python↔Kotlin Parameter Mapping](#appendix-a-mapping-between-python-and-kotlin-parameters)
14. [Appendix B: YOLO Label Format Specification](#appendix-b-yolo-label-format-specification)
15. [Appendix C: Directory Structure After Implementation](#appendix-c-directory-structure-after-implementation)
16. [Appendix D: Visibility Threshold Hierarchy](#appendix-d-visibility-threshold-hierarchy)
17. [Appendix E: photocrop.py Pipeline Reference](#appendix-e-photocroppy-pipeline-reference)

---

## 1. Executive Summary

### Goal

Bring the photo detection quality of the Python **photo-pose-detector** (photocrop) into the Kotlin **petrie-file-importer** desktop application, while preserving the existing CV pipeline as a fallback and adding user-controllable settings for every stage of the detection pipeline.

### Key Capabilities

1. **ML detection at import** — User selects detection mode and tunes settings on the Photo Scan wizard's import step before starting the scan.
2. **Per-photo settings & re-detection** — On the detection/refinement screens, user can tweak settings for the current photo and rerun detection.
3. **Settings propagation to batch** — User can apply their tweaked settings to all remaining unprocessed images in the batch, restarting preprocessing with the new parameters.
4. **Training data export** — A "Save Training Data" mode that exports scaled-down images + corner coordinates in YOLO pose format for future model training (done in Python).
5. **CV refinement as post-processing** — After ML detection, optional CV refinement (Sobel edge search, line intersection) can snap corners to precise edges.
6. **Fiducial refinement** — Optional iterative ONNX model that refines individual corners at higher resolution.
7. **Fidelity to photocrop.py** — Every pipeline stage, parameter, and code path from `photocrop.py` is represented. Settings map 1:1 to `photocrop.py` CLI flags.

### Design Principles

- **Python trains, Kotlin consumes** — All model training happens in photo-pose-detector. The Kotlin app only does inference and training data collection.
- **Graceful degradation** — If ONNX models aren't available, fall back to classical CV. If a model fails on an image, fall back to CV.
- **Settings are first-class** — Every pipeline parameter is user-visible and tunable, organized by pipeline stage, matching photocrop.py's CLI flags.
- **Pipeline is composable** — Each detection step (detect → pose → refine → dedup) is a separate component that can be enabled/disabled and configured independently.
- **Visibility tracked end-to-end** — Corner visibility from the ONNX pose model is preserved through CV and fiducial refinement, affecting dedup, export, and adaptive margins.

---

## 2. Architecture Overview

### Pipeline Flow

```
┌─────────────────────────────────────────────────────────────────────────┐
│              Photo Scan Tab → Wizard Import Step                        │
│  ┌─────────────────────────────────────────────────────────────────┐    │
│  │ Detection Settings (inside Custom Settings card)                │    │
│  │  Preset: [Quick ▼] [Crop] [Warp] [Best] [CV Only]             │    │
│  │  Mode:  [Hybrid ▼]                                              │    │
│  │  ...per-stage settings...                                        │    │
│  └─────────────────────────────────────────────────────────────────┘    │
│  [Import Photo Scan(s)]                                                │
└─────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                    Detection Orchestrator                                │
│                                                                         │
│  DetectionSettings ──┐                                                  │
│                      ▼                                                  │
│  ┌──────────┐   ┌──────────┐   ┌──────────┐   ┌──────────┐   ┌──────┐ │
│  │  Stage 1  │──▶│  Stage 2  │──▶│ Stage 2b  │──▶│ Stage 3  │──▶│S3.5 │ │
│  │ Detection │   │   Pose    │   │  Refine   │   │CV Refine │   │Fiduc│ │
│  │(or CV)    │   │(or skip)  │   │(or skip)  │   │(or skip) │   │(opt)│ │
│  └──────────┘   └──────────┘   └──────────┘   └──────────┘   └──────┘ │
│        │              │              │              │              │     │
│        ▼              ▼              ▼              ▼              ▼     │
│  ┌──────────────────────────────────────────────────────────────┐      │
│  │  Stage 4: Deduplication (always runs)                        │      │
│  │  Sort by priority → greedy remove by center distance          │      │
│  └──────────────────────────────────────────────────────────────┘      │
│                                                                         │
│  Stages 1-3 & 3.5: ONNX inference (if model available + mode enabled)  │
│  Stage 3 (CV): Classical CV (always available)                          │
│  Stage 4: Always runs                                                   │
└─────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────────┐
│              Overview / Refinement / Summary Screens                    │
│                                                                         │
│  Per-photo: [⚙ Re-detect] → edit settings → re-detect                 │
│  Batch:     [⚙ Apply to Remaining] → propagate settings to queue      │
│  Training:  [💾 Save Training Data] → export scaled image + corners    │
│                                                                         │
│  ┌─ Export Pipeline ────────────────────────────────────────────────┐  │
│  │  Crop mode: [Simple | Simple-Corners | Warp | Warp-Stretch]     │  │
│  │  Adaptive margin: ☑ Visibility-aware margin expansion            │  │
│  │  Border fill: [Grey | White | Custom]                           │  │
│  │  Transparent: ☐ Save as PNG with alpha mask                     │  │
│  └──────────────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────────────┘
```

### Component Architecture

```
┌──────────────────────────────────────────────────────────────────────────┐
│                              UI Layer                                    │
│  PhotoScanImportScreen │ OverviewScreen │ RefinementScreen │ SummaryScreen│
│  (detection settings   │ (box overview │ (corner edit +   │ (export +    │
│   inside Custom Card)  │  + re-detect) │   CV/fiducial    │  training)    │
└──────────────────────────┬───────────────────────────────────────────────┘
                           │
                           ▼
┌──────────────────────────────────────────────────────────────────────────┐
│                        Wizard State Layer                                │
│  PhotoScanWizardState  ←  detectionSettings: DetectionSettings          │
│  PhotoConfiguration    ←  perPhotoSettings: Map<String, DetectionSettings>│
│  PreProcessedImage     ←  settings: DetectionSettings (snapshot)         │
└──────────────────────────┬───────────────────────────────────────────────┘
                           │
                           ▼
┌──────────────────────────────────────────────────────────────────────────┐
│                      Application Layer                                   │
│  DetectionOrchestrator ──→ coordinates all pipeline stages               │
│  PhotoScanExportService ──→ export (simple/warp/fallback) + training     │
│  TrainingDataExportService ──→ scaled images + YOLO labels                │
└──────────────────────────┬───────────────────────────────────────────────┘
                           │
                           ▼
┌──────────────────────────────────────────────────────────────────────────┐
│                     Infrastructure Layer                                 │
│                                                                          │
│  ┌─────────────────────────┐  ┌──────────────────────────────────────┐ │
│  │   Detection Ports        │  │   Detection Adapters                  │ │
│  │                          │  │                                      │ │
│  │  PhotoScanDetectorPort  │──│── CvDetectorAdapter                   │ │
│  │  OnnxDetectionPort      │──│── OnnxDetectionAdapter                │ │
│  │  OnnxPosePort           │──│── OnnxPoseAdapter                     │ │
│  │  OnnxFiducialPort       │──│── OnnxFiducialAdapter                 │ │
│  │  CvRefinementPort       │──│── SobelEdgeRefinementAdapter          │ │
│  │  DetectionDedupStrategy │──│── DistanceDeduplicationAdapter         │ │
│  │  ModelResourcePort      │──│── ClasspathModelResourceAdapter        │ │
│  │  TrainingDataPort       │──│── FilesystemTrainingDataAdapter        │ │
│  └─────────────────────────┘  └──────────────────────────────────────┘ │
│                                                                          │
│  ┌─────────────────────────┐  ┌──────────────────────────────────────┐ │
│  │  Existing Adapters      │  │  Existing Domain                      │ │
│  │  RectangleDetector      │  │  DetectedPhoto, PhotoCorner,         │ │
│  │  HybridCornerDetector   │  │  DetectedPhotoWithVisibility,         │ │
│  │  PhotoScanDetectorSvc  │  │  PhotoScanConfiguration, PhotoScanProfile│
│  └─────────────────────────┘  └──────────────────────────────────────┘ │
└──────────────────────────────────────────────────────────────────────────┘
```

---

## 3. Detection Settings — Full Taxonomy

### Settings organized by pipeline stage

Settings are modeled as a `DetectionSettings` data class, serialized to JSON for persistence, and exposed in the UI as organized groups.

#### 3.1 Top-Level Mode Selection

```kotlin
enum class DetectionMode(
    val displayName: String,
    val description: String,
    val usesOnnx: Boolean,
    val providesCorners: Boolean
) {
    COMPUTER_VISION(
        "Computer Vision",
        "Edge detection + contour tracing (no ML required)",
        usesOnnx = false,
        providesCorners = true
    ),
    BOUNDING_BOX(
        "Detection Only",
        "YOLO detection model finds rectangular regions (axis-aligned boxes)",
        usesOnnx = true,
        providesCorners = false
    ),
    POSE(
        "Pose Detection",
        "YOLO pose model finds exact 4 corners for perspective warp",
        usesOnnx = true,
        providesCorners = true
    ),
    HYBRID(
        "Hybrid (Recommended)",
        "Detection model finds regions → pose model refines corners → CV refinement",
        usesOnnx = true,
        providesCorners = true
    );

    val requiresOnnx: Boolean get() = usesOnnx
}
```

#### 3.2 Crop/C export Mode Selection

```kotlin
enum class CropMode(
    val displayName: String,
    val description: String
) {
    SIMPLE(
        "Simple Crop",
        "Axis-aligned bounding box crop from detection model (no perspective)"
    ),
    SIMPLE_CORNERS(
        "Simple Crop (Corners)",
        "Axis-aligned bounding box derived from 4 detected corners (tighter)"
    ),
    WARP(
        "Perspective Warp (Inward)",
        "4-point perspective transform with inward dimension (may lose slivers)"
    ),
    WARP_STRETCH(
        "Perspective Warp (Outward)",
        "4-point perspective transform with outward dimension (preserves all content)"
    )
}
```

#### 3.3 Full Settings Data Class

Every setting below maps to a `photocrop.py` CLI flag (see Appendix A).

```kotlin
@Serializable
data class DetectionSettings(
    // ── Mode ──────────────────────────────────────────────────────
    val mode: DetectionMode = DetectionMode.HYBRID,
    val cropMode: CropMode = CropMode.WARP_STRETCH,

    // ── Stage 1: Detection Model ─────────────────────────────────
    val detectionConfidence: Float = 0.5f,       // --det-conf
    val detectionIou: Float = 0.45f,             // --iou
    val detectionImageSize: Int = 640,            // --imgsz

    // ── Stage 2: Pose Model ──────────────────────────────────────
    val poseConfidence: Float = 0.5f,             // --pose-conf
    val poseIou: Float = 0.45f,                   // (uses --iou for NMS)
    val poseCropExpand: Float = 0.15f,            // --pose-crop-expand
    val poseRefineEnabled: Boolean = false,        // --pose-refine
    val poseRefineExpand: Float = 0.05f,          // --pose-refine-expand

    // ── Stage 2b: Pose Sweep ──────────────────────────────────────
    val poseSweepEnabled: Boolean = false,          // --pose-sweep
    val poseSweepCropExpands: List<Float> = listOf(0.05f, 0.10f, 0.15f, 0.20f), // --sweep-crop-expands
    val poseSweepRefineExpands: List<Float> = listOf(0.03f, 0.05f, 0.10f, 0.15f), // --sweep-refine-expands
    val poseSweepXyEnabled: Boolean = false,        // --pose-sweep-xy
    val poseSweepXyExpands: List<Float> = listOf(0.05f, 0.10f, 0.15f, 0.20f, 0.25f), // --sweep-xy-expands
    val poseSweepXyEarlyStopVis: Float = 0.70f,     // internal: early stop visibility threshold

    // ── Center Bias ──────────────────────────────────────────────
    val centerBiasEnabled: Boolean = false,        // --center-bias
    // Allocates more crop expansion toward image center for edge-adjacent photos

    // ── Stage 3: CV Refinement ───────────────────────────────────
    val cvRefineEnabled: Boolean = true,           // --cv-refine
    val cvRefineRadius: Int = 40,                  // --cv-refine-radius
    val cvRefineOrientationAware: Boolean = true,  // filters edges by expected corner angle
    val cvRefineNeighborAnchored: Boolean = true,  // projects from high-vis neighbors
    val cvRefineStripSearch: Boolean = true,       // 1D perpendicular search when projection exists
    val cvRefineTwoPass: Boolean = true,           // 2nd pass uses corners refined in 1st pass
    val cvRefineVisThreshold: Float = 0.7f,        // visibility below this triggers refinement

    // ── Auto-Refine ──────────────────────────────────────────────
    val autoRefineEnabled: Boolean = false,         // --auto-refine
    // Only applies CV refinement to photos with <3 visible corners (vis >= 0.3)

    // ── Stage 3.5: Fiducial Refinement ────────────────────────────
    val fiducialEnabled: Boolean = false,            // --fiducial-model (set path to enable)
    val fiducialIterations: Int = 2,              // --fiducial-iterations
    val fiducialConfidence: Float = 0.5f,           // --fiducial-conf
    val fiducialModelPath: String = "",            // --fiducial-model (empty = disabled)

    // ── Stage 4: Classical CV (fallback mode) ────────────────────
    val cvMaxPhotos: Int = 4,                      // targetPhotoCount
    val cvGamma: Double = 1.4,                     // internal
    val cvAdaptiveBlockSize: Int = 31,             // internal
    val cvAdaptiveC: Int = 10,                     // internal
    val cvMorphKernelSize: Int = 5,                // internal
    val cvMinArea: Int = 2000,                     // internal
    val cvMaxAspectRatio: Float = 5.0f,            // internal
    val cvMinAngleDiff: Float = 60f,               // internal
    val cvMaxAngleDiff: Float = 120f,              // internal
    val cvWholeImageThreshold: Float = 0.80f,      // internal
    val cvMinQuadRatio: Float = 0.3f,              // internal
    val cvColorEdgeThreshold: Float = 35f,         // internal
    val cvColorEdgeMinRatio: Float = 0.30f,         // internal

    // ── Deduplication ───────────────────────────────────────────
    val dedupEnabled: Boolean = true,              // --dedup-dist (0 = disabled)
    val dedupDistanceThreshold: Float = 0.12f,      // --dedup-dist (fraction of min-dim)
    val dedupVisThreshForCenter: Float = 0.25f,     // internal: visibility threshold for center computation
    val dedupLowVisPenalty: Float = 0.5f,           // internal: multiply confidence by this when <3 vis corners

    // ── Crop Limits ────────────────────────────────────────────
    val cropLimitsEnabled: Boolean = true,          // internal: prevent expansion into adjacent detections
    val cropLimitsMaxIntrusion: Float = 0.15f,      // internal: max fraction of adjacent box dimension

    // ── Export / Crop Settings ───────────────────────────────────
    val cropMargin: Float = 0f,                    // --crop-margin (fraction of diagonal, 0=no margin)
    val borderFill: String = "grey",              // --border-fill: "grey", "white", or "R,G,B"/"#RRGGBB"
    val cropTransparent: Boolean = false,          // --crop-transparent (save as PNG with alpha)

    // ── Adaptive Margin ─────────────────────────────────────────
    val adaptiveMarginEnabled: Boolean = false,     // --adaptive-margin
    val adaptiveMarginThreshold: Float = 0.5f,     // --adaptive-margin-thresh
    val adaptiveMarginMax: Float = 0.03f,           // --adaptive-margin-max (fraction of diagonal)

    // ── Warp Fallback ──────────────────────────────────────────
    val warpFallbackThreshold: Float = 0.3f,       // --warp-fallback-thresh

    // ── Corrections ──────────────────────────────────────────
    val correctionMode: CorrectionMode = CorrectionMode.AUTO,  // AUTO / CROP / CROP_AND_ROTATE / PERSPECTIVE
    val rotationThreshold: Double = 1.5,           // degrees before CROP_AND_ROTATE
    val skewThreshold: Double = 3.0,              // degrees of corner deviation before PERSPECTIVE

    // ── Training Data Export ─────────────────────────────────────
    val trainingDataExportEnabled: Boolean = false, // Save training data alongside export
    val trainingDataScale: Float = 1.0f,           // Scale factor for training images

    // ── Preset ─────────────────────────────────────────────────
    val presetName: String = "default"
) {
    companion object {
        /** Factory presets matching photocrop.py's preset system */
        fun quick(): DetectionSettings = DetectionSettings(
            mode = DetectionMode.HYBRID,
            cropMode = CropMode.WARP_STRETCH,
            poseRefineEnabled = false,
            cvRefineEnabled = false,
            autoRefineEnabled = false,
            presetName = "quick"
        )

        fun crop(): DetectionSettings = DetectionSettings(
            mode = DetectionMode.HYBRID,
            cropMode = CropMode.SIMPLE_CORNERS,
            poseRefineEnabled = true,
            cvRefineEnabled = false,
            autoRefineEnabled = true,
            cropMargin = 0.02f,
            adaptiveMarginEnabled = true,
            presetName = "crop"
        )

        fun warp(): DetectionSettings = DetectionSettings(
            mode = DetectionMode.HYBRID,
            cropMode = CropMode.WARP_STRETCH,
            poseRefineEnabled = true,
            cvRefineEnabled = false,
            autoRefineEnabled = true,
            cropMargin = 0.02f,
            borderFill = "white",
            adaptiveMarginEnabled = true,
            presetName = "warp"
        )

        fun best(): DetectionSettings = DetectionSettings(
            mode = DetectionMode.HYBRID,
            cropMode = CropMode.WARP_STRETCH,
            poseSweepXyEnabled = true,
            cvRefineEnabled = true,
            autoRefineEnabled = true,
            cropMargin = 0.02f,
            borderFill = "white",
            adaptiveMarginEnabled = true,
            presetName = "best"
        )

        fun cvOnly(): DetectionSettings = DetectionSettings(
            mode = DetectionMode.COMPUTER_VISION,
            cropMode = CropMode.SIMPLE,
            presetName = "cv_only"
        )
    }
}

@Serializable
enum class CorrectionMode {
    AUTO,             // Choose CROP / CROP_AND_ROTATE / PERSPECTIVE based on geometry thresholds
    CROP,             // Always axis-aligned crop (fastest, matches CropMode.SIMPLE)
    CROP_AND_ROTATE,  // Crop + rotation correction for slightly tilted photos
    PERSPECTIVE       // Always 4-point perspective warp (matches CropMode.WARP_STRETCH)
}

enum class BorderFillPreset(val displayName: String, val rgb: Triple<Int,Int,Int>) {
    GREY("Grey", Triple(114, 114, 114)),
    WHITE("White", Triple(255, 255, 255)),
    BLACK("Black", Triple(0, 0, 0))
}
```

#### 3.4 Settings Groups for UI Organization

| Group | Settings | Visibility |
|-------|----------|------------|
| **Detection Mode** | `mode`, `cropMode`, preset selector | Always visible |
| **ONNX Detection** | `detectionConfidence`, `detectionIou`, `detectionImageSize` | When `mode != CV` |
| **Pose Model** | `poseConfidence`, `poseIou`, `poseCropExpand`, `poseRefine*`, `poseSweep*`, `centerBiasEnabled` | When `mode == POSE \|\| mode == HYBRID` |
| **CV Refinement** | `cvRefineEnabled`, `cvRefineRadius`, `cvRefineOrientationAware`, `cvRefineNeighborAnchored`, `cvRefineStripSearch`, `cvRefineTwoPass`, `cvRefineVisThreshold`, `autoRefineEnabled` | When mode enables ML |
| **Fiducial Refinement** | `fiducialEnabled`, `fiducialIterations`, `fiducialConfidence`, `fiducialModelPath` | Advanced / Developer section |
| **Classical CV** | `cvGamma`, `cvAdaptive*`, `cvMorphKernelSize`, etc. | When `mode == CV`, or always in "Advanced" |
| **Deduplication** | `dedupEnabled`, `dedupDistanceThreshold` | Advanced section |
| **Crop & Export** | `cropMode`, `cropMargin`, `borderFill`, `cropTransparent`, `adaptiveMargin*`, `warpFallbackThreshold` | Always visible (collapsed) |
| **Corrections** | `correctionMode`, `rotationThreshold`, `skewThreshold` | "Corrections" section |
| **Training Export** | `trainingDataExportEnabled`, `trainingDataScale` | "Advanced" / "Developer" section |

#### 3.5 Preset System

Users can select from named presets that set all parameters at once, then tweak individual values:

| Preset | Mode | Crop | Refine | Sweep | Auto-Refine | CV Refine | Margin | Border | Description |
|--------|------|------|--------|-------|-------------|-----------|---------|--------|-------------|
| **Quick** | Hybrid | Warp-Stretch | ✗ | ✗ | ✗ | ✗ | 0 | grey | Fastest: detect → pose → done |
| **Crop** | Hybrid | Simple-Corners | ✓ | ✗ | ✓ | ✗ | 0.02 | grey | Good: auto-refine + tighter crop |
| **Warp** | Hybrid | Warp-Stretch | ✓ | ✗ | ✓ | ✗ | 0.02 | white | High quality: full pipeline + perspective |
| **Best** | Hybrid | Warp-Stretch | ✓ | XY-sweep | ✓ | ✓ | 0.02 | white | Best quality: sweep + all refinements |
| **CV Only** | CV | Simple | — | — | — | — | 0 | grey | Classical CV only (no ML) |

---

## 4. Ports & Adapters Design

### 4.1 New Port Interfaces

#### `OnnxDetectionPort` — YOLO Detection Model

```kotlin
interface OnnxDetectionPort {
    fun detectBoundingBoxes(image: BufferedImage, settings: DetectionSettings): List<DetectedBoundingBox>
    fun isAvailable(): Boolean
    fun close()
}

data class DetectedBoundingBox(
    val x: Float,       // Center X in original image coordinates
    val y: Float,       // Center Y in original image coordinates
    val width: Float,   // Width in original image coordinates
    val height: Float,  // Height in original image coordinates
    val confidence: Float
)
```

#### `OnnxPosePort` — YOLO Pose Model

```kotlin
interface OnnxPosePort {
    fun detectPose(imageCrop: BufferedImage, cropOrigin: Offset, settings: DetectionSettings): List<DetectedPhotoWithVisibility>
    fun isAvailable(): Boolean
    fun close()
}

data class Offset(val x: Int, val y: Int)

data class DetectedPhotoWithVisibility(
    val photo: DetectedPhoto,
    val cornerVisibility: List<Float>,  // TL, TR, BR, BL — matches DetectedPhoto corner order
    val dedupPriority: Float            // confidence × (0.5 if vis_count < 3 else 1.0)
)
```

#### `OnnxFiducialPort` — Fiducial Corner Model

```kotlin
interface OnnxFiducialPort {
    /**
     * Refine corners using iterative fiducial detection.
     *
     * For each corner, crops a region, runs the fiducial model, and maps the result
     * back to original coordinates. Repeats for [iterations] rounds if enabled.
     *
     * @param image Full original image
     * @param initialCorners Starting approximate corners
     * @param iterations Number of refinement rounds (default 2)
     * @param confidence Minimum detection confidence to accept
     * @return Refined corners with updated visibility
     */
    fun refineCorners(
        image: BufferedImage,
        initialCorners: DetectedPhoto,
        iterations: Int = 2,
        confidence: Float = 0.5f
    ): DetectedPhotoWithVisibility

    fun isAvailable(): Boolean
    fun close()
}
```

#### `CvRefinementPort` — Sobel Edge Refinement

```kotlin
interface CvRefinementPort {
    /**
     * Two-pass CV refinement of detected corners using Sobel edge detection.
     *
     * Pass 1: Refines corners with visible neighbors. Their boosted visibility
     *         makes them available as neighbors in Pass 2.
     * Pass 2: Corners that had no reliable neighbors in Pass 1 may now benefit
     *         from newly-refined neighbors.
     *
     * Only refines corners with visibility < cvRefineVisThreshold (default 0.7).
     *
     * @param image Original full-resolution image
     * @param initialCorners Corners from ML detection
     * @param cornerVisibility Visibility per corner (TL, TR, BR, BL)
     * @param settings Refinement settings
     * @return Refined corners with updated visibility (boosted to 0.95 for CV-refined corners)
     */
    fun refineCorners(
        image: BufferedImage,
        initialCorners: DetectedPhoto,
        cornerVisibility: List<Float>,
        settings: DetectionSettings
    ): CvRefinementResult
}

data class CvRefinementResult(
    val photo: DetectedPhoto,
    val cornerVisibility: List<Float>,
    val originalVisibility: List<Float>  // Visibility before CV boosting (for adaptive margin)
)
```

#### `DetectionDeduplicationPort` — Keypoint-Center Distance NMS

```kotlin
interface DetectionDeduplicationPort {
    /**
     * Remove duplicate/near-duplicate detections using greedy center-distance NMS.
     *
     * Strategy from photocrop.py:
     * 1. Compute center from visible keypoints (vis >= dedupVisThreshForCenter = 0.25)
     * 2. Compute priority: confidence × (dedupLowVisPenalty=0.5 if <3 visible corners else 1.0)
     * 3. Sort by priority descending
     * 4. Greedy keep: only keep results whose center is >= dedupDistanceThreshold apart
     *
     * @param detections List of detected photos with visibility and priority
     * @param imageWidth Original image width (for distance normalization)
     * @param imageHeight Original image height (for distance normalization)
     * @param settings Deduplication settings
     * @return Filtered list with duplicates removed
     */
    fun deduplicate(
        detections: List<DetectedPhotoWithVisibility>,
        imageWidth: Int,
        imageHeight: Int,
        settings: DetectionSettings
    ): List<DetectedPhotoWithVisibility>
}
```

#### `TrainingDataPort` — Training Data Export

```kotlin
interface TrainingDataPort {
    fun saveTrainingData(
        sourceImage: BufferedImage,
        detectedPhoto: DetectedPhoto,
        cornerVisibility: List<Float>,
        originalFileName: String,
        index: Int,
        settings: DetectionSettings,
        outputDirectory: String
    ): TrainingDataResult

    fun isTrainingDataOutputAvailable(outputDirectory: String): Boolean
}

data class TrainingDataResult(
    val imagePath: String,
    val labelPath: String,
    val imageSize: Pair<Int, Int>,
    val success: Boolean,
    val error: String? = null
)
```

### 4.2 Updated Domain Models

Add to `PhotoScanModels.kt`:

```kotlin
data class DetectedPhoto(
    val id: String = java.util.UUID.randomUUID().toString(),
    val topLeft: PhotoCorner = PhotoCorner(),
    val topRight: PhotoCorner = PhotoCorner(),
    val bottomLeft: PhotoCorner = PhotoCorner(),
    val bottomRight: PhotoCorner = PhotoCorner(),
    val configuration: PhotoScanConfiguration = PhotoScanConfiguration(),
    val applyPerspectiveCorrection: Boolean = true,
    val rotation: RotationAngle = RotationAngle.NONE,
    // ── NEW FIELDS ──
    val confidence: Float = 1.0f,
    val detectionMode: DetectionMode = DetectionMode.COMPUTER_VISION,
    val cornerVisibility: List<Float> = listOf(1f, 1f, 1f, 1f), // TL, TR, BR, BL
    val originalVisibility: List<Float> = listOf(1f, 1f, 1f, 1f), // Before CV boosting
    val cropMode: CropMode = CropMode.WARP_STRETCH,
    val borderFill: String = "grey",
    val cropMargin: Float = 0f,
) {
    companion object {
        fun fromYoloKeypoints(
            kp0: PhotoCorner, kp1: PhotoCorner, kp2: PhotoCorner, kp3: PhotoCorner,
            confidence: Float = 1.0f,
            visibility: List<Float> = listOf(1f, 1f, 1f, 1f),
            id: String = java.util.UUID.randomUUID().toString()
        ): DetectedPhoto = DetectedPhoto(
            id = id,
            topLeft = kp1,        // UL → TL
            topRight = kp2,       // UR → TR
            bottomLeft = kp0,     // LL → BL
            bottomRight = kp3,    // LR → BR
            confidence = confidence,
            cornerVisibility = visibility,
            originalVisibility = visibility,
        )
    }

    fun toYoloKeypoints(): List<PhotoCorner> = listOf(
        bottomLeft,  // kp0: LL
        topLeft,     // kp1: UL
        topRight,    // kp2: UR
        bottomRight  // kp3: LR
    )

    /** Count corners with visibility >= threshold */
    fun visibleCornerCount(threshold: Float = 0.3f): Int =
        cornerVisibility.count { it >= threshold }

    /** Dedup priority: confidence × (lowVisPenalty if <3 visible corners else 1.0) */
    fun dedupPriority(lowVisPenalty: Float = 0.5f): Float =
        if (visibleCornerCount() < 3) confidence * lowVisPenalty else confidence

    /** Center computed from visible keypoints */
    fun visibleKeypointCenter(visThreshold: Float = 0.25f): PhotoCorner {
        val visible = cornersWithVisibility(visThreshold)
        if (visible.isEmpty()) return PhotoCorner(
            x = (topLeft.x + topRight.x + bottomLeft.x + bottomRight.x) / 4f,
            y = (topLeft.y + topRight.y + bottomLeft.y + bottomRight.y) / 4f
        )
        val avgX = visible.map { it.first.x }.average().toFloat()
        val avgY = visible.map { it.first.y }.average().toFloat()
        return PhotoCorner(x = avgX, y = avgY)
    }

    private fun cornersWithVisibility(threshold: Float): List<Pair<PhotoCorner, Float>> =
        listOf(
            topLeft to cornerVisibility[0],
            topRight to cornerVisibility[1],
            bottomRight to cornerVisibility[2],
            bottomLeft to cornerVisibility[3]
        ).filter { it.second >= threshold }
}
```

### 4.3 Updated Existing Port: `PhotoScanDetectorPort`

```kotlin
interface PhotoScanDetectorPort {
    fun detectPhotos(image: BufferedImage, settings: DetectionSettings): List<DetectedPhotoWithVisibility>

    @Deprecated("Use detectPhotos(image, settings)", replaceWith = ReplaceWith("detectPhotos(image, DetectionSettings())"))
    fun detectPhotos(image: BufferedImage): List<DetectedPhoto> =
        detectPhotos(image, DetectionSettings()).map { it.photo }
}
```

---

## 5. Pipeline Stages & Reusable Components

### 5.1 Pipeline Orchestrator

The orchestrator mirrors `photocrop.py`'s `pipeline()` function exactly:

```kotlin
class DetectionOrchestrator(
    private val cvDetector: PhotoScanDetectorPort,
    private val onnxDetection: OnnxDetectionPort,
    private val onnxPose: OnnxPosePort,
    private val onnxFiducial: OnnxFiducialPort,
    private val cvRefinement: CvRefinementPort,
    private val deduplication: DetectionDeduplicationPort
) {
    fun detectPhotos(image: BufferedImage, settings: DetectionSettings): List<DetectedPhotoWithVisibility> {
        return when (settings.mode) {
            DetectionMode.COMPUTER_VISION -> detectWithCV(image, settings)
            DetectionMode.BOUNDING_BOX -> detectBoundingBoxOnly(image, settings)
            DetectionMode.POSE -> detectPoseOnly(image, settings)
            DetectionMode.HYBRID -> detectHybrid(image, settings)
        }
    }

    private fun detectHybrid(image: BufferedImage, settings: DetectionSettings): List<DetectedPhotoWithVisibility> {
        // Stage 1: Detection → bounding boxes
        val boxes = onnxDetection.detectBoundingBoxes(image, settings)
        if (boxes.isEmpty()) return emptyList()

        // Compute crop limits to prevent expansion into adjacent detections
        val cropLimits = if (settings.cropLimitsEnabled)
            computeCropLimits(boxes, image.width, image.height, settings.cropLimitsMaxIntrusion)
        else null

        // Stage 2: For each box, expand → crop → pose
        val allResults = mutableListOf<DetectedPhotoWithVisibility>()
        for (box in boxes) {
            val expansion = if (settings.centerBiasEnabled)
                centerBiasedExpand(box, settings.poseCropExpand, image.width, image.height, cropLimits)
            else
                standardExpand(box, settings.poseCropExpand, image.width, image.height, cropLimits)

            val crop = subImage(image, expansion)
            val poseResults = onnxPose.detectPose(crop, Offset(expansion.x, expansion.y), settings)

            // Stage 2b: Optional pose refine pass
            if (settings.poseRefineEnabled && poseResults.isNotEmpty()) {
                val best = poseResults.maxByOrNull { it.dedupPriority }!!
                val tighterCrop = deriveTighterCrop(best.photo, settings.poseRefineExpand, image.width, image.height, cropLimits)
                val refinedCrop = subImage(image, tighterCrop)
                val refinedResults = onnxPose.detectPose(refinedCrop, Offset(tighterCrop.x, tighterCrop.y), settings)
                allResults.addAll(refinedResults)
            } else {
                allResults.addAll(poseResults)
            }
        }

        // Stage 2c: Optional sweep (grid search over expand/refine values)
        if (settings.poseSweepEnabled || settings.poseSweepXyEnabled) {
            val sweepResults = runSweep(image, boxes, settings, cropLimits)
            // Merge sweep results: keep per-photo best
            allResults.addAll(sweepResults)
        }

        // Stage 3: CV refinement
        var withCvRefined = applyCvRefinement(image, allResults, settings)

        // Stage 3.5: Fiducial refinement (optional)
        if (settings.fiducialEnabled && onnxFiducial.isAvailable()) {
            withCvRefined = withCvRefined.map { detected ->
                if (detected.cornerVisibility.any { it < 0.5f }) {
                    val refined = onnxFiducial.refineCorners(
                        image, detected.photo,
                        settings.fiducialIterations, settings.fiducialConfidence
                    )
                    detected.copy(
                        photo = refined.photo,
                        cornerVisibility = refined.cornerVisibility,
                        originalVisibility = detected.originalVisibility
                    )
                } else detected
            }
        }

        // Stage 4: Deduplication
        return if (settings.dedupEnabled)
            deduplication.deduplicate(withCvRefined, image.width, image.height, settings)
        else withCvRefined
    }

    private fun applyCvRefinement(
        image: BufferedImage,
        results: List<DetectedPhotoWithVisibility>,
        settings: DetectionSettings
    ): List<DetectedPhotoWithVisibility> {
        // Auto-refine: only apply to photos with <3 visible corners
        if (settings.autoRefineEnabled && !settings.cvRefineEnabled) {
            return results.map { detected ->
                if (detected.photo.visibleCornerCount(0.3f) < 3) {
                    val result = cvRefinement.refineCorners(
                        image, detected.photo, detected.cornerVisibility, settings)
                    detected.copy(
                        photo = result.photo,
                        cornerVisibility = result.cornerVisibility,
                        originalVisibility = result.originalVisibility
                    )
                } else detected
            }
        }
        if (!settings.cvRefineEnabled) return results

        return results.map { detected ->
            val result = cvRefinement.refineCorners(
                image, detected.photo, detected.cornerVisibility, settings)
            detected.copy(
                photo = result.photo,
                cornerVisibility = result.cornerVisibility,
                originalVisibility = result.originalVisibility
            )
        }
    }

    private fun runSweep(
        image: BufferedImage,
        boxes: List<DetectedBoundingBox>,
        settings: DetectionSettings,
        cropLimits: List<CropLimit>?
    ): List<DetectedPhotoWithVisibility> {
        if (settings.poseSweepXyEnabled) {
            return runXySweep(image, boxes, settings, cropLimits)
        }
        // Uniform sweep: all combinations of cropExpands × refineExpands
        val allResults = mutableListOf<DetectedPhotoWithVisibility>()
        for (box in boxes) {
            val candidates = mutableListOf<SweepCandidate>()
            for (expand in settings.poseSweepCropExpands) {
                // Without refine
                val expansion = standardExpand(box, expand, image.width, image.height, cropLimits)
                val crop = subImage(image, expansion)
                val results = onnxPose.detectPose(crop, Offset(expansion.x, expansion.y), settings)
                candidates.addAll(results.map { SweepCandidate(expand, null, it) })

                // With refine
                for (refine in settings.poseSweepRefineExpands) {
                    // ... run refine pass and score
                }
            }
            // Pick best per detection: maximize (vis_count, min_vis, confidence)
            val best = candidates.maxWithOrNull(compareBy<SweepCandidate> { it.score() })
            if (best != null) allResults.add(best.result)
        }
        return allResults
    }

    // Tiered X/Y sweep with early stopping (mirrors photocrop.py logic)
    private fun runXySweep(...): List<DetectedPhotoWithVisibility> { ... }

    // Center-biased expansion (mirrors photocrop.py _center_biased_expand)
    private fun centerBiasedExpand(
        box: DetectedBoundingBox, expandFraction: Float,
        imageWidth: Int, imageHeight: Int, cropLimits: List<CropLimit>?
    ): CropRegion { ... }

    // Standard symmetric expansion
    private fun standardExpand(
        box: DetectedBoundingBox, expand: Float,
        imageWidth: Int, imageHeight: Int, cropLimits: List<CropLimit>?
    ): CropRegion { ... }
}
```

### 5.2 ONNX Preprocessing

Direct port of `photocrop.py`'s `preprocess_letterbox()` and `preprocess_crop()`:

```kotlin
object OnnxPreprocessing {
    /** Letterbox resize: preserve aspect ratio, pad with grey (114,114,114). Used by detection model. */
    fun letterbox(image: BufferedImage, targetSize: Int = 640): Triple<FloatBuffer, ScaleInfo, Pair<Int, Int>> {
        val scale = minOf(targetSize.toFloat() / image.width, targetSize.toFloat() / image.height)
        val newW = (image.width * scale).toInt()
        val newH = (image.height * scale).toInt()
        val padX = (targetSize - newW) / 2
        val padY = (targetSize - newH) / 2
        // Resize, pad, normalize to [0,1], arrange as NCHW FloatBuffer
        // Return (buffer, ScaleInfo(ratio, padX, padY, origW, origH), cropOrigin for coordinate remapping)
    }

    /** Stretch resize: resize to exactly targetSize×targetSize, no padding. Used by pose model. */
    fun stretchResize(image: BufferedImage, targetSize: Int = 640): FloatBuffer {
        // Resize to exactly targetSize×targetSize, normalize to [0,1], NCHW
    }

    data class ScaleInfo(
        val ratio: Float,    // resize scale factor
        val padX: Int,       // horizontal padding for letterbox
        val padY: Int,       // vertical padding for letterbox
        val origWidth: Int,  // original image width
        val origHeight: Int  // original image height
    )
}
```

### 5.3 ONNX Postprocessing

Handles both legacy `[1,5,N]` and NMS `[1,N,6]` detection output formats (photocrop.py supports both):

```kotlin
object OnnxPostprocessing {
    /**
     * Post-process detection model output.
     * Supports two formats:
     *   Legacy: [1, 5, N_anchors] — (cx, cy, w, h, confidence)
     *   NMS:    [1, N, 6] — (x1, y1, x2, y2, confidence, class)
     */
    fun postprocessDetection(
        output: Array<*>?,  // ONNX output tensor
        scaleInfo: OnnxPreprocessing.ScaleInfo,
        settings: DetectionSettings
    ): List<DetectedBoundingBox> { ... }

    /**
     * Post-process pose model output.
     * Output shape: [1, 300, 18] — up to 300 detections
     * Per-row: [x1,y1,x2,y2,conf,class, kp0x,kp0y,kp0v, kp1x,kp1y,kp1v, kp2x,kp2y,kp2v, kp3x,kp3y,kp3v]
     */
    fun postprocessPose(
        output: Array<*>?,
        cropOrigin: Offset,
        cropWidth: Int, cropHeight: Int,
        settings: DetectionSettings
    ): List<DetectedPhotoWithVisibility> { ... }

    /** Compute dedup priority: confidence × (0.5 if <3 visible corners else 1.0) */
    private fun computeDedupPriority(confidence: Float, visibility: List<Float>, visThreshold: Float = 0.25f): Float {
        val visibleCount = visibility.count { it >= visThreshold }
        return if (visibleCount < 3) confidence * 0.5f else confidence
    }
}
```

### 5.4 Sobel Edge Refinement (CV Post-Processing)

Direct port of `photocrop.py`'s `refine_corners_cv()`. Two-pass refinement:

**Pass 1:** Refines corners with reliable neighbors. Their boosted visibility (set to 0.95 for CV-refined corners) makes them available as neighbors in Pass 2.

**Pass 2:** Corners that had no reliable neighbors in Pass 1 now may benefit from newly-refined neighbors.

For each corner with visibility < `cvRefineVisThreshold` (default 0.7):

1. **Orientation-aware edge search:** Each corner type (TL/TR/BR/BL) has known edge geometry:
   - TL: horizontal below, vertical right
   - TR: horizontal below, vertical left
   - BR: horizontal above, vertical left
   - BL: horizontal above, vertical right

2. **Neighbor-anchored projection:** Adjacent high-visibility corners project an expected position:
   - TL neighbors: {h→TR, v→BL}
   - TR neighbors: {h→TL, v→BR}
   - BR neighbors: {h→BL, v→TR}
   - BL neighbors: {h→BR, v→TL}
   - Only uses neighbors with visibility ≥ `_NEIGHBOR_VIS_THRESHOLD` (0.5)

3. **Search strategy by projection quality:**
   | `projected_axis` | Primary method | Fallback |
   |---|---|---|
   | `"x"` or `"y"` (partial) | Strip search | 2D window search |
   | `"both"` (full) | 2D window search | Strip search |
   | `"none"` | 2D window search with NN constraint | None |

4. **Strip search:** Scan perpendicular to projected axis (strip_half_width=15px), find peaks using gradient magnitude, constrained by detection bbox ±30px.

5. **2D window search:** Sobel gradients in radius×radius window, filter by edge magnitude > threshold, fit weighted least-squares lines, compute intersection.

```kotlin
class SobelEdgeRefinementAdapter : CvRefinementPort {
    override fun refineCorners(
        image: BufferedImage,
        initialCorners: DetectedPhoto,
        cornerVisibility: List<Float>,
        settings: DetectionSettings
    ): CvRefinementResult {
        val originalVisibility = cornerVisibility.toList()
        var currentVisibility = cornerVisibility.toMutableList()
        var currentCorners = initialCorners

        // Two-pass refinement
        for (pass in 0..1) {
            val result = refineSinglePass(image, currentCorners, currentVisibility, settings)
            currentCorners = result.first
            // Boost refined corner visibility to 0.95 (they're now reliable neighbors)
            currentVisibility = result.second.toMutableList()
        }

        return CvRefinementResult(
            photo = currentCorners,
            cornerVisibility = currentVisibility,
            originalVisibility = originalVisibility
        )
    }

    private fun refineSinglePass(
        image: BufferedImage,
        corners: DetectedPhoto,
        visibility: List<Float>,
        settings: DetectionSettings
    ): Pair<DetectedPhoto, List<Float>> { ... }

    // Orientation-aware edge filtering
    // Neighbor-anchored projection
    // Strip search
    // 2D window search with weighted line fitting
    // All ported from photocrop.py
}
```

### 5.5 Fiducial Refinement (Optional)

```kotlin
class OnnxFiducialAdapter(
    private val modelResourcePort: ModelResourcePort
) : OnnxFiducialPort {
    /**
     * Iterative corner refinement using a separate fiducial ONNX model.
     *
     * For each corner with visibility < threshold:
     * 1. Crop a 640×640 region around the approximate corner position
     * 2. Run letterbox preprocessing + ONNX inference
     * 3. Map detected fiducial corners back to original coordinates
     * 4. Repeat for [iterations] rounds (re-crops around updated position)
     *
     * Fiducial model output has 4 classes: UL(0), UR(1), LL(2), LR(3)
     */
    override fun refineCorners(
        image: BufferedImage,
        initialCorners: DetectedPhoto,
        iterations: Int,
        confidence: Float
    ): DetectedPhotoWithVisibility { ... }
}
```

### 5.6 Deduplication

Direct port of `photocrop.py`'s `dedup_pose_results()`:

```kotlin
class DistanceDeduplicationAdapter : DetectionDeduplicationPort {
    override fun deduplicate(
        detections: List<DetectedPhotoWithVisibility>,
        imageWidth: Int, imageHeight: Int,
        settings: DetectionSettings
    ): List<DetectedPhotoWithVisibility> {
        if (!settings.dedupEnabled || detections.size <= 1) return detections

        val minDist = min(imageWidth, imageHeight).toFloat() * settings.dedupDistanceThreshold

        // Sort by priority descending (confidence × low-vis penalty)
        val sorted = detections.sortedByDescending { it.dedupPriority }

        val kept = mutableListOf<DetectedPhotoWithVisibility>()
        for (candidate in sorted) {
            val candidateCenter = candidate.photo.visibleKeypointCenter(settings.dedupVisThreshForCenter)
            val isTooClose = kept.any { keptItem ->
                val keptCenter = keptItem.photo.visibleKeypointCenter(settings.dedupVisThreshForCenter)
                val dx = candidateCenter.x - keptCenter.x
                val dy = candidateCenter.y - keptCenter.y
                sqrt(dx * dx + dy * dy) < minDist
            }
            if (!isTooClose) kept.add(candidate)
        }
        return kept
    }
}
```

### 5.7 Export / Crop Pipeline

Mirrors `photocrop.py`'s `save_crops()` exactly:

```kotlin
// In PhotoScanExportService or a new CropExportService

fun exportPhoto(
    sourceImage: BufferedImage,
    detected: DetectedPhotoWithVisibility,
    settings: DetectionSettings
): BufferedImage {
    val photo = detected.photo
    val vis = detected.cornerVisibility
    val origVis = detected.originalVisibility

    // Warp fallback: if any corner has original NN visibility < threshold, use simple crop
    if (settings.cropMode == CropMode.WARP || settings.cropMode == CropMode.WARP_STRETCH) {
        if (origVis.any { it < settings.warpFallbackThreshold }) {
            return cropSimpleCorners(sourceImage, detected, settings)
        }
    }

    return when (settings.cropMode) {
        CropMode.SIMPLE -> cropSimple(sourceImage, detected, settings)
        CropMode.SIMPLE_CORNERS -> cropSimpleCorners(sourceImage, detected, settings)
        CropMode.WARP -> cropWarp(sourceImage, detected, settings, dimensionMode = "inward")
        CropMode.WARP_STRETCH -> cropWarp(sourceImage, detected, settings, dimensionMode = "outward")
    }
}

private fun cropSimpleCorners(
    sourceImage: BufferedImage,
    detected: DetectedPhotoWithVisibility,
    settings: DetectionSettings
): BufferedImage {
    val photo = detected.photo
    val vis = detected.cornerVisibility

    // Fall back to detection bbox if <2 visible corners
    val visibleCorners = listOf(
        photo.topLeft to vis[0],
        photo.topRight to vis[1],
        photo.bottomRight to vis[2],
        photo.bottomLeft to vis[3]
    ).filter { it.second >= 0.3f }

    val bbox = if (visibleCorners.size >= 2) {
        // Tight bounding box from visible keypoints
        val xs = visibleCorners.map { it.first.x }
        val ys = visibleCorners.map { it.first.y }
        BoundingBox(xs.min(), ys.min(), xs.max(), ys.max())
    } else {
        // Fall back to detection model bbox
        photo.getBounds().let { BoundingBox(it.minX, it.minY, it.maxX, it.maxY) }
    }

    // Apply adaptive margin: low-visibility corners get more margin
    var margin = settings.cropMargin * diagonal(photo)
    if (settings.adaptiveMarginEnabled) {
        val minVis = origVis.minOrNull() ?: 1f
        if (minVis < settings.adaptiveMarginThreshold) {
            val extraFract = settings.adaptiveMarginMax * (1f - minVis / settings.adaptiveMarginThreshold)
            margin += extraFract * diagonal(photo)
        }
    }

    // Expand bbox by margin and crop
    val expanded = bbox.expand(margin.toInt())
    return subImage(sourceImage, expanded)
}

private fun cropWarp(
    sourceImage: BufferedImage,
    detected: DetectedPhotoWithVisibility,
    settings: DetectionSettings,
    dimensionMode: String  // "inward" or "outward"
): BufferedImage {
    val photo = detected.photo
    val vis = detected.cornerVisibility
    val origVis = detected.originalVisibility

    // Source corners: fall back to bbox-derived corners for low-vis keypoints
    val srcCorners = listOf(
        Triple(photo.topLeft, vis[0], origVis[0]),
        Triple(photo.topRight, vis[1], origVis[1]),
        Triple(photo.bottomRight, vis[2], origVis[2]),
        Triple(photo.bottomLeft, vis[3], origVis[3])
    ).map { (corner, vis, origVis) ->
        if (origVis < settings.warpFallbackThreshold) {
            // Derive corner from bbox
            derivedFromBbox(corner, photo)
        } else corner
    }

    // Compute output dimensions
    val wTop = distance(srcCorners[0], srcCorners[1])
    val wBot = distance(srcCorners[3], srcCorners[2])
    val hLeft = distance(srcCorners[0], srcCorners[3])
    val hRight = distance(srcCorners[1], srcCorners[2])
    val outW = if (dimensionMode == "outward") max(wTop, wBot) else (wTop + wBot) / 2
    val outH = if (dimensionMode == "outward") max(hLeft, hRight) else (hLeft + hRight) / 2

    // Apply perspective transform using PerspectiveTransformer
    // Border fill: parse settings.borderFill
    // Transparent: use BGRA with alpha mask if settings.cropTransparent
    ...
}

// Center-biased expansion (mirrors photocrop.py _center_biased_expand)
private fun centerBiasedExpand(
    box: DetectedBoundingBox,
    expandFraction: Float,
    imageWidth: Int, imageHeight: Int,
    cropLimits: List<CropLimit>?
): CropRegion {
    val x1 = box.x - box.width / 2
    val y1 = box.y - box.height / 2
    val x2 = box.x + box.width / 2
    val y2 = box.y + box.height / 2

    // Weights: more expansion toward image center
    val distLeft = x1
    val distRight = imageWidth - x2
    val distTop = y1
    val distBottom = imageHeight - y2

    val weightLeft = distLeft / (distLeft + distRight)
    val weightRight = distRight / (distLeft + distRight)
    val weightTop = distTop / (distTop + distBottom)
    val weightBottom = distBottom / (distTop + distBottom)

    val expandPxW = box.width * expandFraction
    val expandPxH = box.height * expandFraction

    var left = x1 - 2 * expandPxW * weightLeft
    var right = x2 + 2 * expandPxW * weightRight
    var top = y1 - 2 * expandPxH * weightTop
    var bottom = y2 + 2 * expandPxH * weightBottom

    // Apply crop limits (prevent expansion into adjacent detections)
    if (cropLimits != null) { ... }

    return CropRegion(left.toInt(), top.toInt(), (right - left).toInt(), (bottom - top).toInt())
}
```

---

## 6. UI Layout & Interaction Design

### 6.1 Photo Scan Wizard — Import Step: Detection Settings

The **Photo Scan** tab contains a wizard whose first step is `PhotoScanImportScreen`. Detection settings are a new collapsible subsection inside the existing "Custom Settings" card, placed **above** the current Organization settings and **below** the "Auto-detect bounding boxes" toggle.

The existing screen already has a pattern of collapsible subsections (Organization, Filename, Conflict Resolution, etc.). Detection Settings follows the same pattern as a new top-level subsection called "Detection".

```
┌─────────────────────────────────────────────────────────────────────────┐
│  Photo Scan Import                                                       │
│                                                                         │
│  ☑ Auto-detect bounding boxes                                           │
│                                                                         │
│  Source:    [/path/to/scan.png          ] [Select File] [Select Folder] │
│  Destination: [~/Pictures/PhotoScan    ] [Select Destination Folder]     │
│                                                                         │
│  ┌─ EXIF info ────────────────────────────────────────────────────── ┐─┐│
│  │ Extracted photos preserve the original EXIF...                    ││
│  └──────────────────────────────────────────────────────────────────── ┘│
│                                                                         │
│  [Import Photo Scan(s)]                                                │
│                                                                         │
│  ┌─ Custom Settings ─────────────────────────────────────── [▼ Hide] ──┐│
│  │  ┌─ Detection ────────────────────────────────────── [▼ Hide] ──┐ ││
│  │  │  Preset:  [Quick] [Crop] [Warp] [Best] [CV Only]            │ ││
│  │  │  Mode:    [Hybrid ▼]  (Detection→Pose→CV Refinement)       │ ││
│  │  │                                                               │ ││
│  │  │  ┌─ ONNX Detection ─────────────────────────────────────┐  │ ││
│  │  │  │  Confidence: ──────●────────  0.50                     │  │ ││
│  │  │  │  IoU Threshold: ────●───────  0.45                    │  │ ││
│  │  │  │  Image Size: [640 ▼]                                     │  │ ││
│  │  │  │  ⓘ Model not found — install models to enable ML modes  │  │ ││
│  │  │  └───────────────────────────────────────────────────────────┘  │ ││
│  │  │                                                               │ ││
│  │  │  ┌─ Pose Model ───────────────────────────────────────────┐  │ ││
│  │  │  │  Confidence: ──────●────────  0.50                     │  │ ││
│  │  │  │  Crop Expand: ─────●────────  0.15                     │  │ ││
│  │  │  │  ☑ Enable Refine Pass   Expand: ──●──── 0.05          │  │ ││
│  │  │  │  ☐ Enable Sweep                                           │  │ ││
│  │  │  │  ☐ Center Bias (shift expansion toward image center)  │  │ ││
│  │  │  └───────────────────────────────────────────────────────────┘  │ ││
│  │  │                                                               │ ││
│  │  │  ┌─ CV Refinement ─────────────────────────────────────────┐  │ ││
│  │  │  │  ☑ Enable CV Refinement                                  │  │ ││
│  │  │  │  Search Radius: ──────●────────  40px                    │  │ ││
│  │  │  │  ☑ Orientation-Aware   ☑ Neighbor-Anchored              │  │ ││
│  │  │  │  ☑ Strip Search         ☑ Two-Pass                      │  │ ││
│  │  │  │  Visibility Threshold: ──●──── 0.70                      │  │ ││
│  │  │  │  ☐ Auto-Refine (only photos with <3 visible corners)   │  │ ││
│  │  │  └───────────────────────────────────────────────────────────┘  │ ││
│  │  │                                                               │ ││
│  │  │  ┌─ Crop & Export ──────────────────────────────────────────┐  │ ││
│  │  │  │  Crop Mode: [Warp-Stretch ▼]                              │  │ ││
│  │  │  │  Margin: ──●──── 0.02   ☑ Adaptive Margin                 │  │ ││
│  │  │  │  Border Fill: [White ▼]    ☐ Transparent PNG             │  │ ││
│  │  │  │  Warp Fallback Threshold: ──●──── 0.30                    │  │ ││
│  │  │  └───────────────────────────────────────────────────────────┘  │ ││
│  │  │                                                               │ ││
│  │  │  ┌─ Classical CV (fallback) ─────────────── [▼ Show More] ─┐  │ ││
│  │  │  │  Gamma: ──────●────────  1.4    Block Size: ──●─── 31  │  │ ││
│  │  │  │  Min Area: ───●────────  2000   Max Aspect: ──●─── 5.0 │  │ ││
│  │  │  └───────────────────────────────────────────────────────────┘  │ ││
│  │  │                                                               │ ││
│  │  │  ┌─ Advanced / Developer ──────────────── [▼ Show More] ──┐  │ ││
│  │  │  │  ☑ Deduplication  Distance: ──●─── 0.12  IoU: ──●── 0.45│  │ ││
│  │  │  │  ☐ Fiducial Model  Path: [............]  Iterations: 2  │  │ ││
│  │  │  │  ☐ Crop Limits  Max Intrusion: ──●─── 0.15             │  │ ││
│  │  │  │  ☐ Save Training Data  Scale: [1.0 ▼]                    │  │ ││
│  │  │  │  Training dir: [~/photo-pose-detector/training_data]     │  │ ││
│  │  │  └───────────────────────────────────────────────────────────┘  │ ││
│  │  └──────────────────────────────────────────────────────────────────┘ ││
│  │  ┌─ Organization ─────────────────────────── [▼ Hide] ────────────┐│
│  │  │  ... (existing settings) ...                                    ││
│  │  └──────────────────────────────────────────────────────────────────┘│
│  └────────────────────────────────────────────────────────────────────────┘│
└─────────────────────────────────────────────────────────────────────────────┘
```

**UI Integration Notes:**

- The Detection Settings section is a new `PhotoScanCollapsibleSubsection` inside the existing "Custom Settings" `OutlinedCard`, using the exact same pattern as "Organization"
- It appears **above** the existing Organization subsection since detection is the first pipeline step
- The existing "Auto-detect bounding boxes" toggle controls whether detection runs at all; when unchecked, detection settings are hidden/disabled
- When `DetectionMode == COMPUTER_VISION`, the ONNX subsections (Detection, Pose, Fiducial) are hidden
- When `DetectionMode != COMPUTER_VISION` and ONNX models are unavailable, show ⚠ warning and "Install models" link
- Presets appear as `FilterChip` buttons (matching existing pattern from Organization's folder presets)
- All slider controls use Material3 `Slider`
- Settings are persisted via the existing `PhotoScanImportTabSettings` → `AppSettings` → `SettingsPort` flow
- The existing "Custom Settings" card already saves changes via `LaunchedEffect`; detection settings use the same debounce-and-save pattern

### 6.2 Overview Screen — Detection Controls

```
┌─────────────────────────────────────────────────────────────────────────┐
│  ← Back          Bounding Box Overview          [⚙ Re-detect] [?]      │
│                                                                         │
│  ┌──────────────────────────────────────────────────────────────────┐  │
│  │  📷 Detected: 3 photos  Mode: Hybrid  Best confidence: 0.87   │  │
│  │                                                                  │  │
│  │  ┌───────┐     ┌──────────┐     ┌───────────┐                  │  │
│  │  │ Photo │     │  Photo   │     │  Photo    │                  │  │
│  │  │   1   │     │    2     │     │    3      │                  │  │
│  │  │ [CV]  │     │  [HYB]  │     │  [HYB]    │                  │  │
│  │  └───────┘     └──────────┘     └───────────┘                  │  │
│  │  vis: ●●●●      vis: ●●●○       vis: ●●○○                      │  │
│  └──────────────────────────────────────────────────────────────────┘  │
│                                                                         │
│  [◀ Prev] Image 1/5 [Next ▶]   ⋮ 3/5 pre-processed                    │
│                                                                         │
│  [4-Point] [Add Box] [+ Zoom] [- Zoom] [Fit] [🗑 Delete]              │
│                                                                         │
│  ┌─ Per-Image Detection ─────────────────────────────── [▼ Hide] ─┐ │
│  │  Mode: [Hybrid ▼]  Confidence: ──●── 0.50                       │ │
│  │  ☑ CV Refinement   Radius: ──●── 40                              │ │
│  │  Crop Mode: [Warp-Stretch ▼]                                    │ │
│  │  [🔄 Re-detect This Image]  [🔄 Re-detect All Remaining]        │ │
│  └────────────────────────────────────────────────────────────────────┘ │
│                                                                         │
│                                [Next → Summary]                         │
└─────────────────────────────────────────────────────────────────────────┘
```

- **Re-detect This Image** — Uses current settings to re-run detection, replacing all bounding boxes
- **Re-detect All Remaining** — Propagates current settings to unprocessed images, restarting preprocessing
- Mode badge shows detection method (CV/HYB/POSE/BB)
- Visibility dots: green (>0.7), yellow (>0.3), red (≤0.3)

### 6.3 Refinement Screen — Corner-Level CV Refinement

```
┌─────────────────────────────────────────────────────────────────────────┐
│  ← Overview     Refine Bounding Box              [?]                    │
│                                                                         │
│  ┌──────────────────────────────────────────────────────────────────┐  │
│  │         ⊙ TL(vis:0.92)           ⊙ TR(vis:0.71)                │  │
│  │              ╲                    ╱                              │  │
│  │               ╲  Photo 2 ────────╱                              │  │
│  │              ╱    [HYB]        ╲                                │  │
│  │         ⊙ BL(vis:0.88)           ⊙ BR(vis:0.45) ⚠              │  │
│  └──────────────────────────────────────────────────────────────────┘  │
│                                                                         │
│  [⚙ Re-detect] [🔍 CV Refine All] [🔍 CV Refine Low Corners]       │
│  [🔍 Fiducial Refine]  (if model available)                           │
│                                                                         │
│  Confidence: 0.87  Mode: Hybrid  Visibility: ●●●○                     │
│  Crop: Warp-Stretch  Margin: adaptive (2%+3%)                          │
│                                                                         │
│  [◀ Box 1/3] [Box ▶] [↺ Undo] [↻ Redo] [🔍+] [🔍-] [🔍Fit]        │
│  [←5°] [→5°] [±5%] [∓5%]                                              │
└─────────────────────────────────────────────────────────────────────────┘
```

### 6.4 Summary Screen — Training Data Toggle & Export Settings

```
┌─────────────────────────────────────────────────────────────────────────┐
│  ← Overview     Export Photos                     [?]                    │
│                                                                         │
│  Photo 1:                   Photo 2:               Photo 3:            │
│  Mode: Warp-Stretch         Mode: Warp-Stretch     Mode: Simple-Crop  │
│  Margin: adaptive 2%+3%     Margin: 2%             Margin: 0%         │
│  Border: white              Border: white          Border: grey        │
│  [Warp ↻] [Crop ↻]         [Warp ↻] [Crop ↻]      [Simple ↻]        │
│                                                                         │
│  ── Export Settings ──────────────────────────────────────────────────  │
│  Destination: [~/Pictures/PhotoScan] [Browse]                          │
│  Format: [JPEG 90% ▼]                                                  │
│                                                                         │
│  ── Training Data ────────────────────────────────────────────────────  │
│  ☐ Save Training Data                                                   │
│  Training dir: [~/photo-pose-detector/training_data] [Browse]         │
│  Scale: [Original ▼]                                                   │
│                                                                         │
│  [Rotate All CW] [Perspective All] [Clear All]                         │
│                                                                         │
│                   [Export 3 Photos]                                     │
└─────────────────────────────────────────────────────────────────────────┘
```

---

## 7. Training Data Export Mode

### 7.1 Purpose

When the user manually adjusts corners, they are creating **ground truth annotations** for future model training. This mode captures those annotations in YOLO pose format for use in photo-pose-detector.

### 7.2 Export Format

For each detected+confirmed photo, two files are written:

**Image:** `{outputDir}/images/{originalName}_{index}.jpg` — scaled copy of source image (quality 95%)

**Label:** `{outputDir}/labels/{originalName}_{index}.txt` — YOLO pose format (17 columns):
```
0 cx cy w h kp0x kp0y kp0v kp1x kp1y kp1v kp2x kp2y kp2v kp3x kp3y kp3v
```
- `class_id` = 0 (single class: "photo")
- `cx, cy, w, h` = bounding box (normalized [0,1])
- `kp0=LL=BL, kp1=UL=TL, kp2=UR=TR, kp3=LR=BR` in YOLO keypoint order
- `kp_v` = visibility (0=not visible, 2=visible — always 2 since user confirmed)
- All coordinates normalized to [0,1] relative to exported image dimensions

**Manifest:** `{outputDir}/manifest.json`
```json
{
  "version": "1.0",
  "exportDate": "2026-05-10T18:00:00Z",
  "detectionSettings": { ... full settings used ... },
  "images": [{
    "sourceFile": "scan_001.png",
    "imageFile": "images/scan_001_0.jpg",
    "labelFile": "labels/scan_001_0.txt",
    "detectionMode": "HYBRID",
    "originalSize": [4000, 6000],
    "exportSize": [4000, 6000],
    "photos": [{
      "index": 0,
      "confidence": 0.87,
      "cornerVisibility": [0.92, 0.71, 0.45, 0.88],
      "manualAdjustment": true
    }]
  }]
}
```

---

## 8. Live Settings Propagation

### 8.1 Architecture

```kotlin
// In PhotoScanWizardState
val detectionSettings: MutableStateFlow<DetectionSettings> =
    MutableStateFlow(DetectionSettings())  // Global settings from wizard import step

val perImageSettings: MutableStateFlow<Map<String, DetectionSettings>> =
    MutableStateFlow(emptyMap())  // Per-photo overrides

data class PreProcessedImage(
    val file: File,
    val image: BufferedImage,
    val boxes: List<BoundingBox>,
    val detectedPhotos: List<DetectedPhotoWithVisibility>? = null, // NEW: full detection results
    val settings: DetectionSettings,  // Snapshot of settings used for this detection
    val timestamp: Long = System.currentTimeMillis()
)
```

### 8.2 Re-detect Current Image

When user clicks "Re-detect This Image":

```kotlin
fun reDetectCurrentImage(
    state: PhotoScanWizardState,
    orchestrator: DetectionOrchestrator,
    appLogger: AppLogger
) {
    val image = state.image.value ?: return
    val settings = state.detectionSettings.value

    val detected = orchestrator.detectPhotos(image, settings)
    val boxes = detected.map { it.photo.toBoundingBox() }

    state.setDetectedBoxes(boxes)

    // Update pre-processed cache with full results
    val currentIndex = state.currentImageIndex.value
    state.putPreProcessed(currentIndex, PreProcessedImage(
        file = state.sourceFiles[currentIndex],
        image = image,
        boxes = boxes,
        detectedPhotos = detected,
        settings = settings
    ))
}
```

### 8.3 Propagate Settings to Remaining Images

When user clicks "Re-detect All Remaining":

```kotlin
fun propagateSettingsToRemaining(
    scope: CoroutineScope,
    state: PhotoScanWizardState,
    orchestrator: DetectionOrchestrator,
    appLogger: AppLogger
) {
    scope.launch {
        val settings = state.detectionSettings.value
        val allFiles = state.sourceFiles
        val startIndex = state.currentImageIndex.value + 1

        state.clearPreProcessedFrom(startIndex)
        state.setPreProcessing(true)

        for (i in startIndex until allFiles.size) {
            try {
                val file = allFiles[i]
                val image = withContext(Dispatchers.IO) { ImageIO.read(file) }
                if (image == null) continue

                val detected = if (settings.mode != DetectionMode.COMPUTER_VISION ||
                                   state.cvAutoDetectEnabled.value) {
                    withContext(Dispatchers.Default) {
                        orchestrator.detectPhotos(image, settings)
                    }
                } else emptyList()

                val boxes = detected.map { it.photo.toBoundingBox() }

                state.putPreProcessed(i, PreProcessedImage(
                    file = file, image = image,
                    boxes = boxes, detectedPhotos = detected,
                    settings = settings
                ))
            } catch (e: Exception) {
                appLogger.warn("Failed to process image ${allFiles[i].name}: ${e.message}")
            }
        }
        state.setPreProcessing(false)
    }
}
```

### 8.4 Settings Change Detection

When settings change on the import screen, show a warning if pre-processed images exist:

> ⚠ "You have 5 pre-processed images. Changing settings will require re-detection. [Re-detect All] [Keep Current]"

---

## 9. Implementation Phases

### Phase 1: Foundation (DetectionSettings + Domain Models)
**Effort:** 2-3 days

| Step | What | Files |
|------|------|-------|
| 1.1 | Create `DetectionSettings` data class | `domain/model/DetectionSettings.kt` (new) |
| 1.2 | Create `DetectionMode` enum | `domain/model/DetectionMode.kt` (new) |
| 1.3 | Create `CropMode` enum | `domain/model/CropMode.kt` (new) |
| 1.4 | Create `CorrectionMode` enum | `domain/model/CorrectionMode.kt` (new) |
| 1.5 | Add `confidence`, `detectionMode`, `cornerVisibility`, `originalVisibility`, `cropMode`, `borderFill`, `cropMargin` to `DetectedPhoto` | `domain/model/PhotoScanModels.kt` (modify) |
| 1.6 | Add `fromYoloKeypoints()`/`toYoloKeypoints()` and helper methods to `DetectedPhoto` | `domain/model/PhotoScanModels.kt` (modify) |
| 1.7 | Create `DetectedPhotoWithVisibility` | `domain/model/PhotoScanModels.kt` (modify) |
| 1.8 | Create new port interfaces | `domain/port/OnnxDetectionPort.kt`, `OnnxPosePort.kt`, `OnnxFiducialPort.kt`, `CvRefinementPort.kt`, `DetectionDeduplicationPort.kt`, `TrainingDataPort.kt` (new) |
| 1.9 | Update `PhotoScanDetectorPort` to accept `DetectionSettings` | `domain/port/PhotoScanPort.kt` (modify) |
| 1.10 | Add `DetectionSettings` to `PhotoScanWizardState` and `PreProcessedImage` | `infrastructure/wizard/PhotoScanWizardState.kt` (modify) |
| 1.11 | Add `DetectionSettings` to `PhotoScanProfile` | `domain/model/PhotoScanProfile.kt` (modify) |
| 1.12 | Write unit tests for YOLO mapping, settings serialization, presets | Test files (new) |

### Phase 2: ONNX Detection & Pose Adapters
**Effort:** 3-4 days

| Step | What | Files |
|------|------|-------|
| 2.1 | Add ONNX Runtime dependency to `build.gradle.kts` | `build.gradle.kts` (modify) |
| 2.2 | Implement `OnnxPreprocessing` (letterbox + stretch resize) | `infrastructure/photoscan/OnnxPreprocessing.kt` (new) |
| 2.3 | Implement `OnnxPostprocessing` (legacy + NMS format, pose keypoints) | `infrastructure/photoscan/OnnxPostprocessing.kt` (new) |
| 2.4 | Implement `OnnxDetectionAdapter` | `infrastructure/photoscan/OnnxDetectionAdapter.kt` (new) |
| 2.5 | Implement `OnnxPoseAdapter` | `infrastructure/photoscan/OnnxPoseAdapter.kt` (new) |
| 2.6 | Write unit tests with mock ONNX outputs | Test files (new) |

### Phase 3: Pipeline Orchestrator & Adapters
**Effort:** 3-4 days

| Step | What | Files |
|------|------|-------|
| 3.1 | Implement `DetectionOrchestrator` with all 4 modes | `application/DetectionOrchestrator.kt` (new) |
| 3.2 | Implement `SobelEdgeRefinementAdapter` (two-pass, orientation-aware, neighbor-anchored, strip search) | `infrastructure/photoscan/SobelEdgeRefinementAdapter.kt` (new) |
| 3.3 | Implement `DistanceDeduplicationAdapter` (center-distance greedy NMS with priority) | `infrastructure/photoscan/DistanceDeduplicationAdapter.kt` (new) |
| 3.4 | Implement `OnnxFiducialAdapter` (optional iterative refinement) | `infrastructure/photoscan/OnnxFiducialAdapter.kt` (new) |
| 3.5 | Implement `FilesystemModelResourceAdapter` (classpath + filesystem paths) | `infrastructure/adapter/ClasspathModelResourceAdapter.kt` (modify) |
| 3.6 | Update `PhotoScanDetectorService` to delegate to orchestrator | `infrastructure/photoscan/PhotoScanDetectorService.kt` (modify) |
| 3.7 | Implement crop/export pipeline (simple, simple-corners, warp, warp-stretch, adaptive margin, center bias, crop limits) | `application/PhotoScanExportService.kt` (modify) |
| 3.8 | Register all new components in `AppModule.kt` | `di/AppModule.kt` (modify) |
| 3.9 | Integration tests | Test files (new) |

### Phase 4: Correction Strategy
**Effort:** 1-2 days

| Step | What | Files |
|------|------|-------|
| 4.1 | Implement `determineCorrectionStrategy()` | `domain/model/CorrectionMode.kt` (modify) |
| 4.2 | Integrate with export pipeline (CROP / CROP_AND_ROTATE / PERSPECTIVE) | `application/PhotoScanExportService.kt` (modify) |
| 4.3 | Unit tests for correction strategy selection | Test files (new) |

### Phase 5: UI — Settings Panel
**Effort:** 3-4 days

| Step | What | Files |
|------|------|-------|
| 5.1 | Create `DetectionSettingsPanel` composable | `ui/screens/wizard/DetectionSettingsPanel.kt` (new) |
| 5.2 | Add preset selector (FilterChip row) | `ui/screens/wizard/DetectionSettingsPanel.kt` (new) |
| 5.3 | Add mode selector with conditional sections | `ui/screens/wizard/DetectionSettingsPanel.kt` (new) |
| 5.4 | Add settings groups (ONNX Detection, Pose, CV Refine, Crop, CV, Advanced) | `ui/screens/wizard/DetectionSettingsPanel.kt` (new) |
| 5.5 | Integrate into `PhotoScanImportScreen` as Detection subsection inside Custom Settings | `ui/screens/wizard/PhotoScanImportScreen.kt` (modify) |
| 5.6 | Persist settings to `PhotoScanProfile` | `infrastructure/adapter/SettingsAdapter.kt` (modify) |
| 5.7 | Show "Model not available" warning when ONNX unavailable | `ui/screens/wizard/PhotoScanImportScreen.kt` (modify) |

### Phase 6: UI — Re-detection & Propagation
**Effort:** 2-3 days

| Step | What | Files |
|------|------|-------|
| 6.1 | Add detection mode badge + visibility dots to bounding boxes | `infrastructure/wizard/BoundingBox.kt` (modify) |
| 6.2 | Add "Re-detect" button to Overview screen | `ui/screens/wizard/OverviewScreen.kt` (modify) |
| 6.3 | Add "Re-detect All Remaining" with confirmation dialog | `ui/screens/wizard/OverviewScreen.kt` (modify) |
| 6.4 | Add per-image settings panel (collapsed by default) | `ui/screens/wizard/OverviewScreen.kt` (modify) |
| 6.5 | Implement settings propagation in `WizardContainer` | `ui/screens/wizard/WizardContainer.kt` (modify) |
| 6.6 | Update `preProcessRemainingImages` to use `DetectionSettings` snapshot | `ui/screens/wizard/WizardContainer.kt` (modify) |
| 6.7 | Add visibility color coding to refinement screen corners | `ui/screens/wizard/RefinementScreen.kt` (modify) |
| 6.8 | Add "CV Refine All / Refine Low Corners / Fiducial Refine" buttons | `ui/screens/wizard/RefinementScreen.kt` (modify) |

### Phase 7: Training Data Export
**Effort:** 2-3 days

| Step | What | Files |
|------|------|-------|
| 7.1 | Implement `FilesystemTrainingDataAdapter` | `infrastructure/training/FilesystemTrainingDataAdapter.kt` (new) |
| 7.2 | Implement `YoloLabelWriter` (17-column format) | `infrastructure/training/YoloLabelWriter.kt` (new) |
| 7.3 | Add "Save Training Data" toggle + directory selector to Summary screen | `ui/screens/wizard/SummaryScreen.kt` (modify) |
| 7.4 | Integrate training data export into `PhotoScanExportService` | `application/PhotoScanExportService.kt` (modify) |
| 7.5 | Integration test: end-to-end export produces valid YOLO files | Test files (new) |

### Phase 8: Validation & Polish
**Effort:** 3-4 days

| Step | What | Files |
|------|------|-------|
| 8.1 | Test with real scanned photos (CV + ML + Hybrid modes) | Manual testing |
| 8.2 | Performance profiling: ONNX model loading time, detection latency | Profiling |
| 8.3 | Edge cases: missing models, corrupt models, GPU fallback | Error handling |
| 8.4 | Graceful degradation: ONNX failure → CV fallback | `DetectionOrchestrator` |
| 8.5 | Batch stress test: 50+ images with settings propagation | `WizardContainer` |
| 8.6 | Settings persistence round-trip test | `SettingsAdapter` |
| 8.7 | Cross-platform testing (macOS, Windows, Linux) | Manual |

---

## 10. Validation Strategy

### 10.1 Unit Tests

| Test | What It Verifies |
|------|-----------------|
| `DetectionSettingsTest` | Serialization round-trip, preset factories, default values, all parameters |
| `DetectedPhotoYoloMappingTest` | `fromYoloKeypoints()` / `toYoloKeypoints()` round-trip |
| `DetectionModeTest` | Mode properties, ONNX requirements, fallback logic |
| `CropModeTest` | All 4 crop modes produce correct output |
| `BorderFillPresetTest` | RGB parsing for grey/white/black/custom |
| `OnnxPreprocessingTest` | Letterbox padding math, stretch resize normalization |
| `OnnxPostprocessingTest` | Legacy `[1,5,N]` format, NMS `[1,N,6]` format, pose keypoint extraction |
| `SobelEdgeRefinementTest` | Two-pass refinement, orientation-aware filtering, neighbor projection, strip search |
| `DistanceDeduplicationTest` | Priority computation, center-distance threshold, low-vis penalty |
| `CenterBiasExpansionTest` | Asymmetric expansion toward image center |
| `CropLimitsTest` | Expansion capped by adjacent detections |
| `AdaptiveMarginTest` | Per-corner margin based on visibility |
| `CorrectionModeTest` | Strategy selection based on geometry thresholds |
| `DetectionOrchestratorTest` | Mode routing, fallback on ONNX failure, CV-only path, sweep logic |
| `YoloLabelWriterTest` | Coordinate normalization, 17-column format validation |
| `TrainingDataExportTest` | End-to-end: image + corners → files |
| `VisibilityTrackingTest` | Original visibility preserved through CV boosting and adaptive margin |

### 10.2 Integration Tests

| Test | What It Verifies |
|------|-----------------|
| `CvDetectionIntegrationTest` | Full CV pipeline: load image → detect → extract corners |
| `OnnxDetectionIntegrationTest` | Full ONNX pipeline: load model → detect → corners (with test model) |
| `HybridDetectionIntegrationTest` | Hybrid: ONNX detect → pose → CV refine → dedup → export |
| `SweepIntegrationTest` | Sweep and X/Y sweep produce better results than single-pass |
| `FiducialIntegrationTest` | Iterative fiducial refinement improves corner accuracy |
| `BatchProcessingIntegrationTest` | Multiple images with settings propagation |
| `TrainingDataRoundTripTest` | Export → verify label format matches photo-pose-detector expectations |
| `SettingsPersistenceTest` | Save/load DetectionSettings from profile |
| `CropExportIntegrationTest` | Simple, Simple-Corners, Warp (inward), Warp-Stretch (outward) |

### 10.3 Cross-Validation with Python

Run the same images through both `photocrop.py` and the Kotlin `DetectionOrchestrator` with matching settings:

1. Take 10-20 test scanned images
2. Run `photocrop.py` with each preset (quick, crop, warp, best)
3. Run Kotlin `DetectionOrchestrator` with matching `DetectionSettings`
4. Compare: number of detected photos, corner coordinates (within 5px tolerance), dedup results, visibility values
5. Document discrepancies and tune parameters

### 10.4 ONNX Model Validation

- Verify `detection_model.onnx` produces equivalent results to Python Ultralytics inference
- Verify `pose_model.onnx` produces equivalent results to Python Ultralytics inference
- Test with edge cases: very dark images, very light images, extreme perspective, multiple photos
- Verify both legacy `[1,5,N]` and NMS `[1,N,6]` output formats are handled

---

## 11. Integration Points

### 11.1 From photo-pose-detector → petrie-file-importer

| Artifact | Format | Integration Point |
|----------|--------|-------------------|
| `detection_model.onnx` | ONNX opset 17 | Bundled in `src/main/resources/models/` |
| `pose_model.onnx` | ONNX opset 17 | Bundled in `src/main/resources/models/` |
| `fiducial_model.onnx` (optional) | ONNX opset 17 | Configurable path in settings |
| Training data format spec | YOLO pose (17-column) | `FilesystemTrainingDataAdapter` writes, `data_generator/` reads |
| Model I/O shapes | (1,3,640,640) → detection/pose outputs | `OnnxPreprocessing` / `OnnxPostprocessing` |
| Preprocessing details | Letterbox (detection) + stretch (pose) | `OnnxPreprocessing` |
| Preset configurations | quick/crop/warp/best | `DetectionSettings` companion factories |

### 11.2 From petrie-file-importer → photo-pose-detector

| Artifact | Format | Integration Point |
|----------|--------|-------------------|
| Training data exports | YOLO pose images/labels | `data_generator/` can ingest directly |
| Manifest JSON | Version, settings, metadata | For reproducibility and debugging |
| Corner coordinate format | TL/TR/BR/BL ↔ kp0/kp1/kp2/kp3 | `fromYoloKeypoints()` / `toYoloKeypoints()` |

### 11.3 ONNX Runtime Dependency

```kotlin
// build.gradle.kts
val onnxRuntimeVersion = "1.17.0"

dependencies {
    implementation("org.onnxruntime:onnxruntime:$onnxRuntimeVersion")
    // Optional GPU acceleration:
    // implementation("org.onnxruntime:onnxruntime_gpu:$onnxRuntimeVersion")
}
```

### 11.4 Settings Serialization

`DetectionSettings` uses `kotlinx.serialization` for persistence alongside `PhotoScanProfile`:

```kotlin
@Serializable
data class PhotoScanProfile(
    // ... existing fields ...
    val detectionSettings: DetectionSettings = DetectionSettings()
)
```

---

## 12. Risks & Mitigations

| Risk | Mitigation |
|------|-----------|
| ONNX models not available at runtime | `ModelResourcePort.isModelAvailable()` → disable ML modes, show warning in UI. CV mode always works. |
| ONNX Runtime native library conflicts | Use CPU-only `onnxruntime` by default. Document GPU setup for advanced users. |
| ONNX inference slow on first call | Lazy-load models on app start (background thread). Show "Loading ML models..." overlay. |
| Model version mismatch (new model → old app) | Bundle model version in `ModelResourcePort`. Show warning if version changes. |
| ONNX inference failure on specific images | `DetectionOrchestrator` catches all exceptions, falls back to CV mode with warning log. |
| Python/Kotlin coordinate discrepancies | Use `fromYoloKeypoints()` as single point of mapping. Cross-validation test suite. |
| User overwhelmed by settings | Presets hide complexity. "Advanced" sections collapsed by default. Sensible defaults. |
| Large batch re-detection blocking UI | Re-detection runs on `Dispatchers.Default`. Show progress bar. "Cancel" button stops mid-batch. |
| Training data export fills disk | Default scale 1.0 (original). Show estimated file size. Require explicit directory selection. |
| Settings propagation race condition | `DetectionSettings` is immutable data class — snapshot at process start, never mutated during processing. |
| Memory pressure from batch processing | Process one image at a time, release `BufferedImage` references after detection. |
| CV refinement produces worse results | `cvRefineEnabled` defaults to `true` but user can disable. Always compare before/after corner positions. Original visibility tracked separately for adaptive margin. |
| Fiducial model not available | `fiducialEnabled` defaults to `false`. Show "Install fiducial model" in Advanced settings. |
| Dual ONNX output format | `OnnxPostprocessing` handles both legacy `[1,5,N]` and NMS `[1,N,6]` formats automatically. |

---

## Appendix A: Mapping Between Python and Kotlin Parameters

| Python (photocrop.py) | Kotlin (DetectionSettings) | Default |
|------------------------|----------------------------|---------|
| `--detection-model` | `ModelResourcePort.loadDetectionModel()` | bundled |
| `--pose-model` | `ModelResourcePort.loadPoseModel()` | bundled |
| `--preset` | `DetectionSettings.presetName` + factories | "default" |
| `--det-conf` | `detectionConfidence` | 0.5 |
| `--pose-conf` | `poseConfidence` | 0.5 |
| `--iou` | `detectionIou` / `poseIou` | 0.45 |
| `--imgsz` | `detectionImageSize` | 640 |
| `--pose-crop-expand` | `poseCropExpand` | 0.15 |
| `--pose-refine` | `poseRefineEnabled` | false |
| `--pose-refine-expand` | `poseRefineExpand` | 0.05 |
| `--pose-sweep` | `poseSweepEnabled` | false |
| `--sweep-crop-expands` | `poseSweepCropExpands` | [0.05, 0.10, 0.15, 0.20] |
| `--sweep-refine-expands` | `poseSweepRefineExpands` | [0.03, 0.05, 0.10, 0.15] |
| `--pose-sweep-xy` | `poseSweepXyEnabled` | false |
| `--sweep-xy-expands` | `poseSweepXyExpands` | [0.05, 0.10, 0.15, 0.20, 0.25] |
| `--center-bias` | `centerBiasEnabled` | false |
| `--cv-refine` | `cvRefineEnabled` | true |
| `--cv-refine-radius` | `cvRefineRadius` | 40 |
| `--auto-refine` | `autoRefineEnabled` | false |
| `--dedup-dist` | `dedupDistanceThreshold` | 0.12 |
| `--crop simple` | `CropMode.SIMPLE` | — |
| `--crop simple-corners` | `CropMode.SIMPLE_CORNERS` | — |
| `--crop warp` | `CropMode.WARP` | — |
| `--crop warp-stretch` | `CropMode.WARP_STRETCH` | — |
| `--crop-margin` | `cropMargin` | 0 |
| `--crop-transparent` | `cropTransparent` | false |
| `--border-fill` | `borderFill` | "grey" |
| `--adaptive-margin` | `adaptiveMarginEnabled` | false |
| `--adaptive-margin-thresh` | `adaptiveMarginThreshold` | 0.5 |
| `--adaptive-margin-max` | `adaptiveMarginMax` | 0.03 |
| `--warp-fallback-thresh` | `warpFallbackThreshold` | 0.3 |
| `--fiducial-model` | `fiducialModelPath` | "" |
| `--fiducial-iterations` | `fiducialIterations` | 2 |
| `--fiducial-conf` | `fiducialConfidence` | 0.5 |
| (N/A — CV-only params) | `cvGamma` | 1.4 |
| (N/A — CV-only params) | `cvAdaptiveBlockSize` | 31 |
| (N/A — CV-only params) | `cvAdaptiveC` | 10 |
| (N/A — internal) | `cvRefineOrientationAware` | true |
| (N/A — internal) | `cvRefineNeighborAnchored` | true |
| (N/A — internal) | `cvRefineStripSearch` | true |
| (N/A — internal) | `cvRefineTwoPass` | true |
| (N/A — internal) | `cvRefineVisThreshold` | 0.7 |
| (N/A — internal) | `dedupVisThreshForCenter` | 0.25 |
| (N/A — internal) | `dedupLowVisPenalty` | 0.5 |
| (N/A — internal) | `cropLimitsMaxIntrusion` | 0.15 |

---

## Appendix B: YOLO Label Format Specification

Training data export uses the same 17-column format that `data_generator/generate_pose.py` produces:

```
class_id cx cy w h kp0x kp0y kp0v kp1x kp1y kp1v kp2x kp2y kp2v kp3x kp3y kp3v
```

| Column | Description | Range |
|--------|-------------|-------|
| `class_id` | Single class: "photo" | Always `0` |
| `cx` | Bounding box center X | [0, 1] normalized |
| `cy` | Bounding box center Y | [0, 1] normalized |
| `w` | Bounding box width | [0, 1] normalized |
| `h` | Bounding box height | [0, 1] normalized |
| `kp0x/kp0y/kp0v` | LL (Lower-Left) = screen bottom-left | [0,1], [0,1], {0,2} |
| `kp1x/kp1y/kp1v` | UL (Upper-Left) = screen top-left | [0,1], [0,1], {0,2} |
| `kp2x/kp2y/kp2v` | UR (Upper-Right) = screen top-right | [0,1], [0,1], {0,2} |
| `kp3x/kp3y/kp3v` | LR (Lower-Right) = screen bottom-right | [0,1], [0,1], {0,2} |

**Visibility values:** `0` = not visible/not in image, `2` = visible (always 2 for user-confirmed corners)

**Coordinate system:** All coordinates are normalized to [0, 1] relative to the exported image dimensions. Origin is top-left. X increases rightward. Y increases downward.

**Keypoint order mapping between Kotlin and Python:**
| Kotlin DetectedPhoto | YOLO keypoint | Screen coordinate |
|---|---|---|
| `topLeft` | `kp1` (UL) | top-left |
| `topRight` | `kp2` (UR) | top-right |
| `bottomRight` | `kp3` (LR) | bottom-right |
| `bottomLeft` | `kp0` (LL) | bottom-left |

---

## Appendix C: Directory Structure After Implementation

```
src/main/kotlin/org/kryspetrie/fileimport/
├── domain/
│   ├── model/
│   │   ├── CorrectionMode.kt              (NEW)
│   │   ├── CropMode.kt                    (NEW)
│   │   ├── DetectedBoundingBox.kt          (NEW)
│   │   ├── DetectionMode.kt                (NEW)
│   │   ├── DetectionSettings.kt            (NEW)
│   │   ├── PhotoScanModels.kt              (MODIFIED - add fields, YOLO mapping, visibility, cropMode)
│   │   ├── PhotoScanProfile.kt            (MODIFIED - add detectionSettings, exportMode)
│   │   └── ...
│   └── port/
│       ├── CvRefinementPort.kt             (NEW)
│       ├── DetectionDeduplicationPort.kt   (NEW)
│       ├── ModelResourcePort.kt            (EXISTING)
│       ├── OnnxDetectionPort.kt            (NEW)
│       ├── OnnxFiducialPort.kt             (NEW)
│       ├── OnnxPosePort.kt                (NEW)
│       ├── PhotoScanPort.kt               (MODIFIED - add settings parameter)
│       ├── TrainingDataPort.kt            (NEW)
│       └── ...
├── application/
│   ├── DetectionOrchestrator.kt            (NEW)
│   ├── TrainingDataExportService.kt        (NEW)
│   ├── PhotoScanExportService.kt          (MODIFIED - add crop modes, adaptive margin, training export)
│   ├── PerspectiveCorrectionService.kt     (MODIFIED - add CROP_AND_ROTATE, inward/outward warp)
│   └── ...
├── infrastructure/
│   ├── photoscan/
│   │   ├── OnnxDetectionAdapter.kt         (NEW)
│   │   ├── OnnxPoseAdapter.kt             (NEW)
│   │   ├── OnnxFiducialAdapter.kt          (NEW)
│   │   ├── OnnxPreprocessing.kt           (NEW)
│   │   ├── OnnxPostprocessing.kt          (NEW)
│   │   ├── SobelEdgeRefinementAdapter.kt  (NEW)
│   │   ├── DistanceDeduplicationAdapter.kt (NEW)
│   │   ├── HybridCornerDetector.kt         (MODIFIED - accept DetectionSettings)
│   │   ├── RectangleDetector.kt            (MODIFIED - accept DetectionSettings)
│   │   ├── PhotoScanDetectorService.kt     (MODIFIED - delegate to DetectionOrchestrator)
│   │   └── dead/                           (EXISTING - no changes)
│   ├── training/
│   │   ├── FilesystemTrainingDataAdapter.kt (NEW)
│   │   └── YoloLabelWriter.kt              (NEW)
│   ├── wizard/
│   │   ├── PhotoScanWizardState.kt         (MODIFIED - add DetectionSettings, visibility, detectedPhotos)
│   │   └── ...
│   └── adapter/
│       ├── ClasspathModelResourceAdapter.kt (MODIFIED - support filesystem paths, fiducial model)
│       └── ...
├── di/
│   └── AppModule.kt                        (MODIFIED - register all new components)
└── ui/
    └── screens/
        └── wizard/
            ├── DetectionSettingsPanel.kt    (NEW)
            ├── PhotoScanImportScreen.kt     (MODIFIED - add Detection subsection)
            ├── OverviewScreen.kt           (MODIFIED - add re-detect, per-image settings, visibility badges)
            ├── RefinementScreen.kt         (MODIFIED - add CV/fiducial refine, visibility display)
            ├── SummaryScreen.kt            (MODIFIED - add training data toggle, crop mode per-photo)
            └── WizardContainer.kt           (MODIFIED - propagate settings, re-detection)

src/main/resources/
└── models/
    ├── detection_model.onnx                 (BUNDLED)
    ├── pose_model.onnx                      (BUNDLED)
    └── fiducial_model.onnx                  (OPTIONAL - configurable path)

src/test/kotlin/.../ (new test files for all new components)
```

---

## Appendix D: Visibility Threshold Hierarchy

photocrop.py uses 6 different visibility thresholds for different purposes. The Kotlin implementation must track these consistently:

| Threshold | Value | Purpose | Kotlin Setting |
|-----------|-------|---------|---------------|
| `_VIS_THRESH_DEDUP` | 0.25 | Minimum visibility to include a keypoint in center computation for deduplication | `dedupVisThreshForCenter` |
| `_VIS_THRESH_SWEEP` | 0.30 | Minimum visibility for sweep scoring (count as "visible") | (internal to sweep logic) |
| `_VIS_THRESH_FALLBACK` | 0.30 | Below this, fall back from warp to simple crop | `warpFallbackThreshold` |
| `_VIS_THRESH_AUTO_REFINE` | 0.30 | Corners below this trigger auto-refine (auto-refine applies when <3 corners ≥ this) | (internal to auto-refine logic) |
| `_NEIGHBOR_VIS_THRESHOLD` | 0.50 | Minimum visibility for a neighbor to be used in CV refinement projection | (internal to CV refinement) |
| `cv_refine_vis_threshold` | 0.70 | Below this, a corner gets CV refinement | `cvRefineVisThreshold` |

**Tracking original vs. boosted visibility:**

CV refinement boosts refined corners' visibility to 0.95. But for adaptive margin and warp fallback, we need the **original** NN visibility (before boosting). `DetectedPhoto` tracks both:
- `cornerVisibility`: current visibility (may be boosted by CV refinement)
- `originalVisibility`: visibility from ONNX pose model (before any boosting)

---

## Appendix E: photocrop.py Pipeline Reference

This appendix documents the exact pipeline order in `photocrop.py` for cross-reference during implementation:

```
1. Load image
2. Run detection model → bounding boxes with confidence
3. Compute crop limits (prevent expansion into adjacent detections)
4. For each detection box:
   a. Expand box by poseCropExpand (symmetric or center-biased)
   b. Crop sub-image
   c. Run pose model → keypoints + visibility
   d. Map keypoints back to original coordinates
   e. Compute dedup_priority = confidence × (0.5 if <3 visible else 1.0)
5. [Optional] Refine pass: tighter crop from keypoints → re-run pose
6. [Optional] Sweep: grid search over expand/refine values
7. Deduplicate: greedy by priority, min center distance
8. [Optional] CV refinement: two-pass Sobel edge + line intersection
   - Only corners with vis < cv_refine_vis_threshold (0.7)
   - Pass 1: use high-vis neighbors for projection
   - Pass 2: use pass-1 refined corners as new neighbors
9. [Optional] Auto-refine: only photos with <3 visible corners
10. [Optional] Fiducial refinement: iterative ONNX model per corner
11. Export: simple / simple-corners / warp / warp-stretch
    - Adaptive margin: extra margin for low-vis corners
    - Border fill: grey / white / custom RGB
    - Transparent: PNG with alpha mask
    - Warp fallback: if any original visibility < 0.3 → simple crop instead
```

---

*Document maintained by: Development Team*  
*Last Updated: 2026-05-10*  
*Version: 2.0*
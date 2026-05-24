**Project:** Multi-Photo Extraction and Perspective Correction Pipeline
**Target:** Kotlin Multiplatform (KMP) - Android, iOS, Desktop (JVM)

> ⚠️ **NOTE:** The "Traditional Computer Vision ONLY" constraint described below has been superseded by the ML-based detection approach detailed in [ML_DETECTION_INTEGRATION_PLAN.md](./ML_DETECTION_INTEGRATION_PLAN.md). The application now uses ONNX YOLO models (detection + pose + optional fiducial) alongside CV refinement, not pure traditional CV. The CLAHE/bilateral/Canny/contour pipeline described here is still relevant as the fallback COMPUTER_VISION detection mode, but the primary detection path is ML-based. Refer to the new plan for the current architecture.
>
> This document is retained as a reference for the CV-only fallback path and algorithm details.

**Core Constraint (Historical):** Traditional Computer Vision ONLY (No ML), tolerant of uneven lighting, gradient backgrounds, and blending edges.

---

## 1. Architecture & Cross-Platform Strategy

To achieve a fully cross-platform Kotlin application without relying on ML, the architecture will leverage **Kotlin Multiplatform (KMP)** utilizing **OpenCV** as the underlying computer vision engine.

### 1.1 Dependency Management
Since OpenCV does not have an official unified KMP artifact, the project will use the `expect`/`actual` paradigm to bridge the C++ OpenCV implementations across platforms:
* **Common (`commonMain`):** Defines the `PhotoDetector` interface, data models (`Point2D`, `Quadrilateral`), and the orchestration logic.
* **JVM/Desktop (`jvmMain`):** Binds to the official `opencv-java` artifact (JNI wrappers).
* **Android (`androidMain`):** Utilizes the OpenCV Android SDK.
* **iOS (`iosMain`):** Uses Kotlin/Native `cinterop` to bind to the OpenCV iOS Framework.

### 1.2 CI/CD Integration
Given the complexity of compiling/linking native C++ libraries across multiple targets, your GitHub Actions pipeline should utilize a matrix build. Ensure runners for Intel and Apple Silicon Macs are configured correctly, particularly for the `iosX64` and `iosArm64` targets, to avoid architecture mismatch errors during the cinterop generation.

---

## 2. The Traditional CV Pipeline (Algorithm)

Since the background is a fairly uniform color but suffers from uneven lighting (gradients) and the photos may be blurry or blend into the background, standard global thresholding will fail. The pipeline requires localized contrast enhancement and robust edge linking.

### Step 2.1: Grayscale & Contrast Equalization
1.  **Grayscale Conversion:** Convert the RGB image to Grayscale.
2.  **CLAHE (Contrast Limited Adaptive Histogram Equalization):** Apply CLAHE. This is critical for uneven lighting. It divides the image into small tiles and equalizes the histogram locally, which will sharply define the borders of the photos even if they sit in a dark gradient patch of the background.
3.  **Blurring:** Apply a **Bilateral Filter** rather than a Gaussian Blur. Bilateral filtering reduces noise (handling the slight blur constraint) while preserving sharp edges, which is essential for contour detection.

### Step 2.2: Dynamic Background Masking
Because the borders of the photos might blend with the background:
1.  **Background Sampling:** Since the photos always leave a border of the background visible, sample the pixels along the outermost perimeter (top, bottom, left, right edges) of the full image.
2.  **Color Range Thresholding:** Calculate the mean and variance of the background color from the samples. Create a dynamic threshold range (e.g., Mean $\pm$ Tolerance).
3.  **Mask Generation:** Create a binary mask where pixels falling within the background color range are black, and all other pixels are white.
4.  **Morphological Closing:** Apply a morphological `CLOSE` operation (Dilation followed by Erosion) to the binary mask. This will bridge the gaps where the photo blended into the background, ensuring the shape remains a cohesive polygon.

### Step 2.3: Edge & Contour Detection
1.  **Canny Edge Detection:** Apply the Canny algorithm to the morphologically closed mask (not the original image).
2.  **Find Contours:** Use `findContours` with the `RETR_EXTERNAL` flag (since we only care about the outermost boundaries of the photos, not subjects *within* the photos) and `CHAIN_APPROX_SIMPLE` to compress horizontal, vertical, and diagonal segments.

### Step 2.4: Polygon Approximation (Quadrilateral Extraction)
Iterate through the detected contours:
1.  **Area Filtering:** Discard contours with an area smaller than a predefined threshold (e.g., < 5% of total image area) to ignore dust or noise.
2.  **Approximation:** Use `approxPolyDP` to approximate the contour shape. The $\epsilon$ parameter should be dynamically scaled based on the contour's arc length (e.g., `0.02 * arcLength`).
3.  **Vertex Count:** If the approximated polygon has exactly 4 vertices, it is flagged as a candidate.
4.  **Fallback Convex Hull:** If `approxPolyDP` yields e.g., 5-6 points due to a rounded or slightly damaged physical photo corner, compute the `convexHull` of the contour, and re-run the approximation to force a 4-point quadrilateral.

---

## 3. Perspective Correction (3D Projection)

Because the camera angle introduces 3D perspective distortion (non-rectangular quadrilaterals), a simple affine transformation (cropping/rotating) is insufficient. We must compute a perspective transform matrix to map the skewed quadrilateral to a perfect 2D rectangle.

### Step 3.1: Point Ordering
The 4 points extracted must be consistently ordered: Top-Left (TL), Top-Right (TR), Bottom-Right (BR), Bottom-Left (BL).
* Sum of $x, y$: Smallest is TL, largest is BR.
* Difference of $y - x$: Smallest is TR, largest is BL.

### Step 3.2: Dimensional Calculation
Calculate the width and height of the corrected image using the Euclidean distance formula to ensure the aspect ratio is preserved without stretching.

* Width: $max(\sqrt{(BR_x - BL_x)^2 + (BR_y - BL_y)^2}, \sqrt{(TR_x - TL_x)^2 + (TR_y - TL_y)^2})$
* Height: $max(\sqrt{(TR_x - BR_x)^2 + (TR_y - BR_y)^2}, \sqrt{(TL_x - BL_x)^2 + (TL_y - BL_y)^2})$

### Step 3.3: The Warp
1.  Define the destination points: `(0,0)`, `(Width-1, 0)`, `(Width-1, Height-1)`, `(0, Height-1)`.
2.  Calculate the $3 \times 3$ transformation matrix $M$ using OpenCV's `getPerspectiveTransform(srcPoints, dstPoints)`.
3.  Apply `warpPerspective(originalImage, M)` to extract and flatten the photo.

*(Note: Since rotation is guaranteed to be < 30 degrees, the initial ordering logic will not fail. Point ordering typically only breaks when an image is rotated > 45 degrees, causing corners to swap logical quadrants).*

---

## 4. Edge Cases & Mitigation

| Edge Case | Proposed Mitigation |
| :--- | :--- |
| **Photo blends entirely into the background (e.g., dark photo on dark background)** | The `CLOSE` morphological operation in Step 2.2 combines with the perimeter sampling. If a photo touches the edge of the frame, the `RETR_EXTERNAL` contour will follow the frame boundary. Enforce a rule requiring a minimum background border to prevent this. |
| **Slightly blurry photos** | The Bilateral filter smooths gradients without destroying the high-frequency data of the photo edges. Edge detection runs on the high-contrast mask, not the raw blurry photo. |
| **Shadows cast by the photos** | Shadows often mimic quadrilaterals. Adaptive thresholding and color distance checks (Step 2.2) will separate the physical object (photo) from a shadow (which maintains the background hue but lowers luminance). |

---

## 5. Output and I/O

1.  **Concurrency:** Wrap the pipeline in Kotlin Coroutines (`Dispatchers.Default` for CPU-bound OpenCV operations) so the UI thread remains unblocked during scanning.
2.  **File Writing:** Use `expect`/`actual` or a KMP file system library like `okio` to define the output directory paths.
3.  **Format:** Write the cropped `Mat` matrices out as high-quality JPEGs or lossless PNGs to the target file system using `Imgcodecs.imwrite`.

---

**Title:** Kotlin Multiplatform & OpenCV Build Architecture
**Purpose:** Define the Gradle structure, dependency bridging, and memory management for C++ interoperability across JVM, Android, and iOS.

## 1. Project Structure
The project utilizes Kotlin Multiplatform (KMP). Because OpenCV lacks an official unified KMP library, we must build an abstraction layer using the `expect`/`actual` mechanism.

* `core-cv/src/commonMain`: Interfaces, data classes, and the pipeline orchestrator.
* `core-cv/src/jvmMain`: Binds to `org.openpnp:opencv` (Java JNI wrappers for Desktop).
* `core-cv/src/androidMain`: Binds to the OpenCV Android SDK AAR.
* `core-cv/src/iosMain`: Uses Kotlin/Native `cinterop` to bind to the `opencv2.framework` (C++ Objective-C wrapper).

## 2. Gradle Configuration (build.gradle.kts)
This demonstrates configuring the KMP targets and handling the iOS cinterop.

```kotlin
plugins {
    kotlin("multiplatform")
    id("com.android.library")
}

kotlin {
    androidTarget {
        compilations.all {
            kotlinOptions.jvmTarget = "17"
        }
    }
    
    jvm("desktop") {
        compilations.all {
            kotlinOptions.jvmTarget = "17"
        }
    }
    
    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.compilations.getByName("main") {
            val opencv by cinterops.creating {
                // Defines the Objective-C bridging header for OpenCV
                defFile(project.file("src/nativeInterop/cinterop/opencv.def"))
                compilerOpts("-framework", "opencv2", "-F${project.projectDir}/OpenCV")
            }
        }
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0")
            }
        }
        val jvmMain by getting {
            dependencies {
                // Desktop JNI bindings
                implementation("org.openpnp:opencv:4.7.0-0") 
            }
        }
        val androidMain by getting {
            dependencies {
                // Assuming OpenCV AAR is included as a local module
                implementation(project(":opencv"))
            }
        }
    }
}
```

---

**Title:** Localized Thresholding & Contour Extraction Algorithm
**Purpose:** Detail the step-by-step image processing pipeline to find quadrilateral candidates in uneven lighting.

## 1. Algorithm Rationale
Global thresholding (like Otsu's method) fails on uneven gradients. If the top left is bright and the bottom right is dark, a single color threshold will destroy the photo boundaries. We solve this using CLAHE (Contrast Limited Adaptive Histogram Equalization) followed by Bilateral Filtering.

## 2. Pipeline Execution Steps

### Step 1: Grayscale & CLAHE
Convert to grayscale to reduce computational load. Apply CLAHE to equalize lighting locally.
* **CLAHE Clip Limit:** 2.0 (Prevents over-amplifying noise).
* **Grid Size:** 8x8 (Standard for breaking an image into local contrast zones).

### Step 2: Bilateral Filtering
Instead of a Gaussian blur which destroys edges, Bilateral filtering replaces the intensity of each pixel with a weighted average of intensity values from nearby pixels. Crucially, the weights depend not only on Euclidean distance but also on radiometric differences (color intensity). This preserves sharp photo edges while blurring the gradient background.
* **Diameter (d):** 9
* **Sigma Color:** 75 (Allows pixels of varying colors in the background gradient to mix).
* **Sigma Space:** 75

### Step 3: Edge Detection & Morphological Closing
Run Canny edge detection. Because edges might break where the photo blends with the background, apply a Morphological CLOSE operation. A CLOSE is a Dilation followed by an Erosion. It fills in small holes and connects broken edge lines.

### 4. Code Implementation (Platform-Agnostic Concepts mapped to JVM)

```kotlin
import org.opencv.core.*
import org.opencv.imgproc.Imgproc

class PhotoDetector {

    fun detectQuadrilaterals(source: Mat): List<MatOfPoint2f> {
        val gray = Mat()
        Imgproc.cvtColor(source, gray, Imgproc.COLOR_BGR2GRAY)

        // 1. CLAHE
        val clahe = Imgproc.createCLAHE(2.0, Size(8.0, 8.0))
        val equalized = Mat()
        clahe.apply(gray, equalized)

        // 2. Bilateral Filter
        val blurred = Mat()
        Imgproc.bilateralFilter(equalized, blurred, 9, 75.0, 75.0)

        // 3. Canny Edge
        val edges = Mat()
        Imgproc.Canny(blurred, edges, 50.0, 150.0)

        // 4. Morphological Close
        val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(5.0, 5.0))
        val closedEdges = Mat()
        Imgproc.morphologyEx(edges, closedEdges, Imgproc.MORPH_CLOSE, kernel)

        // 5. Find Contours
        val contours = mutableListOf<MatOfPoint>()
        val hierarchy = Mat()
        Imgproc.findContours(closedEdges, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)

        val validQuads = mutableListOf<MatOfPoint2f>()
        val minArea = source.rows() * source.cols() * 0.05 // 5% of image area

        for (contour in contours) {
            val area = Imgproc.contourArea(contour)
            if (area < minArea) continue

            val contour2f = MatOfPoint2f(*contour.toArray())
            val perimeter = Imgproc.arcLength(contour2f, true)
            val approx = MatOfPoint2f()
            
            // Dynamic epsilon based on perimeter length
            Imgproc.approxPolyDP(contour2f, approx, 0.02 * perimeter, true)

            if (approx.total() == 4L && Imgproc.isContourConvex(MatOfPoint(*approx.toArray()))) {
                validQuads.add(approx)
            }
            
            contour2f.release()
            approx.release()
        }

        // Cleanup Native Memory
        gray.release()
        equalized.release()
        blurred.release()
        edges.release()
        closedEdges.release()
        hierarchy.release()
        contours.forEach { it.release() }

        return validQuads
    }
}
```

---

# 03_PERSPECTIVE_CORRECTION_MATH.md
**Title:** 3D Perspective Projection and Image Warping
**Purpose:** Define the mathematical models and logic required to map an angled quadrilateral into a flattened, rectangular coordinate space.

## 1. Vertex Ordering Math
OpenCV requires points to be mapped in a predictable order. We enforce the order: Top-Left (TL), Top-Right (TR), Bottom-Right (BR), Bottom-Left (BL).
Given 4 points $(x,y)$:
* **TL:** Point with the minimum sum of $x + y$.
* **BR:** Point with the maximum sum of $x + y$.
* **TR:** Point with the minimum difference of $y - x$.
* **BL:** Point with the maximum difference of $y - x$.

## 2. Aspect Ratio and Dimensions
To prevent stretching, we calculate the maximum Euclidean distance between the top/bottom and left/right pairs. The Euclidean distance formula is:

$$d = \sqrt{(x_2 - x_1)^2 + (y_2 - y_1)^2}$$

* **Calculated Width:** $\max(\text{distance}(BR, BL), \text{distance}(TR, TL))$
* **Calculated Height:** $\max(\text{distance}(TR, BR), \text{distance}(TL, BL))$

## 3. The Perspective Transform Matrix
To warp the image, we must compute a $3 \times 3$ perspective transformation matrix $M$. This matrix maps the coordinates of the source quadrilateral $[x, y, 1]^T$ to the coordinates of the destination rectangle $[x', y', w']^T$.

$$\begin{bmatrix} x' \\ y' \\ w' \end{bmatrix} = \begin{bmatrix} m_{11} & m_{12} & m_{13} \\ m_{21} & m_{22} & m_{23} \\ m_{31} & m_{32} & m_{33} \end{bmatrix} \begin{bmatrix} x \\ y \\ 1 \end{bmatrix}$$

OpenCV's `getPerspectiveTransform` solves this system of linear equations automatically when provided the 4 ordered source points and 4 destination points.

## 4. Kotlin Implementation

```kotlin
import org.opencv.core.*
import org.opencv.imgproc.Imgproc
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sqrt

fun extractAndWarp(sourceImage: Mat, corners: MatOfPoint2f): Mat {
    val points = corners.toArray().toList()
    
    // 1. Order the points
    val sum = points.sortedBy { it.x + it.y }
    val diff = points.sortedBy { it.y - it.x }
    
    val tl = sum.first()
    val br = sum.last()
    val tr = diff.first()
    val bl = diff.last()

    // 2. Calculate Width and Height
    val widthA = sqrt((br.x - bl.x).pow(2) + (br.y - bl.y).pow(2))
    val widthB = sqrt((tr.x - tl.x).pow(2) + (tr.y - tl.y).pow(2))
    val maxWidth = max(widthA, widthB).toInt()

    val heightA = sqrt((tr.x - br.x).pow(2) + (tr.y - br.y).pow(2))
    val heightB = sqrt((tl.x - bl.x).pow(2) + (tl.y - bl.y).pow(2))
    val maxHeight = max(heightA, heightB).toInt()

    // 3. Define Source and Destination Matrices
    val srcMat = MatOfPoint2f(tl, tr, br, bl)
    val dstMat = MatOfPoint2f(
        Point(0.0, 0.0),
        Point(maxWidth - 1.0, 0.0),
        Point(maxWidth - 1.0, maxHeight - 1.0),
        Point(0.0, maxHeight - 1.0)
    )

    // 4. Calculate Transformation Matrix and Warp
    val transformMatrix = Imgproc.getPerspectiveTransform(srcMat, dstMat)
    val warpedImage = Mat()
    Imgproc.warpPerspective(
        sourceImage, 
        warpedImage, 
        transformMatrix, 
        Size(maxWidth.toDouble(), maxHeight.toDouble())
    )

    // Memory cleanup
    srcMat.release()
    dstMat.release()
    transformMatrix.release()

    return warpedImage
}
```

---

**Title:** Quality Assurance, Pipeline Tuning, and Fallback Strategies
**Purpose:** Establish a robust methodology for verifying computer vision accuracy without ML, and provide architectural alternatives if the primary pipeline degrades.

## 1. Testing Strategy: Golden Master (Visual Regression)
Because traditional CV relies on hardcoded thresholds (e.g., Canny 50/150, Minimum Area 5%), you cannot use standard unit tests. You must use a **Golden Master** dataset.

### Step 1: Create the Ground Truth Dataset
1.  Take 50 sample photos with your intended camera. Ensure variations in background color, lighting gradients, photo blur, and rotation (< 30 degrees).
2.  Manually crop these images in a photo editor and save them as the "Golden Source of Truth".

### Step 2: Implement the Regression Test Suite
Create a JVM-based test harness that loops through the 50 raw images.
1.  Run the pipeline to output the cropped quadrilaterals.
2.  Compare the algorithm's output coordinates against the manual crop bounding boxes using Intersection over Union (IoU).
3.  **Success Metric:** An IoU score of > 0.90 is considered a successful detection.

## 2. Iteration and Parameter Tuning
If tests fail on specific gradient backgrounds, iterate on these specific variables:
* **Bilateral Filter `sigmaColor`:** Increase this (up to 150) if the background gradient is highly irregular. This tells the algorithm that wider color variances still belong to the same "flat" background surface.
* **Contour Approximation `epsilon`:** The multiplier `0.02 * perimeter`. If corners are being rounded off, decrease to `0.01`. If jagged edges are creating 5-sided polygons, increase to `0.03` or implement a Convex Hull fallback.

## 3. Alternate Plan A: Hough Line Transform
If the morphological closing and contour detection completely fail because the photos blend perfectly into the background gradient, abandon `findContours`.

* **Approach:** After Canny Edge detection, run a Probabilistic Hough Line Transform (`Imgproc.HoughLinesP`).
* **Logic:** This detects straight mathematical lines rather than closed loops.
* **Execution:** Find all horizontal-ish and vertical-ish lines. Calculate the mathematical intersections of these lines. The four outermost intersection points represent the corners of the photo. This approach is highly resistant to broken edges but mathematically heavier.

## 4. Alternate Plan B: Adaptive Thresholding Fallback
If CLAHE + Bilateral filtering is too slow for real-time mobile processing (particularly on older iOS devices running through cinterop):
* **Approach:** Swap Steps 1 & 2 in the pipeline for `Imgproc.adaptiveThreshold`.
* **Execution:** Use `ADAPTIVE_THRESH_GAUSSIAN_C`. This calculates the threshold for a pixel based on a small surrounding block (e.g., 11x11 pixels). It is vastly faster than Bilateral filtering and naturally ignores gradual lighting changes, though it is slightly more susceptible to noise/blur.

---

**Title:** Advanced Synthetic Ground Truth Generator
**Purpose:** Generate highly realistic test datasets for computer vision validation by simulating uneven gradients, color noise, drop shadows, anti-aliased (blurry) photo edges, and fetching real image textures from the web.

## 1. Architectural Upgrades

To push the traditional CV pipeline to its limits, the test data must simulate real-world physical imperfections:
1.  **Variable Gradients & Color Noise:** Cameras introduce sensor noise (grain), especially in uneven lighting. We simulate this by applying a linear color gradient and adding a Gaussian noise matrix over the background.
2.  **Live Photo Textures:** Instead of blank white polygons, the generator dynamically downloads images from `placecats.com`. This tests if the Canny edge detector gets confused by high-contrast subjects *inside* the photo.
3.  **Perspective Warping:** The downloaded image is warped into the randomized quadrilateral using `getPerspectiveTransform` to simulate an angled photograph.
4.  **Drop Shadows:** A physical photo sits slightly above the surface. We simulate this by generating a darkened, heavily blurred polygon offset along the Y-axis, alpha-blended into the background.
5.  **Blurry Edges:** To prevent perfectly aliased digital edges (which are too easy to detect), the photo mask is blurred before compositing, mimicking depth-of-field blur.

## 2. The Mathematics of Alpha Compositing

OpenCV's standard drawing tools (`fillPoly`, `copyTo`) create hard pixel edges. To create smooth drop shadows and blurry edges, we must use **Alpha Compositing**.

Given a Foreground image ($F$), a Background image ($B$), and an Alpha Mask ($\alpha$) where values range from $0.0$ to $1.0$:

$$C_{out} = (\alpha \times F) + ((1 - \alpha) \times B)$$

To implement this efficiently in OpenCV, we convert the matrices to 32-bit floats (`CV_32FC3`), perform the scalar multiplication, and convert back to 8-bit integers (`CV_8UC3`).

## 3. Kotlin Implementation

Ensure your project has internet permissions to fetch the images. You can place this file in your `src/test/kotlin` directory.

```kotlin
import org.opencv.core.*
import org.opencv.imgcodecs.Imgcodecs
import org.opencv.imgproc.Imgproc
import java.io.File
import java.net.URL
import kotlin.random.Random

object AdvancedSyntheticGenerator {

    fun generateTestImages(outputDirectory: String, count: Int = 5) {
        val dir = File(outputDirectory)
        if (!dir.exists()) dir.mkdirs()

        for (i in 1..count) {
            val width = 1920
            val height = 1080
            val bgImage = Mat(height, width, CvType.CV_8UC3)

            // 1. Generate Gradient Background
            generateGradientBackground(bgImage, width, height)

            // 2. Add Color Noise (Sensor Grain)
            val noise = Mat(height, width, CvType.CV_16SC3)
            Core.randn(noise, 0.0, 15.0) // Mean 0, Standard Deviation 15
            val bgWithNoise = Mat()
            bgImage.convertTo(bgWithNoise, CvType.CV_16SC3)
            Core.add(bgWithNoise, noise, bgWithNoise)
            bgWithNoise.convertTo(bgImage, CvType.CV_8UC3)

            // 3. Define Random Quadrilateral
            val padding = 250.0
            val pt1 = Point(padding + Random.nextDouble(-80.0, 80.0), padding + Random.nextDouble(-80.0, 80.0)) // TL
            val pt2 = Point(width - padding + Random.nextDouble(-80.0, 80.0), padding + Random.nextDouble(-80.0, 80.0)) // TR
            val pt3 = Point(width - padding + Random.nextDouble(-80.0, 80.0), height - padding + Random.nextDouble(-80.0, 80.0)) // BR
            val pt4 = Point(padding + Random.nextDouble(-80.0, 80.0), height - padding + Random.nextDouble(-80.0, 80.0)) // BL
            val quadCorners = MatOfPoint2f(pt1, pt2, pt3, pt4)
            val quadCornersInt = MatOfPoint(pt1, pt2, pt3, pt4)

            // 4. Generate Drop Shadow
            applyDropShadow(bgImage, quadCornersInt, width, height)

            // 5. Fetch and Warp the Cat Photo
            val catImage = fetchCatImage()
            val catWarped = Mat.zeros(Size(width.toDouble(), height.toDouble()), CvType.CV_8UC3)
            
            val catCorners = MatOfPoint2f(
                Point(0.0, 0.0),
                Point(catImage.cols().toDouble(), 0.0),
                Point(catImage.cols().toDouble(), catImage.rows().toDouble()),
                Point(0.0, catImage.rows().toDouble())
            )
            
            val perspectiveTransform = Imgproc.getPerspectiveTransform(catCorners, quadCorners)
            Imgproc.warpPerspective(catImage, catWarped, perspectiveTransform, catWarped.size())

            // 6. Create Blurry Edge Mask and Composite
            compositePhotoWithBlurryEdges(bgImage, catWarped, quadCornersInt, width, height)

            // 7. Save Artifacts
            val imgPath = "$outputDirectory/cat_test_$i.jpg"
            Imgcodecs.imwrite(imgPath, bgImage)
            saveTruthJson("$outputDirectory/cat_test_$i.json", i, listOf(pt1, pt2, pt3, pt4))

            // Memory Cleanup
            bgImage.release()
            noise.release()
            bgWithNoise.release()
            quadCorners.release()
            quadCornersInt.release()
            catImage.release()
            catWarped.release()
            catCorners.release()
            perspectiveTransform.release()
        }
    }

    private fun generateGradientBackground(image: Mat, width: Int, height: Int) {
        val r1 = Random.nextDouble(20.0, 80.0)
        val g1 = Random.nextDouble(20.0, 80.0)
        val b1 = Random.nextDouble(50.0, 150.0)
        
        val r2 = Random.nextDouble(100.0, 200.0)
        val g2 = Random.nextDouble(100.0, 200.0)
        val b2 = Random.nextDouble(150.0, 255.0)

        for (y in 0 until height) {
            val ratio = y.toDouble() / height
            val r = r1 * (1 - ratio) + r2 * ratio
            val g = g1 * (1 - ratio) + g2 * ratio
            val b = b1 * (1 - ratio) + b2 * ratio
            Imgproc.line(image, Point(0.0, y.toDouble()), Point(width.toDouble(), y.toDouble()), Scalar(b, g, r), 1) // BGR order
        }
    }

    private fun applyDropShadow(bgImage: Mat, quadCorners: MatOfPoint, width: Int, height: Int) {
        val shadowLayer = Mat.zeros(Size(width.toDouble(), height.toDouble()), CvType.CV_8UC3)
        
        // Offset shadow points slightly down and right
        val offsetPoints = quadCorners.toArray().map { Point(it.x + 25.0, it.y + 35.0) }
        val offsetMat = MatOfPoint(*offsetPoints.toTypedArray())
        
        // Draw dark gray polygon
        Imgproc.fillPoly(shadowLayer, listOf(offsetMat), Scalar(30.0, 30.0, 30.0))
        
        // Heavy blur for soft shadow
        Imgproc.GaussianBlur(shadowLayer, shadowLayer, Size(81.0, 81.0), 0.0)

        // Darken background where shadow exists (simple subtraction blending)
        Core.subtract(bgImage, shadowLayer, bgImage)
        
        shadowLayer.release()
        offsetMat.release()
    }

    private fun fetchCatImage(): Mat {
        return try {
            // Randomize dimensions slightly to get different cats
            val w = Random.nextInt(400, 600)
            val h = Random.nextInt(400, 600)
            val url = URL("[https://placecats.com/$w/$h](https://placecats.com/$w/$h)")
            val bytes = url.readBytes()
            val matOfByte = MatOfByte(*bytes)
            Imgcodecs.imdecode(matOfByte, Imgcodecs.IMREAD_COLOR)
        } catch (e: Exception) {
            println("Failed to download cat, falling back to gray matrix.")
            Mat(500, 500, CvType.CV_8UC3, Scalar(150.0, 150.0, 150.0))
        }
    }

    private fun compositePhotoWithBlurryEdges(bgImage: Mat, photoWarped: Mat, quadCorners: MatOfPoint, width: Int, height: Int) {
        // Create binary mask of the photo
        val mask = Mat.zeros(Size(width.toDouble(), height.toDouble()), CvType.CV_8UC1)
        Imgproc.fillPoly(mask, listOf(quadCorners), Scalar(255.0))

        // Blur the mask to create anti-aliased, slightly blurry edges
        Imgproc.GaussianBlur(mask, mask, Size(11.0, 11.0), 0.0)

        // Convert to 3 channels so it can be multiplied with BGR images
        val mask3Channel = Mat()
        Imgproc.cvtColor(mask, mask3Channel, Imgproc.COLOR_GRAY2BGR)

        // Convert to floats for alpha math (0.0 to 1.0)
        val maskFloat = Mat()
        mask3Channel.convertTo(maskFloat, CvType.CV_32FC3, 1.0 / 255.0)

        val bgFloat = Mat()
        bgImage.convertTo(bgFloat, CvType.CV_32FC3)
        
        val photoFloat = Mat()
        photoWarped.convertTo(photoFloat, CvType.CV_32FC3)

        // Calculate (1 - alpha)
        val inverseMaskFloat = Mat()
        Core.subtract(Mat(maskFloat.size(), CvType.CV_32FC3, Scalar(1.0, 1.0, 1.0)), maskFloat, inverseMaskFloat)

        // Apply Alpha Blending Math
        val fgBlended = Mat()
        val bgBlended = Mat()
        Core.multiply(photoFloat, maskFloat, fgBlended)
        Core.multiply(bgFloat, inverseMaskFloat, bgBlended)

        val finalFloat = Mat()
        Core.add(fgBlended, bgBlended, finalFloat)

        // Convert back to 8-bit integer and apply to background
        finalFloat.convertTo(bgImage, CvType.CV_8UC3)

        // Memory Cleanup
        mask.release()
        mask3Channel.release()
        maskFloat.release()
        bgFloat.release()
        photoFloat.release()
        inverseMaskFloat.release()
        fgBlended.release()
        bgBlended.release()
        finalFloat.release()
    }

    private fun saveTruthJson(path: String, id: Int, points: List<Point>) {
        val jsonContent = """
        {
            "id": $id,
            "coordinates": [
                {"x": ${points[0].x}, "y": ${points[0].y}},
                {"x": ${points[1].x}, "y": ${points[1].y}},
                {"x": ${points[2].x}, "y": ${points[2].y}},
                {"x": ${points[3].x}, "y": ${points[3].y}}
            ]
        }
        """.trimIndent()
        File(path).writeText(jsonContent)
    }
}
```

---

# 06_LIBRARY_SELECTION_AND_IMPLEMENTATION.md
**Title:** Cross-Platform Computer Vision Library Architecture
**Purpose:** Evaluate and select the optimal computer vision library for a Kotlin Multiplatform (KMP) application targeting Linux, Windows, and macOS (Intel & ARM), and provide the native integration code.

## 1. Library Comparison Matrix

For a professional Kotlin application, the choice of computer vision library significantly impacts deployment complexity and feature availability. 

| Feature | OpenCV (Direct / OpenPnP) | JavaCV (Bytedeco) | BoofCV |
| :--- | :--- | :--- | :--- |
| **Language Base** | C++ (JNI Wrappers) | C++ (Presets for OpenCV/FFmpeg) | **Pure Java / Kotlin** |
| **Built-in Features**| Highest (State-of-the-art CV) | High (Includes FFmpeg, etc.) | High (Focused strictly on CV) |
| **Performance** | Excellent (Native C++) | Excellent (Native C++) | Good (Highly optimized JVM) |
| **Deployment Ease** | Moderate (Requires native binaries) | Hard (Heavyweight dependencies) | **Easiest** (Single JAR, no JNI) |
| **Architecture** | Intel / ARM (Requires manual config) | Intel / ARM (Built-in presets) | **Universal** (Runs anywhere JVM runs) |

### Decision Guide
* **Choose BoofCV** if your team has strictly **zero** experience with JNI/Native binaries, and you need the application to run seamlessly on any OS without compiling C++ libraries.
* **Choose JavaCV** if you also need advanced video processing (FFmpeg) alongside image scanning, and don't mind massive artifact sizes (hundreds of MBs).
* **Choose OpenCV (via OpenPnP)** if you need the absolute best performance, the most documented algorithms (like `CLAHE`), and are comfortable managing native binaries in your CI/CD pipeline.

**Recommendation:** For this specific photo-scanning project, **OpenCV via the OpenPnP wrapper** is the most balanced choice. It provides the advanced algorithms required for localized contrast and perspective warping while simplifying the native binary distribution that usually plagues direct C++ wrappers.

---

## 2. Implementation: The OpenCV/Kotlin Pipeline

The following code utilizes the standard OpenCV Java API, which is accessible in Kotlin. 

### Step 2.1: Pre-Processing (CLAHE & Bilateral Filter)
This stage handles the "uneven lighting" and "blurry images" constraints.

```kotlin
import org.opencv.core.*
import org.opencv.imgproc.Imgproc

class ImagePreprocessor {
    fun prepareImage(input: Mat): Mat {
        val gray = Mat()
        Imgproc.cvtColor(input, gray, Imgproc.COLOR_BGR2GRAY)

        // Localized contrast enhancement for gradient backgrounds
        val clahe = Imgproc.createCLAHE(2.0, Size(8.0, 8.0))
        val equalized = Mat()
        clahe.apply(gray, equalized)

        // Noise reduction that preserves photo edges
        val blurred = Mat()
        Imgproc.bilateralFilter(equalized, blurred, 9, 75.0, 75.0)
        
        gray.release()
        equalized.release()
        
        return blurred
    }
}
```

### Step 2.2: Perspective Correction Math
To perform the 3D correction, we map the detected non-rectangular quadrilateral to a perfectly flat rectangle. The dimensions are calculated using the Euclidean distance formula:$d = \sqrt{(x_2 - x_1)^2 + (y_2 - y_1)^2}$Kotlinimport org.

```kotlin
opencv.core.*
import org.opencv.imgproc.Imgproc
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sqrt

class PerspectiveWarper {
    fun getPerspectiveTransform(src: MatOfPoint2f): Mat {
        val corners = src.toArray()
        
        // Calculate target dimensions based on maximum edge lengths
        val width = max(
            distance(corners[0], corners[1]), // Top edge
            distance(corners[2], corners[3])  // Bottom edge
        )
        
        val height = max(
            distance(corners[0], corners[3]), // Left edge
            distance(corners[1], corners[2])  // Right edge
        )

        val dst = MatOfPoint2f(
            Point(0.0, 0.0),
            Point(width, 0.0),
            Point(width, height),
            Point(0.0, height)
        )

        val transformMatrix = Imgproc.getPerspectiveTransform(src, dst)
        dst.release()
        
        return transformMatrix
    }

    private fun distance(p1: Point, p2: Point): Double {
        return sqrt((p1.x - p2.x).pow(2) + (p1.y - p2.y).pow(2))
    }
}
```

### 3. Cross-Platform Deployment Strategy
The primary challenge for "fully cross-platform" Kotlin apps using OpenCV is binary architecture matching. For macOS specifically, you must handle the transition between Intel (x86_64) and Apple Silicon (aarch64).Strategy 1: Library LoadingYou must ensure the JVM can find the correct .so (Linux), .dll (Windows), or .dylib (macOS) at runtime.Kotlin// If using org.openpnp:opencv
nu.pattern.OpenCV.loadLocally() 

#### Strategy 2: CI/CD Pipeline (GitHub Actions)
Since native libraries must be linked against the host architecture, utilize a matrix strategy in your GitHub Actions workflow to compile your application.YAMLjobs:

```
  build:
    strategy:
      matrix:
        os: [ubuntu-latest, windows-latest, macos-13, macos-latest]
        # macos-13 is Intel (x86_64)
        # macos-latest is Apple Silicon (ARM64)
    runs-on: ${{ matrix.os }}
    steps:
      - uses: actions/checkout@v4
      - name: Set up JDK 17
        uses: actions/setup-java@v3
        with:
          java-version: '17'
          distribution: 'temurin'
      - name: Build with Gradle
        run: ./gradlew build
```

#### Strategy 3: Universal Binaries
When packaging for macOS (using tools like jpackage or Compose Multiplatform's packaging tools), ensure the correct OpenCV native .dylib libraries are bundled into the app's Resources folder. You will need to generate two separate installers (.dmg or .pkg) for Mac users—one for Intel and one for Apple Silicon—or utilize Apple's lipo tool via a custom bash script to stitch the two OpenCV dylib files into a single "Universal" binary before packaging.

---

# 07_ADAPTIVE_BACKGROUND_DETECTION.md
**Title:** Adaptive Background Detection and Matte Extraction
**Purpose:** Define an algorithm to extract a clean binary matte (foreground = white, background = black) from images suffering from severe, uneven lighting gradients and high noise floors.

## 1. Algorithm Rationale

Standard global thresholding compares every pixel against a single fixed number (e.g., 127). On a gradient background, the "dark" side of the background might actually be darker than the "light" side of your foreground object. If you use a global threshold, the matte will bleed, merging the object with the dark side of the background. 

To solve this, we use an algorithm that evaluates pixels locally rather than globally, combining **Adaptive Thresholding** with **Morphological Noise Reduction**.

## 2. Pipeline Execution Steps

The core concept is to slide a "window" (a neighborhood of pixels) across the image. For every single pixel, the algorithm calculates a unique, localized threshold based entirely on the pixels immediately surrounding it.

### Step 1: Grayscale Conversion
Color data often complicates edge boundaries. Convert the RGB image to a single-channel grayscale matrix, where pixel intensities range from 0 (black) to 255 (white).

### Step 2: Adaptive Thresholding
Iterate through every pixel coordinate $(x,y)$ in the image.
1.  **Define the Neighborhood (Block Size):** Extract a square matrix of surrounding pixels. This must be an odd number (e.g., $11 \times 11$, $21 \times 21$) so the target pixel is perfectly centered.
2.  **Calculate Local Threshold ($T$):** Compute the weighted average of this neighborhood. A Gaussian weighted average is preferred because it gives more importance to pixels closer to the center, preserving shape corners better than a flat mean.
3.  **Apply Constant Offset ($C$):** Subtract a fine-tuning constant $C$ from the calculated average. This constant helps pull the threshold just below the local background noise floor, preventing slight gradient shifts from being detected as edges.
4.  **Binarize:** Evaluate the target pixel intensity $I(x,y)$ to output the final Matte pixel:

$$Matte(x,y) = \begin{cases} 255 & \text{if } I(x,y) > T(x,y) - C \\ 0 & \text{otherwise} \end{cases}$$

### Step 3: Morphological "Opening" (Salt Noise Removal)
Adaptive thresholding on noisy images often leaves behind "salt noise" (tiny isolated white pixels scattered in the black background).
1.  **Erosion:** Slide a small kernel (e.g., $3 \times 3$) over the matte. If *any* pixel under the kernel is black, force the center pixel to black. This entirely deletes tiny white specks.
2.  **Dilation:** Immediately follow with the inverse operation. If any pixel under the kernel is white, force the center to white. This restores the boundaries of the main foreground objects that were slightly shaved off during the erosion phase.

### Step 4: Morphological "Closing" (Pepper Noise Removal)
Sometimes the foreground object has dark spots that get mistakenly thresholded as background (leaving black holes in your solid white matte).
1.  **Dilation followed by Erosion:** Apply the reverse of Step 3. This fills in small internal holes and bridges tiny gaps in the edges without expanding the overall size of the foreground object.

---

# SYSTEM ROLE & OBJECTIVE
Act as a Senior Staff Software Engineer specializing in Kotlin Multiplatform (KMP) and Computer Vision (OpenCV). Your objective is to implement a cross-platform (Linux, Windows, macOS Intel/ARM) photo-scanning application that extracts multiple photographs from a single camera image and corrects their 3D perspective.

# STRICT HARD CONSTRAINTS (DO NOT DEVIATE)
1. **Language & Build:** Strictly use Kotlin and Gradle. Do not provide Python or C++ solutions. 
2. **No Machine Learning:** You are strictly forbidden from using ML models, neural networks, or AI inference (e.g., YOLO, TensorFlow, PyTorch). You must use pure, traditional mathematical computer vision algorithms. 
3. **Library:** Use the standard OpenCV Java API via the `org.openpnp:opencv` artifact. Do not use JavaCV/Bytedeco unless explicitly requested, to avoid binary bloat.
4. **Native Memory Management:** OpenCV `Mat` objects are allocated in native C++ memory, which the Kotlin Garbage Collector cannot track. You MUST manually call `.release()` on every single `Mat`, `MatOfPoint`, and `MatOfPoint2f` object after use, or wrap them in a custom scoped resource block. Failure to do so will cause catastrophic memory leaks.

# ARCHITECTURAL DIRECTIVES
* Structure the project as a Kotlin Multiplatform library.
* Use `expect/actual` patterns if wrapping the OpenCV API for specific UI targets, but the core domain logic should reside in a shared module that targets the JVM (Desktop/Android) using the OpenPnP JNI wrapper.
* Assume the CI/CD pipeline will use GitHub Actions with a matrix build to compile the macOS targets for both Intel (`x86_64`) and Apple Silicon (`aarch64`) as well as for Microsoft Windows, and Debian Linux (.deb)

# THE COMPUTER VISION PIPELINE
Implement the detection pipeline strictly using this sequence to handle uneven gradient backgrounds, noisy sensors, and blending edges:
1. **Pre-processing:** Convert to Grayscale -> Apply CLAHE (Contrast Limited Adaptive Histogram Equalization) with an 8x8 grid -> Apply a Bilateral Filter (not Gaussian) to preserve sharp edges while blurring gradient noise.
2. **Edge Detection:** Apply Canny Edge Detection -> Apply a Morphological CLOSE operation (Dilation then Erosion) to bridge broken edge lines where the photo blends into the background.
3. **Extraction:** Use `findContours` with the `RETR_EXTERNAL` flag to ignore subjects within the photos. Filter contours by a minimum area (e.g., 5% of the image).
4. **Approximation:** Use `approxPolyDP` with a dynamic epsilon based on the contour perimeter. Validate the result is a 4-point convex shape.
5. **Perspective Warping:** Order the 4 vertices (TL, TR, BR, BL). Calculate the true width and height using the Euclidean distance formula to prevent stretching. Use `getPerspectiveTransform` and `warpPerspective` to flatten the extracted photo.

# TESTING DIRECTIVES
Do not write standard unit tests for the CV algorithm, as pixel output is non-deterministic across OS environments. Instead, implement a **Synthetic Ground Truth Generator**:
1. Write a Kotlin script that dynamically generates test matrices: gradient backgrounds, random noise, warped "photos" downloaded from placeholder image sites, and alpha-blended drop shadows.
2. The generator must output the `.jpg` image and a `.json` file containing the exact 4-point coordinates of the generated photo.
3. Write a test harness that runs the detection pipeline against these synthetic images and validates accuracy using an **Intersection over Union (IoU)** mathematical calculation using OpenCV bitwise masking.

# OUTPUT FORMAT
When providing code, separate it into logical files (e.g., `build.gradle.kts`, `PhotoDetector.kt`, `PerspectiveWarper.kt`, `SyntheticGenerator.kt`). Ensure all math and OpenCV imports are included.
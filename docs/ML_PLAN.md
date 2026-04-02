Shifting to a Machine Learning approach is a fantastic pivot. While traditional computer vision is elegant, it fundamentally lacks semantic understanding. It doesn't know what a "photograph" or a "shadow" is; it only sees math and pixel gradients. ML models understand context, making them virtually immune to the gradient and shadow edge-cases we were mitigating earlier.

The best part? You do not need to abandon your Kotlin/OpenCV architecture. You don't need to import massive PyTorch or TensorFlow libraries into your KMP project. OpenCV has a built-in dnn (Deep Neural Network) module that can load and run lightweight, pre-trained ML models seamlessly.

Here is a comprehensive breakdown of how to implement this, along with the best lightweight models for the job.

## 1. Lightweight Pre-Trained ML Models
For document and photo corner detection, you generally choose between two ML architectures: Segmentation (creating a pixel-perfect mask of the photo) or Keypoint Detection (directly guessing the X/Y coordinates of the 4 corners).

### A. Keypoint Detection (Fastest & Most Direct)
Instead of finding edges and guessing quadrilaterals, these models are trained to look at an image and output exactly 8 numbers: the (x,y) coordinates of the Top-Left, Top-Right, Bottom-Right, and Bottom-Left corners.

YOLOv8-Pose (Nano): YOLO (You Only Look Once) is famous for object detection, but its "Pose" variant is used for keypoints. The "Nano" size (yolov8n-pose) is incredibly lightweight (under 10MB) and runs in milliseconds on mobile CPUs. There are many open-source weights available where this model has been fine-tuned specifically for "Document Corner Detection" or "Smart Scanner" applications.

### B. Deep Edge Detection (Drop-In Replacement)
If you want to keep the contour logic we built but want a foolproof edge detector that ignores shadows and gradients:

DexiNed (Dense Extreme Inception Network) or HED (Holistically-Nested Edge Detection): These are lightweight neural networks trained specifically to find object boundaries. If you replace your Canny and Bilateral Filter steps with a pass through DexiNed, it will output a crisp, binary edge map of the photo, completely ignoring the uneven lighting on the table.

### C. Semantic Segmentation (Most Robust)
These models classify every single pixel as either "Background" or "Photo".

MobileNetV3 + U-Net: A U-Net architecture with a MobileNet backbone is the industry standard for mobile document scanning. It is very lightweight and outputs a solid white blob over the photo. You then simply run OpenCV's findContours on that blob. There are many pre-trained "Document Localization" U-Net models available on platforms like HuggingFace.

## 2. Implementation Architecture (Kotlin KMP + OpenCV DNN)
If you use a pre-trained model, the standard workflow is to convert it to the ONNX (Open Neural Network Exchange) format. ONNX is a universal ML format that OpenCV can read natively.

### Step 1: Add the Model to Resources
Drop your lightweight .onnx model file (e.g., document_corner_yolo.onnx) into your application's raw resources folder.

### Step 2: Load the Network in Kotlin
Use OpenCV's dnn module to load the model into memory.

```kotlin
import org.opencv.core.*
import org.opencv.dnn.Dnn
import org.opencv.dnn.Net

class MlPhotoDetector(modelPath: String) {
    private val net: Net = Dnn.readNetFromONNX(modelPath)
    
    // ...
}
```

### Step 3: Create a "Blob" (Pre-processing)
ML models require images to be resized to specific dimensions (e.g., 640x640) and have their color values normalized before they can process them. OpenCV does this instantly with blobFromImage.

```kotlin
fun detectCorners(source: Mat): MatOfPoint2f {
    // Convert image to a 4D tensor blob (e.g., 640x640, normalized)
    val blob = Dnn.blobFromImage(
        source, 
        1.0 / 255.0,        // Scale pixel values to 0-1
        Size(640.0, 640.0), // Standard YOLO input size
        Scalar(0.0, 0.0, 0.0), 
        true,               // Swap RB to RGB
        false
    )
    
    net.setInput(blob)
    
    // Run the forward pass to get the ML predictions
    val output = net.forward()
    
    // ... Parse the output matrix for your 4 coordinates ...
    
    blob.release()
    output.release()
}
```

### Step 4: Post-Processing & Warping
Once the network outputs the 4 coordinates (scaled to the 640x640 input), you simply multiply those coordinates by your original image's aspect ratio to get the true coordinates. Finally, you pass those 4 points into the exact same PerspectiveWarper class we built in the traditional CV pipeline to flatten the image.

The Verdict
If you want the highest success rate with the least amount of algorithmic tweaking, grab a pre-trained YOLO-Pose document corner model in ONNX format and run it through OpenCV's dnn module. It replaces 100 lines of complex filtering and contour logic with a single forward pass of a neural network, while keeping your app perfectly cross-platform.

---

Transitioning from traditional computer vision to a Deep Learning (DNN) approach using OpenCV requires a shift in how you process the image. Instead of manually writing rules for edges and shadows, you pass the image through a pre-trained neural network that outputs the location or shape of the photograph.Here is the comprehensive plan and Kotlin implementation for all three Machine Learning strategies, keeping your application strictly cross-platform using OpenCV's built-in dnn module.


## Strategy 1: Keypoint Detection (YOLO-Pose)

### The Plan:
This is the most direct approach. You use a lightweight model like YOLOv8n-Pose that has been fine-tuned for "document corners." Instead of outputting bounding boxes, it outputs the exact $(x, y)$ coordinates of the Top-Left, Top-Right, Bottom-Right, and Bottom-Left corners.Pros: Extremely fast (runs in milliseconds on CPUs), completely ignores internal photo details and external shadows, and outputs coordinates directly without needing contour logic.Cons: The model must be explicitly trained to look for 4 corners.

### Implementation:

```kotlin
import org.opencv.core.*
import org.opencv.dnn.Dnn
import org.opencv.dnn.Net

class YoloKeypointDetector(modelPath: String) {
    private val net: Net = Dnn.readNetFromONNX(modelPath)

    fun detectCorners(source: Mat): MatOfPoint2f {
        // 1. Pre-process: Resize to 640x640, scale pixels to 0-1, swap BGR to RGB
        val blob = Dnn.blobFromImage(
            source, 1.0 / 255.0, Size(640.0, 640.0), 
            Scalar(0.0, 0.0, 0.0), true, false
        )
        
        net.setInput(blob)

        // 2. Forward Pass: Get the predictions
        val output = net.forward()

        // 3. Parse Output Tensor
        // Note: YOLOv8 outputs a tensor like [1, 56, 8400]. Parsing this requires reshaping.
        // Assuming you parse the highest-confidence bounding box and extract its 4 keypoints:
        val parsedCorners = parseYoloOutput(output, source.width(), source.height())

        blob.release()
        output.release()

        return parsedCorners // Returns the 4 corners scaled back to original image size
    }

    private fun parseYoloOutput(output: Mat, originalWidth: Int, originalHeight: Int): MatOfPoint2f {
        // Implementation details depend on the specific ONNX export tensor shape.
        // It involves finding the highest confidence score, extracting the keypoint (x,y) pairs, 
        // and multiplying by (originalWidth / 640.0) and (originalHeight / 640.0).
        return MatOfPoint2f(Point(10.0, 10.0), Point(100.0, 10.0), Point(100.0, 100.0), Point(10.0, 100.0)) // Mocked
    }
}
```

## Strategy 2: Semantic Segmentation (U-Net / MobileNet)

The Plan:Segmentation models perform pixel-level classification. 

You pass the image in, and the model returns a single-channel "mask" where every pixel that belongs to the photo is white (1.0), and everything else is black (0.0).Pros: Highly robust. Since it outputs a binary mask, you can plug this directly into your existing findContours logic, completely bypassing Grayscale, CLAHE, and Canny edge detection.Cons: Slower than Keypoint detection. Resizing the output mask back to high resolution can sometimes round off the sharp corners slightly.

### Implementation:

```kotlin
import org.opencv.core.*
import org.opencv.dnn.Dnn
import org.opencv.imgproc.Imgproc

class SegmentationDetector(modelPath: String) {
    private val net = Dnn.readNetFromONNX(modelPath)

    fun detectQuadrilateral(source: Mat): List<MatOfPoint2f> {
        // 1. Create blob (U-Net models often use 256x256 or 512x512)
        val blob = Dnn.blobFromImage(source, 1.0 / 255.0, Size(256.0, 256.0), Scalar(0.0, 0.0, 0.0), true, false)
        net.setInput(blob)

        // 2. Forward pass returns a 1-channel mask (0.0 to 1.0)
        val output = net.forward()

        // 3. Reshape the DNN tensor into a standard 2D OpenCV Mat
        // Output tensor is usually [1, 1, 256, 256]. We reshape it to 256x256.
        val mask256 = output.reshape(1, 256)
        
        // 4. Resize mask back to original image dimensions
        val fullMask = Mat()
        Imgproc.resize(mask256, fullMask, source.size())

        // 5. Binarize the mask (convert float probabilities to 8-bit 0 or 255)
        val binaryMask = Mat()
        fullMask.convertTo(binaryMask, CvType.CV_8UC1, 255.0)
        Imgproc.threshold(binaryMask, binaryMask, 127.0, 255.0, Imgproc.THRESH_BINARY)

        // 6. Plug directly into your existing contour logic!
        val contours = mutableListOf<MatOfPoint>()
        Imgproc.findContours(binaryMask, contours, Mat(), Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)
        
        // ... (Run your approxPolyDP logic here to get the 4 points) ...

        blob.release(); output.release(); mask256.release(); fullMask.release(); binaryMask.release()
        
        return listOf() // Return processed quads
    }
}
```

## Strategy 3: Deep Edge Detection (DexiNed / HED)

### The Plan:
This is a 1-to-1 drop-in replacement for your Canny Edge detector. Traditional edge detectors trigger on any sharp contrast change (like a shadow line or a pattern inside the photo). Deep Edge detectors are trained to only output lines that represent the boundaries of physical objects.Pros: Easiest to integrate into your existing pipeline. You keep your Morphological Closing and Contour logic.Cons: Models like DexiNed are computationally heavier than MobileNet Segmentation or YOLO.

### Implementation:

```kotlin
import org.opencv.core.*
import org.opencv.dnn.Dnn
import org.opencv.imgproc.Imgproc

class DeepEdgeDetector(modelPath: String) {
    private val net = Dnn.readNetFromONNX(modelPath)

    fun getEdgeMap(source: Mat): Mat {
        // 1. Create blob. HED often requires mean subtraction rather than 0-1 scaling.
        val blob = Dnn.blobFromImage(
            source, 1.0, Size(500.0, 500.0), 
            Scalar(104.00698793, 116.66876762, 122.67891434), // Common ImageNet mean
            false, false
        )
        net.setInput(blob)

        // 2. Get the Edge Map prediction
        val output = net.forward()
        val edgeMap500 = output.reshape(1, 500)

        // 3. Resize back to original
        val finalEdgeMap = Mat()
        Imgproc.resize(edgeMap500, finalEdgeMap, source.size())
        
        // Convert to 8-bit for contour processing
        finalEdgeMap.convertTo(finalEdgeMap, CvType.CV_8UC1, 255.0)

        blob.release(); output.release(); edgeMap500.release()

        // 4. Return this instead of the Canny output. Pass this directly into morphEx and findContours.
        return finalEdgeMap 
    }
}
```
# Model Management

Petrie uses ONNX machine learning models for photo scanning, face detection, face recognition, and orientation detection. This document covers how models are loaded, where they're stored, and how to manually install them if automatic download fails.

---

## Model Inventory

| Model | File Name | Size | Source | Loaded From |
|-------|-----------|------|--------|-------------|
| YOLO Detection | `detection_model.onnx` | ~10 MB | Bundled in JAR | Classpath |
| YOLO Pose | `pose_model.onnx` | ~38 MB | Bundled in JAR | Classpath |
| YOLO12n Face Detection | `face_detection_model.onnx` | ~10 MB | Bundled in JAR | Classpath |
| Corner Regression | `corner_regression_model.onnx` | ~9.5 MB | Bundled in JAR | Classpath |
| Orientation Detection | `orientation_detection_model.onnx` | ~346 MB | **Lazy download** | `~/.petrie-importer/models/` |
| ArcFace MobileFaceNet | `face_embedding_model.onnx` | ~8 MB² | **Lazy download** | `~/.petrie-importer/models/` |

> ² The face embedding model is downloaded as a zip archive (~7.7 MB) and automatically extracted.

### Bundled vs. Downloaded

- **Bundled models** are packaged inside the JAR at `src/main/resources/models/`. They're always available and require no user action.
- **Downloaded models** are fetched on first use from the internet and cached locally. If the download fails or the URL becomes unavailable, models can be manually installed (see below).

---

## Model Download URLs

| Model | URL |
|-------|-----|
| Orientation Detection | `https://huggingface.co/Chuckame/deep-image-orientation-angle-detection/resolve/main/deep-image-orientation-angle-detection.onnx` |
| Face Embedding | `https://hailo-model-zoo.s3.eu-west-2.amazonaws.com/FaceRecognition/arcface/arcface_mobilefacenet/pretrained/2022-08-24/arcface_mobilefacenet.zip` |

### Why Zip for the Face Embedding Model?

The ArcFace MobileFaceNet model from the Hailo Model Zoo is distributed as a `.zip` archive containing `mbf.onnx`. The download adapter automatically extracts this file and saves it as `face_embedding_model.onnx`. If you're manually installing, you'll need to extract it yourself (see instructions below).

---

## Manual Model Installation

If automatic download fails (network issues, URL changes, firewall restrictions), you can manually place model files in the correct directory.

### Directory

All downloaded models are stored in:

```
~/.petrie-importer/models/
```

On most systems:
- **macOS**: `/Users/<your-username>/.petrie-importer/models/`
- **Linux**: `/home/<your-username>/.petrie-importer/models/`
- **Windows**: `C:\Users\<your-username>\.petrie-importer\models\`

Create the directory if it doesn't exist:

```bash
mkdir -p ~/.petrie-importer/models
```

### Installing the Orientation Detection Model

1. Download the model from HuggingFace:
   ```bash
   curl -L -o ~/.petrie-importer/models/orientation_detection_model.onnx \
     "https://huggingface.co/Chuckame/deep-image-orientation-angle-detection/resolve/main/deep-image-orientation-angle-detection.onnx"
   ```

2. Verify the file size (~346 MB):
   ```bash
   ls -lh ~/.petrie-importer/models/orientation_detection_model.onnx
   ```

3. Restart Petrie if it was running. The orientation detection feature will automatically detect the model.

### Installing the Face Embedding Model

The face embedding model is distributed as a zip archive. You have two options:

#### Option A: Download and Extract Automatically (Recommended)

```bash
# Download the zip archive
curl -L -o /tmp/arcface_mobilefacenet.zip \
  "https://hailo-model-zoo.s3.eu-west-2.amazonaws.com/FaceRecognition/arcface/arcface_mobilefacenet/pretrained/2022-08-24/arcface_mobilefacenet.zip"

# Extract mbf.onnx and rename to face_embedding_model.onnx
unzip -j /tmp/arcface_mobilefacenet.zip mbf.onnx -d ~/.petrie-importer/models/
mv ~/.petrie-importer/models/mbf.onnx ~/.petrie-importer/models/face_embedding_model.onnx

# Clean up
rm /tmp/arcface_mobilefacenet.zip
```

#### Option B: Download via Browser

1. Open this URL in your browser:  
   `https://hailo-model-zoo.s3.eu-west-2.amazonaws.com/FaceRecognition/arcface/arcface_mobilefacenet/pretrained/2022-08-24/arcface_mobilefacenet.zip`

2. Extract the zip file and find `mbf.onnx` inside.

3. Copy `mbf.onnx` to `~/.petrie-importer/models/face_embedding_model.onnx`

4. Restart Petrie.

### Verification

To verify models are correctly installed:

```bash
ls -lh ~/.petrie-importer/models/
```

Expected output:
```
orientation_detection_model.onnx  ~346MB
face_embedding_model.onnx          ~8MB
```

The application will automatically detect models on startup. No configuration needed.

---

## Model Specifications

### Orientation Detection Model

| Property | Value |
|----------|-------|
| **Architecture** | ViT (Vision Transformer) |
| **Source** | [Chuckame/deep-image-orientation-angle-detection](https://huggingface.co/Chuckame/deep-image-orientation-angle-detection) |
| **Input** | 1×3×224×224 (NCHW, float32) |
| **Output** | 1×4 (probabilities for 0°, 90°, 180°, 270°) |
| **File size** | ~346 MB |
| **License** | MIT |

### Face Embedding Model (ArcFace MobileFaceNet)

| Property | Value |
|----------|-------|
| **Architecture** | MobileFaceNet with ArcFace loss |
| **Source** | [Hailo Model Zoo — arcface_mobilefacenet](https://github.com/hailo-ai/hailo_model_zoo) |
| **Input** | 1×3×112×112 (NCHW, float32, normalized to [-1, 1]) |
| **Output** | 1×512 (L2-normalized embedding vector) |
| **File size** | ~7.8 MB (zip), ~8 MB (extracted ONNX) |
| **Parameters** | 2.04M |
| **LFW accuracy** | 99.43% |
| **License** | MIT |

### Face Detection Model (Bundled)

| Property | Value |
|----------|-------|
| **Architecture** | YOLO12n-face |
| **Input** | 1×3×640×640 (NCHW, float32) |
| **Output** | Bounding boxes + confidence scores |
| **File size** | ~10 MB |
| **Confidence threshold** | 0.5 (configurable) |

---

## Adding a New Model

To add a new downloadable model:

1. **Add model ID** to `ModelDownloadPort.companion` constants.
2. **Add model metadata** to `HuggingFaceModelDownloadAdapter.modelMetadata` map:
   ```kotlin
   ModelDownloadPort.NEW_MODEL_ID to ModelMetadata(
       id = ModelDownloadPort.NEW_MODEL_ID,
       name = "Human-readable Model Name",
       description = "What this model does.",
       downloadUrl = "https://example.com/path/to/model.onnx",
       fileName = "new_model.onnx",
       downloadSize = 50L * 1024 * 1024, // ~50 MB
       isZip = false,  // set to true if the download is a zip archive
       zipEntryName = null,  // required if isZip = true
   ),
   ```
3. **Add model loading** to `ClasspathModelResourceAdapter` (class path fallback + download directory).
4. **Add version string** to `ClasspathModelResourceAdapter.newModelVersion()`.
5. **Add to model availability check** in `ClasspathModelResourceAdapter.isModelAvailable()`.
6. **Update this document** with the new model's specifications and manual install instructions.
7. **Update `PRODUCTION_READINESS.md`** model size table.

### If the model is distributed as a zip archive:

Set `isZip = true` and `zipEntryName` to the name of the `.onnx` file inside the zip. The download adapter will:
1. Download the `.zip` file to a `.downloading` temp file
2. Extract the specified entry from the zip
3. Save the extracted `.onnx` file as the final model file
4. Delete the `.zip` temp file

If the zip entry cannot be found, a descriptive error message listing the available entries is shown.

---

## Troubleshooting

### "Model file not found (HTTP 404)" error

The download URL may have changed. Check this document for the current URL, or look for an updated version of the application. You can also try to manually download the model using the URL in the "Model Download URLs" table above.

### "Model repository is private or unavailable (HTTP 401/403)" error

The model hosting may require authentication or may have been moved. Manually install the model by following the instructions in "Manual Model Installation" above.

### Orientation detection doesn't work

1. Check if the model file exists: `ls -lh ~/.petrie-importer/models/orientation_detection_model.onnx`
2. If missing, follow "Installing the Orientation Detection Model" above.
3. Check the file size — it should be ~346 MB. A smaller file may be corrupted.

### Face grouping/identification doesn't work

1. Check if the model file exists: `ls -lh ~/.petrie-importer/models/face_embedding_model.onnx`
2. If missing, follow "Installing the Face Embedding Model" above.
3. The file should be ~8 MB. If you see `mbf.onnx` instead of `face_embedding_model.onnx`, rename it:
   ```bash
   mv ~/.petrie-importer/models/mbf.onnx ~/.petrie-importer/models/face_embedding_model.onnx
   ```

### Embedding dimension mismatch

The ArcFace MobileFaceNet model produces **512-dimensional** embeddings. Classic MobileFaceNet produced 128-dimensional embeddings. If you see "invalid dimension" errors, ensure you're using the correct model version (`arcface_mobilefacenet`, not an older 128-dim model). Old embeddings with 128 dimensions are still supported for reading, but new extractions will use 512 dimensions.
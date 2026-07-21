# Plan: Face Detection, Grouping & Person Identification

> **Status:** 🔧 Phase 1 Foundation Implemented (domain models, ports, People tab UI)  
> **Prerequisite:** Face detection (YOLO) already exists; face region tagging already exists  
> **New capability:** Extract face embeddings, cluster faces across photos, identify/tag known persons  
> **Adversarial Review:** ✅ All 25 issues from 2025-07-18 review addressed  

---

## Problem Statement

Currently, face tagging in Petrie is **manual and per-photo** — each scanned image's face regions are independent. Users must type the same person's name for every photo. There is no way to:

1. **Recognize** that a face in photo A is the same person as in photo B
2. **Auto-suggest** a name based on previously-tagged faces
3. **Group** all photos containing a specific person
4. **Maintain** a person directory that persists across sessions

This plan adds face **identification** (not just detection): extracting a numeric embedding from each detected face, clustering similar embeddings across photos, and maintaining a person directory for auto-suggestion.

---

## Current State

| Capability | Implemented? | Details |
|-----------|-------------|---------|
| Face detection (bounding boxes) | ✅ | `FaceDetectionPort` → `FaceDetectionService` → YOLO12n ONNX |
| Manual face region tagging | ✅ | `FaceSelectorOverlay`, `FaceRegionState`, naming cycle |
| Face region XMP write | ✅ | `XmpMetadataWriter` → MWG-RS regions |
| Face region XMP read (inherit) | ✅ | `FaceRegionTransformer.readFaceRegionsFromXmp()` |
| Face coordinate transform (crop) | ✅ | `FaceRegionTransformer.transformFaceRegionsFromSource()` |
| Face embedding extraction | ❌ | **New — needed for identification** |
| Face similarity comparison | ❌ | **New — needed for grouping** |
| Person directory / identity | ❌ | **New — needed for auto-suggestion** |
| Auto-suggest from known persons | ❌ | **New — UX feature** |
| Cross-photo face clustering | ❌ | **New — batch workflow** |

---

## Architecture

### Hexagonal Design

```
┌──────────────────────────────────────────────────────────────────┐
│  UI Layer                                                        │
│  PersonDirectoryPanel · FaceSuggestChip · BatchGroupingView      │
├──────────────────────────────────────────────────────────────────┤
│  Application Layer                                               │
│  PersonService · FaceGroupingService (future)                     │
│  FaceEmbeddingService (orchestrates detection → embedding)      │
├──────────────────────────────────────────────────────────────────┤
│  Domain Layer                                                    │
│  Models: Person · FaceEmbedding · FaceCluster · PersonDirectory │
│  Ports:   FaceEmbeddingPort · PersonDirectoryPort               │
├──────────────────────────────────────────────────────────────────┤
│  Infrastructure Layer                                            │
│  ArcFaceEmbeddingAdapter (ONNX) · JsonPersonDirectoryAdapter     │
│  (future: SQLite adapter for large directories)                 │
└──────────────────────────────────────────────────────────────────┘
```

### Key Design Decisions

1. **Embeddings, not classification** — We extract fixed-length vectors (embeddings) and compare them with cosine similarity. This is more flexible than a closed-set classifier because new persons can be added without retraining.

2. **Local-only, no cloud** — All inference runs ONNX on-device. No face data leaves the machine. This matches the project's privacy-first philosophy.

3. **Progressive enhancement** — Face detection + manual tagging still works without embedding models. Embedding-based grouping is an optional enhancement that activates when the model is available.

4. **Person directory is per-user, persisted locally** — Stored in `~/.petrie-importer/persons.json` (or SQLite for large directories). Each person has a name and one or more representative embeddings.

---

## Domain Models

### `FaceEmbedding.kt` — ✅ Implemented

> **Note:** The code below shows the original plan. The actual implemented version has several adversarial-review fixes:
> - `vector` uses Base64-encoded `vectorBase64` string instead of raw `List<Float>` (2x smaller JSON)
> - `sourceRegion` uses structured `NormalizedRect` instead of `BoundingBox?` (type-safe coordinates)
> - Thresholds use `MATCH_THRESHOLD` / `AUTO_TAG_THRESHOLD` (0.65 / 0.75) as single source of truth
> - `FaceMatchingConfig` in `AppSettings` provides user-configurable thresholds
> - `faceCrop`, `NormalizedRect`, and `PixelRect` bridging types added for alignment pipeline
> - See `domain/model/FaceEmbedding.kt` for the actual implementation.

```kotlin
@Serializable
data class FaceEmbedding(
    val id: String = DomainDefaults.generateId(),
    val vectorBase64: String = "",       // Base64-encoded embedding (compact, 2x smaller than List<Float>)
    val quality: Float = 1.0f,
    val estimatedYaw: Float = 0f,       // Head yaw angle for diversity tracking
    val modelName: String = "",          // Model name guard (cross-model comparison is invalid)
    val sourcePath: String = "",
    val sourceRegion: NormalizedRect = NormalizedRect(), // Structured coordinates, not String
) {
    companion object {
        const val MIN_QUALITY_FOR_MATCHING = 0.3f
        const val MIN_FACE_SIZE_PX = 50
        const val DIM_MOBILEFACENET = 128
        const val DIM_ARCFACE_R50 = 512
        const val MATCH_THRESHOLD = 0.65f      // Single source of truth (was SAME_PERSON_THRESHOLD)
        const val AUTO_TAG_THRESHOLD = 0.75f    // Single source of truth (was CONFIDENT_MATCH_THRESHOLD)
    }
    val vector: FloatArray by lazy { decodeVector(vectorBase64) }
    fun cosineSimilarity(other: FaceEmbedding): Float { ... }
}

@Serializable
data class NormalizedRect(val x: Float, val y: Float, val w: Float, val h: Float) {
    val isValid: Boolean get() = w > 0f && h > 0f
    fun toPixels(imageWidth: Int, imageHeight: Int): PixelRect
    companion object {
        fun fromDetectedFace(face: DetectedFace, imageWidth: Int, imageHeight: Int): NormalizedRect
    }
}

@Serializable
data class FaceCrop(
    val sourcePath: String, val sourceRegion: NormalizedRect,
    val alignedWidth: Int = 112, val alignedHeight: Int = 112,
    val yaw: Float = 0f, val detectionConfidence: Float = 1.0f,
)

@Serializable
data class FaceMatchingConfig(
    val matchThreshold: Float = FaceEmbedding.MATCH_THRESHOLD,
    val autoTagThreshold: Float = FaceEmbedding.AUTO_TAG_THRESHOLD,
    val maxGallerySize: Int = 20, val maxDirectorySize: Int = 500,
)
```

### `Person.kt` — ✅ Implemented

> **Note:** The actual implementation has several adversarial-review fixes:
> - `gallery` instead of `embeddings` (clearer naming for progressive enrichment)
> - `photoCount` is a **computed property** (`sourcePaths.size`) — no denormalized field
> - No `autoDetect`/`autoIdentify` per-person fields (moved to global `AppSettings`)
> - `withEmbedding()` uses diversity-aware eviction, `mergeGallery()` uses diversity-aware merge
> - `validateName()` for name validation (blank, length, control chars)
> - Thresholds reference `FaceEmbedding.MATCH_THRESHOLD` (single source of truth)

```kotlin
@Serializable
data class Person(
    val id: String = DomainDefaults.generateId(),
    val name: String = "",
    val gallery: List<FaceEmbedding> = emptyList(),    // "gallery" not "embeddings"
    val thumbnailPath: String = "",
    val sourcePaths: List<String> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
) {
    val photoCount: Int get() = sourcePaths.size    // Computed, not stored
    fun matchScore(candidate: FaceEmbedding): Float
    fun isLikelyMatch(candidate: FaceEmbedding): Boolean = matchScore(candidate) >= FaceEmbedding.MATCH_THRESHOLD
    fun withEmbedding(embedding: FaceEmbedding, maxGallerySize: Int = 20): Person  // Diversity-aware eviction
    fun mergeGallery(other: Person, maxGallerySize: Int = 20): Person              // Diversity-aware merge
    companion object {
        fun validateName(name: String, maxLength: Int = 100): String?  // Returns error or null
    }
}
```

### `PersonDirectory.kt`

```kotlin
@Serializable
data class PersonDirectory(
    /** All known persons */
    val persons: List<Person> = emptyList(),
    
    /** Version for migration */
    val version: Int = 1,
) {
    /** Find the best-matching person for an embedding, or null if no match */
    fun findBestMatch(embedding: FaceEmbedding, threshold: Float = FaceEmbedding.SAME_PERSON_THRESHOLD): Person? {
        return persons
            .map { person -> person to person.similarityScore(embedding) }
            .filter { (_, score) -> score >= threshold }
            .maxByOrNull { (_, score) -> score }
            ?.first
    }
    
    /** Find all persons that match above threshold */
    fun findAllMatches(embedding: FaceEmbedding, threshold: Float = FaceEmbedding.SAME_PERSON_THRESHOLD): List<Pair<Person, Float>> {
        return persons
            .map { person -> person to person.similarityScore(embedding) }
            .filter { (_, score) -> score >= threshold }
            .sortedByDescending { (_, score) -> score }
    }
    
    fun withPerson(person: Person): PersonDirectory =
        copy(persons = persons.map { if (it.id == person.id) person else it }.let { 
            if (person.id !in it.map { p -> p.id }) it + person else it 
        })
    
    fun withoutPerson(personId: String): PersonDirectory =
        copy(persons = persons.filter { it.id != personId })
    
    fun personById(id: String): Person? = persons.find { it.id == id }
    fun personByName(name: String): Person? = persons.find { it.name.equals(name, ignoreCase = true) }
}
```

---

## Domain Ports

### `FaceEmbeddingPort.kt`

```kotlin
interface FaceEmbeddingPort {
    /** Extract a face embedding from a cropped face region.
     *  Returns null if the model is not available or extraction fails. */
    suspend fun extractEmbedding(faceImage: ProcessedImage): FaceEmbedding?
    
    /** Extract embeddings for multiple detected faces from a single image.
     *  Crops each detected face from the source image and extracts its embedding. */
    suspend fun extractEmbeddings(
        sourceImage: ProcessedImage,
        detectedFaces: List<DetectedFace>,
    ): List<FaceEmbedding>
    
    /** Whether the embedding model is available and loaded */
    fun isEmbeddingAvailable(): Boolean
    
    /** Pre-load the model (call early to avoid delay on first use) */
    suspend fun preload(): Boolean
}
```

### `PersonDirectoryPort.kt`

```kotlin
interface PersonDirectoryPort {
    /** Load the person directory */
    suspend fun loadDirectory(): PersonDirectory
    
    /** Save the person directory */
    suspend fun saveDirectory(directory: PersonDirectory)
    
    /** Observe the person directory as a reactive flow */
    fun observeDirectory(): StateFlow<PersonDirectory>
    
    /** Add a face embedding to an existing person (or create new person) */
    suspend fun addEmbedding(personName: String, embedding: FaceEmbedding): Person
    
    /** Find best match for an embedding */
    suspend fun findMatch(embedding: FaceEmbedding): Person?
    
    /** Find all matches above threshold */
    suspend fun findMatches(embedding: FaceEmbedding): List<Pair<Person, Float>>
    
    /** Merge two persons (consolidate duplicates) */
    suspend fun mergePersons(personId1: String, personId2: String): Person
    
    /** Delete a person */
    suspend fun deletePerson(personId: String)
    
    /** Rename a person */
    suspend fun renamePerson(personId: String, newName: String): Person
}
```

---

## Infrastructure

### `ArcFaceEmbeddingAdapter.kt`

Follows the same patterns as `YoloFaceDetectionService`:

```kotlin
class ArcFaceEmbeddingAdapter(
    private val modelResourcePort: ModelResourcePort,
    private val ortSessionFactory: OrtSessionFactory,
    private val appLogger: AppLogger? = null,
) : FaceEmbeddingPort {
    
    private val faceService: ArcFaceEmbeddingService? by lazy { initService() }
    
    // Input: 112×112×3 RGB face crop (ArcFace standard input size)
    // Output: 512-dim float vector (L2-normalized)
    
    override suspend fun extractEmbedding(faceImage: ProcessedImage): FaceEmbedding? { ... }
    override suspend fun extractEmbeddings(sourceImage: ProcessedImage, detectedFaces: List<DetectedFace>): List<FaceEmbedding> { ... }
    override fun isEmbeddingAvailable(): Boolean = modelResourcePort.isFaceEmbeddingModelAvailable()
    override suspend fun preload(): Boolean { ... }
}
```

**ONNX model options** (choose one):

| Model | Size | Accuracy | Speed | Embedding Dim |
|-------|------|----------|-------|---------------|
| ArcFace MobileFaceNet | ~8 MB | Good | Fast (5ms) | 512 |
| ArcFace-R50 | ~160 MB | Excellent | Medium (30ms) | 512 |
| ArcFace-R100 | ~250 MB | Best | Slow (80ms) | 512 |

**Current model**: ArcFace MobileFaceNet from the Hailo Model Zoo (~8 MB, 512-dim embeddings).
Downloaded as a zip archive and lazy-loaded on first use. See [MODEL_MANAGEMENT.md](./MODEL_MANAGEMENT.md)
for URL details and manual installation instructions.

**Recommendation**: ArcFace MobileFaceNet provides excellent accuracy (99.43% LFW) with a small
download size. ArcFace-R50 is available as a future "high accuracy" option.

### `JsonPersonDirectoryAdapter.kt`

```kotlin
class JsonPersonDirectoryAdapter(
    private val fileSystem: FileSystemPort,
    private val dispatcherProvider: DispatcherProvider,
) : PersonDirectoryPort {
    
    // Persists to ~/.petrie-importer/persons.json
    // Uses the same StateFlow pattern as SettingsAdapter
    // Max ~1000 persons reasonable for JSON; migrate to SQLite if needed
}
```

---

## Application Services

### `FaceGroupingService.kt`

Orchestrates the batch face grouping workflow:

```kotlin
class FaceGroupingService(
    private val faceDetectionPort: FaceDetectionPort,
    private val faceEmbeddingPort: FaceEmbeddingPort,
    private val personDirectoryPort: PersonDirectoryPort,
    private val imageProcessingPort: ImageProcessingPort,
) {
    /** Process a batch of images: detect faces, extract embeddings, group into persons.
     *  Returns FaceGroupingResult with clusters and suggestions. */
    suspend fun groupFacesInBatch(
        images: List<FilePath>,
        progressCallback: (FaceGroupingProgress) -> Unit = {},
    ): FaceGroupingResult
    
    /** Auto-tag detected faces using known persons from the directory.
     *  Returns suggested person names for each face, with confidence. */
    suspend fun autoTagFaces(
        faces: List<DetectedFaceWithEmbedding>,
    ): List<FaceSuggestion>
    
    /** Add a confirmed person identification to the directory.
     *  Learns the embedding for future auto-tagging. */
    suspend fun confirmPerson(
        personName: String,
        embedding: FaceEmbedding,
        sourcePath: String,
    ): Person
}
```

### `FaceGroupingResult.kt`

```kotlin
data class FaceGroupingResult(
    /** Groups of faces that likely belong to the same person */
    val clusters: List<FaceCluster>,
    
    /** Faces that couldn't be confidently grouped */
    val ungrouped: List<DetectedFaceWithEmbedding>,
    
    /** Suggestions for matching ungrouped faces to known persons */
    val suggestions: List<FaceSuggestion>,
    
    /** Statistics */
    val totalFaces: Int,
    val totalGroups: Int,
    val durationMs: Long,
)

data class FaceCluster(
    val representativeEmbedding: FaceEmbedding,
    val faces: List<DetectedFaceWithEmbedding>,
    val suggestedName: String? = null, // from person directory match
    val personId: String? = null,       // from person directory match
)

data class DetectedFaceWithEmbedding(
    val detectedFace: DetectedFace,
    val embedding: FaceEmbedding,
    val sourcePath: String,
)

data class FaceSuggestion(
    val face: DetectedFaceWithEmbedding,
    val person: Person,
    val confidence: Float, // cosine similarity
    val isAutoTaggable: Boolean, // confidence >= CONFIDENT_MATCH_THRESHOLD
)
```

---

## UI Components

### 1. People Screen (`PeopleScreen.kt`) — **Implemented**

A full application tab for browsing the person directory, searching by name, opening folders, and managing the face database.

```
┌─────────────────────────────────────────────────────────────┐
│  People                              [Settings] [Export] [+] │
├─────────────────────────────────────────────────────────────┤
│  🔍 [Search by name________________________________] [✕]     │
├─────────────────────────────────────────────────────────────┤
│  ┌─────────────────────────────────────────────────────┐    │
│  │ 👤 Grandma          47 photos · 5 refs    [📁]     │    │
│  └─────────────────────────────────────────────────────┘    │
│  ┌─────────────────────────────────────────────────────┐    │
│  │ 👤 Dad             32 photos · 4 refs    [📁]     │    │
│  └─────────────────────────────────────────────────────┘    │
│  ┌─────────────────────────────────────────────────────┐    │
│  │ 👤 Mom             28 photos · 3 refs    [📁]     │    │
│  └─────────────────────────────────────────────────────┘    │
│  ┌─────────────────────────────────────────────────────┐    │
│  │ 👤 Krys            15 photos · 2 refs    [📁]     │    │
│  └─────────────────────────────────────────────────────┘    │
│  ┌─────────────────────────────────────────────────────┐    │
│  │ 👤 Uncle Bob        8 photos · 1 ref               │    │
│  └─────────────────────────────────────────────────────┘    │
└─────────────────────────────────────────────────────────────┘
```

Features:
- **Search by name**: Filter the person directory by name
- **Person cards**: Show name, photo count, reference count, open folder button
- **Add person**: Manual add via dialog
- **Person detail dialog**: Rename, delete, merge, open folder, view source paths
- **Export/import**: Zip bundle of the person database
- **Settings**: Auto-detect faces on import, auto-identify faces

### 2. Face Suggestion Chip — (Phase 2)

A sidebar/panel in the metadata editor showing all known persons:

```
┌─────────────────────────────────────────┐
│  People                              + │
├─────────────────────────────────────────┤
│  👤 Grandma (47 photos)              ✏️ │
│  👤 Dad (32 photos)                   ✏️ │
│  👤 Mom (28 photos)                   ✏️ │
│  👤 Krys (15 photos)                  ✏️ │
│  👤 Uncle Bob (8 photos)              ✏️ │
├─────────────────────────────────────────┤
│  💡 Auto-detect faces in this photo    │
│  📋 Group faces across all photos      │
└─────────────────────────────────────────┘
```

- Shows thumbnail, name, photo count
- Click to rename
- Swipe/delete to remove person
- "Auto-detect" button per photo
- "Group faces" starts batch grouping

### 2. Face Suggestion Chip (`FaceSuggestChip.kt`)

Replaces the bare name input in `FaceSelectorOverlay` with auto-suggestions:

```
┌─────────────────────────────────┐
│  Tag this face:                  │
│                                  │
│  ┌──────────┐ ┌──────────┐     │
│  │ 👤 Dad   │ │ 👤 Mom    │     │
│  │  (92%)   │ │  (78%)    │     │
│  └──────────┘ └──────────┘     │
│  ┌──────────┐ ┌──────────────┐  │
│  │👤 Krys   │ │ ✏️ New name..│  │
│  │  (71%)   │ │              │  │
│  └──────────┘ └──────────────┘  │
└─────────────────────────────────┘
```

- Shows known persons sorted by similarity
- Confidence percentage shown
- Tap to accept suggestion
- "New name..." option always available
- Below `CONFIDENT_MATCH_THRESHOLD`: shown as "suggestion" (yellow)
- Above `CONFIDENT_MATCH_THRESHOLD`: shown as "auto-tag" (green)

### 3. Batch Grouping View (`BatchGroupingView.kt`)

Modal/dialog for grouping faces across all photos in a batch:

```
┌───────────────────────────────────────────────────────────┐
│  Group Faces Across Photos                              ✕ │
├───────────────────────────────────────────────────────────┤
│  🔍 Processing... 23 of 47 faces                        │
│  ████████████░░░░░░░░░░░░░░░░░░░░░░░ 49%              │
├───────────────────────────────────────────────────────────┤
│  👤 Grandma (12 faces, 5 photos)         [Merge] [Delete] │
│     photo_001.jpg · photo_003.jpg · photo_007.jpg · +2   │
│     photo_012.jpg · photo_015.jpg                         │
│                                                           │
│  👤 Unknown Group 2 (7 faces, 3 photos)                   │
│     📝 Name: [____________]  [✓ Accept]                   │
│     photo_002.jpg · photo_008.jpg · photo_019.jpg         │
│                                                           │
│  👤 Dad (9 faces, 4 photos)             [Merge] [Delete] │
│                                                           │
│  ⚠️ Ungrouped (3 faces)                                  │
│     photo_005_face3 · photo_011_face1 · photo_022_face1   │
└───────────────────────────────────────────────────────────┘
```

- Shows each detected cluster with face count and photo count
- Auto-names clusters that match known persons
- "Unknown Group" clusters need manual naming
- "Merge" consolidates two persons
- Progress bar for batch processing

---

## Workflows

### Workflow 1: Single-Photo Auto-Tag

```
1. User opens EditScreen for a photo in the scan wizard
2. User taps "Tag Photo" → FaceSelectorOverlay opens
3. User taps "Auto-Detect" → YOLO detects face bounding boxes
4. FaceDetectionPort → List<DetectedFace>
5. For each DetectedFace:
   a. Crop face from source image via ImageProcessingPort
   b. FaceEmbeddingPort.extractEmbedding(crop) → FaceEmbedding
   c. PersonDirectoryPort.findMatch(embedding) → Person? (with confidence)
6. Show chips:
   - Above CONFIDENT_MATCH_THRESHOLD → auto-fill name, auto-add to subjects
   - Below threshold but above SAME_PERSON_THRESHOLD → show as suggestion
   - No match → show "New name..." input
7. User confirms/corrects each face tag
8. On confirm: FaceGroupingService.confirmPerson() → add embedding to person directory
```

### Workflow 2: Batch Face Grouping

```
1. User taps "Group Faces" in the overview or edit screen
2. FaceGroupingService.groupFacesInBatch(allSourceImages) is called
3. For each source image:
   a. Detect faces (YOLO) → List<DetectedFace>
   b. Crop face regions via ImageProcessingPort
   c. Extract embeddings via FaceEmbeddingPort
   d. Progress callback: "Processing 23 of 47 faces..."
4. Cluster all embeddings using agglomerative clustering:
   - Group embeddings with cosine similarity > SAME_PERSON_THRESHOLD
   - Create FaceCluster for each group
5. Match clusters against PersonDirectory:
   - Find best-matching known person for each cluster's centroid
6. Return FaceGroupingResult with:
   - Named clusters (matched to known persons)
   - Unknown clusters (need naming)
   - Ungrouped faces (low quality or unique)
7. Show BatchGroupingView for user review
8. User names unknown persons, merges duplicates
9. All confirmed names are written back to each photo's PhotoScanConfiguration
10. Person directory is updated with new embeddings
```

### Workflow 3: Inherit + Auto-Tag

```
1. User opens EditScreen for a photo that inherits XMP face regions from source
2. Inherited face regions have names but no embeddings yet
3. FaceGroupingService extracts embeddings for inherited regions
4. PersonDirectoryPort.findMatch() for each inherited face:
   a. If confident match → auto-confirm name in person directory
   b. If no match → create new person in directory with inherited name
5. Future photos with similar faces now auto-suggest these persons
```

---

## Implementation Phases

### Phase 1: Face Embedding Extraction (3-5 hours)

**Goal:** Extract a 128-dim embedding from any detected face, compare two embeddings.

| File | Action | Purpose |
|------|--------|---------|
| `domain/model/FaceEmbedding.kt` | **Create** | FaceEmbedding data class with cosineSimilarity() |
| `domain/model/Person.kt` | **Create** | Person data class with embedding list + match methods |
| `domain/model/PersonDirectory.kt` | **Create** | PersonDirectory collection with findBestMatch() |
| `domain/port/FaceEmbeddingPort.kt` | **Create** | Interface for embedding extraction |
| `domain/port/PersonDirectoryPort.kt` | **Create** | Interface for person directory persistence |
| `domain/port/ModelResourcePort.kt` | **Modify** | Add `faceEmbeddingModelStream()`, `isFaceEmbeddingModelAvailable()` |
| `infrastructure/adapter/ClasspathModelResourceAdapter.kt` | **Modify** | Add face embedding model loading (lazy, optional) |
| `infrastructure/photoscan/yolo/ArcFaceEmbeddingService.kt` | **Create** | ONNX inference service for face embeddings |
| `infrastructure/photoscan/FaceEmbeddingAdapter.kt` | **Create** | FaceEmbeddingPort adapter (crop face, resize, extract, return) |
| `infrastructure/adapter/JsonPersonDirectoryAdapter.kt` | **Create** | PersonDirectoryPort adapter (JSON persistence) |
| `di/AppModule.kt` | **Modify** | Register FaceEmbeddingPort, PersonDirectoryPort |

**Model**: ArcFace MobileFaceNet (~8 MB ONNX model, 512-dim embeddings, ~5ms inference).
Lazy-downloaded from the Hailo Model Zoo on first use.
See [MODEL_MANAGEMENT.md](./MODEL_MANAGEMENT.md) for manual installation.

**Deliverable**: Unit tests for `FaceEmbedding.cosineSimilarity()`, `Person.isLikelyMatch()`, `PersonDirectory.findBestMatch()`. Integration test with ONNX model.

### Phase 2: Auto-Suggest in Face Selector (4-6 hours)

**Goal:** When user auto-detects faces in the scan wizard, suggest known person names.

| File | Action | Purpose |
|------|--------|---------|
| `application/FaceGroupingService.kt` | **Create** | Orchestrate detect → embed → match → suggest |
| `domain/model/FaceGroupingResult.kt` | **Create** | Result types for grouping/suggesting |
| `ui/wizard/state/FaceRegionState.kt` | **Modify** | Add `autoTagFaces()` method that uses FaceGroupingService |
| `ui/wizard/FaceSuggestChip.kt` | **Create** | Composable for person suggestion chips |
| `ui/wizard/FaceSelectorOverlay.kt` | **Modify** | Show suggestion chips after auto-detect, integrate naming cycle |
| `di/AppModule.kt` | **Modify** | Register FaceGroupingService |

**Key change to FaceSelectorOverlay flow**:

Current flow:
```
Auto-Detect → YOLO boxes → (unnamed) → Naming Cycle (type name for each)
```

New flow:
```
Auto-Detect → YOLO boxes → Extract Embeddings → Match PersonDirectory →
  Confident match (≥0.75): Auto-fill name, grey chip "Dad (92%)"
  Possible match (≥0.65): Show as suggestion, yellow chip "Mom (71%)"
  No match: "New name..." input
→ User confirms/corrects each → Embeddings added to PersonDirectory
```

**Deliverable**: Face selector shows auto-suggestions from person directory. Newly confirmed names are saved to person directory.

### Phase 3: Person Directory UI (4-6 hours)

**Goal:** Manage known persons — view, rename, merge, delete.

| File | Action | Purpose |
|------|--------|---------|
| `ui/screens/PersonDirectoryPanel.kt` | **Create** | Sidebar panel showing all known persons |
| `ui/screens/PersonDetailDialog.kt` | **Create** | Dialog for viewing/renaming/deleting a person |
| `ui/screens/MediaImportScreen.kt` | **Modify** | Add "People" tab or panel access point |
| `domain/model/AppSettings.kt` | **Modify** | Consider embedding PersonDirectory reference or keeping separate |

**UI design**:
- Person directory panel accessible from sidebar or toolbar
- Shows thumbnail, name, photo count for each person
- Swipe/delete to remove, double-click to rename
- "Merge" action when two persons are selected

**Deliverable**: Person directory panel in UI, persisted across sessions.

### Phase 4: Batch Face Grouping (6-8 hours)

**Goal:** Group all faces across all photos in a batch, name unknown persons, auto-tag known ones.

| File | Action | Purpose |
|------|--------|---------|
| `application/FaceGroupingService.kt` | **Modify** | Add `groupFacesInBatch()` method |
| `domain/model/FaceCluster.kt` | **Create** | FaceCluster with centroid embedding, suggested name |
| `ui/wizard/BatchGroupingView.kt` | **Create** | Modal view for batch grouping review |
| `ui/wizard/OverviewScreen.kt` | **Modify** | Add "Group Faces" button |

**Clustering algorithm**: Agglomerative clustering with cosine distance. Two embeddings are linked if similarity > `SAME_PERSON_THRESHOLD`. A cluster becomes a "person" once it has ≥2 faces from ≥2 different photos (to avoid grouping similar faces from the same photo that are actually different people caught in similar angles).

**Deliverable**: "Group Faces" button in overview screen, batch grouping modal, cluster naming workflow.

### Phase 5: CLI Support (2-3 hours)

**Goal:** Face grouping and person directory management from the command line.

| File | Action | Purpose |
|------|--------|---------|
| `cli/FaceGroupCommand.kt` | **Create** | `photo-import group-faces <directory>` command |
| `cli/PersonCommand.kt` | **Create** | `photo-import person list/rename/merge/delete` subcommands |
| `cli/PhotoImportCli.kt` | **Modify** | Register new subcommands |

**CLI usage**:
```bash
# Detect and group faces across all images in a directory
photo-import group-faces ~/Pictures/Vacation/ --threshold 0.65 --output groups.json

# List known persons
photo-import person list

# Rename a person
photo-import person rename "Grandma" "Grandma Ruth"

# Merge two persons
photo-import person merge "Dad" "Father"

# Auto-tag all images in directory using person directory
photo-import tag-faces ~/Pictures/Vacation/ --auto-tag --confidence 0.75
```

**Deliverable**: Two new CLI subcommands for face grouping and person management.

---

## Technical Considerations

### ONNX Model Selection

| Model | Params | Embedding Dim | Input Size | ONNX Size | Accuracy (LFW) | Inference |
|-------|--------|---------------|------------|-----------|-----------------|-----------|
| ArcFace MobileFaceNet | 2M | 512 | 112×112 | ~8 MB | 99.43% | ~5ms |
| ArcFace-R50 | 36M | 512 | 112×112 | ~160 MB | 99.8% | ~30ms |
| ArcFace-R100 | 79M | 512 | 112×112 | ~250 MB | 99.8% | ~80ms |

**Current model**: ArcFace MobileFaceNet from Hailo Model Zoo (~8 MB, 512-dim). Downloaded as a
zip archive and extracted automatically. See [MODEL_MANAGEMENT.md](./MODEL_MANAGEMENT.md).

**Recommendation**: ArcFace MobileFaceNet offers the best accuracy/size tradeoff. Power users can
swap in ArcFace-R50 later via model download.

### Face Crop Preprocessing

Before embedding extraction, each detected face region must be:

1. **Crop** from source image using the detected bounding box
2. **Align** (optional): ArcFace standard alignment uses 5-point facial landmarks. Without alignment, accuracy drops ~2-3%. For ArcFace MobileFaceNet, simple center-crop is sufficient.
3. **Resize** to 112×112 pixels (ArcFace/MobileFaceNet standard input)
4. **Normalize** to `[−1, 1]` float range (NCHW format)

The preprocessing pipeline can reuse `YoloPreprocessing.preprocessCrop()` with 112×112 size target.

### Threshold Tuning

The `SAME_PERSON_THRESHOLD` (default 0.65) and `CONFIDENT_MATCH_THRESHOLD` (default 0.75) need empirical tuning:

- **0.60-0.65**: Liberal matching — fewer ungrouped faces but more false positives (different people grouped together)
- **0.70-0.75**: Balanced — good for family photos where people look different enough
- **0.80-0.85**: Conservative — more ungrouped faces but very high confidence for auto-tagging

The thresholds should be configurable in `AppSettings` so users can tune for their photo library.

### Privacy

- **All processing is local** — no cloud APIs, no data sent off-device
- **Embeddings are stored locally** — in `~/.petrie-importer/persons.json`
- **Face crops are not stored** — only the numeric embedding vector and a thumbnail path
- **Person directory is user-controlled** — users can delete persons at any time
- **GDPR consideration** — Face embeddings are biometric data. The UI should clearly explain what's stored and allow bulk deletion.

### Persistence: JSON vs SQLite

| Concern | JSON | SQLite |
|---------|------|--------|
| Under 100 persons | ✅ Fast enough | Overkill |
| Over 100 persons | ⚠️ Slow linear scan | ✅ Index on embedding? |
| Embedding vectors | ⚠️ 512 floats × 20 embeddings × 100 persons = ~2 MB | ✅ BLOB column |
| Embedding search | ⚠️ O(n×m) brute force cosine | ⚠️ Still O(n×m), but faster I/O |
| Migration needed | No | Yes |

**Recommendation**: Start with JSON (consistent with SettingsAdapter pattern). If person directories grow beyond 200 persons with 20+ embeddings each, migrate to SQLite with an embedding BLOB column and brute-force cosine similarity search. For reference, brute-force cosine similarity over 1000 persons × 20 embeddings = 20,000 comparisons ≈ 2ms in Kotlin.

### Future: Approximate Nearest Neighbor

For very large person directories (>500 persons), brute-force cosine search becomes slow. Consider:

1. **FAISS (Facebook AI Similarity Search)** — native library, requires JNI
2. **Hierarchical Navigable Small World (HNSW)** — pure Kotlin implementation, ~100 lines
3. **Product Quantization** — compress embeddings to 64 bytes, search in compressed space

These are **not needed for Phase 1-4** but are noted for future scaling.

---

## File Summary — All New and Modified Files

### New Files (12)

| File | Layer | Phase |
|------|-------|-------|
| `domain/model/FaceEmbedding.kt` | Domain | 1 |
| `domain/model/Person.kt` | Domain | 1 |
| `domain/model/PersonDirectory.kt` | Domain | 1 |
| `domain/model/FaceGroupingResult.kt` | Domain | 4 |
| `domain/port/FaceEmbeddingPort.kt` | Domain | 1 |
| `domain/port/PersonDirectoryPort.kt` | Domain | 1 |
| `infrastructure/photoscan/yolo/ArcFaceEmbeddingService.kt` | Infra | 1 |
| `infrastructure/photoscan/FaceEmbeddingAdapter.kt` | Infra | 1 |
| `infrastructure/adapter/JsonPersonDirectoryAdapter.kt` | Infra | 1 |
| `application/FaceGroupingService.kt` | App | 2 |
| `ui/wizard/FaceSuggestChip.kt` | UI | 2 |
| `ui/wizard/BatchGroupingView.kt` | UI | 4 |

### Modified Files (9)

| File | Change | Phase |
|------|--------|-------|
| `domain/port/ModelResourcePort.kt` | Add face embedding model methods | 1 |
| `infrastructure/adapter/ClasspathModelResourceAdapter.kt` | Add face embedding model loading | 1 |
| `ui/wizard/state/FaceRegionState.kt` | Add auto-tag integration | 2 |
| `ui/wizard/FaceSelectorOverlay.kt` | Show suggestion chips | 2 |
| `domain/model/AppSettings.kt` | Optional: reference PersonDirectory | 3 |
| `ui/screens/MediaImportScreen.kt` | Add "People" panel access | 3 |
| `di/AppModule.kt` | Register new ports/services | 1-4 |
| `cli/PhotoImportCli.kt` | Register face CLI subcommands | 5 |
| `cli/ScanPresets.kt` or new `FaceGroupCommand.kt` | CLI face grouping | 5 |

---

## Estimated Effort

| Phase | Hours | Deliverable |
|-------|-------|-------------|
| Phase 1: Embedding extraction | 3-5 hr | FaceEmbedding data model + ONNX inference + PersonDirectory persistence |
| Phase 2: Auto-suggest in face selector | 4-6 hr | Suggestion chips after auto-detect, PersonDirectory auto-learn |
| Phase 3: Person directory UI | 4-6 hr | Person management panel, rename/merge/delete |
| Phase 4: Batch face grouping | 6-8 hr | Cross-photo clustering, batch grouping modal |
| Phase 5: CLI support | 2-3 hr | `group-faces` and `person` CLI commands |
| **Total** | **19-28 hr** | |

---

## Dependencies

| Dependency | Version | Purpose | Size |
|-----------|---------|---------|------|
| ONNX Runtime | Already in project | Run face embedding model | 0 (existing) |
| ArcFace MobileFaceNet ONNX model | ~8 MB | Face embedding extraction | Lazy download (zip) |
| BoofCV | Already in project | Affine face alignment (optional) | 0 (existing) |
| Apache Commons Imaging | Already in project | XMP face region I/O | 0 (existing) |
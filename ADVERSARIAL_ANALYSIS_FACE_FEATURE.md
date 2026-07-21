# 🔍 Adversarial Analysis — Face Detection, Tagging & Person Library

**Date:** 2026-07-20  
**Scope:** Face detection, face embedding extraction, person directory, name tagging suggestions, face library view, and text-based search  
**Prior report:** `ADVERSARIAL_ANALYSIS.md` (general, 2025-07-18 — 25 issues addressed)  

---

## Executive Summary

The face identification feature is architecturally solid — clean hexagonal design, mutex-protected mutations, cross-model safety guards, diversity-aware gallery eviction, and GDPR privacy controls. However, this adversarial pass found **6 critical bugs, 8 important gaps, and 9 usability/quality issues** that would cause real user pain in production. The most impactful are: quality-filtered embeddings silently poisoning matching, gallery overflow when `maxGallerySize` changes, face thumbnails never rendered, no text-tag search, and no batch face-grouping workflow.

---

## 🔴 Critical Bugs

### C1. `isUsableForMatching()` is defined but never called — low-quality embeddings poison the gallery

**Files:** `FaceEmbedding.kt:224`, `PersonDirectory.kt:findBestMatch()`, `FaceGroupingService.kt`

`FaceEmbedding.isUsableForMatching()` checks `quality >= 0.3f && vector.isNotEmpty()` and the docs say embeddings below `MIN_QUALITY_FOR_MATCHING` "should not be used for identification." However:

- `PersonDirectory.findBestMatch()` computes `matchScore()` on every gallery embedding, regardless of quality
- `Person.withEmbedding()` adds embeddings to the gallery without any quality filter
- `FaceGroupingService.confirmIdentification()` passes embeddings through without filtering
- `FaceEmbeddingAdapter` sets `quality = faceRegion.confidence`, which can be as low as the detection threshold (0.5)

**Impact:** A blurry/occluded face with detection confidence 0.51 gets embedded and stored permanently. That bad embedding inflates `matchScore()` for wrong people, causing false positives that get worse over time. There's no way to remove a single bad embedding from the UI.

**Fix:** Filter in two places:
1. In `PersonDirectory.findBestMatch*()` — skip gallery entries where `!it.isUsableForMatching()`
2. In `Person.withEmbedding()` — reject embeddings below `MIN_QUALITY_FOR_MATCHING` (or at least warn)
3. In `FaceGroupingService.detectAndSuggest()` — skip `isUsableForMatching() == false` embeddings before matching

---

### C2. Gallery overflow when `maxGallerySize` is decreased — stale embeddings never evicted

**Files:** `Person.kt:withEmbedding()`, `FaceMatchingConfig.maxGallerySize`

`withEmbedding()` evicts the most redundant embedding only when `gallery.size >= maxGallerySize`. But `maxGallerySize` is configurable (`FaceMatchingConfig.maxGallerySize`, default 20). If a user:

1. Has a person with 20 embeddings (gallery at capacity)
2. Decreases `maxGallerySize` to 10 in settings
3. Adds a new embedding

Then `withEmbedding()` sees `gallery.size (20) >= maxGallerySize (10)`, evicts only **one** redundant embedding, and the gallery ends up with 20 embeddings — 10 over the new limit.

The gallery size is only enforced on **insertion**, never on **read or configuration change**.

**Impact:** Users who decrease the gallery limit expect immediate reduction. Instead, stale oversized galleries persist indefinitely and slow matching (O(P × G × D) where G is oversized).

**Fix:** Add a `trimToSize(maxGallerySize: Int)` method on `Person` that evicts the most redundant embeddings down to the limit. Call it:
- In `PersonService.getMatchingConfig()` when the config changes (reactive)
- Or in `PersonDirectory.findBestMatch*()` on access (lazy)
- Or add a UI "Apply gallery size limit" action

---

### C3. Source path deduplication in `withEmbedding()` only works if `sourcePath` is non-empty — re-importing the same photo creates duplicate gallery entries

**Files:** `Person.kt:withEmbedding()`

```kotlin
if (embedding.sourcePath.isNotEmpty()) {
    val duplicate = gallery.find { existing ->
        existing.sourcePath == embedding.sourcePath &&
            existing.cosineSimilarity(embedding) > 0.99f
    }
    if (duplicate != null) return this
}
```

If `sourcePath` is empty (which happens when the embedding is created without a path, or the path is lost), the dedup check is **completely skipped**. A user who auto-detects faces on the same photo twice will get duplicate gallery entries.

**Impact:** Gallery fills up with redundant embeddings from the same photo, reducing diversity and wasting the 20-slot budget. After 10 re-imports of the same photo, half the gallery could be duplicates.

**Fix:** Add a fallback dedup check when `sourcePath` is empty — compare embeddings by cosine similarity alone (threshold 0.99). Or better: always compute cosine dedup regardless of `sourcePath`, and only use `sourcePath` as an additional fast-path filter.

---

### C4. `FaceEmbedding.equals()` decodes the lazy `vector` property on every comparison — O(D) per equality check

**Files:** `FaceEmbedding.kt:235-250`

The custom `equals()` override ends with `vector.contentEquals(other.vector)` which forces lazy decoding of the Base64 `vectorBase64` for **both** sides. In a `PersonDirectory` with 100 persons × 20 embeddings, a `findBestMatch` scan triggers `cosineSimilarity()` (which also decodes `vector`) — that's fine. But `equals()` is also called by Kotlin collections operations (`distinct()`, `contains()`, `Set` operations) and by serialization round-trip validation, where each call pays the Base64 decode cost.

More critically, **`Person.withEmbedding()` and `mergeGallery()` call `cosineSimilarity()` within `find`/`maxOfOrNull` lambdas** which already decode `vector`. The `equals()` issue is a performance concern, but the real bug is: **if `vector` is decoded in one thread while another thread mutates the `Person` data class via `copy()`, the `lazy` delegate is not thread-safe.** The `lazy` delegate uses `LazyThreadSafetyMode.SYNCHRONIZED` by default (safe), but `copy()` creates a new instance with a fresh lazy delegate — so simultaneous reads of `vector` on the old and new instance are fine, but simultaneous `equals()`/`hashCode()` on the same instance during a mutation window could double-decode.

**Fix:** Use `lazy(LazyThreadSafetyMode.NONE)` since `FaceEmbedding` is a data class (val-only, effectively immutable after construction). Or cache the decoded vector in a `@Volatile` field instead of `lazy` to avoid synchronization overhead entirely.

---

### C5. Gallery diversity eviction — `maxByOrNull` on "most redundant" is O(G² × D) per insertion, and incorrect for identical galleries

**Files:** `Person.kt:withEmbedding()` (eviction), `Person.kt:mergeGallery()`

The eviction logic finds the gallery entry with the highest max-similarity to any other gallery member:

```kotlin
val mostRedundantIdx = gallery.indices.maxByOrNull { idx ->
    gallery.filterIndexed { i, _ -> i != idx }
        .maxOfOrNull { gallery[idx].cosineSimilarity(it) } ?: 0f
} ?: -1
```

This is O(G² × D) per insertion. For G=20, D=128, this is 20×19×128 ≈ 48K operations per embedding addition. Acceptable, but:

**Bug:** If all gallery embeddings are near-identical (cosine similarity ≈ 1.0 for all pairs), `maxOfOrNull` returns ≈1.0 for every index, and `maxByOrNull` picks an arbitrary one (likely the first). This is correct (any one is fine to evict), but **it evicts the old embedding, not the new one**. The new embedding is then compared against the surviving 19 old identical ones. If the new embedding is slightly different (a better angle), we've kept 19 near-identical old embeddings and evicted the one most similar to them — which is the most redundant among the 20, but we might prefer evicting the oldest or lowest-quality one.

**Better fix:** When evicting, prefer evicting the embedding with the lowest quality score as a tiebreaker among equally-redundant entries.

---

### C6. `FaceGroupingService.detectAndSuggest()` reads `config` and `directory` from two separate StateFlows — TOCTOU race

**Files:** `FaceGroupingService.kt:59-65`

```kotlin
val config = personService.getMatchingConfig()      // Reads settings StateFlow
val directory = personService.directory.value        // Reads directory StateFlow
```

These are two separate reads. If a user changes the `matchThreshold` from 0.65 → 0.80 between the two reads (or another coroutine updates the directory), the config and directory may be inconsistent: the config says threshold=0.80 but the directory includes a person that was matched at 0.65.

**Impact:** Rare but possible — a face that should match at 0.70 may be rejected because the config changed mid-scan, or a face that should be auto-tagged at 0.80 may get auto-tagged because the old threshold was 0.65.

**Fix:** Read both atomically, or accept the minor inconsistency (matching is approximate anyway). Document the race as acceptable for now.

---

## 🟠 Important Gaps & Logic Issues

### I1. No text-tag/keyword search for photos — People screen only searches person names

**Files:** `PeopleScreen.kt`, `PersonService.kt:searchPersonsByName()`

The People screen has a "Search by name" text field that calls `searchPersonsByName(query, directory)`, which only filters `Person.name.contains(query, ignoreCase = true)`. There is **no way to search for photos by text tags** (e.g., "birthday", "beach", "vacation"), even though XMP metadata supports keyword/tags.

The user's request specifically mentions: "We should likely be able to search by text tags as well." This is completely missing.

**Fix:** Add:
1. A `PersonDirectory.searchByKeyword(query)` that searches `Person.sourcePaths` entries for files matching a tag/keyword (requires an index of file → keywords)
2. A tab/toggle in `PeopleScreen` to switch between "Search by person name" and "Search by keyword/tag"
3. Consider integrating with existing `MetadataEditService` or `XmpMetadataWriter` to read XMP keywords from source files

---

### I2. No face thumbnail display — Person cards show a placeholder icon instead of an actual face crop

**Files:** `PeopleScreen.kt:381`, `PersonService.kt:318-321`

```kotlin
// TODO(#16): Load face crop thumbnail from person.thumbnailPath
//   when ThumbnailGenerator is integrated. For now, show a placeholder icon.
```

The `Person.thumbnailPath` field exists but is never populated — no code ever sets it. The `getGallerySources()` method returns source image paths, but the UI doesn't crop faces from them. Users see a generic `Icons.Default.Person` icon for every person, making the People screen nearly useless for visual identification.

**Impact:** This is the most visible gap. The entire point of a face library is to **see faces**, not icon placeholders.

**Fix:**
1. In `FaceEmbeddingAdapter`, when extracting an embedding, also save a face crop image to `~/.petrie-importer/people/thumbnails/{personId}_{embeddingId}.jpg`
2. Set `Person.thumbnailPath` to the most recent crop path
3. In `PeopleScreen`, load and display the thumbnail image using `ThumbnailImage` or `Image` composable with `ImageIO.read()`

---

### I3. Gallery has no quality-aware ordering or pruning — embeddings are added chronologically, not ranked

**Files:** `Person.kt`

The `Person.gallery` docs say "Ordered by quality descending" but `withEmbedding()` appends to the end and the diversity-eviction logic doesn't sort by quality. After multiple insertions and evictions, the gallery order is essentially arbitrary — the highest-quality embedding may be the 15th entry.

The `Person.matchScore()` method uses `maxOf { it.cosineSimilarity(candidate) }` which is correct, but if we ever switch to a top-K approach, order matters.

More importantly: there's no way to **prune low-quality embeddings** from the gallery. A person with 20 embeddings where 10 are from blurry photos has less accurate matching than a person with 10 high-quality embeddings.

**Fix:** After eviction, sort the gallery by `quality` descending. Add a `pruneLowQuality(minQuality: Float)` method.

---

### I4. No batch face grouping workflow — each photo must be processed individually

**Files:** `FaceGroupingService.kt`, plan-face-grouping.md Phase 4

The plan document describes a "Group Faces" batch workflow (Phase 4) that would detect faces across all photos, cluster them, and suggest naming. This is **not implemented**. The current flow requires the user to:

1. Open each photo individually in the Edit screen
2. Click "Auto-Detect" to find faces
3. Review and confirm each face name
4. Repeat for every photo

For a library of 500+ photos, this is impractical. The `FaceGroupingService` has `detectAndSuggest()` for single images but no `groupFacesInBatch()` method.

**Fix:** Implement batch face grouping:
1. Add `FaceGroupingService.groupFacesInBatch(imagePaths: List<String>)` that processes all images and clusters embeddings
2. Add a UI view (`BatchGroupingView`) showing clusters with representative faces
3. Allow naming entire clusters at once

---

### I5. `PersonService.findImagesForPerson()` returns stored paths, not actual file existence checks

**Files:** `PersonService.kt:297-302`

```kotlin
fun findImagesForPerson(personId: String, dir: PersonDirectory): List<String> {
    val person = dir.personById(personId) ?: return emptyList()
    return person.sourcePaths
}
```

This returns `sourcePaths` directly without checking if the files still exist. If a user moves, renames, or deletes photos, the `sourcePaths` become stale. There's no cleanup mechanism.

**Impact:** "Open folder" action in PeopleScreen opens the parent of a non-existent file, which may fail silently (the `validateAndOpenFolder` method handles this gracefully, but the user sees paths to files that no longer exist).

**Fix:** 
1. Add a `validateSourcePaths()` method that checks file existence and removes stale paths
2. Run it periodically (on app start, or on People screen open)
3. Show a warning badge on person cards with stale paths

---

### I6. Import doesn't auto-detect faces even when `autoDetectFacesOnImport` is true

**Files:** `AppSettings.kt:autoDetectFacesOnImport`, `WizardImportHandler.kt`

The `autoDetectFacesOnImport` setting exists in `AppSettings` and has a toggle in the People Settings dialog, but the import flow (`WizardImportHandler`, `ImportService`) does **not** check this setting. The only auto-detection happens when the user manually clicks "Auto-Detect" in the Face Selector Overlay.

**Impact:** Users who enable "Auto-detect faces on import" expect faces to be detected automatically during import, but nothing happens.

**Fix:** In `WizardImportHandler` or `ImportExecutor`, check `settings.autoDetectFacesOnImport` after importing each image and run `FaceGroupingService.detectAndSuggest()` to add source paths and embeddings to the person directory.

---

### I7. No undo for face identifications — confirming a wrong name permanently pollutes the gallery

**Files:** `EditScreen.kt:onNameConfirmed`, `FaceGroupingService.confirmIdentification()`

When a user confirms a face identification, the embedding is immediately added to the person's gallery via `confirmIdentification()`. There is no undo. If the user makes a mistake:

1. The wrong embedding is now part of the person's gallery
2. Future auto-suggestions will be biased toward the wrong person
3. The only way to remove it is to clear **all** embeddings for that person (`clearEmbeddings`)

There's no `removeEmbedding(personId, embeddingId)` method and no UI for removing a single embedding.

**Fix:**
1. Add `PersonService.removeEmbedding(personId, embeddingId)` with mutex protection
2. Show a per-embedding list in the Person Detail dialog with a delete button
3. Consider an undo stack for confirmations (similar to `MetadataEditUndoService`)

---

### I8. `PersonDirectory.findBestMatchWithScore()` returns only the best match — user never sees alternatives

**Files:** `PersonDirectory.kt:findBestMatchWithScore()`, `FaceGroupingService.kt`

When a face is detected, only the single best-matching person is returned. If two people look similar (e.g., siblings), the user only sees one suggestion. If the best match is wrong (above `matchThreshold` but below `autoTagThreshold`), the user has no way to see that a second person was almost as good a match.

**Impact:** Siblings, parent-child pairs, and people who look alike will be misidentified. The fix is simple: show the top-N matches.

**Fix:**
1. In `FaceGroupingService.detectAndSuggest()`, call `directory.findAllMatches()` instead of `findBestMatchWithScore()` and return the top 3 matches
2. In `FaceSuggestion`, add a `List<Pair<Person, Float>>` of alternative matches
3. In the UI, show "Did you mean…?" with the top 3 names when confidence is between `matchThreshold` and `autoTagThreshold`

---

## 🟡 Usability & Quality Issues

### U1. No progress indication for face detection or embedding extraction

**Files:** `EditScreen.kt:autoDetectFaces callback`

When the user clicks "Auto-Detect", the face detection and embedding extraction run in a coroutine with no progress indicator (no spinner, no "Detecting faces…" text). For large images or slow CPUs, this can take 1-5 seconds where the UI appears frozen.

**Fix:** Show a `CircularProgressIndicator` or "Detecting faces…" text while the coroutine is running.

---

### U2. No visual distinction between "confident match" and "potential match" in the Face Selector

**Files:** `EditScreen.kt:367-368`

```kotlin
val name = suggestion.suggestedPerson?.name
if (name != null && suggestion.isConfident) {
```

Confident auto-tags fill the name directly, but potential matches (below `autoTagThreshold` but above `matchThreshold`) are offered as `nameSuggestions` — a simple map of `Int → String`. There's no visual distinction between "we're 92% sure this is Dad" and "this might be Dad (67%)" in the UI. The user sees the same yellow suggestion chip for both.

**Fix:** Show confidence percentage in the suggestion chip. Use green for confident (>0.75), yellow for potential (0.65-0.75), and grey for no match.

---

### U3. People screen search is instant but no debouncing — unnecessary re-computations on every keystroke

**Files:** `PeopleScreen.kt:146`

```kotlin
val filteredPersons = if (searchQuery.isBlank()) {
    directory.persons
} else {
    personService.searchPersonsByName(searchQuery, directory)
}
```

`searchPersonsByName` runs on every recomposition. For a directory with 500 persons, this creates a new filtered list on every keystroke (including intermediate states like "Da", "Dad", "Dad" as the user types). While not a performance bottleneck at current scale, it's wasteful.

**Fix:** Add debounced search (300ms delay after last keystroke) with a `LaunchedEffect`.

---

### U4. Merge confirmation dialog doesn't show consequences — which gallery entries will be removed

**Files:** `PeopleScreen.kt:MergePersonDialog`

The merge dialog says "Merge all face references and photos from another person into [name]" but doesn't show:
- How many gallery entries will be kept vs. evicted (due to `maxGallerySize`)
- Which source paths will be merged
- That the source person will be **permanently deleted**

**Fix:** Show a preview: "Alice (15 refs + 47 photos) ← Bob (8 refs + 23 photos). After merge: 20 refs, 70 photos. Bob will be deleted."

---

### U5. Export doesn't offer a default path — user must type or browse

**Files:** `PeopleScreen.kt:ExportImportDialog`

The export path field starts empty and the user must manually type a path or click Browse. There's no default path (like `~/.petrie-importer/people-export/` or `~/Desktop/people-database.zip`).

**Fix:** Default the export path to `Platform.appDataDir/people-export` or the desktop.

---

### U6. No way to view or navigate to photos containing a specific person — "Open folder" opens the first source path's parent

**Files:** `PeopleScreen.kt:onOpenFolder`, `PersonService.kt:validateAndOpenFolder`

Clicking the folder icon opens `person.sourcePaths.firstOrNull()`'s parent. If the person appears in 50 photos across 10 different folders, only the first folder opens. There's no way to see all photos, filter by person, or navigate to specific photos.

**Fix:**
1. Show the complete list of source paths (already partially done — capped at 20)
2. Add a "Show in library" button that opens the Metadata Editor filtered to that person's photos
3. Consider a "Photos of this person" grid view

---

### U7. `Person.name` defaults to `""` — deserializing JSON with missing name creates an invalid person

**Files:** `Person.kt`, `PersonDirectory.validateForImport()`

`Person(name = "")` passes construction but fails `validateName()` (which requires non-blank). If a JSON import has a person entry with a missing or null `name` field, `kotlinx.serialization` will deserialize it with `name = ""`, and `validateForImport()` will correctly reject it. However, `Person()` (no-arg constructor) creates a person with `name = ""`, and there's no guard in `PersonService.createPerson()` that would prevent creating such a person through code — only the UI dialog validates.

**Fix:** Make `Person.name` a `String` with a non-blank default (e.g., `"Unnamed"`) or add a `require(name.isNotBlank())` check in `Person`'s `init` block. At minimum, add a factory method `Person.create(name: String)` that validates.

---

### U8. Add Person dialog doesn't show the person after creation — user must scroll to find them

**Files:** `PeopleScreen.kt:AaddPersonDialog`

After creating a person, the dialog closes and the person appears in the list. But there's no scroll-to-new-person behavior, and if the list is long, the new person may be offscreen. There's also no way to immediately add an embedding to the new person — it's a shell with no face references.

**Fix:** After creation, select and scroll to the new person, and open the detail dialog.

---

### U9. No feedback when face embedding extraction fails silently

**Files:** `FaceEmbeddingAdapter.kt:75-79`

```kotlin
} catch (e: Exception) {
    appLogger.warn("Face embedding extraction failed for face at (${faceRegion.x1},${faceRegion.y1}): ${e.message}")
    null
}
```

If embedding extraction fails (ONNX error, face too small, etc.), it returns `null` and the user gets **no feedback**. The face is detected and shown but has no name suggestion. The user has no way to know whether embedding failed or simply found no match.

**Fix:** In `FaceSuggestion`, add a field for `extractionFailureReason: String?` and show a tooltip or subtle indicator on faces where extraction failed.

---

## 🧪 Test Gaps

### T1. No test for `Person.withEmbedding()` quality filtering (`isUsableForMatching`)

`PersonTest` tests gallery eviction, deduplication, and merging, but never tests what happens when a low-quality embedding (`quality < 0.3`) is added. Since `isUsableForMatching()` is never called in production code (see C1), this gap compounds the bug.

**Add tests:** Adding an embedding with `quality = 0.2f` should be rejected or marked unusable.

### T2. No test for `FaceMatchingConfig` threshold changes after gallery is populated

**No test verifies** that changing `matchThreshold` from 0.65 → 0.80 narrows matching, or that changing `maxGallerySize` from 20 → 10 should trigger eviction.

### T3. No test for `FaceGroupingService.extractAndMatch()` with non-empty `sourcePath`

The `extractAndMatch()` method has `sourcePath = ""` default and a doc comment saying "an empty sourcePath would break gallery dedup logic". There's no test verifying behavior when `sourcePath` is empty vs. non-empty.

### T4. No test for `PersonDirectory` import with malicious ZIP (path traversal, oversized entries)

The adapter validates ZIP entries for path traversal and size limits, but there's no test verifying these validations. The previous adversarial report flagged C7 (path traversal) but it may have been fixed — tests should confirm.

### T5. No UI/integration test for the `PeopleScreen` flow (create person → add embedding → search → rename → merge → delete)

All current tests are unit tests for domain models. The `PeopleScreen` composable has no UI test coverage.

### T6. No test for `Person.matchScore()` with cross-model embeddings

`FaceEmbedding.cosineSimilarity()` returns 0f when model names differ, but `Person.matchScore()` has no test where the gallery contains embeddings from different models (e.g., `mobilefacenet` and `arcface-r50`). This should return 0f for all comparisons but is untested.

### T7. No stress test for `PersonDirectory.findBestMatch()` performance at P=500, G=20

The architecture docs mention HNSW for P>500, but there's no benchmark or test at scale. A simple performance test would verify that matching completes in <100ms for 500 persons.

---

## 📋 Summary Table

| # | Severity | Category | Issue | Fix Effort |
|---|----------|----------|-------|------------|
| C1 | 🔴 Critical | Bug | `isUsableForMatching()` never called — low-quality embeddings poison gallery | Small |
| C2 | 🔴 Critical | Bug | Gallery overflow when `maxGallerySize` decreased — stale entries never evicted | Small |
| C3 | 🔴 Critical | Bug | Source path dedup skipped when empty — re-import creates duplicates | Small |
| C4 | 🔴 Critical | Perf/Bug | `equals()` triggers lazy Base64 decode; thread-safety concern | Small |
| C5 | 🔴 Critical | Logic | Diversity eviction picks arbitrary entry for identical-gallery case | Small |
| C6 | 🔴 Critical | TOCTOU | Config + directory read from separate StateFlows | Small |
| I1 | 🟠 Important | Gap | No text-tag/keyword search for photos | Medium |
| I2 | 🟠 Important | Gap | No face thumbnail display (TODO #16) — placeholder icon only | Medium |
| I3 | 🟠 Important | Gap | Gallery not quality-ordered or pruned | Small |
| I4 | 🟠 Important | Gap | No batch face grouping workflow — single-photo only | Large |
| I5 | 🟠 Important | Gap | `findImagesForPerson()` returns stale paths, no validation | Small |
| I6 | 🟠 Important | Bug | `autoDetectFacesOnImport` setting exists but import flow doesn't use it | Medium |
| I7 | 🟠 Important | Gap | No undo for face identifications — no `removeEmbedding()` | Medium |
| I8 | 🟠 Important | UX | Only best match shown — siblings/similar faces misidentified | Medium |
| U1 | 🟡 Minor | UX | No progress indicator for face detection/embedding | Small |
| U2 | 🟡 Minor | UX | No visual distinction between confident vs. potential match | Small |
| U3 | 🟡 Minor | UX | No search debouncing on People screen | Small |
| U4 | 🟡 Minor | UX | Merge dialog doesn't preview consequences | Small |
| U5 | 🟡 Minor | UX | Export has no default path | Small |
| U6 | 🟡 Minor | UX | "Open folder" only opens first path — no photo grid | Medium |
| U7 | 🟡 Minor | Logic | `Person(name="")` silently creates invalid person | Small |
| U8 | 🟡 Minor | UX | New person not auto-selected/ scrolled to | Small |
| U9 | 🟡 Minor | UX | Silent embedding extraction failure — no user feedback | Small |

---

## Recommended Priority Order

1. **C1** (quality filter) + **C3** (dedup fix) — These are data-quality bugs that compound over time. Fix before any major user testing.
2. **I2** (face thumbnails) — The most visible missing feature. A face library without faces is confusing.
3. **I7** (single-embedding removal/undo) — Without this, mistakes are permanent and degrading.
4. **C2** (gallery evict on config change) — Simple fix, prevents surprising behavior.
5. **I6** (auto-detect on import) — The setting exists and is exposed; the plumbing is half-done.
6. **I1** (text tag search) — Explicitly requested by user.
7. **I8** (top-N matches) — Significant quality improvement for lookalikes.
8. **C4, C5, C6** — Fix the remaining logic issues.
9. **U1-U9** — Polish and UX improvements.
10. **I4** (batch grouping) — Large effort, prioritized after core issues are fixed.
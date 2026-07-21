package org.kryspetrie.fileimport.domain.model

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.within
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.kryspetrie.fileimport.domain.port.DetectedFace
import kotlin.math.sqrt

@DisplayName("FaceEmbedding")
class FaceEmbeddingTest {

    // ---------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------

    /** Unit vector along axis [axis] of dimension [dim]. */
    private fun unitVector(dim: Int, axis: Int): FloatArray {
        val v = FloatArray(dim)
        v[axis] = 1.0f
        return v
    }

    /** Vector of [dim] dimensions with every element equal to [value], then L2-normalized. */
    private fun constantNormalizedVector(dim: Int, value: Float): FloatArray {
        val v = FloatArray(dim) { value }
        val norm = sqrt(v.fold(0f) { acc, f -> acc + f * f })
        return if (norm < 1e-6f) v else FloatArray(dim) { v[it] / norm }
    }

    /** Creates a FaceEmbedding from a FloatArray with an explicit model name. */
    private fun embeddingWithModel(vec: FloatArray, modelName: String): FaceEmbedding =
        FaceEmbedding(
            embeddingVector = vec,
            modelName = modelName,
        )

    // ---------------------------------------------------------------------------
    // 1. Cosine Similarity
    // ---------------------------------------------------------------------------

    @Nested
    @DisplayName("cosineSimilarity")
    inner class CosineSimilarity {

        @Test
        @DisplayName("identical vectors should have similarity ~1.0")
        fun identicalVectors_highSimilarity() {
            val vec = constantNormalizedVector(128, 0.5f)
            val a = FaceEmbedding(embeddingVector = vec, modelName = "mobilefacenet")
            val b = FaceEmbedding(embeddingVector = vec, modelName = "mobilefacenet")
            assertThat(a.cosineSimilarity(b)).isCloseTo(1.0f, within(0.001f))
        }

        @Test
        @DisplayName("orthogonal vectors should have similarity ~0.0")
        fun orthogonalVectors_zeroSimilarity() {
            // x-axis vs y-axis are orthogonal
            val a = FaceEmbedding(embeddingVector = unitVector(128, 0), modelName = "mobilefacenet")
            val b = FaceEmbedding(embeddingVector = unitVector(128, 1), modelName = "mobilefacenet")
            assertThat(a.cosineSimilarity(b)).isCloseTo(0.0f, within(0.001f))
        }

        @Test
        @DisplayName("opposite vectors should have similarity ~-1.0")
        fun oppositeVectors_negativeOne() {
            val v = constantNormalizedVector(128, 1.0f)
            val neg = FloatArray(128) { -v[it] }
            val a = FaceEmbedding(embeddingVector = v, modelName = "mobilefacenet")
            val b = FaceEmbedding(embeddingVector = neg, modelName = "mobilefacenet")
            assertThat(a.cosineSimilarity(b)).isCloseTo(-1.0f, within(0.001f))
        }

        @Test
        @DisplayName("small perturbation should produce high similarity (>0.9)")
        fun smallPerturbation_highSimilarity() {
            val base = FloatArray(128) { 1.0f }
            val perturbed = FloatArray(128) { i -> base[i] + 0.01f }
            val a = FaceEmbedding(embeddingVector = base, modelName = "mobilefacenet")
            val b = FaceEmbedding(embeddingVector = perturbed, modelName = "mobilefacenet")
            assertThat(a.cosineSimilarity(b)).isGreaterThan(0.9f)
        }

        @Test
        @DisplayName("different people embeddings should produce low similarity (<0.4)")
        fun differentPeople_lowSimilarity() {
            // Simulate two very different random-ish vectors
            val v1 = FloatArray(128) { i -> (i * 0.1f * if (i % 2 == 0) 1f else -1f) }
            val v2 = FloatArray(128) { i -> (i * 0.3f * if (i % 3 == 0) 1f else -1.7f) }
            val a = FaceEmbedding(embeddingVector = v1, modelName = "mobilefacenet")
            val b = FaceEmbedding(embeddingVector = v2, modelName = "mobilefacenet")
            assertThat(a.cosineSimilarity(b)).isLessThan(0.4f)
        }

        @Test
        @DisplayName("empty vectors should return 0f")
        fun emptyVectors_returnZero() {
            val a = FaceEmbedding(embeddingVector = FloatArray(0), modelName = "mobilefacenet")
            val b = FaceEmbedding(embeddingVector = FloatArray(0), modelName = "mobilefacenet")
            assertThat(a.cosineSimilarity(b)).isCloseTo(0.0f, within(0.001f))
        }

        @Test
        @DisplayName("one empty vector should return 0f")
        fun oneEmptyVector_returnZero() {
            val a = FaceEmbedding(embeddingVector = FloatArray(0), modelName = "mobilefacenet")
            val b = FaceEmbedding(embeddingVector = FloatArray(128) { 1f }, modelName = "mobilefacenet")
            assertThat(a.cosineSimilarity(b)).isCloseTo(0.0f, within(0.001f))
        }

        @Test
        @DisplayName("vectors of different dimensions should return 0f (dimension mismatch guard)")
        fun differentDimensions_returnsZero() {
            // 4-dim vs 6-dim: dimension mismatch should return 0f to prevent
            // meaningless partial-overlap comparisons that produce incorrect similarity scores.
            val v1 = FloatArray(4) { 0f }.also { it[0] = 1f }
            val v2 = FloatArray(6) { 0f }.also { it[0] = 1f }
            val a = FaceEmbedding(embeddingVector = v1, modelName = "mobilefacenet")
            val b = FaceEmbedding(embeddingVector = v2, modelName = "mobilefacenet")
            assertThat(a.cosineSimilarity(b)).isCloseTo(0.0f, within(0.001f))
        }

        @Test
        @DisplayName("zero vector should return 0f (denominator guard)")
        fun zeroVector_returnZero() {
            val a = FaceEmbedding(embeddingVector = FloatArray(128), modelName = "mobilefacenet")
            val b = FaceEmbedding(embeddingVector = FloatArray(128) { 1f }, modelName = "mobilefacenet")
            assertThat(a.cosineSimilarity(b)).isCloseTo(0.0f, within(0.001f))
        }
    }

    // ---------------------------------------------------------------------------
    // 2. Base64 Encoding / Decoding
    // ---------------------------------------------------------------------------

    @Nested
    @DisplayName("encodeVector / decodeVector")
    inner class Base64RoundTrip {

        @Test
        @DisplayName("round-trip: encode then decode should produce the same vector")
        fun roundTrip_sameVector() {
            val original = FloatArray(64) { i -> (i - 32) * 0.1f }
            val encoded = FaceEmbedding.encodeVector(original)
            val decoded = FaceEmbedding.decodeVector(encoded)
            assertThat(decoded).hasSize(original.size)
            for (i in original.indices) {
                assertThat(decoded[i]).isCloseTo(original[i], within(0.0001f))
            }
        }

        @Test
        @DisplayName("empty string decodes to empty FloatArray")
        fun emptyString_decodesToEmpty() {
            val result = FaceEmbedding.decodeVector("")
            assertThat(result).isEmpty()
        }

        @Test
        @DisplayName("blank string decodes to empty FloatArray")
        fun blankString_decodesToEmpty() {
            val result = FaceEmbedding.decodeVector("   ")
            assertThat(result).isEmpty()
        }

        @Test
        @DisplayName("invalid Base64 decodes to empty FloatArray")
        fun invalidBase64_decodesToEmpty() {
            val result = FaceEmbedding.decodeVector("!!invalid-base64!!")
            assertThat(result).isEmpty()
        }

        @Test
        @DisplayName("128-dim vector round-trip (MobileFaceNet dimension)")
        fun mobileFaceNet_roundTrip() {
            val vec128 = FloatArray(FaceEmbedding.DIM_MOBILEFACENET) { i ->
                (i - 64) / 128.0f
            }
            val encoded = FaceEmbedding.encodeVector(vec128)
            val decoded = FaceEmbedding.decodeVector(encoded)
            assertThat(decoded).hasSize(FaceEmbedding.DIM_MOBILEFACENET)
            for (i in vec128.indices) {
                assertThat(decoded[i]).isCloseTo(vec128[i], within(0.0001f))
            }
        }

        @Test
        @DisplayName("single-element vector round-trip")
        fun singleElement_roundTrip() {
            val single = floatArrayOf(42.0f)
            val encoded = FaceEmbedding.encodeVector(single)
            val decoded = FaceEmbedding.decodeVector(encoded)
            assertThat(decoded).hasSize(1)
            assertThat(decoded[0]).isCloseTo(42.0f, within(0.001f))
        }

        @Test
        @DisplayName("Base64 with wrong byte count (not multiple of 4) decodes to empty")
        fun wrongByteCount_decodesToEmpty() {
            // Manually craft a valid Base64 string that decodes to 5 bytes (not multiple of 4)
            // "AAAAAAA=" decodes to 4 bytes (1 float) - valid
            // We need something that decodes to non-multiple-of-4 bytes
            // "AAAA" = 3 bytes, which is not multiple of 4 → should return empty
            // Actually "AAAA" decodes to 3 bytes. Let's use a raw approach.
            val result = FaceEmbedding.decodeVector("AAAA") // 3 bytes, not multiple of 4
            assertThat(result).isEmpty()
        }

        @Test
        @DisplayName("encodeVector of empty FloatArray produces encodable output")
        fun emptyVector_encodeRoundTrip() {
            val empty = FloatArray(0)
            val encoded = FaceEmbedding.encodeVector(empty)
            val decoded = FaceEmbedding.decodeVector(encoded)
            assertThat(decoded).isEmpty()
        }

        @Test
        @DisplayName("negative floats survive round-trip")
        fun negativeFloats_roundTrip() {
            val vec = floatArrayOf(-1.5f, 0.0f, 3.14f, -999.99f)
            val encoded = FaceEmbedding.encodeVector(vec)
            val decoded = FaceEmbedding.decodeVector(encoded)
            for (i in vec.indices) {
                assertThat(decoded[i]).isCloseTo(vec[i], within(0.001f))
            }
        }
    }

    // ---------------------------------------------------------------------------
    // 3. Thresholds and Constants
    // ---------------------------------------------------------------------------

    @Nested
    @DisplayName("Constants and thresholds")
    inner class Constants {

        @Test
        @DisplayName("MATCH_THRESHOLD should be 0.65f")
        fun matchThreshold() {
            assertThat(FaceEmbedding.MATCH_THRESHOLD).isCloseTo(0.65f, within(0.001f))
        }

        @Test
        @DisplayName("AUTO_TAG_THRESHOLD should be 0.75f")
        fun autoTagThreshold() {
            assertThat(FaceEmbedding.AUTO_TAG_THRESHOLD).isCloseTo(0.75f, within(0.001f))
        }

        @Test
        @DisplayName("MIN_QUALITY_FOR_MATCHING should be 0.3f")
        fun minQualityForMatching() {
            assertThat(FaceEmbedding.MIN_QUALITY_FOR_MATCHING).isCloseTo(0.3f, within(0.001f))
        }

        @Test
        @DisplayName("MIN_FACE_SIZE_PX should be 50")
        fun minFaceSizePx() {
            assertThat(FaceEmbedding.MIN_FACE_SIZE_PX).isEqualTo(50)
        }

        @Test
        @DisplayName("DIM_MOBILEFACENET should be 128")
        fun dimMobileFaceNet() {
            assertThat(FaceEmbedding.DIM_MOBILEFACENET).isEqualTo(128)
        }

        @Test
        @DisplayName("DIM_ARCFACE_R50 should be 512")
        fun dimArcFaceR50() {
            assertThat(FaceEmbedding.DIM_ARCFACE_R50).isEqualTo(512)
        }
    }

    // ---------------------------------------------------------------------------
    // 4. Cross-Model Safety
    // ---------------------------------------------------------------------------

    @Nested
    @DisplayName("Cross-model safety")
    inner class CrossModelSafety {

        @Test
        @DisplayName("different non-empty model names should return 0f")
        fun differentModels_returnZero() {
            val vec = constantNormalizedVector(128, 1.0f)
            val a = embeddingWithModel(vec, "mobilefacenet")
            val b = embeddingWithModel(vec, "arcface-r50")
            assertThat(a.cosineSimilarity(b)).isCloseTo(0.0f, within(0.001f))
        }

        @Test
        @DisplayName("same non-empty model name should compute similarity normally")
        fun sameModel_computesSimilarity() {
            val vec = constantNormalizedVector(128, 1.0f)
            val a = embeddingWithModel(vec, "mobilefacenet")
            val b = embeddingWithModel(vec, "mobilefacenet")
            assertThat(a.cosineSimilarity(b)).isCloseTo(1.0f, within(0.001f))
        }

        @Test
        @DisplayName("empty modelName on both embeddings should compare normally (no guard)")
        fun bothEmptyModels_comparesNormally() {
            val vec = constantNormalizedVector(128, 1.0f)
            val a = FaceEmbedding(embeddingVector = vec, modelName = "")
            val b = FaceEmbedding(embeddingVector = vec, modelName = "")
            assertThat(a.cosineSimilarity(b)).isCloseTo(1.0f, within(0.001f))
        }

        @Test
        @DisplayName("one empty modelName should compare normally (no cross-model guard)")
        fun oneEmptyModel_comparesNormally() {
            val vec = constantNormalizedVector(128, 1.0f)
            val a = FaceEmbedding(embeddingVector = vec, modelName = "")
            val b = embeddingWithModel(vec, "mobilefacenet")
            // When one modelName is empty, cross-model guard does NOT activate
            assertThat(a.cosineSimilarity(b)).isCloseTo(1.0f, within(0.001f))
        }

        @Test
        @DisplayName("cross-model returns 0f even with identical vectors")
        fun differentModels_identicalVectors_returnZero() {
            val vec = FloatArray(128) { 1.0f }
            val a = embeddingWithModel(vec, "model_a")
            val b = embeddingWithModel(vec.copyOf(), "model_b")
            assertThat(a.cosineSimilarity(b)).isCloseTo(0.0f, within(0.001f))
        }
    }

    // ---------------------------------------------------------------------------
    // 5. isUsableForMatching
    // ---------------------------------------------------------------------------

    @Nested
    @DisplayName("isUsableForMatching")
    inner class IsUsableForMatching {

        @Test
        @DisplayName("quality >= 0.3 with non-blank vectorBase64 → true")
        fun sufficientQualityAndVector_isUsable() {
            val embedding = FaceEmbedding(
                embeddingVector = floatArrayOf(1f),
                quality = 0.3f,
            )
            assertThat(embedding.isUsableForMatching()).isTrue()
        }

        @Test
        @DisplayName("quality > 0.3 with non-blank vectorBase64 → true")
        fun goodQuality_isUsable() {
            val embedding = FaceEmbedding(
                embeddingVector = floatArrayOf(1f),
                quality = 0.9f,
            )
            assertThat(embedding.isUsableForMatching()).isTrue()
        }

        @Test
        @DisplayName("quality < 0.3 → false")
        fun lowQuality_notUsable() {
            val embedding = FaceEmbedding(
                embeddingVector = floatArrayOf(1f),
                quality = 0.29f,
            )
            assertThat(embedding.isUsableForMatching()).isFalse()
        }

        @Test
        @DisplayName("empty vectorBase64 → false even with good quality")
        fun emptyVector_notUsable() {
            val embedding = FaceEmbedding(
                vectorBase64 = "",
                quality = 1.0f,
            )
            assertThat(embedding.isUsableForMatching()).isFalse()
        }

        @Test
        @DisplayName("blank vectorBase64 → false")
        fun blankVector_notUsable() {
            val embedding = FaceEmbedding(
                vectorBase64 = "   ",
                quality = 1.0f,
            )
            assertThat(embedding.isUsableForMatching()).isFalse()
        }

        @Test
        @DisplayName("quality exactly 0.0 → false")
        fun zeroQuality_notUsable() {
            val embedding = FaceEmbedding(
                embeddingVector = floatArrayOf(1f),
                quality = 0.0f,
            )
            assertThat(embedding.isUsableForMatching()).isFalse()
        }
    }

    // ---------------------------------------------------------------------------
    // 6. vector lazy property
    // ---------------------------------------------------------------------------

    @Nested
    @DisplayName("vector lazy property")
    inner class VectorLazyProperty {

        @Test
        @DisplayName("should decode from vectorBase64")
        fun decodesFromBase64() {
            val original = floatArrayOf(1.0f, 2.0f, 3.0f)
            val encoded = FaceEmbedding.encodeVector(original)
            val embedding = FaceEmbedding(vectorBase64 = encoded)
            val decoded = embedding.vector
            assertThat(decoded).hasSize(3)
            assertThat(decoded[0]).isCloseTo(1.0f, within(0.001f))
            assertThat(decoded[1]).isCloseTo(2.0f, within(0.001f))
            assertThat(decoded[2]).isCloseTo(3.0f, within(0.001f))
        }

        @Test
        @DisplayName("creating from embeddingVector constructor should produce valid vectorBase64 that decodes correctly")
        fun constructorProducesValidBase64() {
            val original = FloatArray(128) { i -> i.toFloat() / 128f }
            val embedding = FaceEmbedding(embeddingVector = original)
            // vectorBase64 should be non-blank
            assertThat(embedding.vectorBase64).isNotBlank()
            // vector should decode correctly
            val decoded = embedding.vector
            assertThat(decoded).hasSize(128)
            for (i in original.indices) {
                assertThat(decoded[i]).isCloseTo(original[i], within(0.001f))
            }
        }

        @Test
        @DisplayName("empty vectorBase64 produces empty FloatArray")
        fun emptyBase64_producesEmptyVector() {
            val embedding = FaceEmbedding(vectorBase64 = "")
            assertThat(embedding.vector).isEmpty()
        }
    }

    // ---------------------------------------------------------------------------
    // 7. NormalizedRect
    // ---------------------------------------------------------------------------

    @Nested
    @DisplayName("NormalizedRect")
    inner class NormalizedRectTests {

        @Test
        @DisplayName("isValid should be true when w>0 and h>0")
        fun isValid_positiveDimensions() {
            val rect = NormalizedRect(x = 0.1f, y = 0.2f, w = 0.3f, h = 0.4f)
            assertThat(rect.isValid).isTrue()
        }

        @Test
        @DisplayName("isValid should be false when w=0")
        fun isValid_zeroWidth() {
            val rect = NormalizedRect(x = 0.1f, y = 0.2f, w = 0f, h = 0.4f)
            assertThat(rect.isValid).isFalse()
        }

        @Test
        @DisplayName("isValid should be false when h=0")
        fun isValid_zeroHeight() {
            val rect = NormalizedRect(x = 0.1f, y = 0.2f, w = 0.3f, h = 0f)
            assertThat(rect.isValid).isFalse()
        }

        @Test
        @DisplayName("isValid should be false when w<0")
        fun isValid_negativeWidth() {
            val rect = NormalizedRect(w = -0.1f, h = 0.4f)
            assertThat(rect.isValid).isFalse()
        }

        @Test
        @DisplayName("isValid should be false for default values")
        fun isValid_defaults() {
            val rect = NormalizedRect()
            assertThat(rect.isValid).isFalse()
        }

        @Test
        @DisplayName("centerX should be x + w/2")
        fun centerX() {
            val rect = NormalizedRect(x = 0.2f, y = 0f, w = 0.6f, h = 0.5f)
            assertThat(rect.centerX).isCloseTo(0.5f, within(0.001f))
        }

        @Test
        @DisplayName("centerY should be y + h/2")
        fun centerY() {
            val rect = NormalizedRect(x = 0f, y = 0.1f, w = 0.5f, h = 0.6f)
            assertThat(rect.centerY).isCloseTo(0.4f, within(0.001f))
        }

        @Test
        @DisplayName("fromDetectedFace should normalize pixel coordinates")
        fun fromDetectedFace() {
            val face = DetectedFace(x1 = 100f, y1 = 200f, x2 = 300f, y2 = 500f, confidence = 0.9f)
            val rect = NormalizedRect.fromDetectedFace(face, imageWidth = 1000, imageHeight = 1000)
            assertThat(rect.x).isCloseTo(0.1f, within(0.001f))
            assertThat(rect.y).isCloseTo(0.2f, within(0.001f))
            assertThat(rect.w).isCloseTo(0.2f, within(0.001f))
            assertThat(rect.h).isCloseTo(0.3f, within(0.001f))
        }

        @Test
        @DisplayName("fromDetectedFace with different image dimensions")
        fun fromDetectedFace_nonSquareImage() {
            val face = DetectedFace(x1 = 50f, y1 = 100f, x2 = 250f, y2 = 400f, confidence = 0.8f)
            val rect = NormalizedRect.fromDetectedFace(face, imageWidth = 500, imageHeight = 1000)
            assertThat(rect.x).isCloseTo(0.1f, within(0.001f))
            assertThat(rect.y).isCloseTo(0.1f, within(0.001f))
            assertThat(rect.w).isCloseTo(0.4f, within(0.001f))
            assertThat(rect.h).isCloseTo(0.3f, within(0.001f))
        }

        @Test
        @DisplayName("toPixels should convert normalized coordinates to pixel coordinates")
        fun toPixels() {
            val rect = NormalizedRect(x = 0.1f, y = 0.2f, w = 0.3f, h = 0.4f)
            val pixel = rect.toPixels(imageWidth = 1000, imageHeight = 500)
            assertThat(pixel.x).isEqualTo(100)
            assertThat(pixel.y).isEqualTo(100)
            assertThat(pixel.w).isEqualTo(300)
            assertThat(pixel.h).isEqualTo(200)
        }

        @Test
        @DisplayName("toPixels with default NormalizedRect")
        fun toPixels_defaultRect() {
            val rect = NormalizedRect()
            val pixel = rect.toPixels(imageWidth = 1000, imageHeight = 1000)
            assertThat(pixel.x).isEqualTo(0)
            assertThat(pixel.y).isEqualTo(0)
            assertThat(pixel.w).isEqualTo(0)
            assertThat(pixel.h).isEqualTo(0)
        }

        @Test
        @DisplayName("round-trip: fromDetectedFace → toPixels (within int rounding)")
        fun roundTrip_detectedFace_toPixels() {
            val face = DetectedFace(x1 = 100f, y1 = 150f, x2 = 400f, y2 = 450f, confidence = 0.95f)
            val normalized = NormalizedRect.fromDetectedFace(face, imageWidth = 1000, imageHeight = 1000)
            val pixel = normalized.toPixels(imageWidth = 1000, imageHeight = 1000)
            // Due to float→int rounding, values should be very close
            assertThat(pixel.x).isEqualTo(100)
            assertThat(pixel.y).isEqualTo(150)
            // w = (400-100)/1000 * 1000 = 300, h = (450-150)/1000 * 1000 = 300
            assertThat(pixel.w).isEqualTo(300)
            assertThat(pixel.h).isEqualTo(300)
        }
    }

    // ---------------------------------------------------------------------------
    // 8. FaceCrop and PixelRect
    // ---------------------------------------------------------------------------

    @Nested
    @DisplayName("FaceCrop")
    inner class FaceCropTests {

        @Test
        @DisplayName("default values")
        fun defaults() {
            val crop = FaceCrop()
            assertThat(crop.sourcePath).isEmpty()
            assertThat(crop.sourceRegion).isEqualTo(NormalizedRect())
            assertThat(crop.alignedWidth).isEqualTo(112)
            assertThat(crop.alignedHeight).isEqualTo(112)
            assertThat(crop.yaw).isCloseTo(0f, within(0.001f))
            assertThat(crop.detectionConfidence).isCloseTo(1.0f, within(0.001f))
        }

        @Test
        @DisplayName("custom values")
        fun customValues() {
            val region = NormalizedRect(x = 0.1f, y = 0.2f, w = 0.3f, h = 0.4f)
            val crop = FaceCrop(
                sourcePath = "/img/photo.jpg",
                sourceRegion = region,
                alignedWidth = 224,
                alignedHeight = 224,
                yaw = 15f,
                detectionConfidence = 0.85f,
            )
            assertThat(crop.sourcePath).isEqualTo("/img/photo.jpg")
            assertThat(crop.sourceRegion).isEqualTo(region)
            assertThat(crop.alignedWidth).isEqualTo(224)
            assertThat(crop.alignedHeight).isEqualTo(224)
            assertThat(crop.yaw).isCloseTo(15f, within(0.001f))
            assertThat(crop.detectionConfidence).isCloseTo(0.85f, within(0.001f))
        }
    }

    @Nested
    @DisplayName("PixelRect")
    inner class PixelRectTests {

        @Test
        @DisplayName("default values")
        fun defaults() {
            val rect = PixelRect()
            assertThat(rect.x).isEqualTo(0)
            assertThat(rect.y).isEqualTo(0)
            assertThat(rect.w).isEqualTo(0)
            assertThat(rect.h).isEqualTo(0)
        }

        @Test
        @DisplayName("custom values")
        fun customValues() {
            val rect = PixelRect(x = 10, y = 20, w = 100, h = 200)
            assertThat(rect.x).isEqualTo(10)
            assertThat(rect.y).isEqualTo(20)
            assertThat(rect.w).isEqualTo(100)
            assertThat(rect.h).isEqualTo(200)
        }

        @Test
        @DisplayName("data class equality")
        fun equality() {
            val a = PixelRect(x = 1, y = 2, w = 3, h = 4)
            val b = PixelRect(x = 1, y = 2, w = 3, h = 4)
            assertThat(a).isEqualTo(b)
        }
    }

    // ---------------------------------------------------------------------------
    // 9. vectorSizeBytes
    // ---------------------------------------------------------------------------

    @Nested
    @DisplayName("vectorSizeBytes")
    inner class VectorSizeBytes {

        @Test
        @DisplayName("128-dim vector should be 512 bytes")
        fun mobilefacenet_size() {
            val embedding = FaceEmbedding(embeddingVector = FloatArray(128))
            assertThat(embedding.vectorSizeBytes).isEqualTo(512)
        }

        @Test
        @DisplayName("512-dim vector should be 2048 bytes")
        fun arcface_size() {
            val embedding = FaceEmbedding(embeddingVector = FloatArray(512))
            assertThat(embedding.vectorSizeBytes).isEqualTo(2048)
        }

        @Test
        @DisplayName("empty vector should be 0 bytes")
        fun empty_size() {
            val embedding = FaceEmbedding(vectorBase64 = "")
            assertThat(embedding.vectorSizeBytes).isEqualTo(0)
        }
    }

    // ---------------------------------------------------------------------------
    // 10. equals and hashCode
    // ---------------------------------------------------------------------------

    @Nested
    @DisplayName("equals and hashCode")
    inner class EqualsAndHashCode {

        @Test
        @DisplayName("two embeddings with same vectorBase64 are equal")
        fun same_vectors_equal() {
            val vec = floatArrayOf(1f, 2f, 3f)
            val e1 = FaceEmbedding(embeddingVector = vec, id = "a")
            val e2 = FaceEmbedding(embeddingVector = vec, id = "a")
            assertThat(e1).isEqualTo(e2)
            assertThat(e1.hashCode()).isEqualTo(e2.hashCode())
        }

        @Test
        @DisplayName("two embeddings with different vectors are not equal")
        fun different_vectors_not_equal() {
            val e1 = FaceEmbedding(embeddingVector = floatArrayOf(1f, 2f, 3f), id = "a")
            val e2 = FaceEmbedding(embeddingVector = floatArrayOf(4f, 5f, 6f), id = "a")
            assertThat(e1).isNotEqualTo(e2)
        }

        @Test
        @DisplayName("same object is equal to itself")
        fun reflexive() {
            val e = FaceEmbedding(embeddingVector = floatArrayOf(1f, 2f))
            assertThat(e).isEqualTo(e)
        }

        @Test
        @DisplayName("embeddings with same id but different vectors are not equal")
        fun same_id_different_vectors() {
            val e1 = FaceEmbedding(id = "x", embeddingVector = floatArrayOf(1f))
            val e2 = FaceEmbedding(id = "x", embeddingVector = floatArrayOf(2f))
            assertThat(e1).isNotEqualTo(e2)
        }
    }
}
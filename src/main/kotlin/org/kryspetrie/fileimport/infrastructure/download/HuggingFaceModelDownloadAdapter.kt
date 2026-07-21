package org.kryspetrie.fileimport.infrastructure.download

import java.io.File
import java.io.FileInputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.kryspetrie.fileimport.domain.port.ModelDownloadPort
import org.kryspetrie.fileimport.domain.port.ModelDownloadState
import org.kryspetrie.fileimport.domain.port.ModelInfo
import org.kryspetrie.fileimport.domain.port.DispatcherProvider
import org.kryspetrie.fileimport.infrastructure.logging.AppLogger

/**
 * Downloads ML models from remote sources and stores them in the user's local app data directory.
 *
 * Models are cached at `~/.petrie-importer/models/`.
 *
 * ## Download Sources
 *
 * Each model has a known download URL:
 * - `orientation_detection`: Chuckame/deep-image-orientation-angle-detection ONNX model (~330 MB)
 *   from HuggingFace
 * - `face_embedding`: ArcFace MobileFaceNet ONNX model (~8 MB) from Hailo Model Zoo
 *
 * Small models (face detection, pose, corner regression) are bundled in the JAR and don't need
 * downloading.
 *
 * ## Zip Extraction
 *
 * Some models are distributed as `.zip` archives (marked by [ModelMetadata.isZip]). After
 * downloading, the archive is extracted and the specified [ModelMetadata.zipEntryName] file
 * is saved as the final model file.
 *
 * ## Thread Safety
 *
 * Download state flows are created per model ID and tracked in a concurrent map to prevent
 * duplicate downloads. If a download is already in progress for a given model, the existing state
 * flow is returned.
 */
class HuggingFaceModelDownloadAdapter(
    private val dispatcherProvider: DispatcherProvider,
    private val appLogger: AppLogger? = null,
) : ModelDownloadPort {

    /** Directory where downloaded models are stored. */
    private val modelDir = File(System.getProperty("user.home"), ".petrie-importer/models")

    /** Active download state flows, keyed by model ID. */
    private val downloadStates = ConcurrentHashMap<String, MutableStateFlow<ModelDownloadState>>()

    /** Active download jobs, keyed by model ID. Used for cancellation. */
    private val activeJobs = ConcurrentHashMap<String, kotlinx.coroutines.Job>()

    /** Coroutine scope for download operations. */
    private val downloadScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Known model metadata. */
    private val modelMetadata = mapOf(
        ModelDownloadPort.ORIENTATION_MODEL_ID to ModelMetadata(
            id = ModelDownloadPort.ORIENTATION_MODEL_ID,
            name = "Orientation Detection Model",
            description = "Deep image orientation angle detection model (~330 MB). " +
                "Automatically detects and corrects photo rotation.",
            downloadUrl = ORIENTATION_MODEL_URL,
            fileName = "orientation_detection_model.onnx",
            downloadSize = 346L * 1024 * 1024, // ~346 MB (actual file size)
            isZip = false,
        ),
        ModelDownloadPort.FACE_EMBEDDING_MODEL_ID to ModelMetadata(
            id = ModelDownloadPort.FACE_EMBEDDING_MODEL_ID,
            name = "Face Embedding Model (ArcFace MobileFaceNet)",
            description = "ArcFace MobileFaceNet face recognition model (~8 MB). " +
                "Extracts 512-dimensional face embeddings for person identification and grouping.",
            downloadUrl = FACE_EMBEDDING_MODEL_URL,
            fileName = "face_embedding_model.onnx",
            downloadSize = 8L * 1024 * 1024, // ~8 MB (zip archive)
            isZip = true,
            zipEntryName = "mbf.onnx",
        ),
    )

    init {
        // Ensure model directory exists
        modelDir.mkdirs()
    }

    override fun isModelDownloaded(modelId: String): Boolean {
        // Classpath models are always "downloaded"
        if (modelId in BUNDLED_MODELS) return true

        val metadata = modelMetadata[modelId] ?: return false
        val modelFile = File(modelDir, metadata.fileName)
        return modelFile.exists() && modelFile.length() > 0
    }

    override fun modelSize(modelId: String): Long? {
        if (modelId in BUNDLED_MODELS) return null

        val metadata = modelMetadata[modelId] ?: return null
        val modelFile = File(modelDir, metadata.fileName)
        return if (modelFile.exists()) modelFile.length() else metadata.downloadSize
    }

    override fun modelName(modelId: String): String {
        val metadata = modelMetadata[modelId] ?: return modelId
        val sizeStr = metadata.downloadSize?.let {
            String.format("~%.0f MB", it / (1024.0 * 1024.0))
        } ?: ""
        return "${metadata.name} ($sizeStr)"
    }

    override fun downloadModel(modelId: String): StateFlow<ModelDownloadState> {
        val stateFlow = downloadStates.getOrPut(modelId) {
            MutableStateFlow(ModelDownloadState.Idle)
        }

        // If already downloading or completed, just return the existing flow
        val currentState = stateFlow.value
        if (currentState is ModelDownloadState.Downloading ||
            currentState is ModelDownloadState.Connecting
        ) {
            return stateFlow
        }

        // If already completed successfully, re-verify the file still exists
        if (currentState is ModelDownloadState.Completed) {
            val metadata = modelMetadata[modelId]
            if (metadata != null && File(modelDir, metadata.fileName).exists()) {
                return stateFlow
            }
            // File was deleted — fall through to re-download
        }

        // Mark as connecting immediately
        stateFlow.value = ModelDownloadState.Connecting

        // Launch the download in the background and update the state flow as it progresses
        val job = downloadScope.launch {
            performDownload(modelId, stateFlow)
        }
        activeJobs[modelId] = job

        return stateFlow
    }

    /**
     * Performs the actual download of a model file.
     *
     * Updates [stateFlow] as the download progresses through
     * [Connecting] → [Downloading] → [Completed]/[Failed]/[Cancelled] states.
     *
     * If [ModelMetadata.isZip] is true, the downloaded archive is extracted and the
     * entry matching [ModelMetadata.zipEntryName] is saved as the final model file.
     */
    private suspend fun performDownload(
        modelId: String,
        stateFlow: MutableStateFlow<ModelDownloadState>,
    ) {
        val metadata = modelMetadata[modelId]
        if (metadata == null) {
            stateFlow.value = ModelDownloadState.Failed("Unknown model: $modelId")
            return
        }

        val targetFile = File(modelDir, metadata.fileName)
        val tempFile = File(modelDir, "${metadata.fileName}.downloading")

        try {
            withContext(dispatcherProvider.io) {
                val url = URL(metadata.downloadUrl)
                val connection = url.openConnection() as HttpURLConnection
                connection.connectTimeout = 30_000
                connection.readTimeout = 60_000
                connection.requestMethod = "GET"
                connection.instanceFollowRedirects = true

                val responseCode = connection.responseCode
                if (responseCode != HttpURLConnection.HTTP_OK) {
                    val errorMsg = when (responseCode) {
                        HttpURLConnection.HTTP_NOT_FOUND ->
                            "Model file not found (HTTP 404). The download URL may be outdated — " +
                                "please check for app updates."
                        HttpURLConnection.HTTP_FORBIDDEN, HttpURLConnection.HTTP_UNAUTHORIZED ->
                            "Model repository is private or unavailable (HTTP $responseCode). " +
                                "Place the model file manually at: ${targetFile.absolutePath}"
                        else ->
                            "Server returned HTTP $responseCode"
                    }
                    stateFlow.value = ModelDownloadState.Failed(errorMsg, canRetry = true)
                    return@withContext
                }

                val contentLength = connection.contentLengthLong
                val totalBytes = if (contentLength > 0) contentLength else null

                connection.inputStream.use { input ->
                    tempFile.outputStream().use { output ->
                        val buffer = ByteArray(8192)
                        var bytesReadTotal = 0L
                        var lastProgressTime = 0L

                        while (true) {
                            val bytesRead = input.read(buffer)
                            if (bytesRead == -1) break

                            output.write(buffer, 0, bytesRead)
                            bytesReadTotal += bytesRead

                            // Update progress at most every 500ms
                            val now = System.currentTimeMillis()
                            if (now - lastProgressTime > 500) {
                                val percent = if (totalBytes != null && totalBytes > 0) {
                                    (bytesReadTotal.toFloat() / totalBytes.toFloat()) * 100f
                                } else null

                                stateFlow.value = ModelDownloadState.Downloading(
                                    bytesDownloaded = bytesReadTotal,
                                    totalBytes = totalBytes,
                                    progressPercent = percent,
                                )
                                lastProgressTime = now
                            }
                        }
                    }
                }

                // If the model is distributed as a zip, extract the ONNX file
                if (metadata.isZip) {
                    extractZipEntry(tempFile, metadata.zipEntryName, targetFile)
                    // Clean up the downloaded zip file
                    tempFile.delete()
                } else {
                    // Atomic move: temp → final
                    if (tempFile.exists()) {
                        if (targetFile.exists()) {
                            targetFile.delete()
                        }
                        if (!tempFile.renameTo(targetFile)) {
                            tempFile.copyTo(targetFile, overwrite = true)
                            tempFile.delete()
                        }
                    }
                }

                if (!targetFile.exists() || targetFile.length() == 0L) {
                    stateFlow.value = ModelDownloadState.Failed(
                        "Downloaded file is empty or missing after extraction",
                        canRetry = true,
                    )
                    return@withContext
                }

                stateFlow.value = ModelDownloadState.Completed(targetFile.absolutePath)
                appLogger?.info("Downloaded model: $modelId → ${targetFile.absolutePath} " +
                    "(${targetFile.length()} bytes)")
            }
        } catch (e: CancellationException) {
            stateFlow.value = ModelDownloadState.Cancelled
            tempFile.delete()
        } catch (e: Exception) {
            stateFlow.value = ModelDownloadState.Failed(
                "Download failed: ${e.message}",
                canRetry = true,
            )
            tempFile.delete()
            appLogger?.error("Model download failed: $modelId", e)
        }
    }

    /**
     * Extracts a specific entry from a zip archive and writes it to the target file.
     *
     * @param zipFile The downloaded zip archive.
     * @param entryName The name of the entry to extract (e.g., "mbf.onnx").
     * @param targetFile The file to write the extracted entry to.
     * @throws IllegalStateException if the entry is not found in the zip archive.
     */
    internal fun extractZipEntry(zipFile: File, entryName: String?, targetFile: File) {
        val entryToFind = entryName
            ?: throw IllegalStateException("zipEntryName must be specified for zip downloads")

        ZipInputStream(FileInputStream(zipFile)).use { zipIn ->
            var entry = zipIn.nextEntry
            var found = false

            while (entry != null) {
                if (entry.name == entryToFind || entry.name.endsWith("/$entryToFind")) {
                    // Found the entry — write to target file
                    if (targetFile.exists()) {
                        targetFile.delete()
                    }
                    targetFile.outputStream().buffered().use { out ->
                        val buffer = ByteArray(8192)
                        var bytesRead: Int
                        while (zipIn.read(buffer).also { bytesRead = it } != -1) {
                            out.write(buffer, 0, bytesRead)
                        }
                    }
                    found = true
                    break
                }
                zipIn.closeEntry()
                entry = zipIn.nextEntry
            }

            if (!found) {
                // List available entries for debugging
                ZipInputStream(FileInputStream(zipFile)).use { debugZip ->
                    val availableEntries = mutableListOf<String>()
                    var debugEntry = debugZip.nextEntry
                    while (debugEntry != null) {
                        availableEntries.add(debugEntry.name)
                        debugEntry = debugZip.nextEntry
                    }
                    throw IllegalStateException(
                        "Entry '$entryToFind' not found in zip archive. " +
                            "Available entries: $availableEntries"
                    )
                }
            }
        }
    }

    override fun cancelDownload(modelId: String) {
        activeJobs.remove(modelId)?.cancel()
        val stateFlow = downloadStates[modelId]
        if (stateFlow != null && stateFlow.value is ModelDownloadState.Downloading) {
            stateFlow.value = ModelDownloadState.Cancelled
        }
    }

    override fun deleteModel(modelId: String): Boolean {
        if (modelId in BUNDLED_MODELS) return false

        val metadata = modelMetadata[modelId] ?: return false
        val modelFile = File(modelDir, metadata.fileName)
        return if (modelFile.exists()) {
            modelFile.delete()
        } else {
            false
        }
    }

    override fun availableForDownload(): List<ModelInfo> {
        return modelMetadata.map { (id, metadata) ->
            ModelInfo(
                id = id,
                name = metadata.name,
                description = metadata.description,
                downloadSize = metadata.downloadSize,
                isDownloaded = isModelDownloaded(id),
            )
        }
    }

    /** Metadata for a downloadable model. */
    data class ModelMetadata(
        val id: String,
        val name: String,
        val description: String,
        val downloadUrl: String,
        val fileName: String,
        val downloadSize: Long?,
        /** Whether the download is a zip archive that needs extraction. */
        val isZip: Boolean = false,
        /** Name of the entry to extract from the zip archive. Required when [isZip] is true. */
        val zipEntryName: String? = null,
    )

    companion object {
        /** Set of model IDs that are bundled in the JAR (don't need downloading). */
        private val BUNDLED_MODELS = setOf(
            ModelDownloadPort.FACE_MODEL_ID,
        )

        /**
         * HuggingFace download URL for the orientation detection model.
         *
         * The ONNX file is hosted in the `Chuckame/deep-image-orientation-angle-detection` repo.
         * The file name must match the actual filename in the repository — it is
         * `deep-image-orientation-angle-detection.onnx`, not `model.onnx`.
         */
        private const val ORIENTATION_MODEL_URL =
            "https://huggingface.co/Chuckame/deep-image-orientation-angle-detection/resolve/main/deep-image-orientation-angle-detection.onnx"

        /**
         * Download URL for the ArcFace MobileFaceNet face embedding model.
         *
         * The model is distributed as a zip archive from the Hailo Model Zoo on S3.
         * The archive contains `mbf.onnx` which is extracted and saved as
         * `face_embedding_model.onnx`. This model produces 512-dimensional embeddings from
         * 112×112 face crops, using ArcFace loss and trained on MS1MV3.
         *
         * Model specs:
         * - Input: 1×3×112×112 (NCHW, float32, normalized to [-1, 1])
         * - Output: 1×512 (float32, L2-normalized embedding)
         * - LFW verification accuracy: 99.43%
         * - Parameters: 2.04M
         * - License: MIT
         */
        private const val FACE_EMBEDDING_MODEL_URL =
            "https://hailo-model-zoo.s3.eu-west-2.amazonaws.com/FaceRecognition/arcface/arcface_mobilefacenet/pretrained/2022-08-24/arcface_mobilefacenet.zip"
    }
}
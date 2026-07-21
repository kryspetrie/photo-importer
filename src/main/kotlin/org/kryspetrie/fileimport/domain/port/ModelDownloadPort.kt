package org.kryspetrie.fileimport.domain.port

import kotlinx.coroutines.flow.StateFlow

/**
 * Port interface for downloading ML models from remote sources.
 *
 * This enables lazy model downloading — models that are too large to bundle in the JAR (like the
 * 330 MB orientation detection model) can be downloaded on first use instead.
 *
 * ## Download Flow
 *
 * 1. User enables a feature that requires a model (e.g., auto-orientation)
 * 2. UI checks [ModelDownloadPort.isModelDownloaded] → `false`
 * 3. UI prompts user to download the model
 * 4. User accepts → UI calls [downloadModel] and observes progress
 * 5. On completion, the feature becomes available
 *
 * ## User Cancellation
 *
 * If the user chooses "Later", the download is not initiated. The feature remains unavailable
 * until the model is downloaded manually or the user is prompted again on next feature access.
 */
interface ModelDownloadPort {

    /**
     * Returns whether the specified model has already been downloaded and is available locally.
     *
     * This checks for the model file on disk or classpath. Returns `true` immediately if the model
     * is bundled in the JAR (classpath models are always "downloaded").
     */
    fun isModelDownloaded(modelId: String): Boolean

    /**
     * Returns the size of the model in bytes if downloaded, or the expected download size if not.
     *
     * Returns `null` if the size is unknown (e.g., the remote server doesn't provide
     * Content-Length).
     */
    fun modelSize(modelId: String): Long?

    /**
     * Returns a human-readable name for the model.
     *
     * Example: "Orientation Detection Model (~330 MB)"
     */
    fun modelName(modelId: String): String

    /**
     * Downloads the specified model from the remote source.
     *
     * The download happens asynchronously. Progress is reported via the returned [StateFlow].
     *
     * @param modelId The model identifier (e.g., "orientation_detection")
     * @return A StateFlow of [ModelDownloadState] that updates as the download progresses.
     *   Cancelling the collection of this flow will cancel the download.
     */
    fun downloadModel(modelId: String): StateFlow<ModelDownloadState>

    /**
     * Cancels an in-progress download for the specified model.
     *
     * If a download is in progress, it will be cancelled and the state will transition to
     * [ModelDownloadState.Cancelled]. If no download is in progress, this is a no-op.
     */
    fun cancelDownload(modelId: String)

    /**
     * Deletes a previously downloaded model from local storage.
     *
     * Bundled classpath models cannot be deleted — this method returns `false` for them.
     *
     * @return `true` if the model was deleted, `false` if it couldn't be deleted (bundled) or
     *   didn't exist
     */
    fun deleteModel(modelId: String): Boolean

    /**
     * Returns all models that are available for download (not yet on disk).
     */
    fun availableForDownload(): List<ModelInfo>

    companion object {
        /** Model ID for the orientation detection model. */
        const val ORIENTATION_MODEL_ID = "orientation_detection"

        /** Model ID for the face detection model. */
        const val FACE_MODEL_ID = "face_detection"

        /** Model ID for the face embedding model (MobileFaceNet). */
        const val FACE_EMBEDDING_MODEL_ID = "face_embedding"
    }
}

/** Download state for a model download operation. */
sealed class ModelDownloadState {
    /** No download in progress. */
    data object Idle : ModelDownloadState()

    /** Download is connecting to the server. */
    data object Connecting : ModelDownloadState()

    /** Download is in progress.
     * @property bytesDownloaded Bytes downloaded so far.
     * @property totalBytes Total bytes to download (null if unknown).
     * @property progressPercent Download progress as a percentage (0–100), or null if total is
     *   unknown.
     */
    data class Downloading(
        val bytesDownloaded: Long,
        val totalBytes: Long?,
        val progressPercent: Float?,
    ) : ModelDownloadState()

    /** Download completed successfully.
     * @property modelPath Local file path where the model was saved.
     */
    data class Completed(val modelPath: String) : ModelDownloadState()

    /** Download failed.
     * @property error Error message describing the failure.
     * @property canRetry Whether the download can be retried.
     */
    data class Failed(val error: String, val canRetry: Boolean = true) : ModelDownloadState()

    /** Download was cancelled by the user. */
    data object Cancelled : ModelDownloadState()
}

/** Information about a downloadable model. */
data class ModelInfo(
    /** Unique model identifier (e.g., "orientation_detection"). */
    val id: String,
    /** Human-readable model name. */
    val name: String,
    /** Model description. */
    val description: String,
    /** Expected download size in bytes (null if unknown). */
    val downloadSize: Long?,
    /** Whether the model is currently downloaded and available locally. */
    val isDownloaded: Boolean,
    /** Minimum app version required for this model. */
    val requiredAppVersion: String = "1.0.0",
)
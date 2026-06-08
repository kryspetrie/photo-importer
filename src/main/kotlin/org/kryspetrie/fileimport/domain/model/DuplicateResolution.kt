package org.kryspetrie.fileimport.domain.model

/**
 * Domain value representing the information needed to decide which duplicate file to keep.
 * Decouples the pure decision logic from java.io.File and the full ImageFile aggregate.
 */
data class ResolvableDuplicate(
    val id: String,
    val pixelCount: Long,
    val isRawFormat: Boolean,
    val lastModifiedEpochMillis: Long,
    val fileSize: Long,
)

/** Strategy for choosing which duplicate to keep when resolving a group. */
enum class DuplicateAction {
    KEEP_HIGHEST_RES,
    KEEP_RAW_OVER_JPEG,
    KEEP_NEWEST,
    KEEP_OLDEST,
    KEEP_LARGEST,
}

/**
 * Pure domain function that selects the best file to keep from a list of duplicates, based on the
 * given [action] strategy.
 *
 * Returns the id of the chosen keeper.
 */
fun pickKeeper(candidates: List<ResolvableDuplicate>, action: DuplicateAction): String =
    when (action) {
        DuplicateAction.KEEP_HIGHEST_RES ->
            candidates.maxByOrNull { it.pixelCount }?.id ?: candidates.first().id
        DuplicateAction.KEEP_RAW_OVER_JPEG ->
            candidates.sortedByDescending { it.isRawFormat }.first().id
        DuplicateAction.KEEP_NEWEST ->
            candidates.maxByOrNull { it.lastModifiedEpochMillis }?.id ?: candidates.first().id
        DuplicateAction.KEEP_OLDEST ->
            candidates.minByOrNull { it.lastModifiedEpochMillis }?.id ?: candidates.first().id
        DuplicateAction.KEEP_LARGEST ->
            candidates.maxByOrNull { it.fileSize }?.id ?: candidates.first().id
    }

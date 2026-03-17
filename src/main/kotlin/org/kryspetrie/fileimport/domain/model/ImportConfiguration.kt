package org.kryspetrie.fileimport.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class ImportConfiguration(
    // Organization
    val folderPattern: String = "{yyyy-MM-dd}",
    val fileNamePattern: String = "{original}",
    val fileNameExtension: String = "{ext}",
    val preserveOriginalName: Boolean = true,
    val createSubfolders: Boolean = true,
    val dateSource: DateSource = DateSource.EXIF_DATE,

    // Conflict handling
    val conflictResolution: ConflictResolution = ConflictResolution.RENAME,

    // Already-transferred detection (for "Import New" mode)
    val detectTransferredByHash: Boolean = true,
    val detectTransferredByExif: Boolean = false,

    // Visual duplicate detection
    val detectVisualDuplicates: Boolean = false,
    val perceptualHashThreshold: Float = 0.95f,
    val useSurfMatching: Boolean = false,
    val surfMatchThreshold: Int = 30,

    // RAW+JPEG pair handling
    val rawJpegPairMode: RawJpegPairMode = RawJpegPairMode.IMPORT_BOTH,
    val keepPairsTogether: Boolean = true,

    // Sidecar handling
    val importSidecars: Boolean = true,

    // Post-import
    val verifyAfterCopy: Boolean = true,
    val deleteAfterImport: Boolean = false,
)

@Serializable
data class DeduplicationSettings(
    val enableHashDeduplication: Boolean = true,
    val enablePerceptualHash: Boolean = true,
    val enableExifDeduplication: Boolean = true,
    val enableFilenameDeduplication: Boolean = false,
    val ignoreDifferentFileTypes: Boolean = true,
    val perceptualHashThreshold: Float = 0.95f,
    val autoResolveDuplicates: Boolean = false,
    val enableSurfMatching: Boolean = false,
    val surfMatchThreshold: Int = 30
)

enum class DateSource {
  EXIF_DATE,
  FILE_MODIFIED_DATE,
  FILE_CREATED_DATE
}

enum class ConflictResolution {
  RENAME,
  SKIP,
  REPLACE,
  ASK_USER
}

enum class ImportMode {
  ALL,
  NEW,
  SELECT
}

@Serializable
enum class RawJpegPairMode {
  IMPORT_BOTH,
  RAW_ONLY,
  JPEG_ONLY
}

@Serializable
data class PatternPreset(val name: String, val pattern: String, val isBuiltIn: Boolean = false)

object FolderPresets {
  val builtIn =
      listOf(
          PatternPreset("Flat date", "{yyyy-MM-dd}", isBuiltIn = true),
          PatternPreset("Year-Month", "{yyyy-MM}", isBuiltIn = true),
          PatternPreset("Year / Date", "{yyyy}/{yyyy-MM-dd}", isBuiltIn = true),
          PatternPreset("Year / Month", "{yyyy}/{MM}", isBuiltIn = true),
          PatternPreset("Year / Month / Day", "{yyyy}/{MM}/{dd}", isBuiltIn = true),
          PatternPreset("Year / Month-Day", "{yyyy}/{MM}-{dd}", isBuiltIn = true),
          PatternPreset("Camera / Date", "{camera}/{yyyy-MM-dd}", isBuiltIn = true),
          PatternPreset("Year / Camera", "{yyyy}/{camera}", isBuiltIn = true),
          PatternPreset("Year / Type", "{yyyy}/{type}", isBuiltIn = true),
          PatternPreset("Year / Type / Date", "{yyyy}/{type}/{yyyy-MM-dd}", isBuiltIn = true),
      )

  val examples =
      mapOf(
          "{yyyy-MM-dd}" to "dest/2024-01-15/IMG_0001.jpg",
          "{yyyy-MM}" to "dest/2024-01/IMG_0001.jpg",
          "{yyyy}/{yyyy-MM-dd}" to "dest/2024/2024-01-15/IMG_0001.jpg",
          "{yyyy}/{MM}" to "dest/2024/01/IMG_0001.jpg",
          "{yyyy}/{MM}/{dd}" to "dest/2024/01/15/IMG_0001.jpg",
          "{yyyy}/{MM}-{dd}" to "dest/2024/01-15/IMG_0001.jpg",
          "{camera}/{yyyy-MM-dd}" to "dest/Canon EOS R5/2024-01-15/IMG_0001.jpg",
          "{yyyy}/{camera}" to "dest/2024/Canon EOS R5/IMG_0001.jpg",
          "{yyyy}/{type}" to "dest/2024/Photos/ or dest/2024/Videos/",
      )
}

object SidecarExtensions {
  val extensions = setOf("xmp", "thm", "lrv", "aae", "pp3", "dop", "cos", "nks")

  fun isSidecar(extension: String): Boolean = extensions.contains(extension.lowercase())
}

object FilenamePresets {
  val builtIn =
      listOf(
          PatternPreset("Original", "{original}", isBuiltIn = true),
          PatternPreset("Date + Original", "{yyyy}{MM}{dd}_{original}", isBuiltIn = true),
          PatternPreset("Date-Time", "{yyyy}{MM}{dd}_{HH}{mm}{ss}", isBuiltIn = true),
          PatternPreset("Date + Counter", "{yyyy}{MM}{dd}_{counter}", isBuiltIn = true),
          PatternPreset("Camera + Date", "{camera}_{yyyy}{MM}{dd}_{counter}", isBuiltIn = true),
          PatternPreset(
              "Full EXIF", "{yyyy}{MM}{dd}_{HH}{mm}{ss}_{camera}_ISO{iso}", isBuiltIn = true),
      )
}

object NamePlaceholders {
  val folderPlaceholders =
      mapOf(
          "{yyyy-MM-dd}" to "Year-Month-Day",
          "{yyyy-MM}" to "Year-Month",
          "{yyyy}" to "Year (4 digits)",
          "{yy}" to "Year (2 digits)",
          "{MM}" to "Month (01-12)",
          "{dd}" to "Day (01-31)",
          "{HH}" to "Hour (00-23)",
          "{mm}" to "Minute (00-59)",
          "{camera}" to "Camera model",
          "{make}" to "Camera make",
          "{lens}" to "Lens model",
          "{type}" to "Media type (Photos / Videos)",
      )
  val filePlaceholders =
      mapOf(
          "{original}" to "Original filename",
          "{ext}" to "File extension",
          "{yyyy}" to "Year",
          "{MM}" to "Month",
          "{dd}" to "Day",
          "{HH}" to "Hour",
          "{mm}" to "Minute",
          "{ss}" to "Second",
          "{camera}" to "Camera model",
          "{make}" to "Camera make",
          "{lens}" to "Lens model",
          "{iso}" to "ISO value",
          "{aperture}" to "Aperture (f/x)",
          "{shutter}" to "Shutter speed",
          "{focal}" to "Focal length (mm)",
          "{focal35}" to "35mm equiv. focal length",
          "{width}" to "Image/video width (px)",
          "{height}" to "Image/video height (px)",
          "{counter}" to "Sequential counter",
          "{type}" to "Media type (photo / video)",
          "{duration}" to "Video duration (e.g. 1m30s)",
          "{fps}" to "Video frame rate",
          "{codec}" to "Video codec",
      )
}

package org.kryspetrie.fileimport.infrastructure.adapter

import java.io.File
import java.time.format.DateTimeFormatter
import org.kryspetrie.fileimport.domain.model.ImageFile
import org.kryspetrie.fileimport.domain.model.ImportConfiguration
import org.kryspetrie.fileimport.domain.port.FileStructurePreview
import org.kryspetrie.fileimport.domain.port.NamingPort

class NamingAdapter : NamingPort {
    override fun generateFilePath(
        imageFile: ImageFile,
        destinationRoot: String,
        configuration: ImportConfiguration,
        counter: Int,
    ): String {
        return "${generateFolderPath(imageFile, destinationRoot, configuration)}/" +
            "${generateFileName(imageFile, configuration, counter)}"
    }

    override fun generateFolderPath(
        imageFile: ImageFile,
        destinationRoot: String,
        configuration: ImportConfiguration,
    ): String {
        if (!configuration.createSubfolders) return destinationRoot
        var folderPattern = configuration.folderPattern
        val date =
            imageFile.dateTaken
                ?: imageFile.metadata?.dateTimeOriginal
                ?: imageFile.metadata?.dateTimeModified
                ?: imageFile.metadata?.dateTimeDigitized
                ?: java.time.LocalDateTime.now()

        val mediaType = if (imageFile.fileType.isVideo) "Videos" else "Photos"
        folderPattern =
            replaceDateTokens(folderPattern, date)
                .replace("{camera}", sanitizeFileName(imageFile.metadata?.cameraModel ?: "Unknown"))
                .replace("{make}", sanitizeFileName(imageFile.metadata?.cameraMake ?: "Unknown"))
                .replace("{lens}", sanitizeFileName(imageFile.metadata?.lensModel ?: "Unknown"))
                .replace("{type}", mediaType)

        return "$destinationRoot/$folderPattern"
    }

    override fun generateFileName(
        imageFile: ImageFile,
        configuration: ImportConfiguration,
        counter: Int,
    ): String {
        // Always replace {original} with the actual filename (regardless of preserveOriginalName)
        // When preserveOriginalName=true, we use the file's original name
        // When preserveOriginalName=false, we use the pattern but still recognize {original}
        val fileNameWithoutExt = imageFile.file.nameWithoutExtension
        var fileNamePattern =
            configuration.fileNamePattern.replace("{original}", fileNameWithoutExt)

        val date =
            imageFile.dateTaken
                ?: imageFile.metadata?.dateTimeOriginal
                ?: imageFile.metadata?.dateTimeModified
                ?: imageFile.metadata?.dateTimeDigitized
                ?: java.time.LocalDateTime.now()

        val meta = imageFile.metadata
        val mediaType = if (imageFile.fileType.isVideo) "video" else "photo"
        val durationStr =
            meta?.durationSeconds?.let {
                val m = (it / 60).toInt()
                val s = (it % 60).toInt()
                if (m > 0) "${m}m${s}s" else "${s}s"
            } .orEmpty()
        fileNamePattern =
            replaceDateTokens(fileNamePattern, date)
                .replace("{camera}", sanitizeFileName(meta?.cameraModel .orEmpty()))
                .replace("{make}", sanitizeFileName(meta?.cameraMake .orEmpty()))
                .replace("{lens}", sanitizeFileName(meta?.lensModel .orEmpty()))
                .replace("{iso}", meta?.iso?.toString() .orEmpty())
                .replace("{aperture}", meta?.aperture?.let { "f${it}" } .orEmpty())
                .replace("{shutter}", sanitizeFileName(meta?.shutterSpeed .orEmpty()))
                .replace("{focal}", meta?.focalLength?.let { "${it.toInt()}mm" } .orEmpty())
                .replace("{focal35}", meta?.focalLength35mm?.let { "${it}mm" } .orEmpty())
                .replace("{width}", meta?.imageWidth?.toString() .orEmpty())
                .replace("{height}", meta?.imageHeight?.toString() .orEmpty())
                .replace("{counter}", counter.toString().padStart(4, '0'))
                .replace("{type}", mediaType)
                .replace("{duration}", durationStr)
                .replace("{fps}", meta?.frameRate?.let { "%.0f".format(it) } .orEmpty())
                .replace("{codec}", sanitizeFileName(meta?.videoCodec .orEmpty()))

        val extension =
            if (configuration.fileNameExtension == "{ext}") imageFile.file.extension.lowercase()
            else configuration.fileNameExtension
        return "$fileNamePattern.$extension"
    }

    override fun previewFileStructure(
        images: List<ImageFile>,
        destinationRoot: String,
        configuration: ImportConfiguration,
    ): List<FileStructurePreview> {
        return images.mapIndexed { index, image ->
            val folderPath = generateFolderPath(image, destinationRoot, configuration)
            val fileName = generateFileName(image, configuration, index + 1)
            val destPath = "$folderPath/$fileName"
            val destFile = File(destPath)
            FileStructurePreview(
                sourceFile = image,
                destinationPath = destPath,
                folderPath = folderPath,
                fileName = fileName,
                wouldConflict = wouldConflict(image, destinationRoot, configuration),
                existingFile = destFile.exists(),
            )
        }
    }

    override fun wouldConflict(
        imageFile: ImageFile,
        destinationRoot: String,
        configuration: ImportConfiguration,
    ): Boolean = File(generateFilePath(imageFile, destinationRoot, configuration, 1)).exists()

    override fun resolveConflict(
        imageFile: ImageFile,
        destinationRoot: String,
        configuration: ImportConfiguration,
    ): String {
        var counter = 1
        var path: String
        do {
            path = generateFilePath(imageFile, destinationRoot, configuration, counter)
            counter++
        } while (File(path).exists())
        return path
    }

    private fun replaceDateTokens(pattern: String, date: java.time.LocalDateTime): String =
        pattern
            .replace("{yyyy-MM-dd}", date.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")))
            .replace("{yyyy-MM}", date.format(DateTimeFormatter.ofPattern("yyyy-MM")))
            .replace("{yyyy}", date.format(DateTimeFormatter.ofPattern("yyyy")))
            .replace("{yy}", date.format(DateTimeFormatter.ofPattern("yy")))
            .replace("{MM}", date.format(DateTimeFormatter.ofPattern("MM")))
            .replace("{dd}", date.format(DateTimeFormatter.ofPattern("dd")))
            .replace("{HH}", date.format(DateTimeFormatter.ofPattern("HH")))
            .replace("{mm}", date.format(DateTimeFormatter.ofPattern("mm")))
            .replace("{ss}", date.format(DateTimeFormatter.ofPattern("ss")))

    private fun sanitizeFileName(name: String): String =
        name.replace(Regex("[\\\\/:*?\"<>|]"), "_").replace(Regex("\\s+"), "_").take(50)
}

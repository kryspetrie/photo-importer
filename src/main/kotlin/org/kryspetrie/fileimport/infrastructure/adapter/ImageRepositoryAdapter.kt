package org.kryspetrie.fileimport.infrastructure.adapter

import com.drew.imaging.ImageMetadataReader
import com.drew.metadata.Directory
import com.drew.metadata.avi.AviDirectory
import com.drew.metadata.exif.ExifIFD0Directory
import com.drew.metadata.exif.ExifSubIFDDirectory
import com.drew.metadata.exif.GpsDirectory
import com.drew.metadata.mov.QuickTimeDirectory
import com.drew.metadata.mov.media.QuickTimeVideoDirectory
import com.drew.metadata.mp4.Mp4Directory
import com.drew.metadata.mp4.media.Mp4SoundDirectory
import com.drew.metadata.mp4.media.Mp4VideoDirectory
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.time.ZoneId
import kotlinx.coroutines.withContext
import org.apache.commons.codec.digest.DigestUtils
import org.kryspetrie.fileimport.domain.model.FilePath
import org.kryspetrie.fileimport.domain.model.ImageFile
import org.kryspetrie.fileimport.domain.model.ImageFileType
import org.kryspetrie.fileimport.domain.model.ImageMetadata
import org.kryspetrie.fileimport.domain.model.SidecarExtensions
import org.kryspetrie.fileimport.domain.port.DispatcherProvider
import org.kryspetrie.fileimport.domain.port.ImageRepositoryPort

private const val COPY_BUFFER_SIZE = 262144 // 256KB
private const val HASH_BUFFER_SIZE = 262144 // 256KB — matches COPY_BUFFER_SIZE for consistency

class ImageRepositoryAdapter(private val dispatcherProvider: DispatcherProvider) :
    ImageRepositoryPort {
    private val supportedExtensions = ImageFileType.supportedExtensions()

    override suspend fun scanDirectory(directory: FilePath, recursive: Boolean): List<ImageFile> =
        withContext(dispatcherProvider.io) {
            val mediaFiles = mutableListOf<ImageFile>()
            val allFiles = mutableMapOf<String, MutableList<File>>()

            fun scan(dir: File) {
                val dirFiles = dir.listFiles() ?: return
                dirFiles.forEach { file ->
                    if (file.isDirectory && recursive) {
                        scan(file)
                    } else if (supportedExtensions.contains(file.extension.lowercase())) {
                        mediaFiles.add(
                            ImageFile(path = FilePath(file.absolutePath), fileSize = file.length())
                        )
                    }
                }
                allFiles[dir.absolutePath] = dirFiles.filter { it.isFile }.toMutableList()
            }
            scan(directory.toFile())

            // Build lookup map by (directory, lowercased baseName) for O(1) sidecar matching
            val sidecarLookup = mutableMapOf<Pair<String, String>, MutableList<File>>()
            for ((dirPath, dirFiles) in allFiles) {
                for (file in dirFiles) {
                    if (SidecarExtensions.isSidecar(file.extension)) {
                        val key = dirPath to file.nameWithoutExtension.lowercase()
                        sidecarLookup.getOrPut(key) { mutableListOf() }.add(file)
                    }
                }
            }

            mediaFiles.map { media ->
                val dirPath = media.file.parentFile?.absolutePath ?: return@map media
                val baseName = media.path.nameWithoutExtension.lowercase()
                val sidecarFiles =
                    sidecarLookup[dirPath to baseName]
                        ?.filter { it.absolutePath != media.filePath }
                        ?.map { FilePath(it.absolutePath) } ?: emptyList()
                if (sidecarFiles.isNotEmpty()) media.copy(sidecars = sidecarFiles) else media
            }
        }

    override suspend fun getMetadata(imageFile: ImageFile): ImageMetadata? =
        withContext(dispatcherProvider.io) {
            try {
                val metadata = ImageMetadataReader.readMetadata(imageFile.file)

                if (imageFile.fileType.isVideo) {
                    extractVideoMetadata(metadata)
                } else {
                    extractPhotoMetadata(metadata)
                }
            } catch (_: Exception) {
                null
            }
        }

    private fun extractPhotoMetadata(metadata: com.drew.metadata.Metadata): ImageMetadata {
        val ifd0 = metadata.getFirstDirectoryOfType(ExifIFD0Directory::class.java)
        val sub = metadata.getFirstDirectoryOfType(ExifSubIFDDirectory::class.java)
        val gps = metadata.getFirstDirectoryOfType(GpsDirectory::class.java)

        val dateOriginal =
            safeDate(sub, ExifSubIFDDirectory.TAG_DATETIME_ORIGINAL)
                ?: safeDate(ifd0, ExifIFD0Directory.TAG_DATETIME)
        val geoLocation =
            try {
                gps?.geoLocation
            } catch (_: Exception) {
                null
            }

        return ImageMetadata(
            dateTimeOriginal = dateOriginal,
            dateTimeDigitized = safeDate(sub, ExifSubIFDDirectory.TAG_DATETIME_DIGITIZED),
            dateTimeModified = safeDate(ifd0, ExifIFD0Directory.TAG_DATETIME),
            make = safeString(ifd0, ExifIFD0Directory.TAG_MAKE)?.trim(),
            model = safeString(ifd0, ExifIFD0Directory.TAG_MODEL)?.trim(),
            lensModel = safeString(sub, ExifSubIFDDirectory.TAG_LENS_MODEL)?.trim(),
            focalLength = safeRationalFloat(sub, ExifSubIFDDirectory.TAG_FOCAL_LENGTH),
            focalLength35mm = safeInt(sub, ExifSubIFDDirectory.TAG_35MM_FILM_EQUIV_FOCAL_LENGTH),
            aperture = safeRationalFloat(sub, ExifSubIFDDirectory.TAG_FNUMBER),
            shutterSpeed = safeDesc(sub, ExifSubIFDDirectory.TAG_EXPOSURE_TIME),
            iso = safeInt(sub, ExifSubIFDDirectory.TAG_ISO_EQUIVALENT),
            exposureProgram = safeDesc(sub, ExifSubIFDDirectory.TAG_EXPOSURE_PROGRAM),
            meteringMode = safeDesc(sub, ExifSubIFDDirectory.TAG_METERING_MODE),
            flash = safeDesc(sub, ExifSubIFDDirectory.TAG_FLASH),
            whiteBalance = safeDesc(sub, ExifSubIFDDirectory.TAG_WHITE_BALANCE_MODE),
            exposureCompensation = safeRationalFloat(sub, ExifSubIFDDirectory.TAG_EXPOSURE_BIAS),
            latitude = geoLocation?.latitude,
            longitude = geoLocation?.longitude,
            altitude = safeRationalDouble(gps, GpsDirectory.TAG_ALTITUDE),
            imageWidth =
                safeInt(sub, ExifSubIFDDirectory.TAG_EXIF_IMAGE_WIDTH)
                    ?: safeInt(ifd0, ExifIFD0Directory.TAG_IMAGE_WIDTH),
            imageHeight =
                safeInt(sub, ExifSubIFDDirectory.TAG_EXIF_IMAGE_HEIGHT)
                    ?: safeInt(ifd0, ExifIFD0Directory.TAG_IMAGE_HEIGHT),
            orientation = safeInt(ifd0, ExifIFD0Directory.TAG_ORIENTATION),
            colorSpace = safeDesc(sub, ExifSubIFDDirectory.TAG_COLOR_SPACE),
            software = safeString(ifd0, ExifIFD0Directory.TAG_SOFTWARE)?.trim(),
            copyright = safeString(ifd0, ExifIFD0Directory.TAG_COPYRIGHT)?.trim(),
            artist = safeString(ifd0, ExifIFD0Directory.TAG_ARTIST)?.trim(),
            description = safeString(ifd0, ExifIFD0Directory.TAG_IMAGE_DESCRIPTION)?.trim(),
        )
    }

    private fun extractVideoMetadata(metadata: com.drew.metadata.Metadata): ImageMetadata {
        // Try MP4 directories first, then QuickTime (MOV), then AVI
        val mp4 = metadata.getFirstDirectoryOfType(Mp4Directory::class.java)
        val mp4Video = metadata.getFirstDirectoryOfType(Mp4VideoDirectory::class.java)
        val mp4Sound = metadata.getFirstDirectoryOfType(Mp4SoundDirectory::class.java)
        val qt = metadata.getFirstDirectoryOfType(QuickTimeDirectory::class.java)
        val qtVideo = metadata.getFirstDirectoryOfType(QuickTimeVideoDirectory::class.java)
        val avi = metadata.getFirstDirectoryOfType(AviDirectory::class.java)
        val gps = metadata.getFirstDirectoryOfType(GpsDirectory::class.java)
        // Some video files also embed EXIF
        val ifd0 = metadata.getFirstDirectoryOfType(ExifIFD0Directory::class.java)

        val creationDate =
            safeDate(mp4, Mp4Directory.TAG_CREATION_TIME)
                ?: safeDate(qt, QuickTimeDirectory.TAG_CREATION_TIME)
                ?: safeDate(ifd0, ExifIFD0Directory.TAG_DATETIME)

        val geoLocation =
            try {
                gps?.geoLocation
            } catch (_: Exception) {
                null
            }

        val durationSecs =
            safeLong(mp4, Mp4Directory.TAG_DURATION)?.let { it / 1000.0 }
                ?: safeLong(qt, QuickTimeDirectory.TAG_DURATION)?.let { it / 1000.0 }
                ?: safeLong(avi, AviDirectory.TAG_DURATION)?.let { it / 1000.0 }

        val width =
            safeInt(mp4Video, Mp4VideoDirectory.TAG_WIDTH)
                ?: safeInt(qtVideo, QuickTimeVideoDirectory.TAG_WIDTH)
                ?: safeInt(avi, AviDirectory.TAG_WIDTH)
        val height =
            safeInt(mp4Video, Mp4VideoDirectory.TAG_HEIGHT)
                ?: safeInt(qtVideo, QuickTimeVideoDirectory.TAG_HEIGHT)
                ?: safeInt(avi, AviDirectory.TAG_HEIGHT)

        val frameRate =
            safeFloat(mp4Video, Mp4VideoDirectory.TAG_FRAME_RATE)?.toDouble()
                ?: safeFloat(qtVideo, QuickTimeVideoDirectory.TAG_FRAME_RATE)?.toDouble()

        val videoCodec =
            safeString(mp4Video, Mp4VideoDirectory.TAG_COMPRESSION_TYPE)
                ?: safeString(qtVideo, QuickTimeVideoDirectory.TAG_COMPRESSION_TYPE)
                ?: safeString(avi, AviDirectory.TAG_VIDEO_CODEC)
        val audioCodec = safeString(mp4Sound, Mp4SoundDirectory.TAG_AUDIO_FORMAT)

        val rotation =
            safeInt(mp4Video, Mp4VideoDirectory.TAG_ROTATION)
                ?: safeInt(qtVideo, QuickTimeVideoDirectory.TAG_ROTATION)

        return ImageMetadata(
            dateTimeOriginal = creationDate,
            dateTimeModified =
                safeDate(mp4, Mp4Directory.TAG_MODIFICATION_TIME)
                    ?: safeDate(qt, QuickTimeDirectory.TAG_MODIFICATION_TIME),
            make = safeString(ifd0, ExifIFD0Directory.TAG_MAKE)?.trim(),
            model = safeString(ifd0, ExifIFD0Directory.TAG_MODEL)?.trim(),
            latitude = geoLocation?.latitude,
            longitude = geoLocation?.longitude,
            altitude = safeRationalDouble(gps, GpsDirectory.TAG_ALTITUDE),
            imageWidth = width,
            imageHeight = height,
            durationSeconds = durationSecs,
            frameRate = frameRate,
            videoCodec = videoCodec?.trim(),
            audioCodec = audioCodec?.trim(),
            rotation = rotation,
        )
    }

    private fun safeString(dir: Directory?, tag: Int): String? =
        try {
            dir?.getString(tag)
        } catch (_: Exception) {
            null
        }

    private fun safeDesc(dir: Directory?, tag: Int): String? =
        try {
            dir?.getDescription(tag)
        } catch (_: Exception) {
            null
        }

    private fun safeInt(dir: Directory?, tag: Int): Int? =
        try {
            dir?.getInteger(tag)
        } catch (_: Exception) {
            null
        }

    private fun safeLong(dir: Directory?, tag: Int): Long? =
        try {
            dir?.getLong(tag)
        } catch (_: Exception) {
            null
        }

    private fun safeFloat(dir: Directory?, tag: Int): Float? =
        try {
            dir?.getFloat(tag)
        } catch (_: Exception) {
            null
        }

    private fun safeRationalFloat(dir: Directory?, tag: Int): Float? =
        try {
            dir?.getRational(tag)?.toFloat()
        } catch (_: Exception) {
            null
        }

    private fun safeRationalDouble(dir: Directory?, tag: Int): Double? =
        try {
            dir?.getRational(tag)?.toDouble()
        } catch (_: Exception) {
            null
        }

    private fun safeDate(dir: Directory?, tag: Int): java.time.LocalDateTime? =
        try {
            dir?.getDate(tag)?.toInstant()?.atZone(ZoneId.systemDefault())?.toLocalDateTime()
        } catch (_: Exception) {
            null
        }

    override suspend fun calculateFileHash(imageFile: ImageFile, algorithm: String): String =
        withContext(dispatcherProvider.io) {
            try {
                FileInputStream(imageFile.file).buffered(HASH_BUFFER_SIZE).use {
                    DigestUtils.md5Hex(it)
                }
            } catch (_: Exception) {
                ""
            }
        }

    override suspend fun calculatePerceptualHash(imageFile: ImageFile): Float? = null

    override suspend fun copyFile(
        source: ImageFile,
        destination: FilePath,
        onProgress: (Long, Long) -> Unit,
    ): Boolean =
        withContext(dispatcherProvider.io) {
            try {
                val destFile = destination.toFile()
                destFile.parentFile?.mkdirs()
                val totalBytes = source.fileSize
                var copiedBytes = 0L
                java.io.FileInputStream(source.file).channel.use { srcChannel ->
                    java.io.FileOutputStream(destFile).channel.use { dstChannel ->
                        var position = 0L
                        while (position < totalBytes) {
                            val transferred =
                                srcChannel.transferTo(
                                    position,
                                    COPY_BUFFER_SIZE.toLong(),
                                    dstChannel,
                                )
                            if (transferred <= 0) break
                            position += transferred
                            copiedBytes += transferred
                            onProgress(copiedBytes, totalBytes)
                        }
                    }
                }
                true
            } catch (_: Exception) {
                false
            }
        }

    override suspend fun verifyCopy(source: ImageFile, destination: FilePath): Boolean =
        withContext(dispatcherProvider.io) {
            try {
                // Use the stored hash from the source ImageFile if available,
                // avoiding a redundant re-read of the source file
                val destFile = destination.toFile()
                val sourceHash =
                    source.hash
                        ?: FileInputStream(source.file).buffered(HASH_BUFFER_SIZE).use {
                            DigestUtils.md5Hex(it)
                        }
                val destHash =
                    FileInputStream(destFile).buffered(HASH_BUFFER_SIZE).use {
                        DigestUtils.md5Hex(it)
                    }
                sourceHash == destHash
            } catch (_: Exception) {
                false
            }
        }

    override suspend fun deleteFile(imageFile: ImageFile): Boolean =
        withContext(dispatcherProvider.io) {
            try {
                imageFile.file.delete()
            } catch (_: Exception) {
                false
            }
        }

    override suspend fun fileExists(file: FilePath): Boolean = file.toFile().exists()

    override fun getSupportedExtensions(): Set<String> = supportedExtensions
}

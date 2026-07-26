package org.kryspetrie.fileimport.testsupport

import com.petrielabs.metadataeditor.adapters.exiftool.ExifToolLocator
import com.petrielabs.metadataeditor.adapters.exiftool.ExifToolMetadataEngine
import com.petrielabs.metadataeditor.adapters.exiftool.ExifToolProcessRunner
import com.petrielabs.metadataeditor.adapters.exiftool.HardDeleteBackupDisposal
import com.petrielabs.metadataeditor.adapters.exiftool.ImageWriteBackupAdapter
import com.petrielabs.metadataeditor.application.MetadataApplicationService
import com.petrielabs.metadataeditor.ports.inbound.MetadataEditorPort
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.kryspetrie.fileimport.application.export.MetadataWritingService
import org.kryspetrie.fileimport.domain.port.FaceRegionTransformerPort
import org.kryspetrie.fileimport.domain.port.ImageProcessingPort

/** Shared ExifTool wiring for tests (bundled binary + photo-metadata-editor services). */
object ExifToolTestSupport {
    private val resourceRoots =
        listOf(
            File("appResources/macos"),
            File("appResources/linux"),
            File("appResources/windows"),
        )

    fun assumeExifToolAvailable() {
        configureExifToolResources()
        assumeTrue(resourceRoots.any { root -> File(root, "exiftool/exiftool").isFile }) {
            "Bundled ExifTool missing; run ./gradlew downloadExifTool"
        }
    }

    fun configureExifToolResources() {
        val osDir =
            when {
                System.getProperty("os.name").orEmpty().lowercase().contains("win") -> "windows"
                System.getProperty("os.name").orEmpty().lowercase().contains("mac") -> "macos"
                else -> "linux"
            }
        System.setProperty(
            "compose.application.resources.dir",
            File("appResources", osDir).absolutePath,
        )
    }

    fun createProcessRunner(): ExifToolProcessRunner {
        assumeExifToolAvailable()
        return ExifToolProcessRunner(ExifToolLocator(extraResourceRoots = resourceRoots))
    }

    fun createMetadataEditor(): MetadataEditorPort {
        return MetadataApplicationService(
            engine = ExifToolMetadataEngine(createProcessRunner()),
            backup = ImageWriteBackupAdapter(disposal = HardDeleteBackupDisposal()),
        )
    }

    fun createMetadataWritingService(
        faceRegionTransformer: FaceRegionTransformerPort,
        imageProcessing: ImageProcessingPort,
    ): MetadataWritingService =
        MetadataWritingService(
            metadataEditor = createMetadataEditor(),
            faceRegionTransformer = faceRegionTransformer,
            imageProcessing = imageProcessing,
        )

    /** Copies a classpath resource from metadata-test-fixtures (e.g. `samples/foo.CR2`). */
    fun copySampleResource(resourcePath: String): Path {
        val stream =
            checkNotNull(
                Thread.currentThread().contextClassLoader.getResourceAsStream(resourcePath)
            ) {
                "Missing classpath resource: $resourcePath (metadata-test-fixtures)"
            }
        val suffix = "." + java.nio.file.Path.of(resourcePath).fileName.toString().substringAfterLast('.', "")
        val target = Files.createTempFile("sample-", suffix)
        stream.use { Files.copy(it, target, StandardCopyOption.REPLACE_EXISTING) }
        target.toFile().deleteOnExit()
        return target
    }
}

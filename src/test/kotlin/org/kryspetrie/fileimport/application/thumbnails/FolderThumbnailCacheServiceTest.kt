package org.kryspetrie.fileimport.application.thumbnails

import java.awt.Color
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.kryspetrie.fileimport.application.TestDispatcherProvider
import org.kryspetrie.fileimport.application.TestFileSystemAdapter
import org.kryspetrie.fileimport.domain.model.FilePath
import org.kryspetrie.fileimport.domain.port.ThumbnailExtractorPort
import org.kryspetrie.fileimport.infrastructure.adapter.toProcessedImage
import org.kryspetrie.fileimport.infrastructure.thumbnails.FolderThumbnailCacheAdapter
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@DisplayName("FolderThumbnailCacheAdapter")
class FolderThumbnailCacheServiceTest {

    @TempDir lateinit var tempDir: File

    private lateinit var service: FolderThumbnailCacheAdapter
    private lateinit var albumDir: File
    private lateinit var thumbnailExtractor: ThumbnailExtractorPort

    @BeforeEach
    fun setUp() {
        thumbnailExtractor = mock<ThumbnailExtractorPort>()
        service =
            FolderThumbnailCacheAdapter(
                fileSystem = TestFileSystemAdapter(),
                thumbnailExtractor = thumbnailExtractor,
                dispatcherProvider = TestDispatcherProvider(),
            )
        albumDir = File(tempDir, "album").apply { mkdirs() }
    }

    @Test
    fun getThumbnailWritesToThumbsFolder() = runTest {
        // GIVEN
        val photo = writeTestImage("photo.jpg", Color.RED)
        val source = FilePath(photo.absolutePath)

        // WHEN
        val thumb = service.getThumbnail(source, albumDir.absolutePath, maxPx = 80)

        // THEN
        assertThat(thumb).isNotNull
        assertThat(File(albumDir, ".thumbs/photo.jpg")).exists()
    }

    @Test
    fun reconcileRemovesStaleAndOrphanCaches() = runTest {
        // GIVEN
        val photo = writeTestImage("keep.jpg", Color.BLUE)
        val removed = writeTestImage("gone.jpg", Color.GREEN)
        val sourceKeep = FilePath(photo.absolutePath)
        val sourceRemoved = FilePath(removed.absolutePath)
        service.getThumbnail(sourceKeep, albumDir.absolutePath, maxPx = 80)
        service.getThumbnail(sourceRemoved, albumDir.absolutePath, maxPx = 80)
        removed.delete()

        val orphan = File(albumDir, ".thumbs/orphan.jpg").apply { parentFile.mkdirs() }
        ImageIO.write(BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB), "jpg", orphan)

        // WHEN
        service.reconcileSources(listOf(sourceKeep), albumDir.absolutePath, maxPx = 80)

        // THEN
        assertThat(File(albumDir, ".thumbs/keep.jpg")).exists()
        assertThat(File(albumDir, ".thumbs/gone.jpg")).doesNotExist()
        assertThat(orphan).doesNotExist()
    }

    @Test
    fun invalidateRemovesCachedThumbnail() = runTest {
        // GIVEN
        val photo = writeTestImage("inv.jpg", Color.YELLOW)
        val source = FilePath(photo.absolutePath)
        service.getThumbnail(source, albumDir.absolutePath, maxPx = 80)
        assertThat(File(albumDir, ".thumbs/inv.jpg")).exists()

        // WHEN
        service.invalidate(source, albumDir.absolutePath, maxPx = 80)

        // THEN
        assertThat(File(albumDir, ".thumbs/inv.jpg")).doesNotExist()
    }

    @Test
    fun getThumbnailReadsFromDiskCacheWithoutRegenerating() = runTest {
        // GIVEN
        val photo = writeTestImage("cached.jpg", Color.MAGENTA)
        val source = FilePath(photo.absolutePath)
        service.getThumbnail(source, albumDir.absolutePath, maxPx = 80)
        val cacheFile = File(albumDir, ".thumbs/cached.jpg")
        val cacheMtime = cacheFile.lastModified()

        // WHEN
        val thumb = service.getThumbnail(source, albumDir.absolutePath, maxPx = 80)

        // THEN
        assertThat(thumb).isNotNull
        assertThat(cacheFile.lastModified()).isEqualTo(cacheMtime)
    }

    @Test
    fun getThumbnailRegeneratesWhenSourceIsNewerThanCache() = runTest {
        // GIVEN
        val photo = writeTestImage("stale.jpg", Color.CYAN)
        val source = FilePath(photo.absolutePath)
        service.getThumbnail(source, albumDir.absolutePath, maxPx = 80)
        val cacheFile = File(albumDir, ".thumbs/stale.jpg")
        cacheFile.setLastModified(1_000L)
        photo.setLastModified(System.currentTimeMillis())

        // WHEN
        val thumb = service.getThumbnail(source, albumDir.absolutePath, maxPx = 80)

        // THEN
        assertThat(thumb).isNotNull
        assertThat(cacheFile.lastModified()).isGreaterThan(1_000L)
    }

    @Test
    fun reconcileRemovesStaleCacheWhenSourceNewerThanCache() = runTest {
        // GIVEN
        val photo = writeTestImage("mtime.jpg", Color.ORANGE)
        val source = FilePath(photo.absolutePath)
        service.getThumbnail(source, albumDir.absolutePath, maxPx = 80)
        val cacheFile = File(albumDir, ".thumbs/mtime.jpg")
        cacheFile.setLastModified(1_000L)
        photo.setLastModified(System.currentTimeMillis())

        // WHEN
        service.reconcileSources(listOf(source), albumDir.absolutePath, maxPx = 80)

        // THEN
        assertThat(cacheFile).doesNotExist()
    }

    @Test
    fun getThumbnailScalesLargeImages() = runTest {
        // GIVEN
        val photo = writeTestImage("large.jpg", Color.PINK)
        val source = FilePath(photo.absolutePath)

        // WHEN
        val thumb = service.getThumbnail(source, albumDir.absolutePath, maxPx = 80)

        // THEN
        assertThat(thumb).isNotNull
        assertThat(thumb!!.width).isLessThanOrEqualTo(80)
        assertThat(thumb.height).isLessThanOrEqualTo(80)
        assertThat(thumb.width).isEqualTo(80)
        assertThat(thumb.height).isEqualTo(40)
    }

    @Test
    fun getThumbnailReturnsNullForMissingSource() = runTest {
        // GIVEN
        val missing = FilePath(File(albumDir, "nope.jpg").absolutePath)

        // WHEN
        val thumb = service.getThumbnail(missing, albumDir.absolutePath, maxPx = 80)

        // THEN
        assertThat(thumb).isNull()
    }

    @Test
    fun getThumbnailUsesRawExtractorForRawFiles() = runTest {
        // GIVEN
        val raw = File(albumDir, "photo.cr2").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        val embedded = BufferedImage(60, 40, BufferedImage.TYPE_INT_RGB).toProcessedImage()
        whenever(thumbnailExtractor.extractFromRaw(FilePath(raw.absolutePath))).thenReturn(embedded)

        // WHEN
        val thumb =
            service.getThumbnail(FilePath(raw.absolutePath), albumDir.absolutePath, maxPx = 80)

        // THEN
        assertThat(thumb).isNotNull
        assertThat(thumb!!.width).isEqualTo(60)
        assertThat(File(albumDir, ".thumbs/photo.jpg")).exists()
    }

    @Test
    fun reconcileSourcesNoOpWhenEmpty() = runTest {
        // GIVEN — no files, no .thumbs folder

        // WHEN / THEN — should not throw
        service.reconcileSources(emptyList(), albumDir.absolutePath, maxPx = 80)
        assertThat(File(albumDir, ".thumbs")).doesNotExist()
    }

    @Test
    fun reconcileSourcesNoOpWhenDiskCacheDisabled() = runTest {
        // GIVEN
        val photo = writeTestImage("skip-reconcile.jpg", Color.GRAY)
        val source = FilePath(photo.absolutePath)
        service.getThumbnail(source, albumDir.absolutePath, maxPx = 80)
        val orphan = File(albumDir, ".thumbs/orphan2.jpg").apply { parentFile.mkdirs() }
        ImageIO.write(BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB), "jpg", orphan)

        // WHEN
        service.reconcileSources(
            listOf(source),
            albumDir.absolutePath,
            maxPx = 80,
            diskCacheEnabled = false,
        )

        // THEN — orphan left intact because reconcile was skipped
        assertThat(orphan).exists()
    }

    @Test
    fun getThumbnailSkipsDiskWhenDisabled() = runTest {
        // GIVEN
        val photo = writeTestImage("memory-only.jpg", Color.LIGHT_GRAY)
        val source = FilePath(photo.absolutePath)

        // WHEN
        val thumb =
            service.getThumbnail(
                source,
                albumDir.absolutePath,
                maxPx = 80,
                diskCacheEnabled = false,
            )

        // THEN
        assertThat(thumb).isNotNull
        assertThat(File(albumDir, ".thumbs/memory-only.jpg")).doesNotExist()
    }

    @Test
    fun invalidateSourcesRemovesMultipleCaches() = runTest {
        // GIVEN
        val photoA = writeTestImage("a.jpg", Color.RED)
        File(albumDir, "sub").mkdirs()
        val photoB = writeTestImage("sub/b.jpg", Color.BLUE)
        val sourceA = FilePath(photoA.absolutePath)
        val sourceB = FilePath(photoB.absolutePath)
        service.getThumbnail(sourceA, albumDir.absolutePath, maxPx = 80)
        service.getThumbnail(sourceB, albumDir.absolutePath, maxPx = 80)

        // WHEN
        service.invalidateSources(listOf(sourceA, sourceB), albumDir.absolutePath)

        // THEN
        assertThat(File(albumDir, ".thumbs/a.jpg")).doesNotExist()
        assertThat(File(albumDir, ".thumbs/sub/b.jpg")).doesNotExist()
    }

    @Test
    fun deleteThumbsFolderRemovesEntireCache() = runTest {
        // GIVEN
        val photo = writeTestImage("delete-all.jpg", Color.DARK_GRAY)
        service.getThumbnail(FilePath(photo.absolutePath), albumDir.absolutePath, maxPx = 80)
        assertThat(File(albumDir, ".thumbs/delete-all.jpg")).exists()

        // WHEN
        service.deleteThumbsFolder(FilePath(albumDir.absolutePath))

        // THEN
        assertThat(File(albumDir, ".thumbs")).doesNotExist()
    }

    private fun writeTestImage(name: String, color: Color): File {
        val file = File(albumDir, name)
        val image = BufferedImage(200, 100, BufferedImage.TYPE_INT_RGB)
        image.createGraphics().apply {
            this.color = color
            fillRect(0, 0, 200, 100)
            dispose()
        }
        ImageIO.write(image, "jpg", file)
        return file
    }
}

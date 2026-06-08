package org.kryspetrie.fileimport.infrastructure.adapter

import java.io.File
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.kryspetrie.fileimport.application.TestDispatcherProvider
import org.kryspetrie.fileimport.domain.model.FilePath
import org.kryspetrie.fileimport.domain.model.ImageFile
import org.kryspetrie.fileimport.domain.model.ImageFileType

@DisplayName("ImageRepositoryAdapter")
class ImageRepositoryAdapterTest {
    private lateinit var adapter: ImageRepositoryAdapter

    @TempDir lateinit var tempDir: File

    @BeforeEach
    fun setup() {
        adapter = ImageRepositoryAdapter(TestDispatcherProvider())
    }

    @Nested
    @DisplayName("scanDirectory")
    inner class ScanDirectory {
        @Test
        @DisplayName("should find supported image files")
        fun shouldFindImageFiles() = runTest {
            File(tempDir, "photo1.jpg").writeText("jpg data")
            File(tempDir, "photo2.png").writeText("png data")
            File(tempDir, "readme.txt").writeText("text")

            val files = adapter.scanDirectory(FilePath(tempDir.absolutePath), recursive = false)

            assertThat(files).hasSize(2)
            assertThat(files.map { it.fileName })
                .containsExactlyInAnyOrder("photo1.jpg", "photo2.png")
        }

        @Test
        @DisplayName("should find files recursively")
        fun shouldFindFilesRecursively() = runTest {
            File(tempDir, "photo.jpg").writeText("root")
            File(tempDir, "sub").mkdirs()
            File(tempDir, "sub/nested.jpg").writeText("nested")

            val files = adapter.scanDirectory(FilePath(tempDir.absolutePath), recursive = true)

            assertThat(files).hasSize(2)
        }

        @Test
        @DisplayName("should not recurse when disabled")
        fun shouldNotRecurse() = runTest {
            File(tempDir, "photo.jpg").writeText("root")
            File(tempDir, "sub").mkdirs()
            File(tempDir, "sub/nested.jpg").writeText("nested")

            val files = adapter.scanDirectory(FilePath(tempDir.absolutePath), recursive = false)

            assertThat(files).hasSize(1)
            assertThat(files[0].fileName).isEqualTo("photo.jpg")
        }

        @Test
        @DisplayName("should find video files")
        fun shouldFindVideoFiles() = runTest {
            File(tempDir, "video.mp4").writeText("video data")
            File(tempDir, "movie.mov").writeText("movie data")

            val files = adapter.scanDirectory(FilePath(tempDir.absolutePath), recursive = false)

            assertThat(files).hasSize(2)
            assertThat(files.any { it.fileType == ImageFileType.VIDEO_MP4 }).isTrue()
            assertThat(files.any { it.fileType == ImageFileType.VIDEO_MOV }).isTrue()
        }

        @Test
        @DisplayName("should find RAW files")
        fun shouldFindRawFiles() = runTest {
            File(tempDir, "photo.cr2").writeText("raw")
            File(tempDir, "photo.nef").writeText("raw")
            File(tempDir, "photo.arw").writeText("raw")

            val files = adapter.scanDirectory(FilePath(tempDir.absolutePath), recursive = false)

            assertThat(files).hasSize(3)
            assertThat(files.all { it.fileType.isRaw }).isTrue()
        }

        @Test
        @DisplayName("should associate sidecar files with parent media")
        fun shouldAssociateSidecars() = runTest {
            File(tempDir, "photo.cr2").writeText("raw data")
            File(tempDir, "photo.xmp").writeText("xmp sidecar")
            File(tempDir, "photo.thm").writeText("thumbnail sidecar")
            File(tempDir, "other.jpg").writeText("other photo")

            val files = adapter.scanDirectory(FilePath(tempDir.absolutePath), recursive = false)
            val rawFile = files.find { it.fileType == ImageFileType.RAW_CR2 }

            assertThat(rawFile).isNotNull
            assertThat(rawFile!!.sidecars).hasSize(2)
            assertThat(rawFile.sidecars.map { it.extension })
                .containsExactlyInAnyOrder("xmp", "thm")
        }

        @Test
        @DisplayName("should not associate non-sidecar files as sidecars")
        fun shouldNotAssociateNonSidecars() = runTest {
            File(tempDir, "photo.jpg").writeText("jpeg")
            File(tempDir, "photo.txt").writeText("text note")

            val files = adapter.scanDirectory(FilePath(tempDir.absolutePath), recursive = false)

            assertThat(files).hasSize(1)
            assertThat(files[0].sidecars).isEmpty()
        }

        @Test
        @DisplayName("should return empty list for empty directory")
        fun shouldReturnEmptyForEmptyDir() = runTest {
            val files = adapter.scanDirectory(FilePath(tempDir.absolutePath), recursive = true)
            assertThat(files).isEmpty()
        }
    }

    @Nested
    @DisplayName("calculateFileHash")
    inner class CalculateHash {
        @Test
        @DisplayName("should return consistent MD5 hash")
        fun shouldReturnConsistentHash() = runTest {
            val file = File(tempDir, "hashtest.jpg")
            file.writeText("consistent content")
            val imageFile = ImageFile(path = FilePath(file.absolutePath), fileSize = file.length())

            val hash1 = adapter.calculateFileHash(imageFile)
            val hash2 = adapter.calculateFileHash(imageFile)

            assertThat(hash1).isNotEmpty()
            assertThat(hash1).isEqualTo(hash2)
        }

        @Test
        @DisplayName("should return different hashes for different content")
        fun shouldReturnDifferentHashes() = runTest {
            val file1 = File(tempDir, "file1.jpg")
            file1.writeText("content A")
            val file2 = File(tempDir, "file2.jpg")
            file2.writeText("content B")

            val hash1 =
                adapter.calculateFileHash(
                    ImageFile(path = FilePath(file1.absolutePath), fileSize = file1.length())
                )
            val hash2 =
                adapter.calculateFileHash(
                    ImageFile(path = FilePath(file2.absolutePath), fileSize = file2.length())
                )

            assertThat(hash1).isNotEqualTo(hash2)
        }
    }

    @Nested
    @DisplayName("copyFile")
    inner class CopyFile {
        @Test
        @DisplayName("should copy file to destination")
        fun shouldCopyFile() = runTest {
            val src = File(tempDir, "source.jpg")
            src.writeText("image content")
            val dest = File(tempDir, "copy/dest.jpg")

            val result =
                adapter.copyFile(
                    ImageFile(path = FilePath(src.absolutePath), fileSize = src.length()),
                    FilePath(dest.absolutePath),
                )

            assertThat(result).isTrue()
            assertThat(dest.exists()).isTrue()
            assertThat(dest.readText()).isEqualTo("image content")
        }

        @Test
        @DisplayName("should report progress during copy")
        fun shouldReportProgress() = runTest {
            val src = File(tempDir, "progress.jpg")
            src.writeBytes(ByteArray(1024 * 100))
            val dest = File(tempDir, "progress_copy.jpg")
            val progressUpdates = mutableListOf<Pair<Long, Long>>()

            adapter.copyFile(
                ImageFile(path = FilePath(src.absolutePath), fileSize = src.length()),
                FilePath(dest.absolutePath),
            ) { copied, total ->
                progressUpdates.add(copied to total)
            }

            assertThat(progressUpdates).isNotEmpty()
            assertThat(progressUpdates.last().first).isEqualTo(progressUpdates.last().second)
        }
    }

    @Nested
    @DisplayName("verifyCopy")
    inner class VerifyCopy {
        @Test
        @DisplayName("should verify matching files")
        fun shouldVerifyMatchingFiles() = runTest {
            val src = File(tempDir, "original.jpg")
            src.writeText("same content")
            val dest = File(tempDir, "verified.jpg")
            dest.writeText("same content")

            val result =
                adapter.verifyCopy(
                    ImageFile(path = FilePath(src.absolutePath), fileSize = src.length()),
                    FilePath(dest.absolutePath),
                )

            assertThat(result).isTrue()
        }

        @Test
        @DisplayName("should detect mismatched files")
        fun shouldDetectMismatch() = runTest {
            val src = File(tempDir, "original.jpg")
            src.writeText("content A")
            val dest = File(tempDir, "corrupt.jpg")
            dest.writeText("content B")

            val result =
                adapter.verifyCopy(
                    ImageFile(path = FilePath(src.absolutePath), fileSize = src.length()),
                    FilePath(dest.absolutePath),
                )

            assertThat(result).isFalse()
        }
    }

    @Nested
    @DisplayName("deleteFile")
    inner class DeleteFile {
        @Test
        @DisplayName("should delete existing file")
        fun shouldDeleteFile() = runTest {
            val file = File(tempDir, "deleteme.jpg")
            file.writeText("data")

            val result =
                adapter.deleteFile(
                    ImageFile(path = FilePath(file.absolutePath), fileSize = file.length())
                )

            assertThat(result).isTrue()
            assertThat(file.exists()).isFalse()
        }
    }
}

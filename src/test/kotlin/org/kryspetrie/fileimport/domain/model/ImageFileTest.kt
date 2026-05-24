package org.kryspetrie.fileimport.domain.model

import java.io.File
import java.time.LocalDateTime
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("ImageFile")
class ImageFileTest {
    private lateinit var testFile: File

    @BeforeEach
    fun setup() {
        testFile = File("test.jpg")
    }

    @Nested
    @DisplayName("Creation")
    inner class Creation {
        @Test
        @DisplayName("should create with default values")
        fun shouldCreateWithDefaults() {
            // GIVEN
            val file = File("photo.jpg")

            // WHEN
            val imageFile = ImageFile(file = file)

            // THEN
            assertThat(imageFile.file).isEqualTo(file)
            assertThat(imageFile.fileName).isEqualTo("photo.jpg")
            assertThat(imageFile.isSelected).isFalse()
            assertThat(imageFile.importStatus).isEqualTo(ImportStatus.PENDING)
        }

        @Test
        @DisplayName("should create with all values")
        fun shouldCreateWithAllValues() {
            // GIVEN
            val metadata =
                ImageMetadata(
                    dateTimeOriginal = LocalDateTime.of(2024, 1, 15, 10, 30),
                    make = "Canon",
                    model = "EOS R5",
                )

            // WHEN
            val imageFile =
                ImageFile(file = testFile, hash = "abc123", metadata = metadata, isSelected = true)

            // THEN
            assertThat(imageFile.hash).isEqualTo("abc123")
            assertThat(imageFile.metadata?.make).isEqualTo("Canon")
            assertThat(imageFile.isSelected).isTrue()
        }
    }

    @Nested
    @DisplayName("Date properties")
    inner class DateProperties {
        @Test
        @DisplayName("should return date taken from metadata")
        fun shouldReturnDateTaken() {
            // GIVEN
            val dateTime = LocalDateTime.of(2024, 3, 20, 14, 45)
            val imageFile =
                ImageFile(file = testFile, metadata = ImageMetadata(dateTimeOriginal = dateTime))

            // WHEN
            val dateTaken = imageFile.dateTaken

            // THEN
            assertThat(dateTaken).isEqualTo(dateTime)
        }

        @Test
        @DisplayName("should return formatted date string")
        fun shouldReturnFormattedDate() {
            // GIVEN
            val imageFile =
                ImageFile(
                    file = testFile,
                    metadata = ImageMetadata(dateTimeOriginal = LocalDateTime.of(2024, 6, 15, 0, 0)),
                )

            // WHEN
            val formatted = imageFile.dateTakenFormatted

            // THEN
            assertThat(formatted).isEqualTo("2024-06-15")
        }

        @Test
        @DisplayName("should return Unknown when no metadata")
        fun shouldReturnUnknownWhenNoMetadata() {
            // GIVEN
            val imageFile = ImageFile(file = testFile)

            // WHEN
            val formatted = imageFile.dateTakenFormatted

            // THEN
            assertThat(formatted).isEqualTo("Unknown")
        }
    }
}

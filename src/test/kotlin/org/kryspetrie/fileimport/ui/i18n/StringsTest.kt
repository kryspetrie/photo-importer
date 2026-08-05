package org.kryspetrie.fileimport.ui.i18n

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.kryspetrie.fileimport.domain.model.RegionType
import org.kryspetrie.fileimport.domain.model.i18n.StringKey
import org.kryspetrie.fileimport.domain.port.DispatcherProvider
import org.kryspetrie.fileimport.infrastructure.i18n.JsonLocaleAdapter
import org.kryspetrie.fileimport.ui.wizard.state.FaceSize

@DisplayName("Strings")
class StringsTest {

    private lateinit var strings: Strings
    private val testDispatcher =
        object : DispatcherProvider {
            override val io = Dispatchers.Unconfined
            override val default = Dispatchers.Unconfined
        }

    @BeforeEach
    fun setup() = runTest {
        val adapter = JsonLocaleAdapter(dispatchers = testDispatcher, appLogger = null)
        adapter.setLocale("en")
        strings = Strings(adapter)
    }

    @Nested
    @DisplayName("regionTypeName")
    inner class RegionTypeNameTests {
        @Test
        @DisplayName("returns non-blank for all RegionType values")
        fun returnsNonBlankForAllRegionTypes() {
            RegionType.entries.forEach { type ->
                val name = strings.regionTypeName(type)
                assertThat(name)
                    .describedAs("regionTypeName for $type")
                    .isNotBlank()
            }
        }

        @Test
        @DisplayName("returns distinct values for each RegionType")
        fun returnsDistinctValues() {
            val names = RegionType.entries.map { strings.regionTypeName(it) }
            assertThat(names.toSet()).hasSize(RegionType.entries.size)
        }

        @Test
        @DisplayName("returns English label for FACE")
        fun returnsEnglishLabelForFace() {
            assertThat(strings.regionTypeName(RegionType.FACE)).isEqualTo("Face")
        }

        @Test
        @DisplayName("returns English label for PET")
        fun returnsEnglishLabelForPet() {
            assertThat(strings.regionTypeName(RegionType.PET)).isEqualTo("Pet")
        }

        @Test
        @DisplayName("returns English label for BODY")
        fun returnsEnglishLabelForBody() {
            assertThat(strings.regionTypeName(RegionType.BODY)).isEqualTo("Body")
        }

        @Test
        @DisplayName("returns English label for OBJECT")
        fun returnsEnglishLabelForObject() {
            assertThat(strings.regionTypeName(RegionType.OBJECT)).isEqualTo("Object")
        }
    }

    @Nested
    @DisplayName("faceSizeName")
    inner class FaceSizeNameTests {
        @Test
        @DisplayName("returns non-blank for all FaceSize values")
        fun returnsNonBlankForAllFaceSizes() {
            FaceSize.entries.forEach { size ->
                val name = strings.faceSizeName(size)
                assertThat(name)
                    .describedAs("faceSizeName for $size")
                    .isNotBlank()
            }
        }

        @Test
        @DisplayName("returns distinct values for each FaceSize")
        fun returnsDistinctValues() {
            val names = FaceSize.entries.map { strings.faceSizeName(it) }
            assertThat(names.toSet()).hasSize(FaceSize.entries.size)
        }

        @Test
        @DisplayName("returns English label for SMALL")
        fun returnsEnglishLabelForSmall() {
            assertThat(strings.faceSizeName(FaceSize.SMALL)).isEqualTo("S")
        }

        @Test
        @DisplayName("returns English label for MEDIUM")
        fun returnsEnglishLabelForMedium() {
            assertThat(strings.faceSizeName(FaceSize.MEDIUM)).isEqualTo("M")
        }

        @Test
        @DisplayName("returns English label for LARGE")
        fun returnsEnglishLabelForLarge() {
            assertThat(strings.faceSizeName(FaceSize.LARGE)).isEqualTo("L")
        }
    }

    @Nested
    @DisplayName("convenience accessors")
    inner class ConvenienceAccessorTests {
        @Test
        @DisplayName("all convenience accessors return non-blank")
        fun allConvenienceAccessorsReturnNonBlank() {
            assertThat(strings.appName).isNotBlank()
            assertThat(strings.ok).isNotBlank()
            assertThat(strings.cancel).isNotBlank()
            assertThat(strings.apply).isNotBlank()
            assertThat(strings.save).isNotBlank()
            assertThat(strings.delete).isNotBlank()
            assertThat(strings.reset).isNotBlank()
            assertThat(strings.close).isNotBlank()
            assertThat(strings.back).isNotBlank()
            assertThat(strings.next).isNotBlank()
            assertThat(strings.export).isNotBlank()
        }
    }
}

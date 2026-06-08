package org.kryspetrie.fileimport.domain.model

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("DuplicateInfo")
class DuplicateInfoTest {

    @Test
    @DisplayName("should store primary and duplicate images")
    fun shouldStorePrimaryAndDuplicates() {
        val primary = ImageFile(path = FilePath("primary.jpg"))
        val dup1 = ImageFile(path = FilePath("dup1.jpg"))
        val dup2 = ImageFile(path = FilePath("dup2.jpg"))

        val info =
            DuplicateInfo(
                primaryImage = primary,
                duplicateImages = listOf(dup1, dup2),
                duplicateType = DuplicateType.EXACT_HASH,
                hashMatch = true,
            )

        assertThat(info.primaryImage.fileName).isEqualTo("primary.jpg")
        assertThat(info.duplicateImages).hasSize(2)
        assertThat(info.hashMatch).isTrue()
    }

    @Test
    @DisplayName("DuplicateType should have all expected types")
    fun shouldHaveAllTypes() {
        assertThat(DuplicateType.entries)
            .contains(
                DuplicateType.EXACT_HASH,
                DuplicateType.PERCEPTUAL_HASH,
                DuplicateType.EXIF_MATCH,
                DuplicateType.FILENAME_SIMILAR,
                DuplicateType.CAMERA_PAIR,
                DuplicateType.SURF_MATCH,
            )
    }

    @Test
    @DisplayName("DuplicateResolution should have all options")
    fun shouldHaveAllResolutions() {
        assertThat(DuplicateResolution.entries)
            .contains(
                DuplicateResolution.KEEP_BOTH,
                DuplicateResolution.SKIP_DUPLICATE,
                DuplicateResolution.REPLACE_PRIMARY,
            )
    }
}

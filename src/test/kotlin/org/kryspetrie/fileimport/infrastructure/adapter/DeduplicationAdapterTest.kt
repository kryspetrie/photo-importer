package org.kryspetrie.fileimport.infrastructure.adapter

import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.kryspetrie.fileimport.application.TestDispatcherProvider
import org.kryspetrie.fileimport.domain.model.DeduplicationSettings
import org.kryspetrie.fileimport.domain.model.DuplicateType
import org.kryspetrie.fileimport.domain.model.FilePath
import org.kryspetrie.fileimport.domain.model.ImageFile

@DisplayName("DeduplicationAdapter")
class DeduplicationAdapterTest {
    private val adapter =
        DeduplicationAdapter(
            surfService = SurfDeduplicationService(TestDispatcherProvider()),
            dispatcherProvider = TestDispatcherProvider(),
        )

    @Test
    fun emptyHashesAreNotGroupedAsExactDuplicates() = runTest {
        val a = ImageFile(path = FilePath("/a.jpg"), hash = "")
        val b = ImageFile(path = FilePath("/b.jpg"), hash = "")

        val duplicates =
            adapter.findDuplicates(
                listOf(a, b),
                DeduplicationSettings(enableHashDeduplication = true),
            )

        assertThat(duplicates).noneMatch { it.duplicateType == DuplicateType.EXACT_HASH }
    }

    @Test
    fun getDuplicateTypeIgnoresEmptyHashes() = runTest {
        val a = ImageFile(path = FilePath("/a.jpg"), hash = "")
        val b = ImageFile(path = FilePath("/b.jpg"), hash = "")

        assertThat(
                adapter.getDuplicateType(
                    a,
                    b,
                    DeduplicationSettings(enableHashDeduplication = true),
                )
            )
            .isNull()
    }
}

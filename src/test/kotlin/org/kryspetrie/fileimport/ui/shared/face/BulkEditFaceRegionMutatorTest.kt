package org.kryspetrie.fileimport.ui.shared.face

import java.io.File
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.kryspetrie.fileimport.domain.model.FaceRegion
import org.kryspetrie.fileimport.domain.model.RegionType
import org.kryspetrie.fileimport.ui.screens.metadataeditor.BulkEditState
import org.kryspetrie.fileimport.ui.wizard.state.FaceSize

@DisplayName("BulkEditFaceRegionMutator")
class BulkEditFaceRegionMutatorTest {

    private lateinit var state: BulkEditState
    private lateinit var mutator: BulkEditFaceRegionMutator
    private var lastUpdatedIndex: Int? = null

    @BeforeEach
    fun setUp() {
        state = BulkEditState()
        state.loadFiles(listOf(File("/tmp/a.jpg"), File("/tmp/b.jpg")))
        mutator = BulkEditFaceRegionMutator(state) { index, _ -> lastUpdatedIndex = index }
    }

    @Test
    fun addFaceRegionMergesSubjects() {
        // WHEN
        mutator.addFaceRegion(0, "Alice", 0.5, 0.5, RegionType.FACE, FaceSize.DEFAULT)

        // THEN
        val config = state.fileConfigs[state.files[0].absolutePath]!!.config
        assertThat(config.faceRegions).hasSize(1)
        assertThat(config.subjects).contains("Alice")
        assertThat(lastUpdatedIndex).isEqualTo(0)
    }

    @Test
    fun updateFaceRegionNameUpdatesSubjects() {
        // GIVEN
        mutator.addFaceRegion(0, "", 0.5, 0.5)

        // WHEN
        mutator.updateFaceRegionName(0, 0, "Bob")

        // THEN
        val config = state.fileConfigs[state.files[0].absolutePath]!!.config
        assertThat(config.faceRegions.first().name).isEqualTo("Bob")
        assertThat(config.subjects).contains("Bob")
    }

    @Test
    fun removeFaceRegionRemovesNameFromSubjects() {
        // GIVEN
        mutator.addFaceRegion(0, "Carol", 0.4, 0.4)
        mutator.addFaceRegion(0, "Dave", 0.6, 0.6)

        // WHEN
        mutator.removeFaceRegion(0, 0)

        // THEN
        val config = state.fileConfigs[state.files[0].absolutePath]!!.config
        assertThat(config.faceRegions).hasSize(1)
        assertThat(config.subjects).doesNotContain("Carol")
        assertThat(config.subjects).contains("Dave")
    }

    @Test
    fun addDetectedFaceRegionsAppendsWithoutReplacing() {
        // GIVEN
        val detected =
            listOf(
                FaceRegion(
                    name = "",
                    type = RegionType.FACE.mwgRsValue,
                    x = 0.2,
                    y = 0.2,
                    w = 0.1,
                    h = 0.1,
                )
            )

        // WHEN
        mutator.addDetectedFaceRegions(1, detected)

        // THEN
        val secondFileConfig = state.fileConfigs[state.files[1].absolutePath]!!.config
        assertThat(secondFileConfig.faceRegions).hasSize(1)
        assertThat(lastUpdatedIndex).isEqualTo(1)
    }
}

package org.kryspetrie.fileimport.ui.screens.wizard.edit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.awt.image.BufferedImage
import org.kryspetrie.fileimport.domain.model.MetadataHistory
import org.kryspetrie.fileimport.domain.model.OverrideUiSemantics
import org.kryspetrie.fileimport.domain.model.PhotoScanConfiguration
import org.kryspetrie.fileimport.domain.model.RecentMetadataSet
import org.kryspetrie.fileimport.domain.model.geometry.BoundingBoxList
import org.kryspetrie.fileimport.domain.model.i18n.StringKey
import org.kryspetrie.fileimport.domain.port.PerspectiveCorrectionPort
import org.kryspetrie.fileimport.ui.components.ChunkyScrollbar
import org.kryspetrie.fileimport.ui.i18n.strings
import org.kryspetrie.fileimport.ui.screens.shared.MetadataEditorPanelHeader
import org.kryspetrie.fileimport.ui.screens.shared.metadata.MetadataEditState
import org.kryspetrie.fileimport.ui.screens.shared.metadata.RecentValuesDropdown
import org.kryspetrie.fileimport.ui.wizard.state.PhotoScanWizardState
import org.kryspetrie.fileimport.ui.wizard.state.SourceExifSummary

/**
 * Metadata editor panel — shown in Metadata mode. Contains all the metadata editing functionality.
 *
 * In multi-edit mode, uses [MetadataEditState] to buffer values until the user clicks "Apply". In
 * single-edit mode, [MetadataEditState] is synced from the selected photo's config and changes are
 * applied immediately via direct config updates.
 */
@Composable
internal fun WizardEditMetadataPanel(
    state: PhotoScanWizardState,
    image: BufferedImage,
    perspectiveService: PerspectiveCorrectionPort,
    boundingBoxList: BoundingBoxList,
    photoConfigurations: Map<String, PhotoScanConfiguration>,
    selectedIndices: Set<Int>,
    isMultiEditMode: Boolean,
    metadataHistory: MetadataHistory,
    onMetadataHistoryUpdate: (String, String) -> Unit,
    onMetadataHistoryRemove: (String, String) -> Unit,
    sourceExif: SourceExifSummary?,
    onSelectFaces: (Int) -> Unit,
    onPickLocation: (List<Int>) -> Unit,
    onRecordMetadataSet: (RecentMetadataSet) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val s = strings()
    val isMultiSelect = selectedIndices.size > 1 || isMultiEditMode

    // Single MetadataEditState for both multi-edit buffering and single-edit display
    val editState = remember { MetadataEditState() }

    // ── Single-edit mode: resolve selected photo ──
    val selectedIndex =
        if (!isMultiSelect && selectedIndices.isNotEmpty()) {
            selectedIndices.first()
        } else -1

    val singleEditBoxId: String? =
        if (!isMultiSelect && selectedIndex >= 0 && selectedIndex < boundingBoxList.size()) {
            boundingBoxList.boxes[selectedIndex].id
        } else null

    val singleEditConfig: PhotoScanConfiguration? =
        if (singleEditBoxId != null) {
            photoConfigurations[singleEditBoxId] ?: PhotoScanConfiguration()
        } else null

    // ── Sync editState from config in single-edit mode ──
    // Re-sync when selection changes OR when config fields are updated externally
    // (e.g. location picker updates city/state/country/GPS directly in the config).
    LaunchedEffect(
        singleEditBoxId,
        singleEditConfig?.locationName,
        singleEditConfig?.address,
        singleEditConfig?.city,
        singleEditConfig?.state,
        singleEditConfig?.country,
        singleEditConfig?.gpsLatitude,
        singleEditConfig?.gpsLongitude,
    ) {
        if (!isMultiSelect && singleEditConfig != null) {
            editState.loadFrom(singleEditConfig)
        }
    }

    // ── Clear editState when switching to multi-edit mode ──
    LaunchedEffect(isMultiSelect) {
        if (isMultiSelect) {
            editState.clear()
        }
    }

    ChunkyScrollbar(modifier = modifier) {
        Column(
            modifier = Modifier.padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // ── Header ──
            if (isMultiSelect) {
                MetadataEditorPanelHeader(
                    title =
                        s.t(StringKey.META_PHOTOS_SELECTED, "count" to "${selectedIndices.size}"),
                    onClear = { editState.clear() },
                    onApply = {
                        state.configs.applyMetadataToSelected(editState)
                        onRecordMetadataSet(editState.toRecentMetadataSet())
                    },
                    hint = s.t(StringKey.META_MULTI_EDIT_HINT),
                )
            } else {
                MetadataEditorPanelHeader(
                    title = s.t(StringKey.ACC_THUMBNAIL, "index" to "${selectedIndex + 1}"),
                    onClear = { editState.clear() },
                )
            }

            // ── Recent Values (multi-edit only — each field has its own suggestions in
            // single-edit) ──
            if (isMultiSelect && metadataHistory.recentSets.isNotEmpty()) {
                RecentValuesDropdown(
                    recentSets = metadataHistory.recentSets,
                    onApplySet = { set ->
                        editState.loadFromSet(set)
                        onRecordMetadataSet(set)
                    },
                )
            }

            // ── Metadata sections (shared between modes) ──
            // All field values come from editState; in single-edit mode, field changes are also
            // immediately pushed to the photo config via
            // state.configs.updatePhotoScanConfiguration().

            QuickEditMetadataFields(
                description = editState.description,
                onDescriptionChange = { newValue ->
                    editState.description = newValue
                    singleEditBoxId?.let { id ->
                        state.configs.updatePhotoScanConfiguration(id) {
                            it.copy(description = newValue)
                        }
                    }
                },
                keywords = editState.keywords,
                onKeywordsChange = { newValue ->
                    editState.keywords = newValue
                    singleEditBoxId?.let { id ->
                        state.configs.updatePhotoScanConfiguration(id) {
                            it.copy(keywords = newValue)
                        }
                    }
                },
                originalDate = editState.originalDate,
                onOriginalDateChange = { newValue ->
                    editState.originalDate = newValue
                    singleEditBoxId?.let { id ->
                        state.configs.updatePhotoScanConfiguration(id) {
                            it.copy(originalDate = newValue)
                        }
                    }
                },
                year = editState.year,
                onYearChange = { newValue ->
                    val filtered = newValue.filter { c -> c.isDigit() }.take(4)
                    editState.year = filtered
                    singleEditBoxId?.let { id ->
                        state.configs.updatePhotoScanConfiguration(id) { it.copy(year = filtered) }
                    }
                },
                metadataHistory = metadataHistory,
                onMetadataHistoryUpdate = onMetadataHistoryUpdate,
                onMetadataHistoryRemove = onMetadataHistoryRemove,
                onCommitKeyword =
                    if (!isMultiSelect) {
                        { keyword -> onMetadataHistoryUpdate("keywords", keyword) }
                    } else null,
                boxId = singleEditBoxId,
                state = if (!isMultiSelect) state else null,
                // Override checkboxes (single-edit only)
                overrideDescription =
                    if (!isMultiSelect && singleEditConfig != null) {
                        OverrideUiSemantics.isIncluded(singleEditConfig.overrideDescription)
                    } else null,
                onOverrideDescriptionChange =
                    if (!isMultiSelect) {
                        { included: Boolean ->
                            singleEditBoxId?.let { id ->
                                state.configs.updatePhotoScanConfiguration(id) {
                                    it.copy(
                                        overrideDescription =
                                            OverrideUiSemantics.fromIncluded(included)
                                    )
                                }
                            }
                        }
                    } else null,
                overrideKeywords =
                    if (!isMultiSelect && singleEditConfig != null) {
                        OverrideUiSemantics.isIncluded(singleEditConfig.overrideKeywords)
                    } else null,
                onOverrideKeywordsChange =
                    if (!isMultiSelect) {
                        { included: Boolean ->
                            singleEditBoxId?.let { id ->
                                state.configs.updatePhotoScanConfiguration(id) {
                                    it.copy(
                                        overrideKeywords =
                                            OverrideUiSemantics.fromIncluded(included)
                                    )
                                }
                            }
                        }
                    } else null,
                overrideOriginalDate =
                    if (!isMultiSelect && singleEditConfig != null) {
                        OverrideUiSemantics.isIncluded(singleEditConfig.overrideOriginalDate)
                    } else null,
                onOverrideOriginalDateChange =
                    if (!isMultiSelect) {
                        { included: Boolean ->
                            singleEditBoxId?.let { id ->
                                state.configs.updatePhotoScanConfiguration(id) {
                                    it.copy(
                                        overrideOriginalDate =
                                            OverrideUiSemantics.fromIncluded(included)
                                    )
                                }
                            }
                        }
                    } else null,
                overrideYear =
                    if (!isMultiSelect && singleEditConfig != null) {
                        OverrideUiSemantics.isIncluded(singleEditConfig.overrideYear)
                    } else null,
                onOverrideYearChange =
                    if (!isMultiSelect) {
                        { included: Boolean ->
                            singleEditBoxId?.let { id ->
                                state.configs.updatePhotoScanConfiguration(id) {
                                    it.copy(
                                        overrideYear = OverrideUiSemantics.fromIncluded(included)
                                    )
                                }
                            }
                        }
                    } else null,
                sourceExif = sourceExif,
            )

            LocationSection(
                locationName = editState.locationName,
                onLocationNameChange = { newValue ->
                    editState.locationName = newValue
                    singleEditBoxId?.let { id ->
                        state.configs.updatePhotoScanConfiguration(id) {
                            it.copy(locationName = newValue)
                        }
                    }
                },
                address = editState.address,
                onAddressChange = { newValue ->
                    editState.address = newValue
                    singleEditBoxId?.let { id ->
                        state.configs.updatePhotoScanConfiguration(id) {
                            it.copy(address = newValue)
                        }
                    }
                },
                city = editState.city,
                onCityChange = { newValue ->
                    editState.city = newValue
                    singleEditBoxId?.let { id ->
                        state.configs.updatePhotoScanConfiguration(id) { it.copy(city = newValue) }
                    }
                },
                stateVal = editState.state,
                onStateChange = { newValue ->
                    editState.state = newValue
                    singleEditBoxId?.let { id ->
                        state.configs.updatePhotoScanConfiguration(id) { it.copy(state = newValue) }
                    }
                },
                country = editState.country,
                onCountryChange = { newValue ->
                    editState.country = newValue
                    singleEditBoxId?.let { id ->
                        state.configs.updatePhotoScanConfiguration(id) {
                            it.copy(country = newValue)
                        }
                    }
                },
                gpsLatitude = editState.gpsLatitude,
                onGpsLatitudeChange = { newValue ->
                    editState.gpsLatitude = newValue
                    singleEditBoxId?.let { id ->
                        state.configs.updatePhotoScanConfiguration(id) {
                            it.copy(gpsLatitude = newValue)
                        }
                    }
                },
                gpsLongitude = editState.gpsLongitude,
                onGpsLongitudeChange = { newValue ->
                    editState.gpsLongitude = newValue
                    singleEditBoxId?.let { id ->
                        state.configs.updatePhotoScanConfiguration(id) {
                            it.copy(gpsLongitude = newValue)
                        }
                    }
                },
                metadataHistory = metadataHistory,
                onMetadataHistoryUpdate = onMetadataHistoryUpdate,
                // Location recent values: multi-edit uses editState, single-edit pushes to config
                onApplyRecentLocation =
                    if (!isMultiSelect) {
                        { set ->
                            singleEditBoxId?.let { id ->
                                state.configs.updatePhotoScanConfiguration(id) {
                                    set.mergeLocationInto(it)
                                }
                            }
                            editState.loadFromSet(set)
                        }
                    } else {
                        { set -> editState.loadFromSet(set) }
                    },
                // Location picker (single and multi-edit)
                onPickLocation = { onPickLocation(selectedIndices.toList()) },
                // GPS override (single-edit only)
                overrideGps =
                    if (!isMultiSelect && singleEditConfig != null) {
                        OverrideUiSemantics.isIncluded(singleEditConfig.overrideGps)
                    } else null,
                onOverrideGpsChange =
                    if (!isMultiSelect) {
                        { included: Boolean ->
                            singleEditBoxId?.let { id ->
                                state.configs.updatePhotoScanConfiguration(id) {
                                    it.copy(
                                        overrideGps = OverrideUiSemantics.fromIncluded(included)
                                    )
                                }
                            }
                        }
                    } else null,
                sourceGpsHint =
                    sourceExif?.let {
                        val parts = mutableListOf<String>()
                        it.gpsLatitude?.let { lat ->
                            parts.add(s.t(StringKey.FIELD_SOURCE_LAT, "value" to lat))
                        }
                        it.gpsLongitude?.let { lon ->
                            parts.add(s.t(StringKey.FIELD_SOURCE_LON, "value" to lon))
                        }
                        if (parts.isNotEmpty()) {
                            s.t(StringKey.FIELD_SOURCE_GPS, "value" to parts.joinToString(", "))
                        } else null
                    },
            )

            SubjectsSection(
                subjects = editState.subjects,
                onSubjectsChange = { newValue ->
                    editState.subjects = newValue
                    singleEditBoxId?.let { id ->
                        state.configs.updatePhotoScanConfiguration(id) {
                            it.copy(subjects = newValue)
                        }
                    }
                },
                metadataHistory = metadataHistory,
                onMetadataHistoryUpdate = onMetadataHistoryUpdate,
                onMetadataHistoryRemove = onMetadataHistoryRemove,
                // Face selection (single-edit only)
                onSelectFaces =
                    if (!isMultiSelect && selectedIndex >= 0) {
                        { onSelectFaces(selectedIndex) }
                    } else null,
                faceRegions =
                    if (!isMultiSelect && singleEditConfig != null) {
                        singleEditConfig.faceRegions
                    } else emptyList(),
                onRemoveFace =
                    if (!isMultiSelect && selectedIndex >= 0) {
                        { faceIdx -> state.faceRegions.removeFaceRegion(selectedIndex, faceIdx) }
                    } else null,
                onClearAllFaces =
                    if (!isMultiSelect && selectedIndex >= 0) {
                        { state.faceRegions.clearAllFaceRegions(selectedIndex) }
                    } else null,
            )

            CameraSection(
                cameraMake = editState.cameraMake,
                onCameraMakeChange = { newValue ->
                    editState.cameraMake = newValue
                    singleEditBoxId?.let { id ->
                        state.configs.updatePhotoScanConfiguration(id) {
                            it.copy(cameraMake = newValue)
                        }
                    }
                },
                cameraModel = editState.cameraModel,
                onCameraModelChange = { newValue ->
                    editState.cameraModel = newValue
                    singleEditBoxId?.let { id ->
                        state.configs.updatePhotoScanConfiguration(id) {
                            it.copy(cameraModel = newValue)
                        }
                    }
                },
                lensModel = editState.lensModel,
                onLensModelChange = { newValue ->
                    editState.lensModel = newValue
                    singleEditBoxId?.let { id ->
                        state.configs.updatePhotoScanConfiguration(id) {
                            it.copy(lensModel = newValue)
                        }
                    }
                },
                focalLength = editState.focalLength,
                onFocalLengthChange = { newValue ->
                    editState.focalLength = newValue
                    singleEditBoxId?.let { id ->
                        state.configs.updatePhotoScanConfiguration(id) {
                            it.copy(focalLength = newValue)
                        }
                    }
                },
                aperture = editState.aperture,
                onApertureChange = { newValue ->
                    editState.aperture = newValue
                    singleEditBoxId?.let { id ->
                        state.configs.updatePhotoScanConfiguration(id) {
                            it.copy(aperture = newValue)
                        }
                    }
                },
                shutterSpeed = editState.shutterSpeed,
                onShutterSpeedChange = { newValue ->
                    editState.shutterSpeed = newValue
                    singleEditBoxId?.let { id ->
                        state.configs.updatePhotoScanConfiguration(id) {
                            it.copy(shutterSpeed = newValue)
                        }
                    }
                },
                iso = editState.iso,
                onIsoChange = { newValue ->
                    editState.iso = newValue
                    singleEditBoxId?.let { id ->
                        state.configs.updatePhotoScanConfiguration(id) { it.copy(iso = newValue) }
                    }
                },
                metadataHistory = metadataHistory,
                onMetadataHistoryUpdate = onMetadataHistoryUpdate,
                // Override checkboxes (single-edit only)
                overrideCameraMake =
                    if (!isMultiSelect && singleEditConfig != null) {
                        OverrideUiSemantics.isIncluded(singleEditConfig.overrideCameraMake)
                    } else null,
                onOverrideCameraMakeChange =
                    if (!isMultiSelect) {
                        { included: Boolean ->
                            singleEditBoxId?.let { id ->
                                state.configs.updatePhotoScanConfiguration(id) {
                                    it.copy(
                                        overrideCameraMake =
                                            OverrideUiSemantics.fromIncluded(included)
                                    )
                                }
                            }
                        }
                    } else null,
                overrideCameraModel =
                    if (!isMultiSelect && singleEditConfig != null) {
                        OverrideUiSemantics.isIncluded(singleEditConfig.overrideCameraModel)
                    } else null,
                onOverrideCameraModelChange =
                    if (!isMultiSelect) {
                        { included: Boolean ->
                            singleEditBoxId?.let { id ->
                                state.configs.updatePhotoScanConfiguration(id) {
                                    it.copy(
                                        overrideCameraModel =
                                            OverrideUiSemantics.fromIncluded(included)
                                    )
                                }
                            }
                        }
                    } else null,
                overrideLensModel =
                    if (!isMultiSelect && singleEditConfig != null) {
                        OverrideUiSemantics.isIncluded(singleEditConfig.overrideLensModel)
                    } else null,
                onOverrideLensModelChange =
                    if (!isMultiSelect) {
                        { included: Boolean ->
                            singleEditBoxId?.let { id ->
                                state.configs.updatePhotoScanConfiguration(id) {
                                    it.copy(
                                        overrideLensModel =
                                            OverrideUiSemantics.fromIncluded(included)
                                    )
                                }
                            }
                        }
                    } else null,
                overrideFocalLength =
                    if (!isMultiSelect && singleEditConfig != null) {
                        OverrideUiSemantics.isIncluded(singleEditConfig.overrideFocalLength)
                    } else null,
                onOverrideFocalLengthChange =
                    if (!isMultiSelect) {
                        { included: Boolean ->
                            singleEditBoxId?.let { id ->
                                state.configs.updatePhotoScanConfiguration(id) {
                                    it.copy(
                                        overrideFocalLength =
                                            OverrideUiSemantics.fromIncluded(included)
                                    )
                                }
                            }
                        }
                    } else null,
                overrideAperture =
                    if (!isMultiSelect && singleEditConfig != null) {
                        OverrideUiSemantics.isIncluded(singleEditConfig.overrideAperture)
                    } else null,
                onOverrideApertureChange =
                    if (!isMultiSelect) {
                        { included: Boolean ->
                            singleEditBoxId?.let { id ->
                                state.configs.updatePhotoScanConfiguration(id) {
                                    it.copy(
                                        overrideAperture =
                                            OverrideUiSemantics.fromIncluded(included)
                                    )
                                }
                            }
                        }
                    } else null,
                overrideShutterSpeed =
                    if (!isMultiSelect && singleEditConfig != null) {
                        OverrideUiSemantics.isIncluded(singleEditConfig.overrideShutterSpeed)
                    } else null,
                onOverrideShutterSpeedChange =
                    if (!isMultiSelect) {
                        { included: Boolean ->
                            singleEditBoxId?.let { id ->
                                state.configs.updatePhotoScanConfiguration(id) {
                                    it.copy(
                                        overrideShutterSpeed =
                                            OverrideUiSemantics.fromIncluded(included)
                                    )
                                }
                            }
                        }
                    } else null,
                overrideIso =
                    if (!isMultiSelect && singleEditConfig != null) {
                        OverrideUiSemantics.isIncluded(singleEditConfig.overrideIso)
                    } else null,
                onOverrideIsoChange =
                    if (!isMultiSelect) {
                        { included: Boolean ->
                            singleEditBoxId?.let { id ->
                                state.configs.updatePhotoScanConfiguration(id) {
                                    it.copy(
                                        overrideIso = OverrideUiSemantics.fromIncluded(included)
                                    )
                                }
                            }
                        }
                    } else null,
                // Scanner camera data is irrelevant — suppress source EXIF hints for camera fields
                sourceExif = null,
            )
        }
    }
}

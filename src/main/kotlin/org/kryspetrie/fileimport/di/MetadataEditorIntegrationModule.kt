package org.kryspetrie.fileimport.di

import com.petrielabs.metadataeditor.adapters.exiftool.ExifToolLocator
import com.petrielabs.metadataeditor.adapters.exiftool.ExifToolMetadataEngine
import com.petrielabs.metadataeditor.adapters.exiftool.ExifToolProcessRunner
import com.petrielabs.metadataeditor.adapters.exiftool.ImageWriteBackupAdapter
import com.petrielabs.metadataeditor.adapters.exiftool.PreferTrashBackupDisposal
import com.petrielabs.metadataeditor.application.MetadataApplicationService
import com.petrielabs.metadataeditor.ports.inbound.MetadataEditorPort
import com.petrielabs.metadataeditor.ports.outbound.BackupDisposalPort
import com.petrielabs.metadataeditor.ports.outbound.ImageBackupPort
import com.petrielabs.metadataeditor.ports.outbound.MetadataEnginePort
import java.io.File
import org.koin.dsl.module

/**
 * Wires the photo-metadata-editor library (ExifTool-backed read/write with backup + rollback) into
 * this application's Koin graph.
 */
val metadataEditorIntegrationModule = module {
    single {
        ExifToolLocator(
            extraResourceRoots =
                listOf(
                    File("appResources/macos"),
                    File("appResources/linux"),
                    File("appResources/windows"),
                )
        )
    }
    single { ExifToolProcessRunner(get()) }
    single<MetadataEnginePort> { ExifToolMetadataEngine(get()) }
    single<BackupDisposalPort> { PreferTrashBackupDisposal() }
    single<ImageBackupPort> { ImageWriteBackupAdapter(disposal = get()) }
    single<MetadataEditorPort> { MetadataApplicationService(get(), get()) }
}

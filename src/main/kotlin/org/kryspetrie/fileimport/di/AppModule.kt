package org.kryspetrie.fileimport.di

import org.koin.dsl.module
import org.kryspetrie.fileimport.application.DuplicateScannerService
import org.kryspetrie.fileimport.application.ImportService
import org.kryspetrie.fileimport.application.ReorganizeService
import org.kryspetrie.fileimport.application.WatchFolderService
import org.kryspetrie.fileimport.domain.port.*
import org.kryspetrie.fileimport.infrastructure.adapter.*

val appModule = module {
  single<ImageRepositoryPort> { ImageRepositoryAdapter() }
  single<SettingsPort> { SettingsAdapter() }
  single<NamingPort> { NamingAdapter() }
  single<DeduplicationPort> { DeduplicationAdapter(get()) }
  single<HashCachePort> { HashCacheAdapter() }
  single<DevicePort> { DeviceAdapter() }
  single { ImportHistoryAdapter() }
  single { ImportService(get(), get(), get(), devicePort = get(), hashCache = get()) }
  single { ReorganizeService(get(), get()) }
  single { DuplicateScannerService(get(), get(), get()) }
  single { WatchFolderService(get()) }
}

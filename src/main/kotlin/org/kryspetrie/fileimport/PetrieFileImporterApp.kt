package org.kryspetrie.fileimport

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.MenuBar
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application
import kotlinx.coroutines.runBlocking
import org.koin.core.context.startKoin
import org.kryspetrie.fileimport.di.appModule
import org.kryspetrie.fileimport.domain.model.AppTheme
import org.kryspetrie.fileimport.infrastructure.adapter.SettingsAdapter
import org.kryspetrie.fileimport.ui.PetrieFileImporterApp
import org.kryspetrie.fileimport.ui.createAppIcon

private const val APP_TITLE = "Petrie Image Importer"

fun main(args: Array<String>) {
  if (args.isNotEmpty() && args[0] == "--cli") {
    org.kryspetrie.fileimport.cli.main(args.drop(1).toTypedArray())
    return
  }

  startKoin { modules(appModule) }

  val settingsAdapter = SettingsAdapter()
  val settings = runBlocking { settingsAdapter.loadSettings() }
  val windowState =
      WindowState(width = settings.windowState.width.dp, height = settings.windowState.height.dp)
  val appIcon = BitmapPainter(createAppIcon(512).toComposeImageBitmap())

  application {
    val currentSettings = mutableStateOf(settings)
    val onSettingsChange = { newSettings: org.kryspetrie.fileimport.domain.model.AppSettings ->
      runBlocking { settingsAdapter.saveSettings(newSettings) }
      currentSettings.value = newSettings
    }

    Window(
        onCloseRequest = ::exitApplication,
        state = windowState,
        title = APP_TITLE,
        icon = appIcon) {
          MenuBar {
            Menu("File") { Item("Quit", onClick = ::exitApplication) }
            Menu("View") {
              Item(
                  "Light Theme",
                  onClick = {
                    onSettingsChange(currentSettings.value.copy(theme = AppTheme.LIGHT))
                  })
              Item(
                  "Dark Theme",
                  onClick = { onSettingsChange(currentSettings.value.copy(theme = AppTheme.DARK)) })
              Item(
                  "System Theme",
                  onClick = {
                    onSettingsChange(currentSettings.value.copy(theme = AppTheme.SYSTEM))
                  })
            }
            Menu("Help") { Item("About $APP_TITLE", onClick = {}) }
          }

          PetrieFileImporterApp(
              settings = currentSettings.value,
              onSettingsChange = onSettingsChange,
              windowState = windowState)
        }
  }
}

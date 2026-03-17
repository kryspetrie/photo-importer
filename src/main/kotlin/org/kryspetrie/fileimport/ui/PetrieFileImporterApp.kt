package org.kryspetrie.fileimport.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowState
import org.kryspetrie.fileimport.domain.model.AppSettings
import org.kryspetrie.fileimport.ui.screens.DuplicateScannerScreen
import org.kryspetrie.fileimport.ui.screens.ImportScreen
import org.kryspetrie.fileimport.ui.screens.ReorganizeScreen
import org.kryspetrie.fileimport.ui.theme.PetrieTheme

private enum class AppTab(val label: String, val icon: ImageVector) {
  IMPORT("Import", Icons.Default.Download),
  REORGANIZE("Reorganize", Icons.AutoMirrored.Filled.DriveFileMove),
  DUPLICATES("Library Duplicates", Icons.Default.ContentCopy)
}

@Composable
fun PetrieFileImporterApp(
    settings: AppSettings,
    onSettingsChange: (AppSettings) -> Unit,
    windowState: WindowState,
    modifier: Modifier = Modifier
) {
  PetrieTheme(settings.theme) {
    var currentTab by remember { mutableStateOf(AppTab.IMPORT) }

    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
      Column(modifier = Modifier.fillMaxSize()) {
        NavigationBar(tonalElevation = 1.dp) {
          AppTab.entries.forEach { tab ->
            NavigationBarItem(
                icon = {
                  Icon(tab.icon, contentDescription = tab.label, modifier = Modifier.size(20.dp))
                },
                label = { Text(tab.label, style = MaterialTheme.typography.labelSmall) },
                selected = currentTab == tab,
                onClick = { currentTab = tab })
          }
        }

        Box(modifier = Modifier.fillMaxSize().padding(12.dp)) {
          when (currentTab) {
            AppTab.IMPORT -> ImportScreen(settings = settings, onSettingsChange = onSettingsChange)
            AppTab.REORGANIZE ->
                ReorganizeScreen(settings = settings, onSettingsChange = onSettingsChange)
            AppTab.DUPLICATES ->
                DuplicateScannerScreen(settings = settings, onSettingsChange = onSettingsChange)
          }
        }
      }
    }
  }
}

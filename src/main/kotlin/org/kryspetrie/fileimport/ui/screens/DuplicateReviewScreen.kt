package org.kryspetrie.fileimport.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.kryspetrie.fileimport.domain.model.*

@Composable
fun DuplicateReviewScreen(
    duplicates: List<DuplicateInfo>,
    onResolution: (DuplicateInfo, DuplicateResolution) -> Unit,
    onContinue: () -> Unit,
    onBack: () -> Unit
) {
  Column(
      modifier = Modifier.fillMaxSize().padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Review Duplicates", style = MaterialTheme.typography.headlineSmall)

        if (duplicates.isEmpty()) {
          Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                  Icon(
                      Icons.Default.CheckCircle,
                      null,
                      Modifier.size(48.dp),
                      tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f))
                  Text("No duplicates found!", style = MaterialTheme.typography.bodyLarge)
                }
          }
        } else {
          Text(
              "${duplicates.size} duplicate groups found",
              style = MaterialTheme.typography.bodyMedium)
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
          OutlinedButton(onClick = onBack) { Text("Back") }
          Button(onClick = onContinue) { Text("Continue") }
        }
      }
}

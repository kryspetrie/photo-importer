package org.kryspetrie.fileimport.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.kryspetrie.fileimport.domain.model.DuplicateInfo
import org.kryspetrie.fileimport.domain.model.DuplicateResolution
import org.kryspetrie.fileimport.domain.model.i18n.StringKey
import org.kryspetrie.fileimport.ui.i18n.strings

@Composable
fun DuplicateReviewScreen(
    duplicates: List<DuplicateInfo>,
    onResolution: (DuplicateInfo, DuplicateResolution) -> Unit,
    onContinue: () -> Unit,
    onBack: () -> Unit,
) {
    val s = strings()

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(s.t(StringKey.DUP_REVIEW_TITLE), style = MaterialTheme.typography.headlineSmall)

        if (duplicates.isEmpty()) {
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        Icons.Default.CheckCircle,
                        null,
                        Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                    )
                    Text(s.t(StringKey.DUP_REVIEW_NONE), style = MaterialTheme.typography.bodyLarge)
                }
            }
        } else {
            Text(
                s.t(StringKey.DUP_REVIEW_GROUPS, "count" to duplicates.size.toString()),
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            OutlinedButton(onClick = onBack) { Text(s.t(StringKey.ACTION_BACK)) }
            Button(onClick = onContinue) { Text(s.t(StringKey.DUP_CONTINUE)) }
        }
    }
}

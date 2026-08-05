package org.kryspetrie.fileimport.ui.screens.wizard.photoscan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import org.kryspetrie.fileimport.domain.model.i18n.StringKey
import org.kryspetrie.fileimport.ui.i18n.strings
import org.kryspetrie.fileimport.ui.theme.LocalUiDensityScale

/** Hero copy at the top of the Photo Scan import landing screen. */
@Composable
fun PhotoScanLandingHero(modifier: Modifier = Modifier) {
    val s = strings()
    val density = LocalUiDensityScale.current

    OutlinedCard(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(density.spacingMd),
            verticalArrangement = Arrangement.spacedBy(density.spacingSm),
        ) {
            Text(
                s.t(StringKey.NAV_PHOTO_SCAN),
                style =
                    MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
            )
            Text(
                s.t(StringKey.WIZARD_LANDING_SUBTITLE),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                s.t(StringKey.WIZARD_LANDING_STEPS),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                s.t(StringKey.WIZARD_LANDING_ENTER_HINT),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

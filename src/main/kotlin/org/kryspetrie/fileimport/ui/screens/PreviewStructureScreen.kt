package org.kryspetrie.fileimport.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.koin.compose.koinInject
import org.kryspetrie.fileimport.domain.model.*
import org.kryspetrie.fileimport.domain.port.FileStructurePreview
import org.kryspetrie.fileimport.domain.port.NamingPort
import org.kryspetrie.fileimport.ui.components.ThumbnailImage
import org.kryspetrie.fileimport.ui.components.formatFileSize

private const val THUMB_PX = 60

@Composable
fun PreviewStructureScreen(
    images: List<ImageFile>,
    sourcePath: String,
    destinationPath: String,
    configuration: ImportConfiguration,
    onImport: () -> Unit,
    onBack: () -> Unit
) {
  val namingPort = koinInject<NamingPort>()
  val previews =
      remember(images, destinationPath, configuration) {
        namingPort.previewFileStructure(images, destinationPath, configuration)
      }

  val sourceFolders =
      remember(previews) { previews.map { it.sourceFile.file.parent ?: "" }.distinct().sorted() }
  val destFolders = remember(previews) { previews.map { it.folderPath }.distinct().sorted() }
  val totalSize = images.sumOf { it.fileSize }
  val conflictCount = previews.count { it.wouldConflict }

  Column(
      modifier = Modifier.fillMaxSize().padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Review Import Plan", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Dry run — no files have been copied. Review the planned changes below.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)

        // Summary card
        OutlinedCard(modifier = Modifier.fillMaxWidth()) {
          Column(
              modifier = Modifier.padding(12.dp),
              verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly) {
                      StatColumn("${previews.size}", "Files")
                      StatColumn("${destFolders.size}", "Folders")
                      StatColumn(formatFileSize(totalSize), "Total size")
                    }
                if (conflictCount > 0) {
                  Row(
                      verticalAlignment = Alignment.CenterVertically,
                      horizontalArrangement = Arrangement.spacedBy(4.dp),
                      modifier = Modifier.padding(top = 4.dp)) {
                        Icon(
                            Icons.Default.Warning,
                            null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.error)
                        Text(
                            "$conflictCount file(s) would conflict with existing files",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error)
                      }
                }
              }
        }

        // Folder structure
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
              // Source folders
              OutlinedCard(modifier = Modifier.weight(1f)) {
                Column(
                    modifier = Modifier.padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)) {
                      Text(
                          "Source",
                          style = MaterialTheme.typography.labelMedium,
                          color = MaterialTheme.colorScheme.onSurfaceVariant)
                      Text(
                          sourcePath,
                          style = MaterialTheme.typography.bodySmall,
                          maxLines = 1,
                          overflow = TextOverflow.Ellipsis)
                      HorizontalDivider(
                          color = MaterialTheme.colorScheme.outlineVariant,
                          modifier = Modifier.padding(vertical = 2.dp))
                      sourceFolders.forEach { folder ->
                        val relative =
                            folder.removePrefix(sourcePath).removePrefix("/").ifEmpty { "." }
                        FolderRow(relative)
                      }
                    }
              }

              // Destination folders
              OutlinedCard(modifier = Modifier.weight(1f)) {
                Column(
                    modifier = Modifier.padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)) {
                      Text(
                          "Destination",
                          style = MaterialTheme.typography.labelMedium,
                          color = MaterialTheme.colorScheme.onSurfaceVariant)
                      Text(
                          destinationPath,
                          style = MaterialTheme.typography.bodySmall,
                          maxLines = 1,
                          overflow = TextOverflow.Ellipsis)
                      HorizontalDivider(
                          color = MaterialTheme.colorScheme.outlineVariant,
                          modifier = Modifier.padding(vertical = 2.dp))
                      destFolders.forEach { folder ->
                        val relative =
                            folder.removePrefix(destinationPath).removePrefix("/").ifEmpty { "." }
                        FolderRow(relative)
                      }
                    }
              }
            }

        // Column headers
        Row(
            modifier =
                Modifier.fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically) {
              Spacer(Modifier.width(44.dp))
              Text(
                  "Source",
                  style = MaterialTheme.typography.labelMedium,
                  modifier = Modifier.weight(1f))
              Spacer(Modifier.width(24.dp))
              Text(
                  "Destination",
                  style = MaterialTheme.typography.labelMedium,
                  modifier = Modifier.weight(1f))
            }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        // File mapping list
        LazyColumn(modifier = Modifier.weight(1f)) {
          items(previews) { preview ->
            FilePreviewRow(
                preview = preview, sourcePath = sourcePath, destinationPath = destinationPath)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
          }
        }

        // Footer
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
          OutlinedButton(onClick = onBack) { Text("Back") }
          Button(onClick = onImport) { Text("Proceed — Copy ${previews.size} Files") }
        }
      }
}

@Composable
private fun StatColumn(value: String, label: String) {
  Column(horizontalAlignment = Alignment.CenterHorizontally) {
    Text(value, style = MaterialTheme.typography.titleMedium)
    Text(
        label,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
  }
}

@Composable
private fun FolderRow(relativePath: String) {
  Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(4.dp),
      modifier = Modifier.padding(start = 4.dp)) {
        Icon(
            Icons.Default.Folder,
            null,
            modifier = Modifier.size(14.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
        Text(
            relativePath,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis)
      }
}

@Composable
private fun FilePreviewRow(
    preview: FileStructurePreview,
    sourcePath: String,
    destinationPath: String
) {
  val sourceRelative = preview.sourceFile.filePath.removePrefix(sourcePath).removePrefix("/")
  val destRelative = preview.destinationPath.removePrefix(destinationPath).removePrefix("/")

  Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
      verticalAlignment = Alignment.CenterVertically) {
        ThumbnailImage(
            file = preview.sourceFile.file,
            maxPx = THUMB_PX,
            modifier = Modifier.size(36.dp).clip(MaterialTheme.shapes.small),
            contentScale = ContentScale.Crop)
        Spacer(Modifier.width(8.dp))

        // Source path
        Column(modifier = Modifier.weight(1f)) {
          Text(
              preview.sourceFile.fileName,
              style = MaterialTheme.typography.bodySmall,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis)
          Text(
              sourceRelative,
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis)
        }

        Icon(
            Icons.AutoMirrored.Filled.ArrowForward,
            null,
            modifier = Modifier.padding(horizontal = 6.dp).size(14.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))

        // Destination path
        Column(modifier = Modifier.weight(1f)) {
          Text(
              preview.fileName,
              style = MaterialTheme.typography.bodySmall,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis)
          Row(verticalAlignment = Alignment.CenterVertically) {
            if (preview.wouldConflict) {
              Icon(
                  Icons.Default.Warning,
                  null,
                  modifier = Modifier.size(12.dp).padding(end = 2.dp),
                  tint = MaterialTheme.colorScheme.error)
            }
            Text(
                destRelative,
                style = MaterialTheme.typography.labelSmall,
                color =
                    if (preview.wouldConflict) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis)
          }
        }
      }
}

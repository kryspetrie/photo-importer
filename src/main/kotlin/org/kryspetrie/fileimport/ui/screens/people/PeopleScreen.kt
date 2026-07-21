package org.kryspetrie.fileimport.ui.screens.people

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.automirrored.filled.MergeType
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.kryspetrie.fileimport.application.PersonService
import org.kryspetrie.fileimport.domain.model.FaceMatchingConfig
import org.kryspetrie.fileimport.domain.model.Person
import org.kryspetrie.fileimport.domain.model.PersonDirectory
import org.kryspetrie.fileimport.domain.port.PersonDirectoryImportException
import org.kryspetrie.fileimport.infrastructure.adapter.Platform
import androidx.compose.ui.graphics.toComposeImageBitmap
import org.kryspetrie.fileimport.ui.components.SettingsToggle
import java.awt.FileDialog
import java.awt.Frame
import java.io.File as JavaFile

/**
 * People screen — find images by face name or face similarity.
 *
 * Provides:
 * - Search by person name or face similarity
 * - Person directory grid with face crops and photo counts
 * - Person detail view with rename, delete, merge, open folder
 * - Export/import database as zip bundle (with file chooser dialogs)
 * - Auto-detect and auto-identify settings toggles
 * - Matching threshold configuration
 * - Privacy controls (clear embeddings, embedding counts)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PeopleScreen(
    personService: PersonService = koinInject(),
) {
    val scope = rememberCoroutineScope()
    val directory by personService.directory.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    var showAddPersonDialog by remember { mutableStateOf(false) }
    var showExportImportDialog by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var selectedPerson by remember { mutableStateOf<Person?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    // Validate and clean stale source paths on first load
    LaunchedEffect(Unit) {
        try {
            val removed = personService.validateAndCleanSourcePaths()
            if (removed > 0) {
                snackbarHostState.showSnackbar("Removed $removed stale photo reference(s)")
            }
        } catch (_: Exception) {
            // Silent failure — stale paths will be cleaned on next load
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // ── Top App Bar ──────────────────────────────────────────────
        TopAppBar(
            title = { Text("People") },
            actions = {
                IconButton(onClick = { showSettingsDialog = true }) {
                    Icon(Icons.Default.Settings, contentDescription = "Settings")
                }
                IconButton(onClick = { showExportImportDialog = true }) {
                    Icon(Icons.Default.Download, contentDescription = "Export/Import Database")
                }
                IconButton(onClick = { showAddPersonDialog = true }) {
                    Icon(Icons.Default.Add, contentDescription = "Add Person")
                }
            },
        )

        // ── Snackbar for feedback ─────────────────────────────────────
        SnackbarHost(hostState = snackbarHostState)

        // ── Search Bar ──────────────────────────────────────────────
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            label = { Text("Search by name or file path") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(Icons.Default.Close, contentDescription = "Clear")
                    }
                }
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        )

        // ── Empty State ──────────────────────────────────────────────
        if (directory.persons.isEmpty()) {
            EmptyPeopleState(
                onAddPerson = { showAddPersonDialog = true },
                onImport = { showExportImportDialog = true },
            )
        } else {
            // ── Person Grid ──────────────────────────────────────────
            val filteredPersons = if (searchQuery.isBlank()) {
                directory.persons
            } else {
                personService.searchPersonsByKeyword(searchQuery, directory)
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(filteredPersons, key = { it.id }) { person ->
                    PersonCard(
                        person = person,
                        onClick = { selectedPerson = person },
                        onOpenFolder = {
                            scope.launch {
                                val result = personService.validateAndOpenFolder(
                                    person.sourcePaths.firstOrNull() ?: ""
                                )
                                if (result.isFailure) {
                                    snackbarHostState.showSnackbar(
                                        "Could not open folder: ${result.exceptionOrNull()?.message}"
                                    )
                                }
                            }
                        },
                    )
                }
            }
        }
    }

    // ── Dialogs ────────────────────────────────────────────────────

    if (showAddPersonDialog) {
        AddPersonDialog(
            existingNames = directory.persons.map { it.name },
            onDismiss = { showAddPersonDialog = false },
            onConfirm = { name ->
                scope.launch {
                    try {
                        personService.createPerson(name)
                    } catch (e: Exception) {
                        snackbarHostState.showSnackbar("Error: ${e.message}")
                    }
                }
                showAddPersonDialog = false
            },
        )
    }

    selectedPerson?.let { person ->
        // Use the latest version of the person from the directory (it may have been updated)
        val currentPerson = directory.personById(person.id) ?: person

        PersonDetailDialog(
            person = currentPerson,
            directory = directory,
            onDismiss = { selectedPerson = null },
            onRename = { newName ->
                scope.launch {
                    try {
                        personService.renamePerson(currentPerson.id, newName)
                    } catch (e: Exception) {
                        snackbarHostState.showSnackbar("Error: ${e.message}")
                    }
                }
            },
            onDelete = {
                scope.launch { personService.deletePerson(currentPerson.id) }
                selectedPerson = null
            },
            onMerge = { sourceId ->
                scope.launch {
                    personService.mergePersons(currentPerson.id, sourceId)
                    selectedPerson = null
                }
            },
            onOpenFolder = {
                scope.launch {
                    val path = currentPerson.sourcePaths.firstOrNull() ?: return@launch
                    val result = personService.validateAndOpenFolder(path)
                    if (result.isFailure) {
                        snackbarHostState.showSnackbar(
                            "Could not open folder: ${result.exceptionOrNull()?.message}"
                        )
                    }
                }
            },
            onRemoveSourcePath = { path ->
                scope.launch { personService.removeSourcePath(currentPerson.id, path) }
            },
            onClearEmbeddings = {
                scope.launch {
                    personService.clearEmbeddings(currentPerson.id)
                    snackbarHostState.showSnackbar("Embeddings cleared for ${currentPerson.name}")
                }
            },
            onRemoveEmbedding = { embeddingId ->
                scope.launch {
                    personService.removeEmbedding(currentPerson.id, embeddingId)
                    snackbarHostState.showSnackbar("Face reference removed")
                }
            },
        )
    }

    if (showExportImportDialog) {
        ExportImportDialog(
            onDismiss = { showExportImportDialog = false },
            onExport = { path ->
                scope.launch {
                    try {
                        val result = personService.exportDatabase(path)
                        snackbarHostState.showSnackbar("Exported to $result")
                    } catch (e: Exception) {
                        snackbarHostState.showSnackbar("Export failed: ${e.message}")
                    }
                }
            },
            onImport = { path ->
                scope.launch {
                    try {
                        personService.importDatabase(path)
                        snackbarHostState.showSnackbar("Import successful")
                    } catch (e: PersonDirectoryImportException) {
                        snackbarHostState.showSnackbar("Import failed: ${e.errors.joinToString()}")
                    } catch (e: Exception) {
                        snackbarHostState.showSnackbar("Import failed: ${e.message}")
                    }
                }
            },
        )
    }

    if (showSettingsDialog) {
        PeopleSettingsDialog(
            personService = personService,
            onDismiss = { showSettingsDialog = false },
        )
    }
}

/** Open a native file dialog for selecting a directory. Returns the selected path or null. */
private fun openFileDialog(title: String, isDirectory: Boolean): String? {
    return try {
        val dialog = FileDialog(Frame(), title, FileDialog.LOAD)
        if (isDirectory) {
            // FileDialog doesn't have a directory-only mode on all platforms,
            // but setting file filter helps
        }
        dialog.isVisible = true
        val file = dialog.file ?: return null
        val dir = dialog.directory ?: return null
        if (isDirectory) dir.removeSuffix("/") else "$dir$file"
    } catch (_: Exception) {
        null
    }
}

/** Open a native file dialog for selecting a save location. Returns the selected path or null. */
private fun saveFileDialog(title: String, defaultName: String): String? {
    return try {
        val dialog = FileDialog(Frame(), title, FileDialog.SAVE)
        dialog.file = defaultName
        dialog.isVisible = true
        val file = dialog.file ?: return null
        val dir = dialog.directory ?: return null
        "$dir$file"
    } catch (_: Exception) {
        null
    }
}

// ── Composable Helpers ──────────────────────────────────────────────

/** Empty state shown when the person directory has no entries. */
@Composable
private fun EmptyPeopleState(
    onAddPerson: () -> Unit,
    onImport: () -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Default.People,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                "No people in your directory yet",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Add people manually or import a database to get started.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(24.dp))
            OutlinedButton(onClick = onAddPerson) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Add Person")
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(onClick = onImport) {
                Icon(Icons.Default.Upload, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Import Database")
            }
        }
    }
}

/** Card displaying a person with thumbnail, name, photo count, and actions. */
@Composable
private fun PersonCard(
    person: Person,
    onClick: () -> Unit,
    onOpenFolder: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Person thumbnail — load from thumbnailPath if available, else show placeholder
            Box(
                modifier = Modifier.size(48.dp).clip(MaterialTheme.shapes.medium),
                contentAlignment = Alignment.Center,
            ) {
                val thumbPath = person.thumbnailPath
                val thumbBitmap = remember(thumbPath) {
                    if (thumbPath.isNotBlank()) {
                        try {
                            val file = java.io.File(thumbPath)
                            if (file.exists()) {
                                val source = javax.imageio.ImageIO.read(file)
                                if (source != null) {
                                    // Scale to thumbnail size
                                    val resized = java.awt.image.BufferedImage(
                                        48, 48, java.awt.image.BufferedImage.TYPE_INT_RGB,
                                    )
                                    val g2d = resized.createGraphics()
                                    g2d.setRenderingHint(
                                        java.awt.RenderingHints.KEY_INTERPOLATION,
                                        java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR,
                                    )
                                    g2d.drawImage(source, 0, 0, 48, 48, null)
                                    g2d.dispose()
                                    resized.toComposeImageBitmap()
                                } else null
                            } else null
                        } catch (_: Exception) { null }
                    } else null
                }

                if (thumbBitmap != null) {
                    Image(
                        bitmap = thumbBitmap,
                        contentDescription = person.name,
                        modifier = Modifier.size(48.dp),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    Icon(
                        Icons.Default.Person,
                        contentDescription = null,
                        modifier = Modifier.size(32.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Name and stats
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = person.name.ifBlank { "Unnamed" },
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = buildString {
                        append("${person.photoCount} photo${if (person.photoCount != 1) "s" else ""}")
                        append(" \u00b7 ${person.gallery.size} face reference${if (person.gallery.size != 1) "s" else ""}")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // Open folder button
            if (person.sourcePaths.isNotEmpty()) {
                IconButton(onClick = onOpenFolder) {
                    Icon(
                        Icons.Default.FolderOpen,
                        contentDescription = "Open containing folder",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** Dialog for adding a new person by name. Validates for blanks and duplicates. */
@Composable
private fun AddPersonDialog(
    existingNames: List<String>,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by remember { mutableStateOf("") }

    // Validate on each recomposition
    val trimmedName = name.trim()
    val validationError = Person.validateName(trimmedName)
    val isDuplicate = existingNames.any { it.equals(trimmedName, ignoreCase = true) }
    val nameError: String? = when {
        trimmedName.isBlank() -> null // don't show error while typing
        validationError != null -> validationError
        isDuplicate -> "A person with this name already exists"
        else -> null
    }
    val isValid = trimmedName.isNotBlank() && nameError == null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Person") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    isError = nameError != null,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (nameError != null) {
                    Text(
                        nameError,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (isValid) onConfirm(trimmedName) },
                enabled = isValid,
            ) { Text("Add") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

/** Dialog showing person details, source paths, and actions. */
@Composable
private fun PersonDetailDialog(
    person: Person,
    directory: PersonDirectory,
    onDismiss: () -> Unit,
    onRename: (String) -> Unit,
    onDelete: () -> Unit,
    onMerge: (String) -> Unit,
    onOpenFolder: () -> Unit,
    onRemoveSourcePath: (String) -> Unit,
    onClearEmbeddings: () -> Unit,
    onRemoveEmbedding: (String) -> Unit,
) {
    var showRenameDialog by remember { mutableStateOf(false) }
    var showMergeDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showClearEmbeddingsConfirm by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(person.name.ifBlank { "Unnamed Person" }) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Face gallery count (privacy: shows embedding count)
                Text(
                    "Gallery: ${person.gallery.size} face reference${if (person.gallery.size != 1) "s" else ""}",
                    style = MaterialTheme.typography.bodyMedium,
                )

                // Source photos count
                Text(
                    "Photos: ${person.photoCount}",
                    style = MaterialTheme.typography.bodyMedium,
                )

                // Source paths list
                if (person.sourcePaths.isNotEmpty()) {
                    Text("Photos containing this person:", style = MaterialTheme.typography.labelMedium)
                    LazyColumn(modifier = Modifier.height(120.dp)) {
                        items(person.sourcePaths.take(20)) { path ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(vertical = 2.dp),
                            ) {
                                Text(
                                    text = path.substringAfterLast("/"),
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                IconButton(
                                    onClick = {
                                        val file = JavaFile(path)
                                        val parent = if (file.isFile) file.parentFile else file
                                        if (parent != null && parent.exists()) {
                                            Platform.openWithSystemViewer(parent)
                                        }
                                    },
                                    modifier = Modifier.size(24.dp),
                                ) {
                                    Icon(
                                        Icons.Default.FolderOpen,
                                        contentDescription = "Open folder",
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
                            }
                        }
                    }
                    if (person.sourcePaths.size > 20) {
                        Text(
                            "...and ${person.sourcePaths.size - 20} more",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                // Face reference sources
                // Face reference sources with individual remove buttons
                if (person.gallery.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Face references (${person.gallery.size}):",
                        style = MaterialTheme.typography.labelMedium,
                    )
                    person.gallery.take(10).forEach { embedding ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(vertical = 1.dp),
                        ) {
                            Text(
                                "  \u00b7 ${embedding.sourcePath.substringAfterLast("/").ifBlank { "unknown" }}"
                                    + " (q=${String.format("%.0f%%", embedding.quality * 100)})",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f),
                            )
                            IconButton(
                                onClick = { onRemoveEmbedding(embedding.id) },
                                modifier = Modifier.size(20.dp),
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Remove face reference",
                                    modifier = Modifier.size(12.dp),
                                    tint = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                    }
                    if (person.gallery.size > 10) {
                        Text(
                            "...and ${person.gallery.size - 10} more",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = { showRenameDialog = true }) {
                    Icon(Icons.Default.Tag, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Rename")
                }
                if (person.sourcePaths.isNotEmpty()) {
                    TextButton(onClick = onOpenFolder) {
                        Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Open")
                    }
                }
                if (directory.persons.size >= 2) {
                    TextButton(onClick = { showMergeDialog = true }) {
                        Icon(Icons.AutoMirrored.Filled.MergeType, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Merge")
                    }
                }
                if (person.gallery.isNotEmpty()) {
                    TextButton(onClick = { showClearEmbeddingsConfirm = true }) {
                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Clear Faces")
                    }
                }
                TextButton(onClick = { showDeleteConfirm = true }) {
                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Delete")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
    )

    // Rename sub-dialog (with duplicate name check)
    if (showRenameDialog) {
        RenamePersonDialog(
            currentName = person.name,
            existingNames = directory.persons.map { it.name },
            excludeId = person.id,
            onDismiss = { showRenameDialog = false },
            onConfirm = { newName ->
                onRename(newName)
                showRenameDialog = false
            },
        )
    }

    // Merge sub-dialog
    if (showMergeDialog) {
        MergePersonDialog(
            currentPerson = person,
            allPersons = directory.persons,
            onDismiss = { showMergeDialog = false },
            onMerge = { sourceId ->
                onMerge(sourceId)
                showMergeDialog = false
            },
        )
    }

    // Delete confirmation
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Person?") },
            text = {
                Text(
                    "Remove \"${person.name}\" and all ${person.gallery.size} face references? " +
                        "This cannot be undone."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onDelete()
                    showDeleteConfirm = false
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            },
        )
    }

    // Clear embeddings confirmation (GDPR privacy control)
    if (showClearEmbeddingsConfirm) {
        AlertDialog(
            onDismissRequest = { showClearEmbeddingsConfirm = false },
            title = { Text("Clear Face Data?") },
            text = {
                Text(
                    "Remove all ${person.gallery.size} face embedding(s) for \"${person.name}\"? " +
                        "The person record and photo associations will be kept. " +
                        "This cannot be undone."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onClearEmbeddings()
                    showClearEmbeddingsConfirm = false
                }) { Text("Clear") }
            },
            dismissButton = {
                TextButton(onClick = { showClearEmbeddingsConfirm = false }) { Text("Cancel") }
            },
        )
    }
}

/** Dialog for renaming a person. Validates for blanks and duplicates. */
@Composable
private fun RenamePersonDialog(
    currentName: String,
    existingNames: List<String>,
    excludeId: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var newName by remember { mutableStateOf(currentName) }
    val trimmedName = newName.trim()
    val validationError = Person.validateName(trimmedName)
    val isDuplicate = existingNames.any { it.equals(trimmedName, ignoreCase = true) && it != currentName }
    val nameError: String? = when {
        validationError != null && trimmedName.isNotBlank() -> validationError
        isDuplicate -> "A person with this name already exists"
        else -> null
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename Person") },
        text = {
            Column {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("Name") },
                    singleLine = true,
                    isError = nameError != null,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (nameError != null) {
                    Text(
                        nameError,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (trimmedName.isNotBlank() && nameError == null) onConfirm(trimmedName) },
                enabled = trimmedName.isNotBlank() && nameError == null,
            ) { Text("Rename") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

/** Dialog for merging two persons. */
@Composable
private fun MergePersonDialog(
    currentPerson: Person,
    allPersons: List<Person>,
    onDismiss: () -> Unit,
    onMerge: (String) -> Unit,
) {
    val otherPersons = allPersons.filter { it.id != currentPerson.id }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Merge with Another Person") },
        text = {
            if (otherPersons.isEmpty()) {
                Text("No other persons to merge with.")
            } else {
                Column {
                    Text(
                        "Merge all face references and photos from another person into \"${currentPerson.name}\".",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyColumn(modifier = Modifier.height(200.dp)) {
                        items(otherPersons) { otherPerson ->
                            Card(
                                modifier = Modifier.fillMaxWidth().clickable { onMerge(otherPerson.id) },
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surface,
                                ),
                            ) {
                                Row(
                                    modifier = Modifier.padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(24.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(otherPerson.name.ifBlank { "Unnamed" }, style = MaterialTheme.typography.bodyMedium)
                                        Text(
                                            "${otherPerson.photoCount} photos \u00b7 ${otherPerson.gallery.size} references",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

/** Dialog for exporting/importing the person database. Uses native file chooser. */
@Composable
private fun ExportImportDialog(
    onDismiss: () -> Unit,
    onExport: (String) -> Unit,
    onImport: (String) -> Unit,
) {
    var exportPath by remember { mutableStateOf("") }
    var importPath by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Export / Import People Database") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                // Export section
                Text("Export", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Export your people database as a zip bundle containing all person data and face references. " +
                        "You can use this to back up your data or transfer it to another device.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = exportPath,
                        onValueChange = { exportPath = it },
                        label = { Text("Export directory path") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    OutlinedButton(
                        onClick = {
                            val selected = saveFileDialog("Export People Database", "people-database.zip")
                            if (selected != null) {
                                exportPath = selected
                            }
                        },
                    ) {
                        Icon(Icons.Default.FolderOpen, contentDescription = "Browse", modifier = Modifier.size(16.dp))
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    OutlinedButton(
                        onClick = {
                            onExport(exportPath.ifBlank {
                                Platform.appDataDir.absolutePath + "/people-export"
                            })
                        },
                        enabled = exportPath.isNotBlank(),
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Export")
                    }
                }

                // Import section
                Text("Import", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Import a people database from a zip bundle. This will replace all current person data.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = importPath,
                        onValueChange = { importPath = it },
                        label = { Text("Zip file path") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    OutlinedButton(
                        onClick = {
                            val selected = openFileDialog("Import People Database", isDirectory = false)
                            if (selected != null) {
                                importPath = selected
                            }
                        },
                    ) {
                        Icon(Icons.Default.FolderOpen, contentDescription = "Browse", modifier = Modifier.size(16.dp))
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    OutlinedButton(
                        onClick = {
                            if (importPath.isNotBlank()) {
                                onImport(importPath)
                            }
                        },
                        enabled = importPath.isNotBlank(),
                    ) {
                        Icon(Icons.Default.Upload, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Import")
                    }
                }

                // Import warning
                Text(
                    "Import will validate the data before replacing your current directory. " +
                        "Maximum ${PersonDirectory.MAX_DIRECTORY_SIZE} persons and " +
                        "${PersonDirectory.MAX_TOTAL_EMBEDDINGS} total embeddings.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
    )
}

/** Settings dialog for face detection/identification toggles and matching configuration. */
@Composable
private fun PeopleSettingsDialog(
    personService: PersonService,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var autoDetect by remember { mutableStateOf(false) }
    var autoIdentify by remember { mutableStateOf(false) }
    var matchingConfig by remember { mutableStateOf(FaceMatchingConfig()) }
    var showClearAllConfirm by remember { mutableStateOf(false) }
    var showTrimConfirm by remember { mutableStateOf(false) }

    // Load current settings
    androidx.compose.runtime.LaunchedEffect(Unit) {
        autoDetect = personService.isAutoDetectEnabled()
        autoIdentify = personService.isAutoIdentifyEnabled()
        matchingConfig = personService.getMatchingConfig()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("People Settings") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Note: SettingsToggle uses "checked" parameter (not "isChecked")
                SettingsToggle(
                    label = "Auto-detect faces on import",
                    description = "Automatically detect faces when importing images",
                    checked = autoDetect,
                    onCheckedChange = { enabled ->
                        autoDetect = enabled
                        scope.launch { personService.setAutoDetectEnabled(enabled) }
                    },
                )
                SettingsToggle(
                    label = "Auto-identify faces",
                    description = "Automatically suggest person names from your people directory",
                    checked = autoIdentify,
                    onCheckedChange = { enabled ->
                        autoIdentify = enabled
                        scope.launch { personService.setAutoIdentifyEnabled(enabled) }
                    },
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Matching configuration
                Text("Matching", style = MaterialTheme.typography.titleSmall)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Max face references per person:", modifier = Modifier.weight(1f))
                    Text(
                        "${matchingConfig.maxGallerySize}",
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    OutlinedButton(
                        onClick = {
                            if (matchingConfig.maxGallerySize > 1) {
                                matchingConfig = matchingConfig.copy(maxGallerySize = matchingConfig.maxGallerySize - 1)
                                scope.launch { personService.setMatchingConfig(matchingConfig) }
                            }
                        },
                        enabled = matchingConfig.maxGallerySize > 1,
                    ) { Text("−") }
                    OutlinedButton(
                        onClick = {
                            if (matchingConfig.maxGallerySize < 50) {
                                matchingConfig = matchingConfig.copy(maxGallerySize = matchingConfig.maxGallerySize + 1)
                                scope.launch { personService.setMatchingConfig(matchingConfig) }
                            }
                        },
                        enabled = matchingConfig.maxGallerySize < 50,
                    ) { Text("+") }
                    OutlinedButton(
                        onClick = { showTrimConfirm = true },
                    ) { Text("Trim Now") }
                }
                Text(
                    "Higher values improve recognition accuracy but use more memory. " +
                        "Use \"Trim Now\" to evict redundant face references after decreasing this limit.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(modifier = Modifier.height(8.dp))

                // GDPR Privacy Controls (issue #24)
                Text("Privacy", style = MaterialTheme.typography.titleSmall)
                OutlinedButton(
                    onClick = {
                        showClearAllConfirm = true
                    },
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Clear All Face Data")
                }
                Text(
                    "Remove all face embeddings from all persons. " +
                        "Person names and photo associations are kept.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Done") }
        },
    )

    // Confirmation dialog for clearing all face data
    if (showClearAllConfirm) {
        AlertDialog(
            onDismissRequest = { showClearAllConfirm = false },
            title = { Text("Clear All Face Data?") },
            text = {
                Text(
                    "This will permanently remove all face embeddings from all persons. " +
                        "Person names and photo associations will be kept, but face recognition " +
                        "will need to re-learn faces from scratch. This cannot be undone."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearAllConfirm = false
                        scope.launch {
                            personService.clearAllEmbeddings()
                        }
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) { Text("Clear All") }
            },
            dismissButton = {
                TextButton(onClick = { showClearAllConfirm = false }) { Text("Cancel") }
            },
        )
    }

    // Confirmation dialog for trimming galleries to config size
    if (showTrimConfirm) {
        AlertDialog(
            onDismissRequest = { showTrimConfirm = false },
            title = { Text("Trim Face References?") },
            text = {
                Text(
                    "This will remove the most redundant face references from each person, " +
                        "trimming all galleries to ${matchingConfig.maxGallerySize} entries. " +
                        "The most diverse references are kept. This cannot be undone."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showTrimConfirm = false
                        scope.launch {
                            val evicted = personService.trimAllGalleriesToConfigSize()
                            // The directory will auto-update via StateFlow
                        }
                    },
                ) { Text("Trim") }
            },
            dismissButton = {
                TextButton(onClick = { showTrimConfirm = false }) { Text("Cancel") }
            },
        )
    }
}
package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FolderOff
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.data.local.LanguagePackEntity
import com.example.data.local.ProjectEntity
import com.example.domain.model.LanguageCatalog
import com.example.domain.model.ProjectStatus
import com.example.engine.TimeFormatters
import com.example.ui.components.LanguagePickerSheet
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ProjectsScreen(
    projects: List<ProjectEntity>,
    packs: List<LanguagePackEntity>,
    onReopenProject: (Long) -> Unit,
    onDuplicateProject: (Long) -> Unit,
    onRenameProject: (Long, String) -> Unit,
    onDeleteProject: (Long) -> Unit,
    onAddTargetLanguageToProject: (projectId: Long, targetCode: String) -> Unit,
    onDownloadPack: (String) -> Unit,
    onStartNewProject: () -> Unit,
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }
    var filterLang by remember { mutableStateOf("all") }
    var renameTarget by remember { mutableStateOf<ProjectEntity?>(null) }
    var deleteTarget by remember { mutableStateOf<ProjectEntity?>(null) }
    var addLangProject by remember { mutableStateOf<ProjectEntity?>(null) }

    val availableFilterLangs = remember(projects) {
        val codes = projects.flatMap { p ->
            val src = p.detectedLangCode ?: p.sourceLangCode
            val tgts = p.targetLangCodesCsv.split(",")
            (listOf(src) + tgts).filter { it.isNotBlank() && it != "auto" }
        }.distinct()
        listOf("all") + codes
    }

    val filtered = remember(projects, searchQuery, filterLang) {
        projects.filter { p ->
            val matchesQuery = searchQuery.isBlank() || p.title.contains(searchQuery, ignoreCase = true)
            val matchesLang = filterLang == "all" ||
                p.sourceLangCode == filterLang ||
                p.detectedLangCode == filterLang ||
                p.targetLangCodesCsv.split(",").contains(filterLang)
            matchesQuery && matchesLang
        }
    }

    val dateFormatter = remember { SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Mere Projects", style = MaterialTheme.typography.headlineMedium)
            Button(
                onClick = onStartNewProject,
                modifier = Modifier.testTag("new_project_btn")
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Naya Video")
            }
        }

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Project naam se khojein...") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("projects_search_input")
        )

        if (availableFilterLangs.size > 1) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                availableFilterLangs.forEach { code ->
                    FilterChip(
                        selected = filterLang == code,
                        onClick = { filterLang = code },
                        label = {
                            Text(if (code == "all") "Sabhi" else LanguageCatalog.displayName(code))
                        }
                    )
                }
            }
        }

        if (filtered.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(
                        Icons.Filled.FolderOff,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(48.dp)
                    )
                    Text("Koi project nahi mila", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Har video apne aap save hota hai. Naya video chun kar shuru karein!",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 20.dp)
            ) {
                items(filtered, key = { it.id }) { proj ->
                    ProjectHistoryCard(
                        project = proj,
                        dateLabel = dateFormatter.format(Date(proj.updatedAt)),
                        onReopen = { onReopenProject(proj.id) },
                        onDuplicate = { onDuplicateProject(proj.id) },
                        onRename = { renameTarget = proj },
                        onDelete = { deleteTarget = proj },
                        onAddLanguage = { addLangProject = proj }
                    )
                }
            }
        }
    }

    renameTarget?.let { proj ->
        var titleInput by remember(proj.id) { mutableStateOf(proj.title) }
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("Project Naam Badlein") },
            text = {
                OutlinedTextField(
                    value = titleInput,
                    onValueChange = { titleInput = it },
                    label = { Text("Naya Naam") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (titleInput.isNotBlank()) onRenameProject(proj.id, titleInput)
                        renameTarget = null
                    }
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) { Text("Cancel") }
            }
        )
    }

    deleteTarget?.let { proj ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Project Delete Karein?") },
            text = { Text("\"${proj.title}\" aur iski saari translated lines hamesha ke liye hat jayengi.") },
            confirmButton = {
                Button(onClick = {
                    onDeleteProject(proj.id)
                    deleteTarget = null
                }) { Text("Haan, Delete") }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("Nahi") }
            }
        )
    }

    addLangProject?.let { proj ->
        LanguagePickerSheet(
            titleHinglish = "Bina Video Padhe Nayi Bhasha Jodein",
            includeAutoDetect = false,
            multiSelect = false,
            selectedCodes = proj.targetLangCodesCsv.split(",").toSet(),
            recentCodes = emptyList(),
            packs = packs,
            isTargetPicker = true,
            onSelect = { code ->
                onAddTargetLanguageToProject(proj.id, code)
                addLangProject = null
            },
            onDownloadPack = onDownloadPack,
            onDismiss = { addLangProject = null }
        )
    }
}

@Composable
private fun ProjectHistoryCard(
    project: ProjectEntity,
    dateLabel: String,
    onReopen: () -> Unit,
    onDuplicate: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onAddLanguage: () -> Unit
) {
    val statusEnum = runCatching { ProjectStatus.valueOf(project.status) }.getOrDefault(ProjectStatus.DRAFT)
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onReopen)
            .testTag("project_card_${project.id}")
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val thumbFile = project.thumbnailPath?.let { File(it) }
                if (thumbFile != null && thumbFile.exists()) {
                    AsyncImage(
                        model = thumbFile,
                        contentDescription = project.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(width = 92.dp, height = 62.dp)
                            .clip(RoundedCornerShape(10.dp))
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(width = 92.dp, height = 62.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.Movie, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    }
                }

                Spacer(Modifier.width(12.dp))

                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = project.title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    val src = LanguageCatalog.displayName(project.detectedLangCode ?: project.sourceLangCode)
                    val tgts = project.targetLangCodesCsv.split(",")
                        .filter { it.isNotBlank() }
                        .joinToString(", ") { LanguageCatalog.displayName(it) }
                    Text(
                        text = "$src -> $tgts",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = when (statusEnum) {
                                ProjectStatus.READY -> MaterialTheme.colorScheme.tertiaryContainer
                                ProjectStatus.ERROR -> MaterialTheme.colorScheme.errorContainer
                                else -> MaterialTheme.colorScheme.secondaryContainer
                            }
                        ) {
                            Text(
                                text = statusEnum.labelHinglish,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                        Text(
                            text = "${TimeFormatters.formatShort(project.durationMs)} • $dateLabel",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onAddLanguage,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Bhasha Jodo", style = MaterialTheme.typography.labelMedium)
                }

                Row {
                    IconButton(onClick = onRename, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Filled.Edit, contentDescription = "Rename", modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = onDuplicate, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Filled.ContentCopy, contentDescription = "Duplicate", modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

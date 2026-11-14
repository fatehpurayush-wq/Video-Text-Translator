package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.local.GlossaryRuleEntity
import com.example.domain.model.ExportFormat

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ExportAndCopySheet(
    projectTitle: String,
    onCopyOriginal: (withTiming: Boolean) -> Unit,
    onCopyTranslated: (withTiming: Boolean) -> Unit,
    onCopySideBySide: (withTiming: Boolean) -> Unit,
    onShareFormat: (ExportFormat, (Intent) -> Unit) -> Unit,
    onSaveToUri: (Uri, ExportFormat) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var pendingSaveFormat by remember { mutableStateOf<ExportFormat?>(null) }

    val saveFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("*/*")
    ) { uri ->
        val fmt = pendingSaveFormat
        if (uri != null && fmt != null) {
            onSaveToUri(uri, fmt)
        }
        pendingSaveFormat = null
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Copy Aur Export Karein", style = MaterialTheme.typography.headlineSmall)

            Text("Turant Copy Karein", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { onCopyOriginal(true) },
                    modifier = Modifier.testTag("copy_original_btn")
                ) {
                    Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Saara Original")
                }
                OutlinedButton(
                    onClick = { onCopyTranslated(true) },
                    modifier = Modifier.testTag("copy_translated_btn")
                ) {
                    Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Saara Translation")
                }
                OutlinedButton(
                    onClick = { onCopySideBySide(true) },
                    modifier = Modifier.testTag("copy_side_by_side_btn")
                ) {
                    Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Side-by-Side")
                }
                OutlinedButton(
                    onClick = { onCopyTranslated(false) },
                    modifier = Modifier.testTag("copy_clean_list_btn")
                ) {
                    Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Sirf List (Bina Timing)")
                }
            }

            HorizontalDivider()

            Text("File Export & Share", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.secondary)

            ExportFormat.entries.forEach { format ->
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(format.labelHinglish, style = MaterialTheme.typography.titleSmall)
                            Text(
                                ".${format.extension.uppercase()} file with timestamps",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(
                            onClick = {
                                pendingSaveFormat = format
                                val safe = projectTitle.replace(Regex("[^a-zA-Z0-9_-]"), "_").ifBlank { "subtitles" }
                                saveFileLauncher.launch("${safe}.${format.extension}")
                            },
                            modifier = Modifier.testTag("save_${format.name.lowercase()}")
                        ) {
                            Icon(Icons.Filled.SaveAlt, contentDescription = "Save ${format.extension}")
                        }
                        IconButton(
                            onClick = {
                                onShareFormat(format) { intent -> context.startActivity(intent) }
                            },
                            modifier = Modifier.testTag("share_${format.name.lowercase()}")
                        ) {
                            Icon(Icons.Filled.Share, contentDescription = "Share ${format.extension}", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun GlossaryDialog(
    rules: List<GlossaryRuleEntity>,
    onAddRule: (source: String, target: String) -> Unit,
    onDeleteRule: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    var sourceTerm by remember { mutableStateOf("") }
    var targetTerm by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Meri Words List (Names & Rules)") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Naam, brands ya khaas shabd jodne par translation mein woh hamesha ek jaise rahenge.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = sourceTerm,
                        onValueChange = { sourceTerm = it },
                        label = { Text("Word") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = targetTerm,
                        onValueChange = { targetTerm = it },
                        label = { Text("Aise Likho") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                Button(
                    onClick = {
                        onAddRule(sourceTerm, targetTerm)
                        sourceTerm = ""
                        targetTerm = ""
                    },
                    enabled = sourceTerm.isNotBlank() && targetTerm.isNotBlank(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Rule Jodein")
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 220.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(rules, key = { it.id }) { rule ->
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${rule.sourceTerm}  ->  ${rule.targetTerm}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(onClick = { onDeleteRule(rule.id) }, modifier = Modifier.size(32.dp)) {
                                    Icon(Icons.Filled.Delete, contentDescription = "Delete rule", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Band Karein") }
        }
    )
}

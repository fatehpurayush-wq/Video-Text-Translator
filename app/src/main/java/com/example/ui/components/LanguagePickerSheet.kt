package com.example.ui.components

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.local.LanguagePackEntity
import com.example.domain.model.AccuracyRating
import com.example.domain.model.LanguageCatalog
import com.example.domain.model.LanguageSpec
import com.example.domain.model.PackCategory
import com.example.domain.model.PackStatus

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun LanguagePickerSheet(
    titleHinglish: String,
    includeAutoDetect: Boolean,
    multiSelect: Boolean,
    selectedCodes: Set<String>,
    recentCodes: List<String>,
    packs: List<LanguagePackEntity>,
    isTargetPicker: Boolean,
    onSelect: (String) -> Unit,
    onDownloadPack: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var query by remember { mutableStateOf("") }

    val filtered = remember(query) {
        val q = query.trim().lowercase()
        if (q.isBlank()) LanguageCatalog.allLanguages
        else LanguageCatalog.allLanguages.filter {
            it.englishName.lowercase().contains(q) ||
                it.nativeName.lowercase().contains(q) ||
                it.code.lowercase().contains(q)
        }
    }

    val packMap = remember(packs) { packs.associateBy { it.id } }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = titleHinglish,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (multiSelect) {
                    TextButton(onClick = onDismiss) {
                        Text("Done (${selectedCodes.size})")
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Bhasha khojein (Hindi, Tamil, Japanese...)") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("language_search_input")
            )

            if (recentCodes.isNotEmpty() && query.isBlank()) {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "Recent Bhashayein",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    recentCodes.forEach { code ->
                        val spec = LanguageCatalog.findByCode(code) ?: return@forEach
                        AssistChip(
                            onClick = {
                                onSelect(code)
                                if (!multiSelect) onDismiss()
                            },
                            label = { Text(spec.englishName) }
                        )
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 430.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (includeAutoDetect && query.isBlank()) {
                    item {
                        val isSel = selectedCodes.contains(LanguageCatalog.AUTO_DETECT_CODE)
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSel) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onSelect(LanguageCatalog.AUTO_DETECT_CODE)
                                    if (!multiSelect) onDismiss()
                                }
                                .testTag("lang_item_auto")
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Filled.AutoAwesome,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        "Auto Detect (Offline)",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        "Video ka text padhkar bhasha khud pehchanein",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (isSel) {
                                    Icon(Icons.Filled.CheckCircle, contentDescription = "Selected", tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                }

                items(filtered, key = { it.code }) { spec ->
                    val isSel = selectedCodes.contains(spec.code)
                    val packId = if (isTargetPicker) "trans_${spec.code}" else "ocr_${spec.code}"
                    val pack = packMap[packId]
                    val packStatus = pack?.status?.let { runCatching { PackStatus.valueOf(it) }.getOrNull() }
                        ?: if (!isTargetPicker && spec.isOcrBundled) PackStatus.BUILT_IN else PackStatus.NOT_DOWNLOADED

                    LanguagePickerRow(
                        spec = spec,
                        isSelected = isSel,
                        isTargetPicker = isTargetPicker,
                        packId = packId,
                        packStatus = packStatus,
                        onSelect = {
                            onSelect(spec.code)
                            if (!multiSelect) onDismiss()
                        },
                        onDownloadPack = { onDownloadPack(packId) }
                    )
                }
            }
        }
    }
}

@Composable
private fun LanguagePickerRow(
    spec: LanguageSpec,
    isSelected: Boolean,
    isTargetPicker: Boolean,
    packId: String,
    packStatus: PackStatus,
    onSelect: () -> Unit,
    onDownloadPack: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .testTag("lang_item_${spec.code}")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = spec.englishName,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = spec.nativeName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
                Spacer(Modifier.height(2.dp))
                val statusHint = when {
                    isTargetPicker && !spec.supportsOfflineTranslation ->
                        "Offline translate pack uplabdh nahi (OCR/Roman ready)"
                    packStatus == PackStatus.BUILT_IN -> "Built-in • Bina download offline ready"
                    packStatus == PackStatus.READY -> "Offline Pack Ready"
                    packStatus == PackStatus.DOWNLOADING -> "Download ho raha hai..."
                    !isTargetPicker && spec.ocrAccuracy == AccuracyRating.LOW ->
                        "Accuracy: Kamzor • Pack download zaroori"
                    else -> "Offline Pack download karein (~${if (isTargetPicker) spec.translateSizeMb.toInt() else spec.tessSizeMb} MB)"
                }
                Text(
                    text = statusHint,
                    style = MaterialTheme.typography.bodySmall,
                    color = when (packStatus) {
                        PackStatus.BUILT_IN, PackStatus.READY -> MaterialTheme.colorScheme.tertiary
                        PackStatus.UNAVAILABLE -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }

            if (packStatus == PackStatus.NOT_DOWNLOADED || packStatus == PackStatus.PAUSED || packStatus == PackStatus.ERROR) {
                TextButton(onClick = onDownloadPack) {
                    Icon(Icons.Filled.Download, contentDescription = "Download pack", modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Pack")
                }
            } else if (packStatus == PackStatus.UNAVAILABLE) {
                Icon(Icons.Filled.Warning, contentDescription = "Offline unavailable", tint = MaterialTheme.colorScheme.error)
            }

            if (isSelected) {
                Spacer(Modifier.width(6.dp))
                Icon(Icons.Filled.CheckCircle, contentDescription = "Selected", tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

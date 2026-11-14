package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.local.LanguagePackEntity
import com.example.data.local.ProjectEntity
import com.example.domain.model.AccuracyRating
import com.example.domain.model.LanguageCatalog
import com.example.domain.model.PackStatus
import com.example.domain.model.TranslationStyle
import com.example.ui.components.LanguagePickerSheet

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun Step2LanguageScreen(
    project: ProjectEntity,
    recentTargetLangs: List<String>,
    packs: List<LanguagePackEntity>,
    onSourceSelected: (String) -> Unit,
    onToggleTarget: (code: String, multiMode: Boolean) -> Unit,
    onSwapLanguages: () -> Unit,
    onToggleRoman: (Boolean) -> Unit,
    onStyleSelected: (TranslationStyle) -> Unit,
    onDownloadPack: (String) -> Unit,
    onNextStep: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showSourcePicker by remember { mutableStateOf(false) }
    var showTargetPicker by remember { mutableStateOf(false) }

    val selectedTargets = remember(project.targetLangCodesCsv) {
        project.targetLangCodesCsv.split(",").filter { it.isNotBlank() }
    }
    var multiLangMode by remember(selectedTargets.size) { mutableStateOf(selectedTargets.size > 1) }

    val activeStyle = remember(project.translationStyle) {
        runCatching { TranslationStyle.valueOf(project.translationStyle) }.getOrDefault(TranslationStyle.SIMPLE)
    }

    val packMap = remember(packs) { packs.associateBy { it.id } }

    val hasIndianTarget = selectedTargets.any { LanguageCatalog.findByCode(it)?.supportsRomanScript == true }

    // Check missing packs or warnings
    val missingPackIds = remember(project.sourceLangCode, selectedTargets, packs) {
        val needed = mutableListOf<String>()
        if (!project.isAutoDetectSource) {
            val ocrPack = packMap["ocr_${project.sourceLangCode}"]
            if (ocrPack != null && ocrPack.status != PackStatus.BUILT_IN.name && ocrPack.status != PackStatus.READY.name) {
                needed.add(ocrPack.id)
            }
            val srcTrans = packMap["trans_${project.sourceLangCode}"]
            if (srcTrans != null && srcTrans.status == PackStatus.NOT_DOWNLOADED.name) {
                needed.add(srcTrans.id)
            }
        }
        selectedTargets.forEach { tgt ->
            val tgtTrans = packMap["trans_$tgt"]
            if (tgtTrans != null && tgtTrans.status != PackStatus.READY.name && tgtTrans.status != PackStatus.UNAVAILABLE.name) {
                needed.add(tgtTrans.id)
            }
        }
        needed.distinct()
    }

    val unsupportedOfflinePairs = remember(project.sourceLangCode, selectedTargets) {
        selectedTargets.filter { tgt ->
            val tgtOk = LanguageCatalog.findByCode(tgt)?.supportsOfflineTranslation == true
            val srcOk = project.isAutoDetectSource || LanguageCatalog.findByCode(project.sourceLangCode)?.supportsOfflineTranslation == true
            !tgtOk || !srcOk
        }
    }

    val pivotNoticeNeeded = remember(project.sourceLangCode, selectedTargets) {
        !project.isAutoDetectSource && selectedTargets.any { tgt ->
            LanguageCatalog.usesEnglishPivot(project.sourceLangCode, tgt)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = "Bhasha Chuno",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface
        )

        // Source & Target Pair Selector Card
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Original Language
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .clickable { showSourcePicker = true }
                        .padding(14.dp)
                        .testTag("select_source_lang_btn")
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Original Bhasha (Video Mein)",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = if (project.isAutoDetectSource) "Galat lage? Badlo" else "Manual",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = if (project.isAutoDetectSource) "Auto Detect (Offline)" else LanguageCatalog.displayName(project.sourceLangCode),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                // Swap Button Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilledTonalIconButton(
                        onClick = onSwapLanguages,
                        modifier = Modifier.testTag("swap_languages_btn")
                    ) {
                        Icon(Icons.Filled.SwapHoriz, contentDescription = "Swap languages")
                    }
                }

                // Target Language(s)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .clickable { showTargetPicker = true }
                        .padding(14.dp)
                        .testTag("select_target_lang_btn")
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Translate Karein (Nayi Bhasha)",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.secondary
                        )
                        Text(
                            text = "Search & Pick",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        selectedTargets.forEach { code ->
                            val spec = LanguageCatalog.findByCode(code)
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer
                            ) {
                                Text(
                                    text = spec?.let { "${it.englishName} (${it.nativeName})" } ?: code,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }

                // Recent Languages Row
                if (recentTargetLangs.isNotEmpty()) {
                    Text(
                        text = "Recent Bhashayein",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        recentTargetLangs.forEach { code ->
                            val selected = selectedTargets.contains(code)
                            FilterChip(
                                selected = selected,
                                onClick = { onToggleTarget(code, multiLangMode) },
                                label = { Text(LanguageCatalog.displayName(code)) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            )
                        }
                    }
                }
            }
        }

        // Multi-language & Roman Script Toggles
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Kai languages ek saath", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Ek saath multiple target languages alag tabs mein paayein",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = multiLangMode,
                        onCheckedChange = { enabled ->
                            multiLangMode = enabled
                            if (enabled) showTargetPicker = true
                        },
                        modifier = Modifier.testTag("multi_lang_switch")
                    )
                }

                if (hasIndianTarget) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Roman script dikhao", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "Hindi/Marathi/Tamil aadi ke neeche English letters mein line (jaise: Namaste)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = project.showRomanScript,
                            onCheckedChange = onToggleRoman,
                            modifier = Modifier.testTag("roman_script_switch")
                        )
                    }
                }
            }
        }

        // Translation Style Choice
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Translate, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Translation Ka Style", style = MaterialTheme.typography.titleSmall)
                }
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    TranslationStyle.entries.forEach { style ->
                        FilterChip(
                            selected = activeStyle == style,
                            onClick = { onStyleSelected(style) },
                            label = { Text(style.labelHinglish) },
                            modifier = Modifier.testTag("style_${style.name.lowercase()}")
                        )
                    }
                }
                Text(
                    text = activeStyle.hintHinglish,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Clear Offline Status & Warnings
        if (unsupportedOfflinePairs.isNotEmpty()) {
            StatusAlertCard(
                isError = true,
                title = "Offline Pair Uplabdh Nahi Hai",
                message = "${unsupportedOfflinePairs.joinToString { LanguageCatalog.displayName(it) }} ka offline translate pack nahi aata. Kripya koi aur bhasha chunein."
            )
        } else if (missingPackIds.isNotEmpty()) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Language Pack Download Karein",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Text(
                            "${missingPackIds.size} pack phone mein nahi hai. Ek baar download karein, phir bina internet chalega.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.85f)
                        )
                    }
                    TextButton(
                        onClick = { missingPackIds.forEach(onDownloadPack) },
                        modifier = Modifier.testTag("download_missing_packs_btn")
                    ) {
                        Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Download")
                    }
                }
            }
        } else {
            StatusAlertCard(
                isError = false,
                title = "Offline Ready",
                message = if (pivotNoticeNeeded) {
                    "Saare packs ready hain! Seedha pair na hone par app English ke through offline translate karega."
                } else {
                    "Chune hue language packs phone par ready hain — bina internet chalega."
                }
            )
        }

        if (!project.isAutoDetectSource) {
            val srcSpec = LanguageCatalog.findByCode(project.sourceLangCode)
            if (srcSpec?.ocrAccuracy == AccuracyRating.LOW) {
                StatusAlertCard(
                    isError = true,
                    title = "Low Accuracy Warning",
                    message = "${srcSpec.englishName} script ke liye on-device OCR accuracy kamzor ho sakti hai. Result screen par aap unsure words aasani se fix kar payenge."
                )
            }
        }

        Button(
            onClick = onNextStep,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .testTag("step2_aage_badho_btn")
        ) {
            Text("Aage Badho (Text Nikalo)", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.width(8.dp))
            Icon(Icons.Filled.ArrowForward, contentDescription = null)
        }

        Spacer(Modifier.height(16.dp))
    }

    if (showSourcePicker) {
        LanguagePickerSheet(
            titleHinglish = "Original Bhasha Chuno",
            includeAutoDetect = true,
            multiSelect = false,
            selectedCodes = setOf(project.sourceLangCode),
            recentCodes = emptyList(),
            packs = packs,
            isTargetPicker = false,
            onSelect = onSourceSelected,
            onDownloadPack = onDownloadPack,
            onDismiss = { showSourcePicker = false }
        )
    }

    if (showTargetPicker) {
        LanguagePickerSheet(
            titleHinglish = if (multiLangMode) "Kai Bhashayein Chuno" else "Translate Kis Mein Karein?",
            includeAutoDetect = false,
            multiSelect = multiLangMode,
            selectedCodes = selectedTargets.toSet(),
            recentCodes = recentTargetLangs,
            packs = packs,
            isTargetPicker = true,
            onSelect = { code -> onToggleTarget(code, multiLangMode) },
            onDownloadPack = onDownloadPack,
            onDismiss = { showTargetPicker = false }
        )
    }
}

@Composable
private fun StatusAlertCard(
    isError: Boolean,
    title: String,
    message: String
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (isError) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.tertiaryContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (isError) Icons.Filled.Info else Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary
            )
            Spacer(Modifier.width(10.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (isError) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onTertiaryContainer
                )
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isError) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onTertiaryContainer
                )
            }
        }
    }
}

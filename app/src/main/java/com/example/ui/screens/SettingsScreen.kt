package com.example.ui.screens

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
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.local.LanguagePackEntity
import com.example.data.local.UserSettings
import com.example.domain.model.AppThemeMode
import com.example.domain.model.CaptionStyleConfig
import com.example.domain.model.LanguageCatalog
import com.example.ui.components.LanguagePickerSheet
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    settings: UserSettings,
    packs: List<LanguagePackEntity>,
    packStorageBytes: Long,
    onSetDefaultSource: (String) -> Unit,
    onSetDefaultTarget: (String) -> Unit,
    onSetFrameInterval: (Long) -> Unit,
    onSetMinTextLength: (Int) -> Unit,
    onSetWifiOnly: (Boolean) -> Unit,
    onSetTheme: (AppThemeMode) -> Unit,
    onSaveCaptionDefaults: (CaptionStyleConfig) -> Unit,
    onClearCache: () -> Unit,
    onDeleteAllPacks: () -> Unit,
    onDownloadPack: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var showDefaultSrcPicker by remember { mutableStateOf(false) }
    var showDefaultTgtPicker by remember { mutableStateOf(false) }
    var showDeleteAllConfirm by remember { mutableStateOf(false) }

    val storageMb = packStorageBytes / (1024.0 * 1024.0)

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium)

        // Default Languages
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Translate, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text("Default Bhashayein", style = MaterialTheme.typography.titleSmall)
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showDefaultSrcPicker = true }
                        .padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Default Original Bhasha", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        LanguageCatalog.displayName(settings.defaultSourceLang),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showDefaultTgtPicker = true }
                        .padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Default Translate To", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        LanguageCatalog.displayName(settings.defaultTargetLang),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }
        }

        // Speed vs Accuracy & Noise Filter
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Speed, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                    Spacer(Modifier.width(8.dp))
                    Text("Reading Speed vs Accuracy", style = MaterialTheme.typography.titleSmall)
                }
                val intervalSec = settings.frameSamplingIntervalMs / 1000f
                val modeDesc = when {
                    settings.frameSamplingIntervalMs <= 600L -> "High Accuracy (Har ${String.format("%.1f", intervalSec)}s frame check)"
                    settings.frameSamplingIntervalMs <= 1250L -> "Balanced (Har ${String.format("%.1f", intervalSec)}s frame check)"
                    else -> "Fast Mode (Har ${String.format("%.1f", intervalSec)}s frame check)"
                }
                Text(modeDesc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                Slider(
                    value = settings.frameSamplingIntervalMs.toFloat(),
                    onValueChange = { onSetFrameInterval(it.roundToInt().toLong()) },
                    valueRange = 400f..2500f,
                    modifier = Modifier.testTag("speed_accuracy_slider")
                )

                Text(
                    "Noise Ignore Minimum Length: ${settings.minTextLength} akshar",
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    "Isse chhote random akshar/logo noise ignore kar diye jayenge.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Slider(
                    value = settings.minTextLength.toFloat(),
                    onValueChange = { onSetMinTextLength(it.roundToInt()) },
                    valueRange = 1f..12f,
                    steps = 10
                )
            }
        }

        // Caption Style Defaults
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Subtitles, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text("Caption Style Defaults", style = MaterialTheme.typography.titleSmall)
                }
                val cap = settings.defaultCaptionStyle
                Text("Default Font Size: ${cap.fontSizeSp.roundToInt()} sp", style = MaterialTheme.typography.bodySmall)
                Slider(
                    value = cap.fontSizeSp,
                    onValueChange = { onSaveCaptionDefaults(cap.copy(fontSizeSp = it)) },
                    valueRange = 14f..34f
                )
                Text("Default Max Words Per Line: ${cap.maxWordsPerLine}", style = MaterialTheme.typography.bodySmall)
                Slider(
                    value = cap.maxWordsPerLine.toFloat(),
                    onValueChange = { onSaveCaptionDefaults(cap.copy(maxWordsPerLine = it.roundToInt())) },
                    valueRange = 3f..12f,
                    steps = 8
                )
            }
        }

        // Theme Selector
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Palette, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                    Spacer(Modifier.width(8.dp))
                    Text("Dark Premium Theme", style = MaterialTheme.typography.titleSmall)
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppThemeMode.entries.forEach { mode ->
                        FilterChip(
                            selected = settings.themeMode == mode,
                            onClick = { onSetTheme(mode) },
                            label = { Text(mode.labelHinglish) },
                            modifier = Modifier.testTag("theme_${mode.name.lowercase()}")
                        )
                    }
                }
            }
        }

        // Network & Storage Controls
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Filled.Wifi, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text("Sirf Wi-Fi par download", style = MaterialTheme.typography.titleSmall)
                    }
                    Switch(
                        checked = settings.wifiOnlyDownloads,
                        onCheckedChange = onSetWifiOnly,
                        modifier = Modifier.testTag("settings_wifi_switch")
                    )
                }

                Text(
                    text = String.format("Downloaded Language Packs Storage: %.1f MB", storageMb),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = onClearCache,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("cache_saaf_karo_btn")
                    ) {
                        Icon(Icons.Filled.CleaningServices, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Cache Saaf Karo")
                    }

                    Button(
                        onClick = { showDeleteAllConfirm = true },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("delete_all_packs_btn")
                    ) {
                        Icon(Icons.Filled.DeleteForever, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Delete All Packs")
                    }
                }
            }
        }

        Text(
            text = "100% On-Device Offline Engine • Koi API Key ya Login nahi chahiye.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 18.dp)
        )
    }

    if (showDefaultSrcPicker) {
        LanguagePickerSheet(
            titleHinglish = "Default Original Bhasha",
            includeAutoDetect = true,
            multiSelect = false,
            selectedCodes = setOf(settings.defaultSourceLang),
            recentCodes = emptyList(),
            packs = packs,
            isTargetPicker = false,
            onSelect = onSetDefaultSource,
            onDownloadPack = onDownloadPack,
            onDismiss = { showDefaultSrcPicker = false }
        )
    }

    if (showDefaultTgtPicker) {
        LanguagePickerSheet(
            titleHinglish = "Default Translate Bhasha",
            includeAutoDetect = false,
            multiSelect = false,
            selectedCodes = setOf(settings.defaultTargetLang),
            recentCodes = settings.recentTargetLangs,
            packs = packs,
            isTargetPicker = true,
            onSelect = onSetDefaultTarget,
            onDownloadPack = onDownloadPack,
            onDismiss = { showDefaultTgtPicker = false }
        )
    }

    if (showDeleteAllConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteAllConfirm = false },
            title = { Text("Saare Packs Delete Karein?") },
            text = { Text("Saare downloaded OCR, Translation aur Speech packs hat jayenge. Baad mein dobara download kar sakte hain.") },
            confirmButton = {
                Button(onClick = {
                    onDeleteAllPacks()
                    showDeleteAllConfirm = false
                }) { Text("Haan, Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAllConfirm = false }) { Text("Cancel") }
            }
        )
    }
}

package com.example.ui.screens

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.local.ProjectEntity
import com.example.domain.model.ExtractionSource
import com.example.domain.model.LanguageCatalog
import com.example.domain.model.NormalizedRect
import com.example.domain.model.ProcessingProgress
import com.example.domain.model.ProjectStatus
import com.example.domain.model.QuickTestResult
import com.example.engine.TimeFormatters
import com.example.ui.components.CropOverlayImage
import kotlinx.serialization.json.Json
import kotlin.math.roundToInt

@Composable
fun Step3ExtractSetupScreen(
    project: ProjectEntity,
    previewBitmap: Bitmap?,
    quickTestResult: QuickTestResult?,
    isTestingLanguage: Boolean,
    processingProgress: ProcessingProgress,
    onSelectSource: (ExtractionSource) -> Unit,
    onCropChanged: (NormalizedRect) -> Unit,
    onRunQuickTest: () -> Unit,
    onStartProcessing: (resume: Boolean) -> Unit,
    onCancelProcessing: () -> Unit,
    onOpenResults: () -> Unit,
    modifier: Modifier = Modifier
) {
    val json = remember { Json { ignoreUnknownKeys = true } }
    val activeSource = remember(project.extractionSource) {
        runCatching { ExtractionSource.valueOf(project.extractionSource) }
            .getOrDefault(ExtractionSource.VIDEO_TEXT)
    }
    val cropRect = remember(project.cropRectJson) {
        project.cropRectJson?.let {
            runCatching { json.decodeFromString<NormalizedRect>(it) }.getOrNull()
        } ?: NormalizedRect.FULL_FRAME
    }

    val isPausedProject = project.status == ProjectStatus.PAUSED.name && project.lastProcessedMs > 0L

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = "Text Kaise Nikalein?",
            style = MaterialTheme.typography.headlineMedium
        )

        // Extraction source choices
        ExtractionSource.entries.forEach { option ->
            val selected = activeSource == option
            val icon = when (option) {
                ExtractionSource.VIDEO_TEXT -> Icons.Filled.TextFields
                ExtractionSource.SPEECH -> Icons.Filled.GraphicEq
                ExtractionSource.BOTH -> Icons.Filled.Layers
            }
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                border = if (selected) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null,
                onClick = { onSelectSource(option) },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("extract_source_${option.name.lowercase()}")
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(26.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = option.labelHinglish,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = option.subtitleHinglish,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Text Area Chuno (Interactive Cropper) when OCR is active
        if (activeSource != ExtractionSource.SPEECH) {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Crop, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Text Area Chuno", style = MaterialTheme.typography.titleSmall)
                    }
                    CropOverlayImage(
                        bitmap = previewBitmap,
                        cropRect = cropRect,
                        onCropChanged = onCropChanged
                    )
                }
            }
        }

        // Test Button: "Language test karo"
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
                    Column(Modifier.weight(1f)) {
                        Text("Quality Pehle Check Karein", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Ek frame aur sample sentence test karke dekhein",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    OutlinedButton(
                        onClick = onRunQuickTest,
                        enabled = !isTestingLanguage && !processingProgress.isRunning,
                        modifier = Modifier.testTag("language_test_karo_btn")
                    ) {
                        if (isTestingLanguage) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Filled.Science, contentDescription = null, modifier = Modifier.size(18.dp))
                        }
                        Spacer(Modifier.width(6.dp))
                        Text("Language Test Karo")
                    }
                }

                AnimatedVisibility(visible = quickTestResult != null) {
                    quickTestResult?.let { res ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "Detected: ${LanguageCatalog.displayName(res.detectedLangCode)}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = "Confidence: ${(res.confidence * 100).roundToInt()}%",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.tertiary
                                    )
                                }
                                Text("Original: ${res.originalSample}", style = MaterialTheme.typography.bodySmall)
                                if (res.translatedSample.isNotBlank()) {
                                    Text(
                                        "Translation: ${res.translatedSample}",
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                }
                                if (!res.romanSample.isNullOrBlank()) {
                                    Text(
                                        "Roman: ${res.romanSample}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (res.usedPivotEnglish) {
                                    Text(
                                        "Note: English ke through translate kiya gaya.",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (!res.warningHinglish.isNullOrBlank()) {
                                    Text(
                                        res.warningHinglish,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Background Progress / Error / Start Action Card
        if (processingProgress.isRunning) {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = processingProgress.stageLabelHinglish.ifBlank { "Process chal raha hai..." },
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = "${(processingProgress.progressFraction * 100).roundToInt()}%",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    LinearProgressIndicator(
                        progress = { processingProgress.progressFraction.coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Mili lines: ${processingProgress.foundLinesCount}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = TimeFormatters.formatEta(processingProgress.etaSeconds),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                    OutlinedButton(
                        onClick = onCancelProcessing,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("cancel_processing_btn")
                    ) {
                        Icon(Icons.Filled.Pause, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Roko (Baad Mein Resume Karein)")
                    }
                }
            }
        } else {
            val errMsg = processingProgress.errorMessageHinglish ?: project.errorMessage
            if (!errMsg.isNullOrBlank()) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = errMsg,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(14.dp)
                    )
                }
            }

            if (isPausedProject) {
                Button(
                    onClick = { onStartProcessing(true) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("resume_processing_btn"),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Wahin Se Resume Karein (${TimeFormatters.formatShort(project.lastProcessedMs)})")
                }
            }

            Button(
                onClick = { onStartProcessing(false) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .testTag("start_full_processing_btn")
            ) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (isPausedProject) "Shuru Se Process Karein" else "Aage Badho • Text Nikalo & Translate Karo",
                    style = MaterialTheme.typography.labelLarge
                )
            }

            if (project.status == ProjectStatus.READY.name) {
                OutlinedButton(
                    onClick = onOpenResults,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("open_existing_results_btn")
                ) {
                    Text("Pichhla Result Dekho")
                }
            }
        }

        Spacer(Modifier.height(16.dp))
    }
}

package com.example.ui.screens

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MovieCreation
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.model.AspectRatioOption
import com.example.domain.model.BurnInProgress
import com.example.domain.model.CaptionPosition
import com.example.domain.model.CaptionStyleConfig
import com.example.engine.TimeFormatters
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun VideoBurnInSheet(
    defaultStyle: CaptionStyleConfig,
    burnInProgress: BurnInProgress,
    onStartBurnIn: (CaptionStyleConfig) -> Unit,
    onCancelBurnIn: () -> Unit,
    onShareExportedVideo: (path: String, (Intent) -> Unit) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var style by remember { mutableStateOf(defaultStyle) }

    val textColors = listOf(0xFFFFFFFFL, 0xFFFBBF24L, 0xFF22D3EEL, 0xFF34D399L, 0xFF000000L)
    val bgColors = listOf(0xFF0A0E14L, 0xFF000000L, 0xFF1E293BL, 0xFF3F2E0AL, 0xFFFFFFFFL)

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
            Text("Video Par Lagao (MP4 Export)", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Original text ko box se dhak kar usi jagah translated caption lagayein.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Live Caption Style Preview Box
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF141B24)),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .padding(12.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(style.bgColorHex).copy(alpha = style.bgAlpha))
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    val sample = if (style.showBothOriginalAndTranslation) {
                        "Hello friends\nNamaste doston"
                    } else {
                        "Namaste doston, aaj ka video dekhein"
                    }
                    Text(
                        text = sample,
                        color = Color(style.textColorHex),
                        fontSize = style.fontSizeSp.coerceIn(13f, 24f).sp,
                        fontWeight = if (style.boldFont) FontWeight.Bold else FontWeight.Normal,
                        textAlign = TextAlign.Center
                    )
                }
            }

            // Aspect Ratio
            Text("Video Shape (Aspect Ratio)", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AspectRatioOption.entries.forEach { ar ->
                    FilterChip(
                        selected = style.aspectRatio == ar,
                        onClick = { style = style.copy(aspectRatio = ar) },
                        label = { Text(ar.labelHinglish) }
                    )
                }
            }

            // Caption Position
            Text("Caption Position", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CaptionPosition.entries.forEach { pos ->
                    FilterChip(
                        selected = style.position == pos,
                        onClick = { style = style.copy(position = pos) },
                        label = { Text(pos.labelHinglish) }
                    )
                }
            }

            // Font size & Max words per line
            Column {
                Text("Font Size: ${style.fontSizeSp.roundToInt()} sp", style = MaterialTheme.typography.labelMedium)
                Slider(
                    value = style.fontSizeSp,
                    onValueChange = { style = style.copy(fontSizeSp = it) },
                    valueRange = 14f..34f
                )
            }

            Column {
                Text("Ek Line Mein Max Words: ${style.maxWordsPerLine}", style = MaterialTheme.typography.labelMedium)
                Slider(
                    value = style.maxWordsPerLine.toFloat(),
                    onValueChange = { style = style.copy(maxWordsPerLine = it.roundToInt()) },
                    valueRange = 3f..12f,
                    steps = 8
                )
            }

            Column {
                Text("Cover Box Opacity: ${(style.bgAlpha * 100).roundToInt()}%", style = MaterialTheme.typography.labelMedium)
                Slider(
                    value = style.bgAlpha,
                    onValueChange = { style = style.copy(bgAlpha = it) },
                    valueRange = 0.2f..1f
                )
            }

            // Color Pickers
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Text Color", style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        textColors.forEach { hex ->
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(Color(hex))
                                    .border(
                                        width = if (style.textColorHex == hex) 2.5.dp else 1.dp,
                                        color = if (style.textColorHex == hex) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                        shape = CircleShape
                                    )
                                    .clickable { style = style.copy(textColorHex = hex) }
                            )
                        }
                    }
                }
                Column {
                    Text("Box Color", style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        bgColors.forEach { hex ->
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(Color(hex))
                                    .border(
                                        width = if (style.bgColorHex == hex) 2.5.dp else 1.dp,
                                        color = if (style.bgColorHex == hex) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                        shape = CircleShape
                                    )
                                    .clickable { style = style.copy(bgColorHex = hex) }
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Text Outline (Border)", style = MaterialTheme.typography.bodyMedium)
                Switch(checked = style.outlineEnabled, onCheckedChange = { style = style.copy(outlineEnabled = it) })
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Original + Translation Dono Dikhao", style = MaterialTheme.typography.bodyMedium)
                Switch(
                    checked = style.showBothOriginalAndTranslation,
                    onCheckedChange = { style = style.copy(showBothOriginalAndTranslation = it) }
                )
            }

            if (burnInProgress.isRunning) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("MP4 Video Ban Raha Hai...", style = MaterialTheme.typography.titleSmall)
                            Text("${(burnInProgress.progressFraction * 100).roundToInt()}%", style = MaterialTheme.typography.labelLarge)
                        }
                        LinearProgressIndicator(
                            progress = { burnInProgress.progressFraction.coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(TimeFormatters.formatEta(burnInProgress.etaSeconds), style = MaterialTheme.typography.bodySmall)
                        OutlinedButton(onClick = onCancelBurnIn, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Filled.Close, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Export Roko")
                        }
                    }
                }
            } else {
                burnInProgress.errorMessageHinglish?.let { err ->
                    Text(err, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }

                burnInProgress.outputFilePath?.let { path ->
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "MP4 ready aur Movies/VideoTextTranslator mein save ho gaya!",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedButton(onClick = {
                                onShareExportedVideo(path) { intent -> context.startActivity(intent) }
                            }) {
                                Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Share")
                            }
                        }
                    }
                }

                Button(
                    onClick = { onStartBurnIn(style) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("start_burn_in_btn")
                ) {
                    Icon(Icons.Filled.MovieCreation, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("MP4 Video Banao")
                }
            }
        }
    }
}

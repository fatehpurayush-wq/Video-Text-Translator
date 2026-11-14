package com.example.ui.screens

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.CallMerge
import androidx.compose.material.icons.filled.CallSplit
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FindReplace
import androidx.compose.material.icons.filled.MovieCreation
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.example.data.local.LanguagePackEntity
import com.example.data.local.ProjectEntity
import com.example.domain.model.LanguageCatalog
import com.example.domain.model.SegmentWithTranslation
import com.example.domain.model.TranslationStyle
import com.example.engine.TimeFormatters
import com.example.ui.components.LanguagePickerSheet
import com.example.ui.components.VideoPlayerBox

@Composable
fun ResultEditorScreen(
    project: ProjectEntity,
    activeTargetLang: String,
    segments: List<SegmentWithTranslation>,
    packs: List<LanguagePackEntity>,
    canUndo: Boolean,
    canRedo: Boolean,
    onSelectTargetTab: (String) -> Unit,
    onAddTargetLanguage: (String) -> Unit,
    onChangeSourceAndRetranslate: (String) -> Unit,
    onChangeStyleAndRetranslate: (TranslationStyle) -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onSaveRowEdit: (
        seg: SegmentWithTranslation,
        orig: String,
        trans: String,
        startMs: Long,
        endMs: Long,
        retranslateAfter: Boolean
    ) -> Unit,
    onRetranslateRow: (SegmentWithTranslation) -> Unit,
    onAddRowAfter: (afterOrderIdx: Int, startMs: Long, endMs: Long, orig: String, trans: String) -> Unit,
    onDeleteRow: (Long) -> Unit,
    onMergeWithNext: (Long) -> Unit,
    onSplitRow: (Long) -> Unit,
    onSearchReplace: (find: String, replace: String, inOrig: Boolean, inTrans: Boolean) -> Unit,
    onAddAlwaysReplaceRule: (src: String, tgt: String) -> Unit,
    onDownloadPack: (String) -> Unit,
    onOpenExportSheet: () -> Unit,
    onOpenBurnInSheet: () -> Unit,
    onOpenGlossarySheet: () -> Unit,
    modifier: Modifier = Modifier
) {
    var currentVideoPosMs by remember { mutableLongStateOf(0L) }
    var seekRequestMs by remember { mutableStateOf<Long?>(null) }
    var showLiveOverlay by remember { mutableStateOf(true) }
    var showSearchBar by remember { mutableStateOf(false) }
    var showAddLangPicker by remember { mutableStateOf(false) }
    var showFixSourcePicker by remember { mutableStateOf(false) }
    var editingSegment by remember { mutableStateOf<SegmentWithTranslation?>(null) }

    val targetLangs = remember(project.targetLangCodesCsv) {
        project.targetLangCodesCsv.split(",").filter { it.isNotBlank() }.distinct()
    }

    val activeIndex = remember(segments, currentVideoPosMs) {
        segments.indexOfFirst { currentVideoPosMs in it.startTimeMs..it.endTimeMs }
    }
    val activeSegment = if (activeIndex >= 0) segments[activeIndex] else null

    val listState = rememberLazyListState()
    LaunchedEffect(activeIndex) {
        if (activeIndex >= 0 && !listState.isScrollInProgress) {
            listState.animateScrollToItem(activeIndex)
        }
    }

    val usedPivotAny = remember(segments) { segments.any { it.usedPivotEnglish } }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp)
    ) {
        Spacer(Modifier.height(6.dp))

        // Video Preview Pinned at Top
        VideoPlayerBox(
            videoUri = Uri.parse(project.videoUri),
            seekRequestMs = seekRequestMs,
            onSeekConsumed = { seekRequestMs = null },
            activeSegment = activeSegment,
            showLiveOverlay = showLiveOverlay,
            onToggleLiveOverlay = { showLiveOverlay = it },
            onPositionChanged = { currentVideoPosMs = it }
        )

        Spacer(Modifier.height(8.dp))

        // Target Language Tabs + "+ Bhasha"
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (targetLangs.isNotEmpty()) {
                ScrollableTabRow(
                    selectedTabIndex = targetLangs.indexOf(activeTargetLang).coerceAtLeast(0),
                    edgePadding = 0.dp,
                    modifier = Modifier.weight(1f),
                    containerColor = MaterialTheme.colorScheme.background
                ) {
                    targetLangs.forEach { code ->
                        Tab(
                            selected = code == activeTargetLang,
                            onClick = { onSelectTargetTab(code) },
                            text = { Text(LanguageCatalog.displayName(code)) }
                        )
                    }
                }
            }
            TextButton(
                onClick = { showAddLangPicker = true },
                modifier = Modifier.testTag("add_target_lang_btn")
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(2.dp))
                Text("Bhasha")
            }
        }

        // Compact Action Toolbar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onUndo,
                enabled = canUndo,
                modifier = Modifier.testTag("undo_btn")
            ) {
                Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo")
            }
            IconButton(
                onClick = onRedo,
                enabled = canRedo,
                modifier = Modifier.testTag("redo_btn")
            ) {
                Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = "Redo")
            }

            AssistChip(
                onClick = { showFixSourcePicker = true },
                label = {
                    val srcName = LanguageCatalog.displayName(project.detectedLangCode ?: project.sourceLangCode)
                    Text("$srcName • Galat hai? Badlo")
                },
                leadingIcon = { Icon(Icons.Filled.SwapHoriz, contentDescription = null, modifier = Modifier.size(16.dp)) },
                modifier = Modifier.testTag("fix_detected_lang_btn")
            )

            AssistChip(
                onClick = { showSearchBar = !showSearchBar },
                label = { Text("Search/Replace") },
                leadingIcon = { Icon(Icons.Filled.FindReplace, contentDescription = null, modifier = Modifier.size(16.dp)) },
                modifier = Modifier.testTag("search_replace_toggle_btn")
            )

            AssistChip(
                onClick = onOpenGlossarySheet,
                label = { Text("Meri Words List") },
                leadingIcon = { Icon(Icons.Filled.Book, contentDescription = null, modifier = Modifier.size(16.dp)) },
                modifier = Modifier.testTag("glossary_btn")
            )

            AssistChip(
                onClick = onOpenExportSheet,
                label = { Text("Export / Copy") },
                leadingIcon = { Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(16.dp)) },
                modifier = Modifier.testTag("export_copy_btn")
            )

            Button(
                onClick = onOpenBurnInSheet,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                modifier = Modifier.testTag("video_par_lagao_btn")
            ) {
                Icon(Icons.Filled.MovieCreation, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Video Par Lagao")
            }
        }

        // One-line Pivot Notice + Style Switcher
        if (usedPivotAny) {
            Text(
                text = "Seedha offline pair na hone ki wajah se English ke through translate kiya gaya hai.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.padding(vertical = 2.dp)
            )
        }

        // Search & Replace Bar
        AnimatedVisibility(visible = showSearchBar) {
            SearchReplaceBar(
                onReplace = onSearchReplace
            )
        }

        // Table Column Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp, horizontal = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "ORIGINAL (${LanguageCatalog.displayName(project.detectedLangCode ?: project.sourceLangCode)})",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "TRANSLATION (${LanguageCatalog.displayName(activeTargetLang)})",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.weight(1f)
            )
        }

        // Side-by-Side Table
        if (segments.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Abhi koi lines nahi hain.", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = {
                            onAddRowAfter(-1, 0L, 2000L, "New text", "Naya translation")
                        }
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Pehli Line Jodein")
                    }
                }
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                itemsIndexed(segments, key = { _, seg -> seg.id }) { idx, seg ->
                    SideBySideSegmentCard(
                        index = idx + 1,
                        segment = seg,
                        isActive = idx == activeIndex,
                        showRoman = project.showRomanScript,
                        onTapTimestamp = { seekRequestMs = seg.startTimeMs },
                        onTapRow = { editingSegment = seg },
                        onRetranslate = { onRetranslateRow(seg) }
                    )
                }
            }
        }
    }

    editingSegment?.let { seg ->
        RowEditBottomSheet(
            segment = seg,
            canMergeNext = segments.indexOfFirst { it.id == seg.id } in 0 until segments.lastIndex,
            onSave = { orig, trans, startMs, endMs, retranslate ->
                onSaveRowEdit(seg, orig, trans, startMs, endMs, retranslate)
                editingSegment = null
            },
            onAddAfter = {
                onAddRowAfter(seg.orderIndex, seg.endTimeMs, seg.endTimeMs + 2000L, "New line", "Nayi line")
                editingSegment = null
            },
            onDelete = {
                onDeleteRow(seg.id)
                editingSegment = null
            },
            onMergeNext = {
                onMergeWithNext(seg.id)
                editingSegment = null
            },
            onSplit = {
                onSplitRow(seg.id)
                editingSegment = null
            },
            onAlwaysReplaceRule = onAddAlwaysReplaceRule,
            onDismiss = { editingSegment = null }
        )
    }

    if (showAddLangPicker) {
        LanguagePickerSheet(
            titleHinglish = "Nayi Target Bhasha Jodein",
            includeAutoDetect = false,
            multiSelect = false,
            selectedCodes = targetLangs.toSet(),
            recentCodes = emptyList(),
            packs = packs,
            isTargetPicker = true,
            onSelect = onAddTargetLanguage,
            onDownloadPack = onDownloadPack,
            onDismiss = { showAddLangPicker = false }
        )
    }

    if (showFixSourcePicker) {
        LanguagePickerSheet(
            titleHinglish = "Sahi Original Bhasha Chuno",
            includeAutoDetect = false,
            multiSelect = false,
            selectedCodes = setOf(project.detectedLangCode ?: project.sourceLangCode),
            recentCodes = emptyList(),
            packs = packs,
            isTargetPicker = false,
            onSelect = onChangeSourceAndRetranslate,
            onDownloadPack = onDownloadPack,
            onDismiss = { showFixSourcePicker = false }
        )
    }
}

@Composable
private fun SideBySideSegmentCard(
    index: Int,
    segment: SegmentWithTranslation,
    isActive: Boolean,
    showRoman: Boolean,
    onTapTimestamp: () -> Unit,
    onTapRow: () -> Unit,
    onRetranslate: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (isActive) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f) else MaterialTheme.colorScheme.surfaceContainer,
        border = if (isActive) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else BorderStroke(0.8.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("segment_row_$index")
    ) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // Top Header: No., Timing (tap to seek), Quick actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
                    onClick = onTapTimestamp,
                    modifier = Modifier.testTag("seek_row_$index")
                ) {
                    Text(
                        text = "#$index  •  ${TimeFormatters.formatShort(segment.startTimeMs)} -> ${TimeFormatters.formatShort(segment.endTimeMs)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isActive) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (segment.unsureWordIndices.isNotEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.errorContainer
                        ) {
                            Text(
                                text = "Check Karein",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                        Spacer(Modifier.width(4.dp))
                    }
                    IconButton(
                        onClick = onRetranslate,
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            Icons.Filled.Refresh,
                            contentDescription = "Dobara translate karo",
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(
                        onClick = onTapRow,
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            Icons.Filled.Edit,
                            contentDescription = "Edit line",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // TWO SIDE-BY-SIDE BOXES: LEFT = Original, RIGHT = Translation + Roman
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // LEFT BOX (Original)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerLow)
                        .clickable(onClick = onTapRow)
                        .padding(10.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = LanguageCatalog.displayName(segment.detectedLangCode),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        HighlightedOriginalText(
                            text = segment.originalText,
                            unsureIndices = segment.unsureWordIndices
                        )
                    }
                }

                // RIGHT BOX (Translation + Optional Roman)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .clickable(onClick = onTapRow)
                        .padding(10.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = segment.translatedText.ifBlank { "—" },
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (showRoman && !segment.romanText.isNullOrBlank()) {
                            Text(
                                text = segment.romanText,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HighlightedOriginalText(
    text: String,
    unsureIndices: Set<Int>
) {
    if (unsureIndices.isEmpty()) {
        Text(text = text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
        return
    }
    val words = text.split(Regex("\\s+"))
    val warnBg = MaterialTheme.colorScheme.errorContainer
    val warnText = MaterialTheme.colorScheme.error
    val annotated = buildAnnotatedString {
        words.forEachIndexed { idx, w ->
            if (idx > 0) append(" ")
            if (unsureIndices.contains(idx)) {
                withStyle(
                    SpanStyle(
                        background = warnBg,
                        color = warnText,
                        fontWeight = FontWeight.SemiBold,
                        textDecoration = TextDecoration.Underline
                    )
                ) {
                    append(w)
                }
            } else {
                append(w)
            }
        }
    }
    Text(text = annotated, style = MaterialTheme.typography.bodyMedium)
}

@Composable
private fun SearchReplaceBar(
    onReplace: (find: String, replace: String, inOrig: Boolean, inTrans: Boolean) -> Unit
) {
    var findText by remember { mutableStateOf("") }
    var replaceText by remember { mutableStateOf("") }
    var inOrig by remember { mutableStateOf(true) }
    var inTrans by remember { mutableStateOf(true) }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
    ) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = findText,
                    onValueChange = { findText = it },
                    label = { Text("Khojein") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = replaceText,
                    onValueChange = { replaceText = it },
                    label = { Text("Badlein") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = inOrig, onCheckedChange = { inOrig = it })
                    Text("Original", style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.width(6.dp))
                    Checkbox(checked = inTrans, onCheckedChange = { inTrans = it })
                    Text("Translation", style = MaterialTheme.typography.bodySmall)
                }
                Button(
                    onClick = { onReplace(findText, replaceText, inOrig, inTrans) },
                    enabled = findText.isNotBlank()
                ) {
                    Text("Sab Badlo")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun RowEditBottomSheet(
    segment: SegmentWithTranslation,
    canMergeNext: Boolean,
    onSave: (orig: String, trans: String, startMs: Long, endMs: Long, retranslate: Boolean) -> Unit,
    onAddAfter: () -> Unit,
    onDelete: () -> Unit,
    onMergeNext: () -> Unit,
    onSplit: () -> Unit,
    onAlwaysReplaceRule: (src: String, tgt: String) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var origText by remember(segment.id) { mutableStateOf(segment.originalText) }
    var transText by remember(segment.id) { mutableStateOf(segment.translatedText) }
    var startStr by remember(segment.id) { mutableStateOf(TimeFormatters.formatShort(segment.startTimeMs)) }
    var endStr by remember(segment.id) { mutableStateOf(TimeFormatters.formatShort(segment.endTimeMs)) }

    var ruleSource by remember { mutableStateOf("") }
    var ruleTarget by remember { mutableStateOf("") }

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
            Text("Line Edit Karein", style = MaterialTheme.typography.headlineSmall)

            OutlinedTextField(
                value = origText,
                onValueChange = { origText = it },
                label = { Text("Original Text") },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("edit_original_input")
            )

            Button(
                onClick = {
                    val s = TimeFormatters.parseShortTimeToMs(startStr) ?: segment.startTimeMs
                    val e = TimeFormatters.parseShortTimeToMs(endStr) ?: segment.endTimeMs
                    onSave(origText, transText, s, e, true)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("dobara_translate_karo_btn")
            ) {
                Icon(Icons.Filled.AutoFixHigh, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Dobara Translate Karo")
            }

            OutlinedTextField(
                value = transText,
                onValueChange = { transText = it },
                label = { Text("Translated Text") },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("edit_translated_input")
            )

            // Timing adjustment with typing + nudge buttons
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1f)) {
                    OutlinedTextField(
                        value = startStr,
                        onValueChange = { startStr = it },
                        label = { Text("Start (mm:ss.S)") },
                        singleLine = true
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(onClick = {
                            val cur = TimeFormatters.parseShortTimeToMs(startStr) ?: segment.startTimeMs
                            startStr = TimeFormatters.formatShort((cur - 500L).coerceAtLeast(0L))
                        }) { Text("-0.5s") }
                        TextButton(onClick = {
                            val cur = TimeFormatters.parseShortTimeToMs(startStr) ?: segment.startTimeMs
                            startStr = TimeFormatters.formatShort(cur + 500L)
                        }) { Text("+0.5s") }
                    }
                }
                Column(Modifier.weight(1f)) {
                    OutlinedTextField(
                        value = endStr,
                        onValueChange = { endStr = it },
                        label = { Text("End (mm:ss.S)") },
                        singleLine = true
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(onClick = {
                            val cur = TimeFormatters.parseShortTimeToMs(endStr) ?: segment.endTimeMs
                            endStr = TimeFormatters.formatShort((cur - 500L).coerceAtLeast(200L))
                        }) { Text("-0.5s") }
                        TextButton(onClick = {
                            val cur = TimeFormatters.parseShortTimeToMs(endStr) ?: segment.endTimeMs
                            endStr = TimeFormatters.formatShort(cur + 500L)
                        }) { Text("+0.5s") }
                    }
                }
            }

            // One-tap "Is word ko hamesha aise badlo"
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Is word ko hamesha aise badlo (Meri Words List)",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        origText.split(Regex("\\s+")).filter { it.length >= 2 }.take(8).forEach { w ->
                            FilterChip(
                                selected = ruleSource == w,
                                onClick = { ruleSource = w },
                                label = { Text(w) }
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = ruleSource,
                            onValueChange = { ruleSource = it },
                            label = { Text("Original Word") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = ruleTarget,
                            onValueChange = { ruleTarget = it },
                            label = { Text("Hamesha Likho") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    OutlinedButton(
                        onClick = {
                            onAlwaysReplaceRule(ruleSource, ruleTarget)
                            ruleSource = ""
                            ruleTarget = ""
                        },
                        enabled = ruleSource.isNotBlank() && ruleTarget.isNotBlank(),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.BookmarkAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Hamesha Rule Save Karein")
                    }
                }
            }

            // Row Structural Operations: Add / Merge / Split / Delete
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                OutlinedButton(onClick = onAddAfter) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Nayi Line")
                }
                if (canMergeNext) {
                    OutlinedButton(onClick = onMergeNext) {
                        Icon(Icons.Filled.CallMerge, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Agli Se Jodo")
                    }
                }
                OutlinedButton(onClick = onSplit) {
                    Icon(Icons.Filled.CallSplit, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Split Karo")
                }
                OutlinedButton(onClick = onDelete) {
                    Icon(Icons.Filled.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            }

            Button(
                onClick = {
                    val s = TimeFormatters.parseShortTimeToMs(startStr) ?: segment.startTimeMs
                    val e = TimeFormatters.parseShortTimeToMs(endStr) ?: segment.endTimeMs
                    onSave(origText, transText, s, e, false)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("save_row_edit_btn")
            ) {
                Text("Badlav Save Karein")
            }
        }
    }
}

package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.domain.model.ProjectStatus
import com.example.ui.MainViewModel

@Composable
fun WizardWorkspaceScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val wizardStep by viewModel.wizardStep.collectAsStateWithLifecycle()
    val activeProject by viewModel.activeProject.collectAsStateWithLifecycle()
    val activeTargetLang by viewModel.activeTargetLang.collectAsStateWithLifecycle()
    val segments by viewModel.segments.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val allProjects by viewModel.allProjects.collectAsStateWithLifecycle()
    val allPacks by viewModel.allPacks.collectAsStateWithLifecycle()
    val glossaryRules by viewModel.glossaryRules.collectAsStateWithLifecycle()
    val previewFrame by viewModel.previewFrame.collectAsStateWithLifecycle()
    val quickTestResult by viewModel.quickTestResult.collectAsStateWithLifecycle()
    val isTestingLanguage by viewModel.isTestingLanguage.collectAsStateWithLifecycle()
    val processingProgress by viewModel.processingProgress.collectAsStateWithLifecycle()
    val burnInProgress by viewModel.burnInProgress.collectAsStateWithLifecycle()
    val canUndo by viewModel.canUndo.collectAsStateWithLifecycle()
    val canRedo by viewModel.canRedo.collectAsStateWithLifecycle()

    var showExportSheet by remember { mutableStateOf(false) }
    var showBurnInSheet by remember { mutableStateOf(false) }
    var showGlossaryDialog by remember { mutableStateOf(false) }

    // Automatically jump to Result screen when background processing completes
    LaunchedEffect(activeProject?.status, segments.size) {
        if (wizardStep == 3 &&
            activeProject?.status == ProjectStatus.READY.name &&
            segments.isNotEmpty() &&
            !processingProgress.isRunning
        ) {
            viewModel.setWizardStep(4)
        }
    }

    // Handle system back cleanly across wizard substeps
    BackHandler(enabled = wizardStep > 1) {
        viewModel.setWizardStep(wizardStep - 1)
    }

    Column(modifier = modifier.fillMaxSize()) {
        // Step Indicator Bar
        StepIndicatorHeader(
            currentStep = wizardStep,
            hasProject = activeProject != null,
            hasResults = segments.isNotEmpty() || activeProject?.status == ProjectStatus.READY.name,
            onStepSelected = { step ->
                if (step == 1 || (activeProject != null && (step <= 3 || segments.isNotEmpty()))) {
                    viewModel.setWizardStep(step)
                }
            },
            onBackClick = if (wizardStep > 1) {
                { viewModel.setWizardStep(wizardStep - 1) }
            } else null
        )

        val proj = activeProject
        when (wizardStep) {
            1 -> Step1VideoPickerScreen(
                activeProject = proj,
                recentProjects = allProjects,
                onVideoPicked = { uri -> viewModel.onVideoPicked(uri) },
                onOpenProject = { id -> viewModel.openProject(id) },
                onNextStep = { viewModel.setWizardStep(2) },
                modifier = Modifier.weight(1f)
            )
            2 -> if (proj != null) {
                Step2LanguageScreen(
                    project = proj,
                    recentTargetLangs = settings.recentTargetLangs,
                    packs = allPacks,
                    onSourceSelected = { viewModel.updateSourceLanguage(it) },
                    onToggleTarget = { code, multi -> viewModel.toggleTargetLanguage(code, multi) },
                    onSwapLanguages = { viewModel.swapLanguages() },
                    onToggleRoman = { viewModel.setShowRomanScript(it) },
                    onStyleSelected = { viewModel.setTranslationStyle(it) },
                    onDownloadPack = { viewModel.startOrResumePack(it) },
                    onNextStep = { viewModel.setWizardStep(3) },
                    modifier = Modifier.weight(1f)
                )
            } else {
                viewModel.setWizardStep(1)
            }
            3 -> if (proj != null) {
                Step3ExtractSetupScreen(
                    project = proj,
                    previewBitmap = previewFrame,
                    quickTestResult = quickTestResult,
                    isTestingLanguage = isTestingLanguage,
                    processingProgress = processingProgress,
                    onSelectSource = { viewModel.setExtractionSource(it) },
                    onCropChanged = { viewModel.setCropRect(it) },
                    onRunQuickTest = { viewModel.runQuickLanguageTest() },
                    onStartProcessing = { resume -> viewModel.startFullVideoProcessing(resume) },
                    onCancelProcessing = { viewModel.cancelVideoProcessing() },
                    onOpenResults = { viewModel.setWizardStep(4) },
                    modifier = Modifier.weight(1f)
                )
            } else {
                viewModel.setWizardStep(1)
            }
            4 -> if (proj != null) {
                ResultEditorScreen(
                    project = proj,
                    activeTargetLang = activeTargetLang,
                    segments = segments,
                    packs = allPacks,
                    canUndo = canUndo,
                    canRedo = canRedo,
                    onSelectTargetTab = { viewModel.selectActiveTargetTab(it) },
                    onAddTargetLanguage = { viewModel.addTargetLanguageToExistingProject(it) },
                    onChangeSourceAndRetranslate = { newSrc ->
                        viewModel.retranslateAllWithUpdatedSourceOrStyle(newSourceCode = newSrc)
                    },
                    onChangeStyleAndRetranslate = { newStyle ->
                        viewModel.retranslateAllWithUpdatedSourceOrStyle(newStyle = newStyle)
                    },
                    onUndo = { viewModel.undo() },
                    onRedo = { viewModel.redo() },
                    onSaveRowEdit = { seg, orig, trans, s, e, retranslate ->
                        viewModel.updateSegmentAndMaybeRetranslate(seg, orig, trans, s, e, retranslate)
                    },
                    onRetranslateRow = { viewModel.retranslateSingleRow(it) },
                    onAddRowAfter = { idx, s, e, o, t -> viewModel.addRowAfter(idx, s, e, o, t) },
                    onDeleteRow = { viewModel.deleteRow(it) },
                    onMergeWithNext = { viewModel.mergeWithNext(it) },
                    onSplitRow = { viewModel.splitRow(it) },
                    onSearchReplace = { f, r, io, it -> viewModel.searchAndReplace(f, r, io, it) },
                    onAddAlwaysReplaceRule = { src, tgt -> viewModel.addAlwaysReplaceRule(src, tgt) },
                    onDownloadPack = { viewModel.startOrResumePack(it) },
                    onOpenExportSheet = { showExportSheet = true },
                    onOpenBurnInSheet = { showBurnInSheet = true },
                    onOpenGlossarySheet = { showGlossaryDialog = true },
                    modifier = Modifier.weight(1f)
                )
            } else {
                viewModel.setWizardStep(1)
            }
        }
    }

    if (showExportSheet && activeProject != null) {
        ExportAndCopySheet(
            projectTitle = activeProject!!.title,
            onCopyOriginal = { viewModel.copyOriginal(it) },
            onCopyTranslated = { viewModel.copyTranslated(it) },
            onCopySideBySide = { viewModel.copySideBySide(it) },
            onShareFormat = { fmt, cb -> viewModel.exportAndShare(fmt, cb) },
            onSaveToUri = { uri, fmt -> viewModel.exportToPickedUri(uri, fmt) },
            onDismiss = { showExportSheet = false }
        )
    }

    if (showBurnInSheet && activeProject != null) {
        VideoBurnInSheet(
            defaultStyle = settings.defaultCaptionStyle,
            burnInProgress = burnInProgress,
            onStartBurnIn = { viewModel.startVideoBurnIn(it) },
            onCancelBurnIn = { viewModel.cancelVideoBurnIn() },
            onShareExportedVideo = { path, cb -> viewModel.shareFilePath(path, "video/mp4", cb) },
            onDismiss = { showBurnInSheet = false }
        )
    }

    if (showGlossaryDialog) {
        GlossaryDialog(
            rules = glossaryRules,
            onAddRule = { s, t -> viewModel.addAlwaysReplaceRule(s, t) },
            onDeleteRule = { viewModel.deleteGlossaryRule(it) },
            onDismiss = { showGlossaryDialog = false }
        )
    }
}

@Composable
private fun StepIndicatorHeader(
    currentStep: Int,
    hasProject: Boolean,
    hasResults: Boolean,
    onStepSelected: (Int) -> Unit,
    onBackClick: (() -> Unit)?
) {
    val steps = listOf(
        1 to "Video",
        2 to "Bhasha",
        3 to "Nikalo",
        4 to "Result"
    )

    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (onBackClick != null) {
                IconButton(
                    onClick = onBackClick,
                    modifier = Modifier
                        .size(38.dp)
                        .testTag("wizard_back_btn")
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Peeche Jayein")
                }
            } else {
                Spacer(Modifier.width(8.dp))
            }

            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                steps.forEach { (stepNum, label) ->
                    val enabled = stepNum == 1 || (hasProject && (stepNum <= 3 || hasResults))
                    val active = stepNum == currentStep
                    val completed = stepNum < currentStep || (stepNum == 4 && hasResults && currentStep != 4)

                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .clickable(enabled = enabled) { onStepSelected(stepNum) }
                            .padding(horizontal = 6.dp, vertical = 4.dp)
                            .testTag("step_tab_$stepNum"),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(22.dp)
                                .clip(CircleShape)
                                .background(
                                    when {
                                        active -> MaterialTheme.colorScheme.primary
                                        completed -> MaterialTheme.colorScheme.tertiary
                                        else -> MaterialTheme.colorScheme.surfaceContainerHighest
                                    }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (completed && !active) {
                                Icon(
                                    Icons.Filled.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onTertiary,
                                    modifier = Modifier.size(14.dp)
                                )
                            } else {
                                Text(
                                    text = stepNum.toString(),
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = if (active) FontWeight.Bold else FontWeight.Medium
                            ),
                            color = when {
                                active -> MaterialTheme.colorScheme.primary
                                enabled -> MaterialTheme.colorScheme.onSurface
                                else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

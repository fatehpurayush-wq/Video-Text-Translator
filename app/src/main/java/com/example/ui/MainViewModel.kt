package com.example.ui

import android.app.Application
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.example.VideoTextTranslatorApp
import com.example.data.local.GlossaryRuleEntity
import com.example.data.local.LanguagePackEntity
import com.example.data.local.ProjectEntity
import com.example.data.local.UserSettings
import com.example.domain.model.AccuracyRating
import com.example.domain.model.AppThemeMode
import com.example.domain.model.BurnInProgress
import com.example.domain.model.CaptionStyleConfig
import com.example.domain.model.ExportFormat
import com.example.domain.model.ExtractionSource
import com.example.domain.model.LanguageCatalog
import com.example.domain.model.NormalizedRect
import com.example.domain.model.ProcessingProgress
import com.example.domain.model.ProjectStatus
import com.example.domain.model.QuickTestResult
import com.example.domain.model.SegmentWithTranslation
import com.example.domain.model.TranslationStyle
import com.example.domain.model.UndoSnapshot
import com.example.worker.VideoBurnInWorker
import com.example.worker.VideoProcessingWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as VideoTextTranslatorApp
    private val container = app.container
    private val workManager = WorkManager.getInstance(application)
    private val json = Json { ignoreUnknownKeys = true }

    val settings: StateFlow<UserSettings> = container.settingsDataStore.settingsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UserSettings())

    val allProjects: StateFlow<List<ProjectEntity>> = container.projectRepository.allProjectsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allPacks: StateFlow<List<LanguagePackEntity>> = container.languagePackRepository.allPacksFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val glossaryRules: StateFlow<List<GlossaryRuleEntity>> = container.projectRepository.allGlossaryFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _activeProjectId = MutableStateFlow<Long?>(null)
    val activeProjectId: StateFlow<Long?> = _activeProjectId.asStateFlow()

    val activeProject: StateFlow<ProjectEntity?> = _activeProjectId
        .flatMapLatest { id ->
            if (id == null) flowOf(null) else container.projectRepository.observeProject(id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _activeTargetLangOverride = MutableStateFlow<String?>(null)
    val activeTargetLang: StateFlow<String> = combine(activeProject, _activeTargetLangOverride) { proj, override ->
        override ?: proj?.activeTargetLangCode ?: "hi"
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "hi")

    val segments: StateFlow<List<SegmentWithTranslation>> =
        combine(_activeProjectId, activeTargetLang) { id, tgt -> id to tgt }
            .flatMapLatest { (id, tgt) ->
                if (id == null) flowOf(emptyList())
                else container.projectRepository.observeSegmentsWithTranslation(id, tgt)
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _wizardStep = MutableStateFlow(1)
    val wizardStep: StateFlow<Int> = _wizardStep.asStateFlow()

    private val _previewFrame = MutableStateFlow<Bitmap?>(null)
    val previewFrame: StateFlow<Bitmap?> = _previewFrame.asStateFlow()

    private val _quickTestResult = MutableStateFlow<QuickTestResult?>(null)
    val quickTestResult: StateFlow<QuickTestResult?> = _quickTestResult.asStateFlow()

    private val _isTestingLanguage = MutableStateFlow(false)
    val isTestingLanguage: StateFlow<Boolean> = _isTestingLanguage.asStateFlow()

    private val _bannerMessage = MutableStateFlow<String?>(null)
    val bannerMessage: StateFlow<String?> = _bannerMessage.asStateFlow()

    private val _undoStack = MutableStateFlow<List<UndoSnapshot>>(emptyList())
    private val _redoStack = MutableStateFlow<List<UndoSnapshot>>(emptyList())
    val canUndo: StateFlow<Boolean> = _undoStack.map { it.isNotEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val canRedo: StateFlow<Boolean> = _redoStack.map { it.isNotEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val processingProgress: StateFlow<ProcessingProgress> = _activeProjectId
        .flatMapLatest { id ->
            if (id == null) flowOf(emptyList())
            else workManager.getWorkInfosForUniqueWorkFlow("process_video_$id")
        }
        .map { infos ->
            val info = infos.firstOrNull() ?: return@map ProcessingProgress()
            when (info.state) {
                WorkInfo.State.RUNNING, WorkInfo.State.ENQUEUED -> {
                    val data = info.progress
                    ProcessingProgress(
                        isRunning = true,
                        isPaused = false,
                        progressFraction = data.getFloat(VideoProcessingWorker.KEY_PROGRESS, 0f),
                        processedMs = data.getLong(VideoProcessingWorker.KEY_PROCESSED_MS, 0L),
                        totalMs = data.getLong(VideoProcessingWorker.KEY_TOTAL_MS, 0L),
                        etaSeconds = data.getLong(VideoProcessingWorker.KEY_ETA_SECONDS, -1L),
                        stageLabelHinglish = data.getString(VideoProcessingWorker.KEY_STAGE) ?: "Process chal raha hai...",
                        foundLinesCount = data.getInt(VideoProcessingWorker.KEY_FOUND_COUNT, 0)
                    )
                }
                WorkInfo.State.CANCELLED -> ProcessingProgress(isRunning = false, isPaused = true)
                WorkInfo.State.FAILED -> {
                    val err = info.outputData.getString(VideoProcessingWorker.KEY_ERROR)
                        ?: "Video process karne mein dikkat aayi."
                    ProcessingProgress(isRunning = false, errorMessageHinglish = err)
                }
                else -> ProcessingProgress()
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ProcessingProgress())

    val burnInProgress: StateFlow<BurnInProgress> = _activeProjectId
        .flatMapLatest { id ->
            if (id == null) flowOf(emptyList())
            else workManager.getWorkInfosForUniqueWorkFlow("burn_video_$id")
        }
        .map { infos ->
            val info = infos.firstOrNull() ?: return@map BurnInProgress()
            when (info.state) {
                WorkInfo.State.RUNNING, WorkInfo.State.ENQUEUED -> {
                    val data = info.progress
                    BurnInProgress(
                        isRunning = true,
                        progressFraction = data.getFloat(VideoBurnInWorker.KEY_PROGRESS, 0f),
                        etaSeconds = data.getLong(VideoBurnInWorker.KEY_ETA_SECONDS, -1L)
                    )
                }
                WorkInfo.State.SUCCEEDED -> {
                    BurnInProgress(
                        isRunning = false,
                        progressFraction = 1f,
                        outputFilePath = info.outputData.getString(VideoBurnInWorker.KEY_OUTPUT_PATH)
                    )
                }
                WorkInfo.State.FAILED -> {
                    BurnInProgress(
                        isRunning = false,
                        errorMessageHinglish = info.outputData.getString(VideoBurnInWorker.KEY_ERROR)
                            ?: "Video export fail ho gaya."
                    )
                }
                else -> BurnInProgress()
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), BurnInProgress())

    fun clearBanner() {
        _bannerMessage.value = null
    }

    fun showBanner(msg: String) {
        _bannerMessage.value = msg
    }

    fun setWizardStep(step: Int) {
        _wizardStep.value = step.coerceIn(1, 4)
    }

    fun onVideoPicked(uri: Uri) {
        viewModelScope.launch {
            runCatching {
                app.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            try {
                val now = System.currentTimeMillis()
                val meta = container.videoMetadataHelper.extractMetadataAndThumbnail(uri, now)
                val prefs = settings.value
                val project = ProjectEntity(
                    title = meta.title,
                    videoUri = uri.toString(),
                    thumbnailPath = meta.thumbnailPath,
                    durationMs = meta.durationMs,
                    videoWidth = meta.width,
                    videoHeight = meta.height,
                    sourceLangCode = prefs.defaultSourceLang,
                    isAutoDetectSource = prefs.defaultSourceLang == "auto",
                    targetLangCodesCsv = prefs.defaultTargetLang,
                    activeTargetLangCode = prefs.defaultTargetLang,
                    extractionSource = ExtractionSource.VIDEO_TEXT.name,
                    cropRectJson = json.encodeToString(NormalizedRect.FULL_FRAME),
                    translationStyle = TranslationStyle.SIMPLE.name,
                    showRomanScript = true,
                    status = ProjectStatus.DRAFT.name
                )
                val id = container.projectRepository.createOrUpdateProject(project)
                _activeProjectId.value = id
                _activeTargetLangOverride.value = prefs.defaultTargetLang
                _quickTestResult.value = null
                _undoStack.value = emptyList()
                _redoStack.value = emptyList()
                loadPreviewFrame(uri, meta.durationMs / 4L)
            } catch (e: Exception) {
                showBanner(e.message ?: "Yeh video format supported nahi hai.")
            }
        }
    }

    fun openProject(projectId: Long) {
        viewModelScope.launch {
            val proj = container.projectRepository.getProjectOnce(projectId) ?: return@launch
            _activeProjectId.value = proj.id
            _activeTargetLangOverride.value = proj.activeTargetLangCode
            _undoStack.value = emptyList()
            _redoStack.value = emptyList()
            _quickTestResult.value = null
            loadPreviewFrame(Uri.parse(proj.videoUri), proj.durationMs / 4L)
            val hasSegments = container.projectRepository.getSegmentsOnce(proj.id).isNotEmpty()
            _wizardStep.value = if (hasSegments || proj.status == ProjectStatus.READY.name) 4 else 2
        }
    }

    fun startNewProjectFlow() {
        _activeProjectId.value = null
        _previewFrame.value = null
        _quickTestResult.value = null
        _wizardStep.value = 1
    }

    fun loadPreviewFrame(uri: Uri, timeMs: Long) {
        viewModelScope.launch {
            val bmp = container.videoMetadataHelper.extractFrameAt(uri, timeMs, maxDimension = 960)
            _previewFrame.value = bmp
        }
    }

    fun updateSourceLanguage(code: String) {
        val proj = activeProject.value ?: return
        viewModelScope.launch {
            val updated = proj.copy(
                sourceLangCode = code,
                isAutoDetectSource = code == LanguageCatalog.AUTO_DETECT_CODE
            )
            container.projectRepository.createOrUpdateProject(updated)

            // If user changed source language on an existing project ("Galat hai? Badlo"), also update detectedLangCode
            if (code != LanguageCatalog.AUTO_DETECT_CODE) {
                val spec = LanguageCatalog.findByCode(code)
                if (spec?.ocrAccuracy == AccuracyRating.LOW) {
                    showBanner("${spec.englishName} ke liye OCR accuracy kamzor ho sakti hai. Saaf font wale video par behtar result aata hai.")
                }
            }
        }
    }

    fun toggleTargetLanguage(code: String, multiMode: Boolean) {
        val proj = activeProject.value ?: return
        viewModelScope.launch {
            val currentList = proj.targetLangCodesCsv.split(",").filter { it.isNotBlank() }.toMutableList()
            val updatedList = if (multiMode) {
                if (currentList.contains(code)) {
                    if (currentList.size > 1) currentList.apply { remove(code) } else currentList
                } else {
                    currentList.apply { add(code) }
                }
            } else {
                mutableListOf(code)
            }
            val newActive = if (updatedList.contains(proj.activeTargetLangCode)) proj.activeTargetLangCode else updatedList.first()
            container.projectRepository.createOrUpdateProject(
                proj.copy(
                    targetLangCodesCsv = updatedList.joinToString(","),
                    activeTargetLangCode = newActive
                )
            )
            _activeTargetLangOverride.value = newActive
            container.settingsDataStore.recordRecentTargetLang(code)
        }
    }

    fun selectActiveTargetTab(code: String) {
        _activeTargetLangOverride.value = code
        val proj = activeProject.value ?: return
        viewModelScope.launch {
            container.projectRepository.createOrUpdateProject(proj.copy(activeTargetLangCode = code))
        }
    }

    fun swapLanguages() {
        val proj = activeProject.value ?: return
        val currentSrc = if (proj.isAutoDetectSource) proj.detectedLangCode else proj.sourceLangCode
        if (currentSrc.isNullOrBlank() || currentSrc == "auto") {
            showBanner("Swap karne ke liye pehle Original Bhasha manually chunein.")
            return
        }
        val currentTgt = activeTargetLang.value
        viewModelScope.launch {
            container.projectRepository.createOrUpdateProject(
                proj.copy(
                    sourceLangCode = currentTgt,
                    isAutoDetectSource = false,
                    targetLangCodesCsv = currentSrc,
                    activeTargetLangCode = currentSrc
                )
            )
            _activeTargetLangOverride.value = currentSrc
        }
    }

    fun setShowRomanScript(show: Boolean) {
        val proj = activeProject.value ?: return
        viewModelScope.launch {
            container.projectRepository.createOrUpdateProject(proj.copy(showRomanScript = show))
        }
    }

    fun setTranslationStyle(style: TranslationStyle) {
        val proj = activeProject.value ?: return
        viewModelScope.launch {
            container.projectRepository.createOrUpdateProject(proj.copy(translationStyle = style.name))
        }
    }

    fun setExtractionSource(source: ExtractionSource) {
        val proj = activeProject.value ?: return
        viewModelScope.launch {
            container.projectRepository.createOrUpdateProject(proj.copy(extractionSource = source.name))
        }
    }

    fun setCropRect(rect: NormalizedRect) {
        val proj = activeProject.value ?: return
        viewModelScope.launch {
            container.projectRepository.createOrUpdateProject(
                proj.copy(cropRectJson = json.encodeToString(rect))
            )
        }
    }

    fun runQuickLanguageTest() {
        val proj = activeProject.value ?: return
        if (_isTestingLanguage.value) return
        _isTestingLanguage.value = true
        viewModelScope.launch {
            try {
                val frame = _previewFrame.value ?: container.videoMetadataHelper.extractFrameAt(
                    Uri.parse(proj.videoUri),
                    proj.durationMs / 4L,
                    maxDimension = 960
                )
                if (frame == null) {
                    _quickTestResult.value = QuickTestResult(
                        detectedLangCode = "en",
                        originalSample = "",
                        translatedSample = "",
                        romanSample = null,
                        usedPivotEnglish = false,
                        confidence = 0f,
                        warningHinglish = "Preview frame load nahi ho paya."
                    )
                    return@launch
                }
                _previewFrame.value = frame

                val crop = proj.cropRectJson?.let {
                    runCatching { json.decodeFromString<NormalizedRect>(it) }.getOrNull()
                }
                val srcCode = if (proj.isAutoDetectSource) "auto" else proj.sourceLangCode
                val ocr = container.ocrEngine.recognizeFrame(frame, crop, srcCode)

                val sampleText = ocr.text.ifBlank { "Sample check: Hello, welcome to this video!" }
                val effectiveSrc = if (ocr.text.isBlank()) "en" else ocr.detectedLangCode
                val tgt = activeTargetLang.value
                val style = runCatching { TranslationStyle.valueOf(proj.translationStyle) }
                    .getOrDefault(TranslationStyle.SIMPLE)
                val rules = glossaryRules.value

                val transOutcome = container.translationEngine.translateSingle(
                    text = sampleText,
                    sourceLangCode = effectiveSrc,
                    targetLangCode = tgt,
                    style = style,
                    includeRomanScript = proj.showRomanScript,
                    glossaryRules = rules
                )

                val warning = when {
                    ocr.text.isBlank() -> "Is frame par koi text nahi dikha (niche sample sentence test kiya gaya). 'Text area' check karein."
                    transOutcome.errorHinglish != null -> transOutcome.errorHinglish
                    LanguageCatalog.findByCode(effectiveSrc)?.ocrAccuracy == AccuracyRating.LOW ->
                        "${LanguageCatalog.displayName(effectiveSrc)} ke liye OCR accuracy low ho sakti hai."
                    else -> null
                }

                _quickTestResult.value = QuickTestResult(
                    detectedLangCode = effectiveSrc,
                    originalSample = sampleText,
                    translatedSample = transOutcome.translatedText,
                    romanSample = transOutcome.romanText,
                    usedPivotEnglish = transOutcome.usedPivotEnglish,
                    confidence = ocr.confidence,
                    warningHinglish = warning
                )
            } finally {
                _isTestingLanguage.value = false
            }
        }
    }

    fun startFullVideoProcessing(resume: Boolean = false) {
        val proj = activeProject.value ?: return
        val request = OneTimeWorkRequestBuilder<VideoProcessingWorker>()
            .setInputData(
                workDataOf(
                    VideoProcessingWorker.KEY_PROJECT_ID to proj.id,
                    VideoProcessingWorker.KEY_RESUME to resume
                )
            )
            .build()

        workManager.enqueueUniqueWork(
            "process_video_${proj.id}",
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    fun cancelVideoProcessing() {
        val projId = _activeProjectId.value ?: return
        workManager.cancelUniqueWork("process_video_$projId")
    }

    fun addTargetLanguageToExistingProject(newTargetCode: String) {
        val proj = activeProject.value ?: return
        viewModelScope.launch {
            val style = runCatching { TranslationStyle.valueOf(proj.translationStyle) }
                .getOrDefault(TranslationStyle.SIMPLE)
            val err = container.projectRepository.translateProjectSegmentsToLanguage(
                projectId = proj.id,
                targetLangCode = newTargetCode,
                style = style,
                includeRomanScript = proj.showRomanScript
            )
            if (err != null) {
                showBanner(err)
            } else {
                _activeTargetLangOverride.value = newTargetCode
                container.settingsDataStore.recordRecentTargetLang(newTargetCode)
                showBanner("${LanguageCatalog.displayName(newTargetCode)} bina video dobara padhe jodi gayi!")
            }
        }
    }

    fun retranslateAllWithUpdatedSourceOrStyle(newSourceCode: String? = null, newStyle: TranslationStyle? = null) {
        val proj = activeProject.value ?: return
        viewModelScope.launch {
            val updatedProj = proj.copy(
                sourceLangCode = newSourceCode ?: proj.sourceLangCode,
                detectedLangCode = newSourceCode ?: proj.detectedLangCode,
                isAutoDetectSource = if (newSourceCode != null) false else proj.isAutoDetectSource,
                translationStyle = newStyle?.name ?: proj.translationStyle
            )
            container.projectRepository.createOrUpdateProject(updatedProj)
            val style = newStyle ?: runCatching { TranslationStyle.valueOf(proj.translationStyle) }
                .getOrDefault(TranslationStyle.SIMPLE)
            val err = container.projectRepository.translateProjectSegmentsToLanguage(
                projectId = proj.id,
                targetLangCode = activeTargetLang.value,
                style = style,
                includeRomanScript = updatedProj.showRomanScript
            )
            if (err != null) showBanner(err)
            else showBanner("Saari lines dobara translate ho gayi!")
        }
    }

    // --- Undo / Redo & Segment Editing ---

    private fun pushUndoSnapshot(description: String) {
        val current = segments.value
        if (current.isEmpty()) return
        _undoStack.value = (_undoStack.value + UndoSnapshot(description, current)).takeLast(25)
        _redoStack.value = emptyList()
    }

    fun undo() {
        val projId = _activeProjectId.value ?: return
        val stack = _undoStack.value
        if (stack.isEmpty()) return
        val last = stack.last()
        val current = segments.value
        _undoStack.value = stack.dropLast(1)
        _redoStack.value = (_redoStack.value + UndoSnapshot(last.descriptionHinglish, current)).takeLast(25)
        viewModelScope.launch {
            container.projectRepository.restoreSnapshot(projId, activeTargetLang.value, last.segments)
        }
    }

    fun redo() {
        val projId = _activeProjectId.value ?: return
        val stack = _redoStack.value
        if (stack.isEmpty()) return
        val last = stack.last()
        val current = segments.value
        _redoStack.value = stack.dropLast(1)
        _undoStack.value = (_undoStack.value + UndoSnapshot(last.descriptionHinglish, current)).takeLast(25)
        viewModelScope.launch {
            container.projectRepository.restoreSnapshot(projId, activeTargetLang.value, last.segments)
        }
    }

    fun updateSegmentAndMaybeRetranslate(
        seg: SegmentWithTranslation,
        newOriginal: String,
        newTranslation: String,
        newStartMs: Long,
        newEndMs: Long,
        retranslateAfterSave: Boolean
    ) {
        val proj = activeProject.value ?: return
        pushUndoSnapshot("Row edit")
        viewModelScope.launch {
            val tgt = activeTargetLang.value
            val roman = if (proj.showRomanScript && LanguageCatalog.findByCode(tgt)?.supportsRomanScript == true) {
                com.example.engine.RomanTransliterator.transliterate(newTranslation, tgt)
            } else null

            container.projectRepository.updateSegmentContent(
                segmentId = seg.id,
                projectId = proj.id,
                targetLangCode = tgt,
                newOriginalText = newOriginal,
                newTranslatedText = newTranslation,
                newRomanText = roman,
                newStartMs = newStartMs,
                newEndMs = newEndMs
            )

            if (retranslateAfterSave) {
                val style = runCatching { TranslationStyle.valueOf(proj.translationStyle) }
                    .getOrDefault(TranslationStyle.SIMPLE)
                val src = if (proj.isAutoDetectSource) (proj.detectedLangCode ?: seg.detectedLangCode) else proj.sourceLangCode
                val err = container.projectRepository.retranslateSegment(
                    projectId = proj.id,
                    segmentId = seg.id,
                    sourceLangCode = src,
                    targetLangCode = tgt,
                    style = style,
                    includeRomanScript = proj.showRomanScript
                )
                if (err != null) showBanner(err)
            }
        }
    }

    fun retranslateSingleRow(seg: SegmentWithTranslation) {
        val proj = activeProject.value ?: return
        pushUndoSnapshot("Dobara translate")
        viewModelScope.launch {
            val style = runCatching { TranslationStyle.valueOf(proj.translationStyle) }
                .getOrDefault(TranslationStyle.SIMPLE)
            val src = if (proj.isAutoDetectSource) (proj.detectedLangCode ?: seg.detectedLangCode) else proj.sourceLangCode
            val err = container.projectRepository.retranslateSegment(
                projectId = proj.id,
                segmentId = seg.id,
                sourceLangCode = src,
                targetLangCode = activeTargetLang.value,
                style = style,
                includeRomanScript = proj.showRomanScript
            )
            if (err != null) showBanner(err)
        }
    }

    fun addRowAfter(afterOrderIndex: Int, startMs: Long, endMs: Long, orig: String, trans: String) {
        val proj = activeProject.value ?: return
        pushUndoSnapshot("Nayi line jodi")
        viewModelScope.launch {
            val src = if (proj.isAutoDetectSource) (proj.detectedLangCode ?: "en") else proj.sourceLangCode
            container.projectRepository.addRow(
                projectId = proj.id,
                afterOrderIndex = afterOrderIndex,
                startTimeMs = startMs,
                endTimeMs = endMs,
                detectedLangCode = src,
                targetLangCode = activeTargetLang.value,
                originalText = orig,
                translatedText = trans
            )
        }
    }

    fun deleteRow(segmentId: Long) {
        val projId = _activeProjectId.value ?: return
        pushUndoSnapshot("Line hatayi")
        viewModelScope.launch {
            container.projectRepository.deleteRow(projId, segmentId)
        }
    }

    fun mergeWithNext(segmentId: Long) {
        val projId = _activeProjectId.value ?: return
        pushUndoSnapshot("Lines jodi")
        viewModelScope.launch {
            container.projectRepository.mergeWithNextRow(projId, segmentId, activeTargetLang.value)
        }
    }

    fun splitRow(segmentId: Long) {
        val projId = _activeProjectId.value ?: return
        pushUndoSnapshot("Line todi")
        viewModelScope.launch {
            container.projectRepository.splitRow(projId, segmentId, activeTargetLang.value)
        }
    }

    fun searchAndReplace(findText: String, replaceText: String, inOriginal: Boolean, inTranslation: Boolean) {
        val projId = _activeProjectId.value ?: return
        pushUndoSnapshot("Search & replace")
        viewModelScope.launch {
            val count = container.projectRepository.searchAndReplace(
                projectId = projId,
                targetLangCode = activeTargetLang.value,
                findText = findText,
                replaceText = replaceText,
                inOriginal = inOriginal,
                inTranslation = inTranslation
            )
            showBanner(if (count > 0) "$count jagah badlav kiya gaya." else "Koi matching word nahi mila.")
        }
    }

    fun addAlwaysReplaceRule(sourceTerm: String, targetTerm: String, isNameOrBrand: Boolean = true) {
        viewModelScope.launch {
            container.projectRepository.addGlossaryRule(sourceTerm, targetTerm, "*", isNameOrBrand)
            showBanner("\"$sourceTerm\" -> \"$targetTerm\" rule save ho gaya!")
        }
    }

    fun deleteGlossaryRule(id: Long) {
        viewModelScope.launch {
            container.projectRepository.deleteGlossaryRule(id)
        }
    }

    // --- Export, Copy & Burn-In ---

    fun copyOriginal(includeTimestamps: Boolean) {
        val text = container.exportEngine.buildOriginalCopy(segments.value, includeTimestamps)
        container.exportEngine.copyToClipboard("Original Text", text)
        showBanner("Original text copy ho gaya!")
    }

    fun copyTranslated(includeTimestamps: Boolean) {
        val includeRoman = activeProject.value?.showRomanScript ?: true
        val text = container.exportEngine.buildTranslatedCopy(segments.value, includeTimestamps, includeRoman)
        container.exportEngine.copyToClipboard("Translated Text", text)
        showBanner("Translated text copy ho gaya!")
    }

    fun copySideBySide(includeTimestamps: Boolean) {
        val includeRoman = activeProject.value?.showRomanScript ?: true
        val text = container.exportEngine.buildSideBySideCopy(segments.value, includeTimestamps, includeRoman)
        container.exportEngine.copyToClipboard("Side by Side", text)
        showBanner("Side-by-side text copy ho gaya!")
    }

    fun exportAndShare(format: ExportFormat, onReadyToShare: (Intent) -> Unit) {
        val proj = activeProject.value ?: return
        viewModelScope.launch {
            try {
                val file = container.exportEngine.exportToCacheFile(
                    projectTitle = proj.title,
                    sourceLangCode = proj.detectedLangCode ?: proj.sourceLangCode,
                    targetLangCode = activeTargetLang.value,
                    segments = segments.value,
                    format = format
                )
                val uri = container.exportEngine.shareUriForFile(file)
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = format.mimeType
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                onReadyToShare(Intent.createChooser(intent, "Share karein (${format.extension.uppercase()})"))
            } catch (e: Exception) {
                showBanner("Export fail ho gaya: ${e.localizedMessage ?: "Storage check karein"}")
            }
        }
    }

    fun exportToPickedUri(uri: Uri, format: ExportFormat) {
        val proj = activeProject.value ?: return
        viewModelScope.launch {
            try {
                container.exportEngine.exportToUri(
                    uri = uri,
                    projectTitle = proj.title,
                    sourceLangCode = proj.detectedLangCode ?: proj.sourceLangCode,
                    targetLangCode = activeTargetLang.value,
                    segments = segments.value,
                    format = format
                )
                showBanner("${format.extension.uppercase()} file save ho gayi!")
            } catch (e: Exception) {
                showBanner("Export fail ho gaya: ${e.localizedMessage ?: "Dobara koshish karein"}")
            }
        }
    }

    fun startVideoBurnIn(style: CaptionStyleConfig) {
        val proj = activeProject.value ?: return
        val req = OneTimeWorkRequestBuilder<VideoBurnInWorker>()
            .setInputData(
                workDataOf(
                    VideoBurnInWorker.KEY_PROJECT_ID to proj.id,
                    VideoBurnInWorker.KEY_TARGET_LANG to activeTargetLang.value,
                    VideoBurnInWorker.KEY_STYLE_JSON to json.encodeToString(style)
                )
            )
            .build()
        workManager.enqueueUniqueWork(
            "burn_video_${proj.id}",
            ExistingWorkPolicy.REPLACE,
            req
        )
    }

    fun cancelVideoBurnIn() {
        val projId = _activeProjectId.value ?: return
        workManager.cancelUniqueWork("burn_video_$projId")
    }

    fun shareFilePath(path: String, mimeType: String = "video/mp4", onReady: (Intent) -> Unit) {
        val uri = container.exportEngine.shareUriForFile(File(path))
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        onReady(Intent.createChooser(intent, "Video Share Karein"))
    }

    // --- Projects Management ---

    fun renameProject(projectId: Long, newName: String) {
        viewModelScope.launch {
            container.projectRepository.renameProject(projectId, newName)
        }
    }

    fun duplicateProject(projectId: Long) {
        viewModelScope.launch {
            container.projectRepository.duplicateProject(projectId)
            showBanner("Project ki copy ban gayi!")
        }
    }

    fun deleteProject(projectId: Long) {
        viewModelScope.launch {
            if (_activeProjectId.value == projectId) _activeProjectId.value = null
            container.projectRepository.deleteProject(projectId)
        }
    }

    // --- Language Packs & Settings ---

    fun refreshLanguagePacks() {
        viewModelScope.launch {
            container.languagePackRepository.syncCatalogAndDiskState()
        }
    }

    fun startOrResumePack(packId: String) {
        container.languagePackRepository.startOrResumeDownload(packId)
    }

    fun pausePack(packId: String) {
        container.languagePackRepository.pauseDownload(packId)
    }

    fun deletePack(packId: String) {
        viewModelScope.launch {
            container.languagePackRepository.deletePack(packId)
        }
    }

    fun deleteAllPacks() {
        viewModelScope.launch {
            container.languagePackRepository.deleteAllDownloadedPacks()
            showBanner("Saare downloaded language packs delete ho gaye!")
        }
    }

    fun calculatePackStorageBytes(): Long =
        container.languagePackRepository.calculateDownloadedStorageBytes(allPacks.value)

    fun setWifiOnly(wifiOnly: Boolean) {
        viewModelScope.launch { container.settingsDataStore.setWifiOnlyDownloads(wifiOnly) }
    }

    fun setDefaultSourceLang(code: String) {
        viewModelScope.launch { container.settingsDataStore.setDefaultSourceLang(code) }
    }

    fun setDefaultTargetLang(code: String) {
        viewModelScope.launch { container.settingsDataStore.setDefaultTargetLang(code) }
    }

    fun setFrameSamplingInterval(ms: Long) {
        viewModelScope.launch { container.settingsDataStore.setFrameSamplingIntervalMs(ms) }
    }

    fun setMinTextLength(len: Int) {
        viewModelScope.launch { container.settingsDataStore.setMinTextLength(len) }
    }

    fun setThemeMode(mode: AppThemeMode) {
        viewModelScope.launch { container.settingsDataStore.setThemeMode(mode) }
    }

    fun saveDefaultCaptionStyle(style: CaptionStyleConfig) {
        viewModelScope.launch {
            container.settingsDataStore.setDefaultCaptionStyle(style)
            showBanner("Caption style defaults save ho gaye!")
        }
    }

    fun clearAppCache() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                app.cacheDir.deleteRecursively()
                File(app.filesDir, "exports").deleteRecursively()
            }
            showBanner("Cache aur temporary export files saaf ho gayi!")
        }
    }
}

package com.example.worker

import android.app.Notification
import android.content.Context
import android.content.pm.ServiceInfo
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.R
import com.example.VideoTextTranslatorApp
import com.example.data.local.SegmentEntity
import com.example.data.local.TranslationEntity
import com.example.domain.model.ExtractionSource
import com.example.domain.model.LanguageCatalog
import com.example.domain.model.NormalizedRect
import com.example.domain.model.ProjectStatus
import com.example.domain.model.TranslationStyle
import com.example.engine.ConsolidatedSegment
import com.example.engine.FrameConsolidator
import com.example.engine.RawOcrObservation
import com.example.engine.TimeFormatters
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.coroutines.coroutineContext
import kotlin.math.max
import kotlin.math.roundToInt

class VideoProcessingWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun doWork(): Result {
        val projectId = inputData.getLong(KEY_PROJECT_ID, 0L)
        val resumeFromCheckpoint = inputData.getBoolean(KEY_RESUME, false)
        if (projectId <= 0L) return Result.failure()

        val app = applicationContext as VideoTextTranslatorApp
        val container = app.container
        val db = container.database
        val projectDao = db.projectDao()
        val segmentDao = db.segmentDao()
        val translationDao = db.translationDao()
        val glossaryDao = db.glossaryDao()
        val settings = container.settingsDataStore.settingsFlow.first()

        val project = projectDao.getProjectById(projectId) ?: return Result.failure()
        val totalDurationMs = project.durationMs.coerceAtLeast(1000L)
        val startMs = if (resumeFromCheckpoint) project.lastProcessedMs.coerceIn(0L, totalDurationMs) else 0L
        val targetCodes = project.targetLangCodesCsv.split(",").map { it.trim() }.filter { it.isNotBlank() }
        val extractionSource = runCatching { ExtractionSource.valueOf(project.extractionSource) }
            .getOrDefault(ExtractionSource.VIDEO_TEXT)
        val style = runCatching { TranslationStyle.valueOf(project.translationStyle) }
            .getOrDefault(TranslationStyle.SIMPLE)
        val cropRect = project.cropRectJson?.let {
            runCatching { json.decodeFromString<NormalizedRect>(it) }.getOrNull()
        }

        // Pre-check required offline packs before starting heavy work
        if (!project.isAutoDetectSource && extractionSource != ExtractionSource.SPEECH) {
            if (!container.ocrEngine.isOcrAvailableForLanguage(project.sourceLangCode)) {
                val langName = LanguageCatalog.displayName(project.sourceLangCode)
                val msg = "$langName ka Text Padhne wala (OCR) Pack download nahi hai. Packs screen se download karein."
                projectDao.updateProcessingState(projectId, startMs, ProjectStatus.ERROR.name, msg)
                return Result.failure(workDataOf(KEY_ERROR to msg))
            }
        }
        if (extractionSource != ExtractionSource.VIDEO_TEXT) {
            val speechLang = if (project.isAutoDetectSource) "en" else project.sourceLangCode
            if (!container.speechEngine.isSpeechModelReady(speechLang)) {
                val langName = LanguageCatalog.displayName(speechLang)
                val msg = "$langName ka Bole Hue Shabd (Speech) Pack download nahi hai. Language Packs screen se download karein."
                projectDao.updateProcessingState(projectId, startMs, ProjectStatus.ERROR.name, msg)
                return Result.failure(workDataOf(KEY_ERROR to msg))
            }
        }

        projectDao.updateProcessingState(projectId, startMs, ProjectStatus.PROCESSING.name, null)
        setForeground(createForegroundInfo(project.title, 0, "Taiyari ho rahi hai..."))

        val workStartWallMs = System.currentTimeMillis()
        var currentProcessedMs = startMs

        return try {
            val allSegments = mutableListOf<ConsolidatedSegment>()

            // Keep previously extracted segments when resuming
            if (resumeFromCheckpoint && startMs > 0L) {
                val existing = segmentDao.getSegmentsOnce(projectId)
                existing.forEach { e ->
                    val box = e.boundingBoxJson?.let { runCatching { json.decodeFromString<NormalizedRect>(it) }.getOrNull() }
                    val unsure = e.unsureWordIndicesCsv.split(",").mapNotNull { it.toIntOrNull() }.toSet()
                    allSegments.add(
                        ConsolidatedSegment(
                            startTimeMs = e.startTimeMs,
                            endTimeMs = e.endTimeMs,
                            originalText = e.originalText,
                            detectedLangCode = e.detectedLangCode,
                            sourceType = e.sourceType,
                            confidence = e.confidence,
                            unsureWordIndices = unsure,
                            boundingBox = box
                        )
                    )
                }
            } else {
                segmentDao.deleteAllForProject(projectId)
            }

            // 1. OCR Extraction (if VIDEO_TEXT or BOTH)
            if (extractionSource == ExtractionSource.VIDEO_TEXT || extractionSource == ExtractionSource.BOTH) {
                val ocrObservations = mutableListOf<RawOcrObservation>()
                val intervalMs = settings.frameSamplingIntervalMs.coerceIn(350L, 2500L)
                val retriever = MediaMetadataRetriever()
                try {
                    retriever.setDataSource(applicationContext, Uri.parse(project.videoUri))
                    val option = if (intervalMs <= 600L) {
                        MediaMetadataRetriever.OPTION_CLOSEST
                    } else {
                        MediaMetadataRetriever.OPTION_CLOSEST_SYNC
                    }

                    var tMs = startMs
                    var frameCounter = 0
                    while (tMs <= totalDurationMs && coroutineContext.isActive) {
                        val rawFrame = retriever.getFrameAtTime(tMs * 1000L, option)
                        if (rawFrame != null) {
                            val scaled = container.videoMetadataHelper.scaleDownBitmap(rawFrame, 960)
                            if (scaled !== rawFrame) rawFrame.recycle()
                            try {
                                val ocr = container.ocrEngine.recognizeFrame(
                                    fullFrame = scaled,
                                    cropRect = cropRect,
                                    sourceLangCode = if (project.isAutoDetectSource) "auto" else project.sourceLangCode
                                )
                                if (ocr.text.length >= settings.minTextLength) {
                                    ocrObservations.add(
                                        RawOcrObservation(
                                            timestampMs = tMs,
                                            text = ocr.text,
                                            detectedLangCode = ocr.detectedLangCode,
                                            confidence = ocr.confidence,
                                            unsureWordIndices = ocr.unsureWordIndices,
                                            boundingBox = ocr.boundingBox
                                        )
                                    )
                                }
                            } finally {
                                if (!scaled.isRecycled) scaled.recycle()
                            }
                        }

                        currentProcessedMs = tMs
                        frameCounter++
                        if (frameCounter % 5 == 0 || tMs + intervalMs > totalDurationMs) {
                            val ocrShare = if (extractionSource == ExtractionSource.BOTH) 0.55f else 0.85f
                            val rawFrac = ((tMs - startMs).toFloat() / max(1L, totalDurationMs - startMs)).coerceIn(0f, 1f)
                            val overallFrac = rawFrac * ocrShare
                            val etaSec = estimateEtaSeconds(workStartWallMs, rawFrac / ocrShare)
                            val pct = (overallFrac * 100).roundToInt()

                            setForeground(
                                createForegroundInfo(
                                    project.title,
                                    pct,
                                    "Video text padha ja raha hai • ${TimeFormatters.formatEta(etaSec)}"
                                )
                            )
                            setProgress(
                                workDataOf(
                                    KEY_PROJECT_ID to projectId,
                                    KEY_PROGRESS to overallFrac,
                                    KEY_PROCESSED_MS to tMs,
                                    KEY_TOTAL_MS to totalDurationMs,
                                    KEY_ETA_SECONDS to etaSec,
                                    KEY_STAGE to "Video ke andar ka text padha ja raha hai...",
                                    KEY_FOUND_COUNT to ocrObservations.size
                                )
                            )
                        }
                        if (frameCounter % 20 == 0) {
                            projectDao.updateProcessingState(projectId, tMs, ProjectStatus.PROCESSING.name)
                        }
                        tMs += intervalMs
                    }
                } finally {
                    runCatching { retriever.release() }
                }

                val consolidatedOcr = FrameConsolidator.consolidate(
                    observations = ocrObservations,
                    frameIntervalMs = intervalMs,
                    minTextLength = settings.minTextLength
                )
                allSegments.addAll(consolidatedOcr)
            }

            // 2. Speech Extraction (if SPEECH or BOTH)
            if (extractionSource == ExtractionSource.SPEECH || extractionSource == ExtractionSource.BOTH) {
                val speechLang = if (project.isAutoDetectSource) "en" else project.sourceLangCode
                val speechStart = if (extractionSource == ExtractionSource.BOTH) 0L else startMs
                setProgress(
                    workDataOf(
                        KEY_PROJECT_ID to projectId,
                        KEY_PROGRESS to if (extractionSource == ExtractionSource.BOTH) 0.6f else 0.2f,
                        KEY_PROCESSED_MS to speechStart,
                        KEY_TOTAL_MS to totalDurationMs,
                        KEY_ETA_SECONDS to -1L,
                        KEY_STAGE to "Bole hue shabd sune ja rahe hain...",
                        KEY_FOUND_COUNT to allSegments.size
                    )
                )
                val speechSegments = container.speechEngine.transcribeVideoAudio(
                    videoUri = Uri.parse(project.videoUri),
                    langCode = speechLang,
                    startFromMs = speechStart
                ) { processedMs ->
                    currentProcessedMs = processedMs
                    val frac = (processedMs.toFloat() / totalDurationMs).coerceIn(0f, 1f)
                    val overall = if (extractionSource == ExtractionSource.BOTH) 0.55f + frac * 0.30f else frac * 0.85f
                    val eta = estimateEtaSeconds(workStartWallMs, overall)
                    setForeground(
                        createForegroundInfo(
                            project.title,
                            (overall * 100).roundToInt(),
                            "Bole hue shabd samjhe ja rahe hain • ${TimeFormatters.formatEta(eta)}"
                        )
                    )
                    setProgress(
                        workDataOf(
                            KEY_PROJECT_ID to projectId,
                            KEY_PROGRESS to overall,
                            KEY_PROCESSED_MS to processedMs,
                            KEY_TOTAL_MS to totalDurationMs,
                            KEY_ETA_SECONDS to eta,
                            KEY_STAGE to "Bole hue shabd samjhe ja rahe hain...",
                            KEY_FOUND_COUNT to allSegments.size
                        )
                    )
                }
                allSegments.addAll(speechSegments.filter { it.originalText.length >= settings.minTextLength })
            }

            val orderedSegments = allSegments.sortedBy { it.startTimeMs }
            if (orderedSegments.isEmpty()) {
                val msg = "Is video mein koi saaf text ya aawaz nahi mili. 'Text area chuno' ya 'Bole hue shabd' try karein."
                projectDao.updateProcessingState(projectId, totalDurationMs, ProjectStatus.ERROR.name, msg)
                return Result.failure(workDataOf(KEY_ERROR to msg))
            }

            // Detect dominant language for auto-detect projects
            val dominantLang = if (project.isAutoDetectSource) {
                orderedSegments
                    .groupingBy { it.detectedLangCode }
                    .eachCount()
                    .maxByOrNull { it.value }?.key ?: "en"
            } else {
                project.sourceLangCode
            }
            projectDao.updateDetectedLang(projectId, dominantLang)

            // Save extracted segments
            val entities = orderedSegments.mapIndexed { idx, seg ->
                SegmentEntity(
                    projectId = projectId,
                    orderIndex = idx,
                    startTimeMs = seg.startTimeMs,
                    endTimeMs = seg.endTimeMs,
                    originalText = seg.originalText,
                    detectedLangCode = seg.detectedLangCode,
                    sourceType = seg.sourceType,
                    confidence = seg.confidence,
                    unsureWordIndicesCsv = seg.unsureWordIndices.joinToString(","),
                    boundingBoxJson = seg.boundingBox?.let { json.encodeToString(it) },
                    isModified = false
                )
            }
            val insertedIds = segmentDao.replaceAllForProject(projectId, entities)

            // 3. Offline Translation across all target languages
            setForeground(createForegroundInfo(project.title, 88, "Offline translate ho raha hai..."))
            setProgress(
                workDataOf(
                    KEY_PROJECT_ID to projectId,
                    KEY_PROGRESS to 0.88f,
                    KEY_PROCESSED_MS to totalDurationMs,
                    KEY_TOTAL_MS to totalDurationMs,
                    KEY_ETA_SECONDS to 3L,
                    KEY_STAGE to "Sentences ka offline translation chal raha hai...",
                    KEY_FOUND_COUNT to orderedSegments.size
                )
            )

            val glossary = glossaryDao.getAllRulesOnce()
            val rawTexts = orderedSegments.map { it.originalText }
            var warningMsg: String? = null

            for (tgtCode in targetCodes) {
                val outcomes = container.translationEngine.translateBatch(
                    texts = rawTexts,
                    sourceLangCode = dominantLang,
                    targetLangCode = tgtCode,
                    style = style,
                    includeRomanScript = project.showRomanScript,
                    glossaryRules = glossary
                )
                val firstErr = outcomes.firstOrNull { it.errorHinglish != null }?.errorHinglish
                if (firstErr != null && warningMsg == null) {
                    warningMsg = firstErr
                }
                val transEntities = insertedIds.mapIndexed { idx, segId ->
                    val out = outcomes[idx]
                    TranslationEntity(
                        segmentId = segId,
                        projectId = projectId,
                        targetLangCode = tgtCode,
                        translatedText = out.translatedText,
                        romanText = out.romanText,
                        usedPivotEnglish = out.usedPivotEnglish,
                        styleApplied = style.name
                    )
                }
                translationDao.upsertTranslations(transEntities)
            }

            projectDao.updateProcessingState(
                projectId = projectId,
                processedMs = totalDurationMs,
                status = ProjectStatus.READY.name,
                errorMessage = warningMsg
            )
            Result.success(workDataOf(KEY_PROJECT_ID to projectId, KEY_ERROR to (warningMsg ?: "")))
        } catch (ce: CancellationException) {
            projectDao.updateProcessingState(
                projectId = projectId,
                processedMs = currentProcessedMs,
                status = ProjectStatus.PAUSED.name,
                errorMessage = "Kaam roka gaya hai. Aap wahin se resume kar sakte hain."
            )
            throw ce
        } catch (e: Exception) {
            val msg = e.localizedMessage ?: "Video process karte waqt problem aayi."
            projectDao.updateProcessingState(projectId, currentProcessedMs, ProjectStatus.ERROR.name, msg)
            Result.failure(workDataOf(KEY_ERROR to msg))
        }
    }

    private fun estimateEtaSeconds(startWallMs: Long, fraction: Float): Long {
        if (fraction <= 0.03f) return -1L
        val elapsedMs = System.currentTimeMillis() - startWallMs
        val totalEstMs = (elapsedMs / fraction).toLong()
        return ((totalEstMs - elapsedMs) / 1000L).coerceAtLeast(1L)
    }

    private fun createForegroundInfo(title: String, progressPct: Int, detail: String): ForegroundInfo {
        val cancelIntent = WorkManager.getInstance(applicationContext).createCancelPendingIntent(id)
        val notification: Notification = NotificationCompat.Builder(
            applicationContext,
            VideoTextTranslatorApp.CHANNEL_PROCESSING
        )
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Video Text Translator • $title")
            .setContentText(detail)
            .setProgress(100, progressPct.coerceIn(0, 100), progressPct <= 0)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Roko", cancelIntent)
            .build()

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    companion object {
        const val KEY_PROJECT_ID = "project_id"
        const val KEY_RESUME = "resume"
        const val KEY_PROGRESS = "progress"
        const val KEY_PROCESSED_MS = "processed_ms"
        const val KEY_TOTAL_MS = "total_ms"
        const val KEY_ETA_SECONDS = "eta_seconds"
        const val KEY_STAGE = "stage"
        const val KEY_FOUND_COUNT = "found_count"
        const val KEY_ERROR = "error"
        private const val NOTIFICATION_ID = 4401
    }
}

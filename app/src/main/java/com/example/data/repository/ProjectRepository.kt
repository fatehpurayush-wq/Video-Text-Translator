package com.example.data.repository

import com.example.data.local.GlossaryDao
import com.example.data.local.GlossaryRuleEntity
import com.example.data.local.ProjectDao
import com.example.data.local.ProjectEntity
import com.example.data.local.SegmentDao
import com.example.data.local.SegmentEntity
import com.example.data.local.TranslationDao
import com.example.data.local.TranslationEntity
import com.example.domain.model.NormalizedRect
import com.example.domain.model.SegmentWithTranslation
import com.example.domain.model.TranslationStyle
import com.example.engine.TranslationEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class ProjectRepository(
    private val projectDao: ProjectDao,
    private val segmentDao: SegmentDao,
    private val translationDao: TranslationDao,
    private val glossaryDao: GlossaryDao,
    private val translationEngine: TranslationEngine
) {
    private val json = Json { ignoreUnknownKeys = true }

    val allProjectsFlow: Flow<List<ProjectEntity>> = projectDao.observeAllProjects()
    val allGlossaryFlow: Flow<List<GlossaryRuleEntity>> = glossaryDao.observeAllRules()

    fun observeProject(projectId: Long): Flow<ProjectEntity?> = projectDao.observeProject(projectId)

    fun observeSegmentsWithTranslation(
        projectId: Long,
        targetLangCode: String
    ): Flow<List<SegmentWithTranslation>> {
        return combine(
            segmentDao.observeSegments(projectId),
            translationDao.observeTranslations(projectId, targetLangCode)
        ) { segments, translations ->
            val transMap = translations.associateBy { it.segmentId }
            segments.map { seg ->
                val trans = transMap[seg.id]
                val unsure = seg.unsureWordIndicesCsv
                    .split(",")
                    .mapNotNull { it.trim().toIntOrNull() }
                    .toSet()
                val box = seg.boundingBoxJson?.let {
                    runCatching { json.decodeFromString<NormalizedRect>(it) }.getOrNull()
                }
                val style = trans?.styleApplied?.let {
                    runCatching { TranslationStyle.valueOf(it) }.getOrDefault(TranslationStyle.SIMPLE)
                } ?: TranslationStyle.SIMPLE

                SegmentWithTranslation(
                    id = seg.id,
                    projectId = seg.projectId,
                    orderIndex = seg.orderIndex,
                    startTimeMs = seg.startTimeMs,
                    endTimeMs = seg.endTimeMs,
                    originalText = seg.originalText,
                    detectedLangCode = seg.detectedLangCode,
                    sourceType = seg.sourceType,
                    confidence = seg.confidence,
                    unsureWordIndices = unsure,
                    boundingBox = box,
                    isModified = seg.isModified,
                    translatedText = trans?.translatedText ?: "",
                    romanText = trans?.romanText,
                    usedPivotEnglish = trans?.usedPivotEnglish ?: false,
                    styleApplied = style
                )
            }
        }
    }

    suspend fun getProjectOnce(projectId: Long): ProjectEntity? = projectDao.getProjectById(projectId)

    suspend fun createOrUpdateProject(project: ProjectEntity): Long {
        val now = System.currentTimeMillis()
        return if (project.id == 0L) {
            projectDao.insertProject(project.copy(createdAt = now, updatedAt = now))
        } else {
            projectDao.updateProject(project.copy(updatedAt = now))
            project.id
        }
    }

    suspend fun renameProject(projectId: Long, newTitle: String) {
        val existing = projectDao.getProjectById(projectId) ?: return
        projectDao.updateProject(existing.copy(title = newTitle.trim(), updatedAt = System.currentTimeMillis()))
    }

    suspend fun deleteProject(projectId: Long) {
        projectDao.deleteProjectById(projectId)
    }

    suspend fun duplicateProject(projectId: Long): Long = withContext(Dispatchers.IO) {
        val original = projectDao.getProjectById(projectId) ?: return@withContext 0L
        val now = System.currentTimeMillis()
        val newProjId = projectDao.insertProject(
            original.copy(
                id = 0L,
                title = "${original.title} (Copy)",
                createdAt = now,
                updatedAt = now
            )
        )
        val oldSegs = segmentDao.getSegmentsOnce(projectId)
        val oldTrans = translationDao.getAllForProjectOnce(projectId).groupBy { it.segmentId }

        for (seg in oldSegs) {
            val newSegId = segmentDao.insertSegment(seg.copy(id = 0L, projectId = newProjId))
            val transForSeg = oldTrans[seg.id].orEmpty().map {
                it.copy(id = 0L, segmentId = newSegId, projectId = newProjId)
            }
            if (transForSeg.isNotEmpty()) {
                translationDao.upsertTranslations(transForSeg)
            }
        }
        newProjId
    }

    suspend fun getSegmentsOnce(projectId: Long): List<SegmentEntity> = segmentDao.getSegmentsOnce(projectId)

    suspend fun updateSegmentContent(
        segmentId: Long,
        projectId: Long,
        targetLangCode: String,
        newOriginalText: String,
        newTranslatedText: String,
        newRomanText: String?,
        newStartMs: Long,
        newEndMs: Long
    ) {
        val seg = segmentDao.getSegmentsOnce(projectId).firstOrNull { it.id == segmentId } ?: return
        segmentDao.updateSegment(
            seg.copy(
                originalText = newOriginalText,
                startTimeMs = newStartMs.coerceAtLeast(0L),
                endTimeMs = newEndMs.coerceAtLeast(newStartMs + 200L),
                isModified = true
            )
        )
        val existingTrans = translationDao.getForProjectAndLangOnce(projectId, targetLangCode)
            .firstOrNull { it.segmentId == segmentId }
        translationDao.upsertTranslation(
            TranslationEntity(
                id = existingTrans?.id ?: 0L,
                segmentId = segmentId,
                projectId = projectId,
                targetLangCode = targetLangCode,
                translatedText = newTranslatedText,
                romanText = newRomanText,
                usedPivotEnglish = existingTrans?.usedPivotEnglish ?: false,
                styleApplied = existingTrans?.styleApplied ?: TranslationStyle.SIMPLE.name
            )
        )
        touchProject(projectId)
    }

    suspend fun retranslateSegment(
        projectId: Long,
        segmentId: Long,
        sourceLangCode: String,
        targetLangCode: String,
        style: TranslationStyle,
        includeRomanScript: Boolean
    ): String? {
        val seg = segmentDao.getSegmentsOnce(projectId).firstOrNull { it.id == segmentId } ?: return "Row nahi mili"
        val rules = glossaryDao.getAllRulesOnce()
        val outcome = translationEngine.translateSingle(
            text = seg.originalText,
            sourceLangCode = if (sourceLangCode == "auto") seg.detectedLangCode else sourceLangCode,
            targetLangCode = targetLangCode,
            style = style,
            includeRomanScript = includeRomanScript,
            glossaryRules = rules
        )
        if (outcome.errorHinglish != null) return outcome.errorHinglish

        val existingTrans = translationDao.getForProjectAndLangOnce(projectId, targetLangCode)
            .firstOrNull { it.segmentId == segmentId }
        translationDao.upsertTranslation(
            TranslationEntity(
                id = existingTrans?.id ?: 0L,
                segmentId = segmentId,
                projectId = projectId,
                targetLangCode = targetLangCode,
                translatedText = outcome.translatedText,
                romanText = outcome.romanText,
                usedPivotEnglish = outcome.usedPivotEnglish,
                styleApplied = style.name
            )
        )
        touchProject(projectId)
        return null
    }

    /**
     * Translates all segments of an existing project into a newly added target language
     * WITHOUT re-reading the video!
     */
    suspend fun translateProjectSegmentsToLanguage(
        projectId: Long,
        targetLangCode: String,
        style: TranslationStyle,
        includeRomanScript: Boolean
    ): String? {
        val project = projectDao.getProjectById(projectId) ?: return "Project nahi mila"
        val segments = segmentDao.getSegmentsOnce(projectId)
        if (segments.isEmpty()) return "Is project mein koi text rows nahi hain"

        val srcLang = if (project.isAutoDetectSource) {
            project.detectedLangCode ?: segments.first().detectedLangCode
        } else {
            project.sourceLangCode
        }

        val rules = glossaryDao.getAllRulesOnce()
        val outcomes = translationEngine.translateBatch(
            texts = segments.map { it.originalText },
            sourceLangCode = srcLang,
            targetLangCode = targetLangCode,
            style = style,
            includeRomanScript = includeRomanScript,
            glossaryRules = rules
        )

        val firstError = outcomes.firstOrNull { it.errorHinglish != null }?.errorHinglish
        if (firstError != null) return firstError

        val existingMap = translationDao.getForProjectAndLangOnce(projectId, targetLangCode).associateBy { it.segmentId }
        val toSave = segments.mapIndexed { idx, seg ->
            val out = outcomes[idx]
            TranslationEntity(
                id = existingMap[seg.id]?.id ?: 0L,
                segmentId = seg.id,
                projectId = projectId,
                targetLangCode = targetLangCode,
                translatedText = out.translatedText,
                romanText = out.romanText,
                usedPivotEnglish = out.usedPivotEnglish,
                styleApplied = style.name
            )
        }
        translationDao.upsertTranslations(toSave)

        val updatedTargets = (project.targetLangCodesCsv.split(",").filter { it.isNotBlank() } + targetLangCode)
            .distinct()
            .joinToString(",")
        projectDao.updateProject(
            project.copy(
                targetLangCodesCsv = updatedTargets,
                activeTargetLangCode = targetLangCode,
                updatedAt = System.currentTimeMillis()
            )
        )
        return null
    }

    suspend fun addRow(
        projectId: Long,
        afterOrderIndex: Int,
        startTimeMs: Long,
        endTimeMs: Long,
        detectedLangCode: String,
        targetLangCode: String,
        originalText: String,
        translatedText: String
    ) {
        val current = segmentDao.getSegmentsOnce(projectId)
        val insertOrder = afterOrderIndex + 1
        current.filter { it.orderIndex >= insertOrder }.forEach {
            segmentDao.updateSegment(it.copy(orderIndex = it.orderIndex + 1))
        }
        val segId = segmentDao.insertSegment(
            SegmentEntity(
                projectId = projectId,
                orderIndex = insertOrder,
                startTimeMs = startTimeMs,
                endTimeMs = endTimeMs,
                originalText = originalText,
                detectedLangCode = detectedLangCode,
                sourceType = "MANUAL",
                confidence = 1f,
                isModified = true
            )
        )
        translationDao.upsertTranslation(
            TranslationEntity(
                segmentId = segId,
                projectId = projectId,
                targetLangCode = targetLangCode,
                translatedText = translatedText
            )
        )
        touchProject(projectId)
    }

    suspend fun deleteRow(projectId: Long, segmentId: Long) {
        segmentDao.deleteSegmentById(segmentId)
        reindexSegments(projectId)
        touchProject(projectId)
    }

    suspend fun mergeWithNextRow(projectId: Long, segmentId: Long, targetLangCode: String) {
        val list = segmentDao.getSegmentsOnce(projectId)
        val idx = list.indexOfFirst { it.id == segmentId }
        if (idx < 0 || idx >= list.lastIndex) return
        val first = list[idx]
        val second = list[idx + 1]

        val transMap = translationDao.getForProjectAndLangOnce(projectId, targetLangCode).associateBy { it.segmentId }
        val t1 = transMap[first.id]?.translatedText.orEmpty()
        val t2 = transMap[second.id]?.translatedText.orEmpty()
        val r1 = transMap[first.id]?.romanText.orEmpty()
        val r2 = transMap[second.id]?.romanText.orEmpty()

        segmentDao.updateSegment(
            first.copy(
                endTimeMs = maxOf(first.endTimeMs, second.endTimeMs),
                originalText = "${first.originalText} ${second.originalText}".trim(),
                isModified = true
            )
        )
        translationDao.upsertTranslation(
            TranslationEntity(
                id = transMap[first.id]?.id ?: 0L,
                segmentId = first.id,
                projectId = projectId,
                targetLangCode = targetLangCode,
                translatedText = "$t1 $t2".trim(),
                romanText = "$r1 $r2".trim().ifBlank { null },
                usedPivotEnglish = transMap[first.id]?.usedPivotEnglish ?: false,
                styleApplied = transMap[first.id]?.styleApplied ?: TranslationStyle.SIMPLE.name
            )
        )
        segmentDao.deleteSegmentById(second.id)
        reindexSegments(projectId)
        touchProject(projectId)
    }

    suspend fun splitRow(projectId: Long, segmentId: Long, targetLangCode: String) {
        val list = segmentDao.getSegmentsOnce(projectId)
        val idx = list.indexOfFirst { it.id == segmentId }
        if (idx < 0) return
        val seg = list[idx]
        val words = seg.originalText.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        if (words.size < 2) return

        val midWord = words.size / 2
        val part1 = words.take(midWord).joinToString(" ")
        val part2 = words.drop(midWord).joinToString(" ")
        val midTime = seg.startTimeMs + ((seg.endTimeMs - seg.startTimeMs) / 2L).coerceAtLeast(300L)

        val transMap = translationDao.getForProjectAndLangOnce(projectId, targetLangCode).associateBy { it.segmentId }
        val tWords = transMap[seg.id]?.translatedText.orEmpty().trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        val tMid = (tWords.size / 2).coerceAtLeast(if (tWords.size >= 2) 1 else tWords.size)
        val tPart1 = tWords.take(tMid).joinToString(" ")
        val tPart2 = tWords.drop(tMid).joinToString(" ")

        list.filter { it.orderIndex > seg.orderIndex }.forEach {
            segmentDao.updateSegment(it.copy(orderIndex = it.orderIndex + 1))
        }
        segmentDao.updateSegment(
            seg.copy(endTimeMs = midTime, originalText = part1, isModified = true)
        )
        transMap[seg.id]?.let {
            translationDao.upsertTranslation(it.copy(translatedText = tPart1))
        }
        val newSegId = segmentDao.insertSegment(
            seg.copy(
                id = 0L,
                orderIndex = seg.orderIndex + 1,
                startTimeMs = midTime,
                endTimeMs = maxOf(seg.endTimeMs, midTime + 400L),
                originalText = part2,
                isModified = true
            )
        )
        translationDao.upsertTranslation(
            TranslationEntity(
                segmentId = newSegId,
                projectId = projectId,
                targetLangCode = targetLangCode,
                translatedText = tPart2
            )
        )
        touchProject(projectId)
    }

    suspend fun searchAndReplace(
        projectId: Long,
        targetLangCode: String,
        findText: String,
        replaceText: String,
        inOriginal: Boolean,
        inTranslation: Boolean
    ): Int {
        if (findText.isBlank()) return 0
        var count = 0
        if (inOriginal) {
            val segs = segmentDao.getSegmentsOnce(projectId)
            for (seg in segs) {
                if (seg.originalText.contains(findText, ignoreCase = true)) {
                    count++
                    segmentDao.updateSegment(
                        seg.copy(
                            originalText = seg.originalText.replace(findText, replaceText, ignoreCase = true),
                            isModified = true
                        )
                    )
                }
            }
        }
        if (inTranslation) {
            val trans = translationDao.getForProjectAndLangOnce(projectId, targetLangCode)
            for (tr in trans) {
                if (tr.translatedText.contains(findText, ignoreCase = true)) {
                    count++
                    translationDao.upsertTranslation(
                        tr.copy(
                            translatedText = tr.translatedText.replace(findText, replaceText, ignoreCase = true)
                        )
                    )
                }
            }
        }
        if (count > 0) touchProject(projectId)
        return count
    }

    suspend fun restoreSnapshot(
        projectId: Long,
        targetLangCode: String,
        snapshotSegments: List<SegmentWithTranslation>
    ) = withContext(Dispatchers.IO) {
        val segEntities = snapshotSegments.mapIndexed { idx, s ->
            SegmentEntity(
                id = 0L,
                projectId = projectId,
                orderIndex = idx,
                startTimeMs = s.startTimeMs,
                endTimeMs = s.endTimeMs,
                originalText = s.originalText,
                detectedLangCode = s.detectedLangCode,
                sourceType = s.sourceType,
                confidence = s.confidence,
                unsureWordIndicesCsv = s.unsureWordIndices.joinToString(","),
                boundingBoxJson = s.boundingBox?.let { json.encodeToString(it) },
                isModified = s.isModified
            )
        }
        val newIds = segmentDao.replaceAllForProject(projectId, segEntities)
        val transEntities = snapshotSegments.mapIndexed { idx, s ->
            TranslationEntity(
                segmentId = newIds[idx],
                projectId = projectId,
                targetLangCode = targetLangCode,
                translatedText = s.translatedText,
                romanText = s.romanText,
                usedPivotEnglish = s.usedPivotEnglish,
                styleApplied = s.styleApplied.name
            )
        }
        translationDao.upsertTranslations(transEntities)
        touchProject(projectId)
    }

    suspend fun addGlossaryRule(sourceTerm: String, targetTerm: String, targetLangCode: String = "*", isNameOrBrand: Boolean = true) {
        if (sourceTerm.isBlank() || targetTerm.isBlank()) return
        glossaryDao.insertRule(
            GlossaryRuleEntity(
                sourceTerm = sourceTerm.trim(),
                targetTerm = targetTerm.trim(),
                targetLangCode = targetLangCode,
                isNameOrBrand = isNameOrBrand
            )
        )
    }

    suspend fun deleteGlossaryRule(id: Long) {
        glossaryDao.deleteRuleById(id)
    }

    private suspend fun reindexSegments(projectId: Long) {
        val segs = segmentDao.getSegmentsOnce(projectId)
        segs.forEachIndexed { index, seg ->
            if (seg.orderIndex != index) {
                segmentDao.updateSegment(seg.copy(orderIndex = index))
            }
        }
    }

    private suspend fun touchProject(projectId: Long) {
        projectDao.getProjectById(projectId)?.let {
            projectDao.updateProject(it.copy(updatedAt = System.currentTimeMillis()))
        }
    }
}

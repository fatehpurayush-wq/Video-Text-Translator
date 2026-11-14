package com.example.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val videoUri: String,
    val thumbnailPath: String? = null,
    val durationMs: Long = 0L,
    val videoWidth: Int = 0,
    val videoHeight: Int = 0,
    val sourceLangCode: String = "auto",
    val detectedLangCode: String? = null,
    val isAutoDetectSource: Boolean = true,
    val targetLangCodesCsv: String = "hi",
    val activeTargetLangCode: String = "hi",
    val extractionSource: String = "VIDEO_TEXT",
    val cropRectJson: String? = null,
    val translationStyle: String = "SIMPLE",
    val showRomanScript: Boolean = true,
    val lastProcessedMs: Long = 0L,
    val status: String = "DRAFT",
    val errorMessage: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "segments",
    foreignKeys = [
        ForeignKey(
            entity = ProjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["projectId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("projectId")]
)
data class SegmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val projectId: Long,
    val orderIndex: Int,
    val startTimeMs: Long,
    val endTimeMs: Long,
    val originalText: String,
    val detectedLangCode: String,
    val sourceType: String = "OCR",
    val confidence: Float = 1f,
    val unsureWordIndicesCsv: String = "",
    val boundingBoxJson: String? = null,
    val isModified: Boolean = false
)

@Entity(
    tableName = "translations",
    foreignKeys = [
        ForeignKey(
            entity = SegmentEntity::class,
            parentColumns = ["id"],
            childColumns = ["segmentId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = ProjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["projectId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("segmentId"), Index("projectId"), Index(value = ["segmentId", "targetLangCode"], unique = true)]
)
data class TranslationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val segmentId: Long,
    val projectId: Long,
    val targetLangCode: String,
    val translatedText: String,
    val romanText: String? = null,
    val usedPivotEnglish: Boolean = false,
    val styleApplied: String = "SIMPLE"
)

@Entity(tableName = "glossary_rules")
data class GlossaryRuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sourceTerm: String,
    val targetTerm: String,
    val targetLangCode: String = "*",
    val isNameOrBrand: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "language_packs")
data class LanguagePackEntity(
    @PrimaryKey val id: String, // e.g., "ocr_tel", "trans_hi", "speech_hi"
    val category: String,      // OCR, TRANSLATION, SPEECH
    val langCode: String,
    val displayName: String,
    val sizeBytes: Long,
    val downloadedBytes: Long = 0L,
    val status: String,        // BUILT_IN, NOT_DOWNLOADED, DOWNLOADING, PAUSED, READY, UNAVAILABLE, ERROR
    val accuracyRating: String, // GOOD, AVERAGE, LOW
    val localPath: String? = null,
    val errorMessage: String? = null,
    val updatedAt: Long = System.currentTimeMillis()
)

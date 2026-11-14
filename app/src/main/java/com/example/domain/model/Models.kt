package com.example.domain.model

import kotlinx.serialization.Serializable

enum class ExtractionSource(val labelHinglish: String, val subtitleHinglish: String) {
    VIDEO_TEXT("Video ke andar ka text", "Subtitles, titles, boards aur on-screen likhai"),
    SPEECH("Bole hue shabd", "Video ki aawaz se offline speech-to-text"),
    BOTH("Dono (Text + Aawaz)", "Screen likhai aur bole hue shabd ek saath")
}

enum class TranslationStyle(val labelHinglish: String, val hintHinglish: String) {
    SIMPLE("Simple", "Aasaan aur rozmarra ki bhasha"),
    FORMAL("Formal", "Shisht aur professional andaaz"),
    CASUAL("Casual", "Dostana aur natural bolchal"),
    LITERAL("Literal", "Shabd-ba-shabd kareeb translation")
}

enum class AspectRatioOption(val labelHinglish: String) {
    ORIGINAL("Original Shape"),
    VERTICAL_9_16("Vertical 9:16"),
    SQUARE_1_1("Square 1:1")
}

enum class AppThemeMode(val labelHinglish: String) {
    OBSIDIAN("Obsidian Dark"),
    AMOLED("Midnight AMOLED"),
    SLATE("Deep Slate")
}

enum class ProjectStatus(val labelHinglish: String) {
    DRAFT("Taiyar (Draft)"),
    PROCESSING("Process Ho Raha Hai"),
    PAUSED("Ruka Hua (Paused)"),
    READY("Poora Hua"),
    ERROR("Error Aaya")
}

enum class PackCategory(val labelHinglish: String) {
    OCR("Text Padhna (OCR)"),
    TRANSLATION("Translate Karna"),
    SPEECH("Bole Hue Shabd")
}

enum class PackStatus(val labelHinglish: String) {
    BUILT_IN("Pehle Se Ready"),
    READY("Downloaded"),
    DOWNLOADING("Download Ho Raha Hai"),
    PAUSED("Ruka Hua"),
    NOT_DOWNLOADED("Download Karein"),
    UNAVAILABLE("Offline Uplabdh Nahi"),
    ERROR("Download Failed")
}

enum class CaptionPosition(val labelHinglish: String) {
    AUTO_ORIGINAL_SPOT("Original Text Ki Jagah"),
    BOTTOM("Neeche (Bottom)"),
    TOP("Upar (Top)")
}

enum class ExportFormat(val labelHinglish: String, val extension: String, val mimeType: String) {
    SRT_TRANSLATED("SRT (Sirf Translation)", "srt", "application/x-subrip"),
    SRT_ORIGINAL("SRT (Sirf Original)", "srt", "application/x-subrip"),
    SRT_BILINGUAL("SRT (Dono Saath)", "srt", "application/x-subrip"),
    VTT("WebVTT (.vtt)", "vtt", "text/vtt"),
    TXT("Plain Text (.txt)", "txt", "text/plain"),
    CSV("Spreadsheet (.csv)", "csv", "text/csv"),
    XLSX("Excel (.xlsx)", "xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
    DOCX("Word (.docx)", "docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
    PDF("PDF Document (.pdf)", "pdf", "application/pdf"),
    JSON("JSON Data (.json)", "json", "application/json")
}

@Serializable
data class NormalizedRect(
    val left: Float = 0f,
    val top: Float = 0f,
    val right: Float = 1f,
    val bottom: Float = 1f
) {
    val isFullFrame: Boolean
        get() = left <= 0.02f && top <= 0.02f && right >= 0.98f && bottom >= 0.98f

    companion object {
        val FULL_FRAME = NormalizedRect(0f, 0f, 1f, 1f)
        val BOTTOM_SUBTITLE = NormalizedRect(0.06f, 0.72f, 0.94f, 0.96f)
    }
}

@Serializable
data class CaptionStyleConfig(
    val fontSizeSp: Float = 20f,
    val textColorHex: Long = 0xFFFFFFFF,
    val bgColorHex: Long = 0xFF0A0E14,
    val bgAlpha: Float = 0.85f,
    val outlineEnabled: Boolean = true,
    val outlineColorHex: Long = 0xFF000000,
    val position: CaptionPosition = CaptionPosition.AUTO_ORIGINAL_SPOT,
    val maxWordsPerLine: Int = 7,
    val showBothOriginalAndTranslation: Boolean = false,
    val aspectRatio: AspectRatioOption = AspectRatioOption.ORIGINAL,
    val boldFont: Boolean = true
)

data class SegmentWithTranslation(
    val id: Long,
    val projectId: Long,
    val orderIndex: Int,
    val startTimeMs: Long,
    val endTimeMs: Long,
    val originalText: String,
    val detectedLangCode: String,
    val sourceType: String,
    val confidence: Float,
    val unsureWordIndices: Set<Int>,
    val boundingBox: NormalizedRect?,
    val isModified: Boolean,
    val translatedText: String,
    val romanText: String?,
    val usedPivotEnglish: Boolean,
    val styleApplied: TranslationStyle
)

data class QuickTestResult(
    val detectedLangCode: String,
    val originalSample: String,
    val translatedSample: String,
    val romanSample: String?,
    val usedPivotEnglish: Boolean,
    val confidence: Float,
    val warningHinglish: String?
)

data class ProcessingProgress(
    val isRunning: Boolean = false,
    val isPaused: Boolean = false,
    val progressFraction: Float = 0f,
    val processedMs: Long = 0L,
    val totalMs: Long = 0L,
    val etaSeconds: Long = -1L,
    val stageLabelHinglish: String = "",
    val foundLinesCount: Int = 0,
    val errorMessageHinglish: String? = null
)

data class BurnInProgress(
    val isRunning: Boolean = false,
    val progressFraction: Float = 0f,
    val etaSeconds: Long = -1L,
    val outputFilePath: String? = null,
    val errorMessageHinglish: String? = null
)

data class UndoSnapshot(
    val descriptionHinglish: String,
    val segments: List<SegmentWithTranslation>
)
